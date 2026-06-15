package app.simplexdev.noblsql.sql.sqlite;

import app.simplexdev.noblsql.sql.shared.AbstractJdbcContract;
import com.zaxxer.hikari.HikariConfig;

import java.io.File;

public class SQLite extends AbstractJdbcContract {
    private final File databaseFile;

    public SQLite(final File databaseFile) {
        this.databaseFile = databaseFile;
    }

    @Override
    protected void configure(final HikariConfig config) {
        config.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);
        config.setConnectionTestQuery("SELECT 1");
    }
}
