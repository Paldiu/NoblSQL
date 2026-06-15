package app.simplexdev.noblsql.sql.orm;

import app.simplexdev.noblsql.api.data.Column;
import app.simplexdev.noblsql.api.data.Handles;
import app.simplexdev.noblsql.api.data.Id;
import app.simplexdev.noblsql.api.data.PrimaryKey;
import app.simplexdev.noblsql.api.data.Table;
import app.simplexdev.noblsql.api.data.TypeHandler;
import app.simplexdev.noblsql.api.sql.ResultSetMapper;
import app.simplexdev.noblsql.util.SQLUtils;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class EntityMapper<T> {
    private final Class<T> entityClass;
    private final String tableName;
    private final List<Field> idFields;
    private final List<Field> columnFields;
    private final Map<Field, TypeHandler<?>> handlers;
    private final Dialect dialect;

    public EntityMapper(final Class<T> entityClass, final Dialect dialect) {
        this.entityClass = entityClass;
        this.dialect = dialect;

        final Table table = entityClass.getAnnotation(Table.class);
        if (table == null) {
            throw new IllegalArgumentException(entityClass.getName() + " must be annotated with @Table");
        }
        this.tableName = table.name();

        final List<Field> ids = new ArrayList<>();
        final List<Field> cols = new ArrayList<>();
        final Map<Field, TypeHandler<?>> hdls = new HashMap<>();

        for (final Field field : entityClass.getDeclaredFields()) {
            if (!field.isAnnotationPresent(Column.class)) continue;
            field.setAccessible(true);
            cols.add(field);
            if (field.isAnnotationPresent(Id.class) || field.isAnnotationPresent(PrimaryKey.class)) ids.add(field);
            if (field.isAnnotationPresent(Handles.class)) {
                hdls.put(field, instantiateHandler(field.getAnnotation(Handles.class)));
            }
        }

        if (ids.isEmpty()) {
            throw new IllegalArgumentException(
                entityClass.getName() + " must have at least one field annotated with @Id and @Column");
        }
        this.idFields = List.copyOf(ids);
        this.columnFields = List.copyOf(cols);
        this.handlers = Map.copyOf(hdls);
    }

    public String tableName() {
        return tableName;
    }

    public Dialect dialect() {
        return dialect;
    }

    public int idCount() {
        return idFields.size();
    }

    public String selectAll() {
        return "SELECT * FROM " + q(tableName);
    }

    public String selectById() {
        return "SELECT * FROM " + q(tableName) + " WHERE " + idWhereClause();
    }

    public String insert() {
        final List<String> cols = new ArrayList<>();
        for (final Field f : columnFields) {
            if (!idFields.contains(f)) cols.add(q(columnName(f)));
        }
        return "INSERT INTO " + q(tableName)
            + " (" + String.join(", ", cols) + ")"
            + " VALUES (" + SQLUtils.placeholders(cols.size()) + ")";
    }

    public String insertWithId() {
        final List<String> cols = new ArrayList<>();
        for (final Field f : columnFields) cols.add(q(columnName(f)));
        return "INSERT INTO " + q(tableName)
            + " (" + String.join(", ", cols) + ")"
            + " VALUES (" + SQLUtils.placeholders(cols.size()) + ")";
    }

    public String updateById() {
        final List<String> sets = new ArrayList<>();
        for (final Field f : columnFields) {
            if (!idFields.contains(f)) sets.add(q(columnName(f)) + " = ?");
        }
        return "UPDATE " + q(tableName)
            + " SET " + String.join(", ", sets)
            + " WHERE " + idWhereClause();
    }

    public String deleteById() {
        return "DELETE FROM " + q(tableName) + " WHERE " + idWhereClause();
    }

    /**
     * Generates a dialect-specific upsert (insert-or-update) statement.
     * Uses all columns including id fields. Pair with {@link #valuesWithIdFor}.
     * <ul>
     *   <li>MySQL / MariaDB — {@code ON DUPLICATE KEY UPDATE}</li>
     *   <li>PostgreSQL / H2 / SQLite — {@code ON CONFLICT (...) DO UPDATE SET}</li>
     * </ul>
     */
    public String upsert() {
        final List<String> allCols = columnFields.stream()
            .map(f -> q(columnName(f)))
            .toList();
        final List<String> nonIdCols = columnFields.stream()
            .filter(f -> !idFields.contains(f))
            .map(f -> q(columnName(f)))
            .toList();

        final String base = "INSERT INTO " + q(tableName)
            + " (" + String.join(", ", allCols) + ")"
            + " VALUES (" + SQLUtils.placeholders(allCols.size()) + ")";

        return switch (dialect) {
            case MYSQL, MARIADB -> {
                final String updates = nonIdCols.stream()
                    .map(c -> c + " = VALUES(" + c + ")")
                    .collect(Collectors.joining(", "));
                yield base + " ON DUPLICATE KEY UPDATE " + updates;
            }
            case POSTGRESQL, H2, SQLITE -> {
                final String conflictCols = idFields.stream()
                    .map(f -> q(columnName(f)))
                    .collect(Collectors.joining(", "));
                final String updates = nonIdCols.stream()
                    .map(c -> c + " = EXCLUDED." + c)
                    .collect(Collectors.joining(", "));
                yield base + " ON CONFLICT (" + conflictCols + ") DO UPDATE SET " + updates;
            }
        };
    }

    /** Values for all non-id columns, in column declaration order. Use with {@link #insert()}. */
    public Object[] valuesFor(final T entity) {
        final List<Object> values = new ArrayList<>();
        for (final Field f : columnFields) {
            if (!idFields.contains(f)) values.add(toSql(f, entity));
        }
        return values.toArray();
    }

    /** Values for all columns including ids. Use with {@link #insertWithId()} or {@link #upsert()}. */
    public Object[] valuesWithIdFor(final T entity) {
        final List<Object> values = new ArrayList<>();
        for (final Field f : columnFields) values.add(toSql(f, entity));
        return values.toArray();
    }

    /** Non-id values followed by id values. Use with {@link #updateById()}. */
    public Object[] valuesForUpdate(final T entity) {
        final List<Object> values = new ArrayList<>();
        for (final Field f : columnFields) {
            if (!idFields.contains(f)) values.add(toSql(f, entity));
        }
        for (final Field f : idFields) values.add(toSql(f, entity));
        return values.toArray();
    }

    /** Id field values in declaration order. Use with {@link #selectById()} / {@link #deleteById()}. */
    public Object[] idFor(final T entity) {
        final List<Object> vals = new ArrayList<>();
        for (final Field f : idFields) vals.add(toSql(f, entity));
        return vals.toArray();
    }

    /**
     * Returns a {@link ResultSetMapper} that reads columns and applies TypeHandlers inline
     * while the ResultSet is open. Runs on the I/O thread — use only when no TypeHandler
     * calls Bukkit API.
     */
    public ResultSetMapper<T> mapper() {
        return rs -> {
            try {
                final java.lang.reflect.Constructor<T> ctor = entityClass.getDeclaredConstructor();
                ctor.setAccessible(true);
                final T instance = ctor.newInstance();
                for (final Field f : columnFields) {
                    final Object raw = rs.getObject(columnName(f));
                    f.set(instance, fromSql(f, raw));
                }
                return instance;
            } catch (final ReflectiveOperationException e) {
                throw new SQLException("Failed to map ResultSet to " + entityClass.getName(), e);
            }
        };
    }

    /**
     * Maps a pre-snapshotted row (column_label → raw value) to an entity instance.
     * Call this after {@code publishOn(BukkitSchedulers.mainThread())} so TypeHandlers
     * that invoke Bukkit API run on the correct thread.
     */
    public T fromRawRow(final Map<String, Object> row) {
        try {
            final java.lang.reflect.Constructor<T> ctor = entityClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            final T instance = ctor.newInstance();
            for (final Field f : columnFields) {
                final Object raw = row.get(columnName(f).toLowerCase(Locale.ROOT));
                f.set(instance, fromSql(f, raw));
            }
            return instance;
        } catch (final ReflectiveOperationException e) {
            throw new RuntimeException("Failed to map row to " + entityClass.getName(), e);
        }
    }

    private String idWhereClause() {
        return idFields.stream()
            .map(f -> q(columnName(f)) + " = ?")
            .collect(Collectors.joining(" AND "));
    }

    private String columnName(final Field field) {
        final String name = field.getAnnotation(Column.class).name();
        return name.isEmpty() ? field.getName() : name;
    }

    private String q(final String identifier) {
        return dialect.quoteIdentifier(identifier);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object toSql(final Field field, final Object entity) {
        try {
            final Object value = field.get(entity);
            final TypeHandler handler = handlers.get(field);
            return handler != null ? handler.toSql(value) : value;
        } catch (final IllegalAccessException e) {
            throw new RuntimeException("Cannot read field " + field.getName(), e);
        }
    }

    @SuppressWarnings("rawtypes")
    private Object fromSql(final Field field, final Object raw) {
        final TypeHandler handler = handlers.get(field);
        return handler != null ? handler.fromSql(raw) : raw;
    }

    private static TypeHandler<?> instantiateHandler(final Handles handles) {
        try {
            return handles.value().getDeclaredConstructor().newInstance();
        } catch (final ReflectiveOperationException e) {
            throw new RuntimeException("Cannot instantiate TypeHandler " + handles.value().getName(), e);
        }
    }
}
