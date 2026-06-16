package app.simplexdev.noblsql.sql.pgsql;

import app.simplexdev.noblsql.sql.shared.AbstractJdbcContract;
import app.simplexdev.noblsql.sql.shared.ConnectionDetails;
import com.zaxxer.hikari.HikariConfig;

public class PostgreSQL extends AbstractJdbcContract {
    private final ConnectionDetails details;

    public PostgreSQL(final ConnectionDetails details) {
        this.details = details;
    }

    @Override
    protected void configure(final HikariConfig config) {
        config.setJdbcUrl("jdbc:postgresql://" + details.host() + ":" + details.port() + "/" + details.database());
        config.setDriverClassName("org.postgresql.Driver");
        config.setUsername(details.username());
        config.setPassword(details.password());
        if (details.requireSsl()) {
            config.addDataSourceProperty("ssl", "true");
            config.addDataSourceProperty("sslmode", "require");
        }
        config.setMaximumPoolSize(details.poolSize());
    }
}
