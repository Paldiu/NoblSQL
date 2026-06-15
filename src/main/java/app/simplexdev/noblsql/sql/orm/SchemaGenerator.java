package app.simplexdev.noblsql.sql.orm;

import app.simplexdev.noblsql.api.data.*;
import app.simplexdev.noblsql.api.data.type.*;
import app.simplexdev.noblsql.api.sql.SQLContract;
import reactor.core.publisher.Flux;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class SchemaGenerator {
    private final Dialect dialect;

    public SchemaGenerator(final Dialect dialect) {
        this.dialect = dialect;
    }

    /**
     * Generates all DDL for an entity class: one CREATE TABLE followed by any CREATE INDEX statements.
     * Feed directly into the pipeline via {@link #execute}.
     */
    public List<String> generateDdl(final Class<?> entityClass) {
        final Table table = requireTable(entityClass);
        final List<String> statements = new ArrayList<>();
        statements.add(createTable(entityClass));

        for (final Field field : entityClass.getDeclaredFields()) {
            if (!field.isAnnotationPresent(Column.class)) continue;
            if (!field.isAnnotationPresent(Indexed.class)) continue;

            final Indexed indexed = field.getAnnotation(Indexed.class);
            final String colName = columnName(field);
            final String idxName = indexed.name().isEmpty()
                ? "idx_" + table.name() + "_" + colName
                : indexed.name();
            statements.add(
                "CREATE INDEX IF NOT EXISTS " + q(idxName)
                    + " ON " + q(table.name()) + " (" + q(colName) + ")"
            );
        }
        return List.copyOf(statements);
    }

    /** Executes all generated DDL statements against the given contract and returns affected-row counts. */
    public Flux<Integer> execute(final SQLContract contract, final Class<?> entityClass) {
        return Flux.fromIterable(generateDdl(entityClass))
            .flatMap(contract::update);
    }

    public String createTable(final Class<?> entityClass) {
        final Table table = requireTable(entityClass);
        final List<String> columnDefs = new ArrayList<>();
        final List<String> fkConstraints = new ArrayList<>();

        for (final Field field : entityClass.getDeclaredFields()) {
            if (!field.isAnnotationPresent(Column.class)) continue;

            final boolean isId = field.isAnnotationPresent(Id.class) || field.isAnnotationPresent(PrimaryKey.class);
            final boolean autoInc = field.isAnnotationPresent(AutoIncrement.class);
            columnDefs.add(buildColumnDef(field, isId, autoInc));

            if (field.isAnnotationPresent(ForeignKey.class)) {
                fkConstraints.add(buildFkConstraint(columnName(field), field.getAnnotation(ForeignKey.class)));
            }
        }

        columnDefs.addAll(fkConstraints);
        return "CREATE TABLE IF NOT EXISTS " + q(table.name())
            + " (\n    " + String.join(",\n    ", columnDefs) + "\n)";
    }

    private String buildColumnDef(final Field field, final boolean isId, final boolean autoInc) {
        final StringBuilder def = new StringBuilder(q(columnName(field)))
            .append(" ")
            .append(sqlType(field, isId, autoInc));

        if (isId) {
            def.append(" PRIMARY KEY");
            final String aiKeyword = dialect.autoIncrementKeyword();
            if (autoInc && !aiKeyword.isEmpty()) {
                def.append(" ").append(aiKeyword);
            }
        } else {
            if (field.isAnnotationPresent(NotNull.class)) def.append(" NOT NULL");
            if (field.isAnnotationPresent(Unique.class)) def.append(" UNIQUE");
        }

        if (field.isAnnotationPresent(Default.class)) {
            def.append(" DEFAULT ").append(field.getAnnotation(Default.class).value());
        }

        return def.toString();
    }

    private String sqlType(final Field field, final boolean isId, final boolean autoInc) {
        if (field.isAnnotationPresent(Varchar.class)) {
            final int length = field.getAnnotation(Varchar.class).length();
            if (length <= 0) throw new IllegalArgumentException(
                "@Varchar on " + field.getDeclaringClass().getSimpleName() + "." + field.getName()
                + " must have length > 0 (got " + length + ")");
            return "VARCHAR(" + length + ")";
        }
        if (field.isAnnotationPresent(Text.class)) return "TEXT";

        if (isId && dialect == Dialect.SQLITE) return "INTEGER";

        if (field.isAnnotationPresent(Int.class)) {
            return (isId && autoInc && dialect == Dialect.POSTGRESQL) ? "SERIAL" : "INT";
        }
        if (field.isAnnotationPresent(BigInt.class)) {
            return (isId && autoInc && dialect == Dialect.POSTGRESQL) ? "BIGSERIAL" : "BIGINT";
        }
        if (field.isAnnotationPresent(Bool.class)) return dialect.boolType();
        if (field.isAnnotationPresent(Timestamp.class)) return "TIMESTAMP";
        if (field.isAnnotationPresent(Decimal.class)) {
            final Decimal d = field.getAnnotation(Decimal.class);
            if (d.precision() <= 0) throw new IllegalArgumentException(
                "@Decimal on " + field.getDeclaringClass().getSimpleName() + "." + field.getName()
                + " must have precision > 0 (got " + d.precision() + ")");
            if (d.scale() < 0) throw new IllegalArgumentException(
                "@Decimal on " + field.getDeclaringClass().getSimpleName() + "." + field.getName()
                + " must have scale >= 0 (got " + d.scale() + ")");
            return "DECIMAL(" + d.precision() + ", " + d.scale() + ")";
        }
        if (field.isAnnotationPresent(Blob.class)) return dialect.blobType();
        if (field.isAnnotationPresent(Json.class)) return dialect.jsonType();

        return inferFromJavaType(field.getType());
    }

    private String inferFromJavaType(final Class<?> type) {
        if (type == String.class) return "TEXT";
        if (type == int.class || type == Integer.class) return "INT";
        if (type == long.class || type == Long.class) return "BIGINT";
        if (type == boolean.class || type == Boolean.class) return dialect.boolType();
        if (type == double.class || type == Double.class) return "DOUBLE";
        if (type == float.class || type == Float.class) return "FLOAT";
        if (type == byte[].class) return dialect.blobType();
        return "TEXT";
    }

    private String buildFkConstraint(final String colName, final ForeignKey fk) {
        return "FOREIGN KEY (" + q(colName) + ") REFERENCES " + q(fk.table()) + "(" + q(fk.column()) + ")"
            + " ON DELETE " + fk.onDelete().sql()
            + " ON UPDATE " + fk.onUpdate().sql();
    }

    private String q(final String identifier) {
        return dialect.quoteIdentifier(identifier);
    }

    private static String columnName(final Field field) {
        final String name = field.getAnnotation(Column.class).name();
        return name.isEmpty() ? field.getName() : name;
    }

    private static Table requireTable(final Class<?> entityClass) {
        final Table table = entityClass.getAnnotation(Table.class);
        if (table == null) throw new IllegalArgumentException(entityClass.getName() + " must be annotated with @Table");
        return table;
    }
}
