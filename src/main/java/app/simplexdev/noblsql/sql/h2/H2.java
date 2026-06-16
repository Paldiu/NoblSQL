package app.simplexdev.noblsql.sql.h2;

import app.simplexdev.noblsql.sql.shared.AbstractJdbcContract;
import com.zaxxer.hikari.HikariConfig;

public class H2 extends AbstractJdbcContract {
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final int poolSize;

    public H2(final String host, final int port, final String database,
               final String username, final String password, final int poolSize) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.poolSize = poolSize;
    }

    @Override
    protected void configure(final HikariConfig config) {
        config.setJdbcUrl("jdbc:h2:tcp://" + host + ":" + port + "/" + database);
        config.setDriverClassName("org.h2.Driver");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setConnectionTestQuery("SELECT 1");
    }
}
