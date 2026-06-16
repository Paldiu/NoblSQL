package app.simplexdev.noblsql;

import app.simplexdev.noblsql.api.handler.QueryHandler;
import app.simplexdev.noblsql.api.sql.RedisContract;
import app.simplexdev.noblsql.api.sql.SQLContract;
import app.simplexdev.noblsql.bukkit.BukkitSchedulers;
import app.simplexdev.noblsql.bukkit.command.NoblSQLCommand;
import app.simplexdev.noblsql.handlers.HandlerAwareSQLContract;
import app.simplexdev.noblsql.handlers.HandlerChain;
import app.simplexdev.noblsql.handlers.impl.LoggingHandler;
import app.simplexdev.noblsql.handlers.impl.ValidationHandler;
import app.simplexdev.noblsql.internal.InternalSQLServer;
import app.simplexdev.noblsql.sql.orm.Dialect;
import app.simplexdev.noblsql.sql.orm.MigrationRunner;
import app.simplexdev.noblsql.sql.redis.Redis;
import app.simplexdev.noblsql.sql.selector.SQLSelector;
import app.simplexdev.noblsql.util.NoblLogger;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Duration;
import java.util.Objects;

public final class NoblSQL extends JavaPlugin {

    private static NoblSQL instance;

    private final HandlerChain handlerChain = new HandlerChain();
    private InternalSQLServer internalServer;
    private SQLContract sharedContract;
    private Dialect sharedDialect;
    private RedisContract redisContract;

    @Override
    public void onEnable() {
        instance = this;
        NoblLogger.init(getSLF4JLogger());
        BukkitSchedulers.init(this);
        NoblLogger.info("NoblSQL v{} is starting.", getPluginMeta().getVersion());

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
            event -> NoblSQLCommand.register(event.registrar(), this));

        saveDefaultConfig();

        handlerChain.register(new ValidationHandler());
        handlerChain.register(new LoggingHandler());

        try {
            if (getConfig().getBoolean("use_internal", false)) {
                internalServer = new InternalSQLServer(
                    getConfig().getInt("internal.port", 9092),
                    new File(getDataFolder(), "data")
                );
                internalServer.start();
            }

            sharedDialect = SQLSelector.dialectFromConfig(getConfig());
            sharedContract = new HandlerAwareSQLContract(
                SQLSelector.contractFromConfig(getConfig(), getDataFolder()),
                handlerChain
            );

            sharedContract.connect().block(Duration.ofSeconds(30));
            NoblLogger.info("SQL connection established ({}).", sharedDialect);
        } catch (final Exception e) {
            NoblLogger.error("Failed to establish SQL connection — disabling plugin.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (getConfig().getBoolean("redis.enabled", false)) {
            final Redis redis = new Redis(
                Objects.requireNonNullElse(getConfig().getString("redis.host", "localhost"), "localhost"),
                getConfig().getInt("redis.port", 6379),
                Objects.requireNonNullElse(getConfig().getString("redis.password", ""), ""),
                getConfig().getInt("redis.max_connections", 10)
            );
            try {
                redis.connect().block(Duration.ofSeconds(10));
                this.redisContract = redis;
                NoblLogger.info("Redis connection established.");
            } catch (final Exception e) {
                NoblLogger.warn("Failed to connect to Redis — Redis features will be unavailable. {}", e.getMessage());
            }
        }

        NoblLogger.info("Ready — use NoblSQL#createRepository to obtain a data accessor.");
    }

    @Override
    public void onDisable() {
        NoblLogger.info("NoblSQL is shutting down.");
        if (redisContract != null) {
            redisContract.disconnect().block();
        }
        if (sharedContract != null) {
            sharedContract.disconnect().block();
        }
        if (internalServer != null) {
            internalServer.stop();
        }
        instance = null;
    }

    public static NoblSQL getInstance() {
        return instance;
    }

    /**
     * Registers a custom {@link QueryHandler} into the global handler chain.
     * All queries routed through the shared contract will pass through it.
     * Use {@code @QueryInterceptor} on the handler class to control priority
     * and which {@code QueryType}s it intercepts.
     */
    public void registerHandler(final QueryHandler handler) {
        handlerChain.register(handler);
    }

    public void unregisterHandler(final QueryHandler handler) {
        handlerChain.unregister(handler);
    }

    /**
     * Creates a repository backed by the plugin's shared SQL contract and
     * auto-detected dialect (from config.yml).
     */
    public <T> NoblRepository<T> createRepository(final Class<T> entityClass) {
        return new NoblRepository<>(entityClass, sharedContract, sharedDialect);
    }

    /**
     * Creates a repository with an explicit dialect override against the shared contract.
     */
    public <T> NoblRepository<T> createRepository(final Class<T> entityClass, final Dialect dialect) {
        return new NoblRepository<>(entityClass, sharedContract, dialect);
    }

    /**
     * Creates a repository with a fully custom contract and dialect — useful when a
     * dependent plugin wants its own isolated connection rather than the shared one.
     */
    public <T> NoblRepository<T> createRepository(
            final Class<T> entityClass,
            final SQLContract contract,
            final Dialect dialect) {
        return new NoblRepository<>(entityClass, contract, dialect);
    }

    /**
     * Tears down and rebuilds the shared connection pool using the original startup parameters.
     * Useful for recovering from pool exhaustion or a transient DB outage.
     * Connection URL and credentials come from the original config read at startup; a full
     * parameter change requires a server restart.
     *
     * @return {@code true} if the reconnect succeeded
     */
    public boolean reconnect() {
        try {
            NoblLogger.info("Reconnecting SQL pool...");
            sharedContract.disconnect().block(Duration.ofSeconds(10));
            sharedContract.connect().block(Duration.ofSeconds(30));
            NoblLogger.info("SQL pool reconnected ({}).", sharedDialect);
            return true;
        } catch (final Exception e) {
            NoblLogger.error("Failed to reconnect SQL pool.", e);
            return false;
        }
    }

    public SQLContract getSharedContract() {
        return sharedContract;
    }

    public Dialect getSharedDialect() {
        return sharedDialect;
    }

    /**
     * Returns a {@link MigrationRunner} bound to the shared contract and dialect.
     * Call {@code runner.run(migrations)} to apply pending migrations in version order.
     */
    public MigrationRunner createMigrationRunner() {
        return new MigrationRunner(sharedContract, sharedDialect);
    }

    /**
     * Returns the Redis contract, or {@code null} if Redis is disabled or failed to connect.
     * Check {@code getConfig().getBoolean("redis.enabled")} before calling, or null-check the return value.
     */
    public RedisContract getRedisContract() {
        return redisContract;
    }
}
