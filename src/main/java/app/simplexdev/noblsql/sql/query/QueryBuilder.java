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
        conditions.add(new Condition(column, operator, value, false));
        return this;
    }

    /** Adds an OR condition: {@code OR column operator ?}. */
    public QueryBuilder<T> orWhere(final String column, final String operator, final Object value) {
        conditions.add(new Condition(column, operator, value, true));
        return this;
    }

    /** Adds an AND {@code IN (?, ?, ...)} condition. An empty collection produces {@code 1=0} (always false). */
    public QueryBuilder<T> whereIn(final String column, final Collection<?> values) {
        if (values.isEmpty()) {
            conditions.add(new Condition(column, "__EMPTY_IN__", null, false));
        } else {
            conditions.add(new Condition(column, "IN", new ArrayList<>(values), false));
        }
        return this;
    }

    /** Adds an OR {@code IN (?, ?, ...)} condition. */
    public QueryBuilder<T> orWhereIn(final String column, final Collection<?> values) {
        if (values.isEmpty()) {
            conditions.add(new Condition(column, "__EMPTY_IN__", null, true));
        } else {
            conditions.add(new Condition(column, "IN", new ArrayList<>(values), true));
        }
        return this;
    }

    /** Adds an AND {@code IS NULL} condition. */
    public QueryBuilder<T> whereNull(final String column) {
        conditions.add(new Condition(column, "IS NULL", null, false));
        return this;
    }

    /** Adds an AND {@code IS NOT NULL} condition. */
    public QueryBuilder<T> whereNotNull(final String column) {
        conditions.add(new Condition(column, "IS NOT NULL", null, false));
        return this;
    }

    public QueryBuilder<T> orderBy(final String column, final Order direction) {
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

    private record Condition(String column, String operator, Object value, boolean or) {}

    private record OrderClause(String column, Order direction) {}

    private record Built(String sql, Object[] params) {}
}
