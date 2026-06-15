package app.simplexdev.noblsql.sql.mariadb;

import app.simplexdev.noblsql.sql.shared.AbstractJdbcContract;
import app.simplexdev.noblsql.sql.shared.ConnectionDetails;
import com.zaxxer.hikari.HikariConfig;

public class MariaDB extends AbstractJdbcContract {
    private final ConnectionDetails details;

    public MariaDB(final ConnectionDetails details) {
        this.details = details;
    }

    @Override
    protected void configure(final HikariConfig config) {
        config.setJdbcUrl("jdbc:mariadb://" + details.host() + ":" + details.port() + "/" + details.database());
        config.setDriverClassName("org.mariadb.jdbc.Driver");
        config.setUsername(details.username());
        config.setPassword(details.password());
        if (details.requireSsl()) {
            config.addDataSourceProperty("useSSL", "true");
            config.addDataSourceProperty("requireSSL", "true");
        }
        config.setMaximumPoolSize(10);
    }
}
