package app.simplexdev.noblsql.sql.selector;

import app.simplexdev.noblsql.api.sql.SQLContract;
import app.simplexdev.noblsql.sql.h2.H2;
import app.simplexdev.noblsql.sql.mariadb.MariaDB;
import app.simplexdev.noblsql.sql.mysql.MySQL;
import app.simplexdev.noblsql.sql.orm.Dialect;
import app.simplexdev.noblsql.sql.pgsql.PostgreSQL;
import app.simplexdev.noblsql.sql.shared.ConnectionDetails;
import app.simplexdev.noblsql.sql.sqlite.SQLite;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.Locale;

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
                config.getString("internal.database", "noblsql"),
                config.getString("internal.username", "noblsql"),
                password
            );
        }

        return switch (config.getString("sql_type", "sqlite").toLowerCase(Locale.ROOT)) {
            case "mysql"                           -> new MySQL(externalDetails(config));
            case "mariadb"                         -> new MariaDB(externalDetails(config));
            case "postgresql", "pgsql", "postgres" -> new PostgreSQL(externalDetails(config));
            default                                -> new SQLite(new File(dataFolder, "noblsql.db"));
        };
    }

    public static Dialect dialectFromConfig(final FileConfiguration config) {
        if (config.getBoolean("use_internal", false)) {
            return Dialect.H2;
        }

        return switch (config.getString("sql_type", "sqlite").toLowerCase(Locale.ROOT)) {
            case "mysql"                           -> Dialect.MYSQL;
            case "mariadb"                         -> Dialect.MARIADB;
            case "postgresql", "pgsql", "postgres" -> Dialect.POSTGRESQL;
            default                                -> Dialect.SQLITE;
        };
    }

    private static ConnectionDetails externalDetails(final FileConfiguration config) {
        return new ConnectionDetails(
            config.getString("address", "localhost"),
            config.getInt("port", 3306),
            config.getString("database", "noblsql"),
            config.getString("username", "root"),
            config.getString("password", ""),
            config.getBoolean("require_ssl", false)
        );
    }
}
