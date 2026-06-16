package app.simplexdev.noblsql.sql.mysql;

import app.simplexdev.noblsql.sql.shared.AbstractJdbcContract;
import app.simplexdev.noblsql.sql.shared.ConnectionDetails;
import com.zaxxer.hikari.HikariConfig;

public class MySQL extends AbstractJdbcContract {
    private final ConnectionDetails details;

    public MySQL(final ConnectionDetails details) {
        this.details = details;
    }

    @Override
    protected void configure(final HikariConfig config) {
        final String sslParams = details.requireSsl()
            ? "useSSL=true&requireSSL=true"
            : "useSSL=false";
        config.setJdbcUrl("jdbc:mysql://" + details.host() + ":" + details.port() + "/" + details.database()
            + "?" + sslParams + "&serverTimezone=UTC");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setUsername(details.username());
        config.setPassword(details.password());
        config.setMaximumPoolSize(details.poolSize());
    }
}
