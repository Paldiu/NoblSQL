package app.simplexdev.noblsql.sql.orm;

public enum Dialect {
    SQLITE,
    MYSQL,
    MARIADB,
    POSTGRESQL,
    H2;

    public String autoIncrementKeyword() {
        return switch (this) {
            case SQLITE -> "AUTOINCREMENT";
            case MYSQL, MARIADB, H2 -> "AUTO_INCREMENT";
            case POSTGRESQL -> "";
        };
    }

    public String boolType() {
        return switch (this) {
            case SQLITE -> "INTEGER";
            case MYSQL, MARIADB -> "TINYINT(1)";
            case POSTGRESQL, H2 -> "BOOLEAN";
        };
    }

    public String blobType() {
        return switch (this) {
            case SQLITE, MYSQL, MARIADB, H2 -> "BLOB";
            case POSTGRESQL -> "BYTEA";
        };
    }

    public String jsonType() {
        return switch (this) {
            case SQLITE -> "TEXT";
            case MYSQL, MARIADB, H2 -> "JSON";
            case POSTGRESQL -> "JSONB";
        };
    }

    /** Wraps an identifier in the dialect-appropriate quote characters to prevent injection via user-supplied names. */
    public String quoteIdentifier(final String name) {
        return switch (this) {
            case MYSQL, MARIADB -> "`" + name.replace("`", "``") + "`";
            case SQLITE, POSTGRESQL, H2 -> "\"" + name.replace("\"", "\"\"") + "\"";
        };
    }
}
