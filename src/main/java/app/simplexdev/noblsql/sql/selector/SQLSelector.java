package app.simplexdev.noblsql.sql.selector;

import app.simplexdev.noblsql.api.sql.SQLContract;
import app.simplexdev.noblsql.sql.h2.H2;
import app.simplexdev.noblsql.sql.mariadb.MariaDB;
import app.simplexdev.noblsql.sql.mysql.MySQL;
import app.simplexdev.noblsql.sql.orm.Dialect;
import app.simplexdev.noblsql.sql.pgsql.PostgreSQL;
import app.simplexdev.noblsql.sql.shared.ConnectionDetails;
import app.simplexdev.noblsql.sql.sqlite.SQLite;
import app.simplexdev.noblsql.util.NoblLogger;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.Locale;
import java.util.Objects;

public final class SQLSelector {
    private SQLSelector() {}

    public static SQLContract contractFromConfig(final FileConfiguration config, final File dataFolder) {
        if (config.getBoolean("use_internal", false)) {
            final String password = config.getString("internal.password", "");
            if (password == null || password.isEmpty()) {
                throw new IllegalStateException(
                    "internal.password must not be empty when use_internal is true. "
                    + "An empty password leaves the embedded H2 server unauthenticated. "
                    + "Set a non-empty value in config.yml under internal.password."
                );
            }
            return new H2(
                "localhost",
                config.getInt("internal.port", 9092),
                Objects.requireNonNullElse(config.getString("internal.database", "noblsql"), "noblsql"),
                Objects.requireNonNullElse(config.getString("internal.username", "noblsql"), "noblsql"),
                password,
                config.getInt("internal.pool_size", 10)
            );
        }

        return switch (Objects.requireNonNullElse(config.getString("sql_type", "sqlite"), "sqlite").toLowerCase(Locale.ROOT)) {
            case "mysql"                           -> new MySQL(warnedDetails(config));
            case "mariadb"                         -> new MariaDB(warnedDetails(config));
            case "postgresql", "pgsql", "postgres" -> new PostgreSQL(warnedDetails(config));
            default                                -> new SQLite(new File(dataFolder, "noblsql.db"));
        };
    }

    public static Dialect dialectFromConfig(final FileConfiguration config) {
        if (config.getBoolean("use_internal", false)) {
            return Dialect.H2;
        }

        return switch (Objects.requireNonNullElse(config.getString("sql_type", "sqlite"), "sqlite").toLowerCase(Locale.ROOT)) {
            case "mysql"                           -> Dialect.MYSQL;
            case "mariadb"                         -> Dialect.MARIADB;
            case "postgresql", "pgsql", "postgres" -> Dialect.POSTGRESQL;
            default                                -> Dialect.SQLITE;
        };
    }

    private static ConnectionDetails warnedDetails(final FileConfiguration config) {
        final ConnectionDetails details = externalDetails(config);
        if (details.password().isEmpty()) {
            NoblLogger.warn("External database password is empty — this is a security risk.");
        }
        return details;
    }

    private static ConnectionDetails externalDetails(final FileConfiguration config) {
        return new ConnectionDetails(
            Objects.requireNonNullElse(config.getString("address", "localhost"), "localhost"),
            config.getInt("port", 3306),
            Objects.requireNonNullElse(config.getString("database", "noblsql"), "noblsql"),
            Objects.requireNonNullElse(config.getString("username", "root"), "root"),
            Objects.requireNonNullElse(config.getString("password", ""), ""),
            config.getBoolean("require_ssl", false),
            config.getInt("pool_size", 10)
        );
    }
}
