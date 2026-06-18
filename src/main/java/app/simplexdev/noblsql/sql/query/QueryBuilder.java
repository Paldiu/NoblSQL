package app.simplexdev.noblsql.sql.query;

import app.simplexdev.noblsql.api.sql.SQLContract;
import app.simplexdev.noblsql.bukkit.BukkitSchedulers;
import app.simplexdev.noblsql.sql.orm.EntityMapper;
import app.simplexdev.noblsql.util.SQLUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fluent, parameterized SELECT / DELETE builder tied to a specific entity table.
 * Obtain instances via {@link app.simplexdev.noblsql.NoblRepository#query()}.
 *
 * <p>All column names are passed through {@code Dialect.quoteIdentifier()} before
 * being embedded in SQL, and all values are bound as prepared-statement parameters.
 *
 * <p>Threading: terminal methods use the two-phase read pipeline — raw data is fetched
 * on {@code boundedElastic} and entity mapping runs on the Bukkit main thread.
 */
public final class QueryBuilder<T> {

    private static final Set<String> ALLOWED_OPERATORS = Set.of(
        "=", "!=", "<>", ">", "<", ">=", "<=", "LIKE", "NOT LIKE"
    );

    private static final int MAX_COLUMN_LENGTH = 128;

    private final EntityMapper<T> mapper;
    private final SQLContract contract;

    private final List<Condition> conditions = new ArrayList<>();
    private final List<OrderClause> orders = new ArrayList<>();
    private Integer limitVal = null;
    private Integer offsetVal = null;

    public QueryBuilder(final EntityMapper<T> mapper, final SQLContract contract) {
        this.mapper = mapper;
        this.contract = contract;
    }

    /**
     * Adds an AND condition: {@code column operator ?}.
     * Supported operators: {@code =}, {@code !=}, {@code <>}, {@code >}, {@code <},
     * {@code >=}, {@code <=}, {@code LIKE}, {@code NOT LIKE}.
     */
    public QueryBuilder<T> where(final String column, final String operator, final Object value) {
        validateColumn(column);
        if (!ALLOWED_OPERATORS.contains(operator.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unsupported operator: " + operator);
        }
        conditions.add(new Condition(column, operator, value, false));
        return this;
    }

    /** Adds an OR condition: {@code OR column operator ?}. */
    public QueryBuilder<T> orWhere(final String column, final String operator, final Object value) {
        validateColumn(column);
        if (!ALLOWED_OPERATORS.contains(operator.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unsupported operator: " + operator);
        }
        conditions.add(new Condition(column, operator, value, true));
        return this;
    }

    /** Adds an AND {@code IN (?, ?, ...)} condition. An empty collection produces {@code 1=0} (always false). */
    public QueryBuilder<T> whereIn(final String column, final Collection<?> values) {
        validateColumn(column);
        if (values.isEmpty()) {
            conditions.add(new Condition(column, "__EMPTY_IN__", null, false));
        } else {
            conditions.add(new Condition(column, "IN", new ArrayList<>(values), false));
        }
        return this;
    }

    /** Adds an OR {@code IN (?, ?, ...)} condition. */
    public QueryBuilder<T> orWhereIn(final String column, final Collection<?> values) {
        validateColumn(column);
        if (values.isEmpty()) {
            conditions.add(new Condition(column, "__EMPTY_IN__", null, true));
        } else {
            conditions.add(new Condition(column, "IN", new ArrayList<>(values), true));
        }
        return this;
    }

    /** Adds an AND {@code IS NULL} condition. */
    public QueryBuilder<T> whereNull(final String column) {
        validateColumn(column);
        conditions.add(new Condition(column, "IS NULL", null, false));
        return this;
    }

    /** Adds an AND {@code IS NOT NULL} condition. */
    public QueryBuilder<T> whereNotNull(final String column) {
        validateColumn(column);
        conditions.add(new Condition(column, "IS NOT NULL", null, false));
        return this;
    }

    public QueryBuilder<T> orderBy(final String column, final Order direction) {
        validateColumn(column);
        orders.add(new OrderClause(column, direction));
        return this;
    }

    public QueryBuilder<T> limit(final int n) {
        this.limitVal = n;
        return this;
    }

    public QueryBuilder<T> offset(final int n) {
        this.offsetVal = n;
        return this;
    }

    /** Returns all matching rows mapped to entity instances. */
    public Flux<T> findAll() {
        final Built built = buildSelect(true);
        return contract.queryManyRaw(built.sql(), built.params())
            .publishOn(BukkitSchedulers.mainThread())
            .map(mapper::fromRawRow);
    }

    /** Returns the first matching row, or empty if none. Adds {@code LIMIT 1} when no limit is set. */
    public Mono<T> findFirst() {
        final Built built = buildSelect(true);
        final String sql = limitVal == null ? built.sql() + " LIMIT 1" : built.sql();
        return contract.queryRaw(sql, built.params())
            .publishOn(BukkitSchedulers.mainThread())
            .mapNotNull(mapper::fromRawRow);
    }

    /** Executes {@code SELECT COUNT(*)} with the current WHERE conditions. */
    public Mono<Long> count() {
        final Built built = buildFrom("SELECT COUNT(*) FROM " + q(mapper.tableName()), false);
        return contract.query(built.sql(), rs -> rs.getLong(1), built.params());
    }

    /**
     * Runs a paginated SELECT: issues {@code SELECT COUNT(*)} and a bounded {@code SELECT}
     * in parallel and returns a {@link Page} containing both the current page of items and
     * the total row count across all pages.
     *
     * <p>Any {@code limit}/{@code offset} set on the builder before calling this method are
     * ignored; the page and size parameters control the window instead.
     *
     * @param page zero-based page index (0 = first page)
     * @param size rows per page; must be ≥ 1
     */
    public Mono<Page<T>> paginate(final int page, final int size) {
        if (page < 0)  throw new IllegalArgumentException("page must be >= 0");
        if (size <= 0) throw new IllegalArgumentException("size must be > 0");

        final Built countBuilt = buildFrom("SELECT COUNT(*) FROM " + q(mapper.tableName()), false);
        final Mono<Long> total = contract.query(countBuilt.sql(), rs -> rs.getLong(1), countBuilt.params());

        final Built dataBuilt = buildPageSelect(page, size);
        final Flux<T> items = contract.queryManyRaw(dataBuilt.sql(), dataBuilt.params())
            .publishOn(BukkitSchedulers.mainThread())
            .map(mapper::fromRawRow);

        return Mono.zip(items.collectList(), total)
            .map(t -> new Page<>(t.getT1(), t.getT2(), page, size));
    }

    /**
     * Executes {@code DELETE FROM table WHERE ...} with the current conditions.
     * With no conditions this deletes every row in the table — use with care.
     */
    public Mono<Integer> deleteAll() {
        final Built built = buildFrom("DELETE FROM " + q(mapper.tableName()), false);
        return contract.update(built.sql(), built.params());
    }

    /**
     * Executes {@code UPDATE table SET column = ? WHERE ...}.
     * With no conditions this updates every row in the table.
     */
    public Mono<Integer> updateSet(final String column, final Object value) {
        validateColumn(column);
        final List<Object> params = new ArrayList<>();
        params.add(value);

        final StringBuilder sql = new StringBuilder("UPDATE ")
            .append(q(mapper.tableName()))
            .append(" SET ").append(q(column)).append(" = ?");
        appendWhere(sql, params);

        return contract.update(sql.toString(), params.toArray());
    }

    private Built buildSelect(final boolean includeOrderLimitOffset) {
        return buildFrom("SELECT * FROM " + q(mapper.tableName()), includeOrderLimitOffset);
    }

    private Built buildPageSelect(final int page, final int size) {
        final StringBuilder sql = new StringBuilder("SELECT * FROM " + q(mapper.tableName()));
        final List<Object> params = new ArrayList<>();
        appendWhere(sql, params);
        appendOrder(sql);
        sql.append(" LIMIT ").append(size).append(" OFFSET ").append((long) page * size);
        return new Built(sql.toString(), params.toArray());
    }

    private Built buildFrom(final String prefix, final boolean includeOrderLimitOffset) {
        final StringBuilder sql = new StringBuilder(prefix);
        final List<Object> params = new ArrayList<>();

        appendWhere(sql, params);
        if (includeOrderLimitOffset) {
            appendOrder(sql);
            appendLimitOffset(sql);
        }

        return new Built(sql.toString(), params.toArray());
    }

    private void appendWhere(final StringBuilder sql, final List<Object> params) {
        if (conditions.isEmpty()) return;
        sql.append(" WHERE ");
        for (int i = 0; i < conditions.size(); i++) {
            final Condition c = conditions.get(i);
            if (i > 0) sql.append(c.or() ? " OR " : " AND ");

            switch (c.operator()) {
                case "IS NULL"     -> sql.append(q(c.column())).append(" IS NULL");
                case "IS NOT NULL" -> sql.append(q(c.column())).append(" IS NOT NULL");
                case "__EMPTY_IN__"-> sql.append("1=0");
                case "IN" -> {
                    @SuppressWarnings("unchecked")
                    final Collection<Object> vals = (Collection<Object>) c.value();
                    sql.append(q(c.column())).append(" IN (")
                        .append(SQLUtils.placeholders(vals.size())).append(")");
                    params.addAll(vals);
                }
                default -> {
                    sql.append(q(c.column())).append(" ").append(c.operator()).append(" ?");
                    params.add(c.value());
                }
            }
        }
    }

    private void appendOrder(final StringBuilder sql) {
        if (orders.isEmpty()) return;
        sql.append(" ORDER BY ");
        sql.append(orders.stream()
            .map(o -> q(o.column()) + " " + o.direction().name())
            .collect(Collectors.joining(", ")));
    }

    private void appendLimitOffset(final StringBuilder sql) {
        if (limitVal != null) sql.append(" LIMIT ").append(limitVal);
        if (offsetVal != null) sql.append(" OFFSET ").append(offsetVal);
    }

    private String q(final String identifier) {
        return mapper.dialect().quoteIdentifier(identifier);
    }

    /**
     * Rejects column names that contain control characters or exceed the max length.
     * Quoting via {@link app.simplexdev.noblsql.sql.orm.Dialect#quoteIdentifier} already
     * neutralises embedded quote chars; this check adds defense-in-depth against NUL bytes,
     * newlines, and other non-printable characters that quoting does not remove.
     */
    private static void validateColumn(final String column) {
        if (column == null || column.isEmpty()) {
            throw new IllegalArgumentException("Column name must not be null or empty");
        }
        if (column.length() > MAX_COLUMN_LENGTH) {
            throw new IllegalArgumentException(
                "Column name exceeds max length of " + MAX_COLUMN_LENGTH + ": '" + column + "'");
        }
        for (int i = 0; i < column.length(); i++) {
            final char c = column.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                throw new IllegalArgumentException(
                    "Column name contains illegal control character at index " + i + ": '" + column + "'");
            }
        }
    }

    private record Condition(String column, String operator, Object value, boolean or) {}

    private record OrderClause(String column, Order direction) {}

    private record Built(String sql, Object[] params) {}
}
