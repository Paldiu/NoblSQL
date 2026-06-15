package app.simplexdev.noblsql.sql.redis;

import app.simplexdev.noblsql.api.sql.RedisContract;
import app.simplexdev.noblsql.sql.shared.AccessController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class Redis implements RedisContract {
    private static final int DEFAULT_MAX_CONNECTIONS = 10;

    private final String host;
    private final int port;
    private final String password;
    private final int maxConnections;
    private volatile JedisPool pool;
    private volatile AccessController accessController;

    public Redis(final String host, final int port, final String password, final int maxConnections) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.maxConnections = maxConnections;
    }

    public Redis(final String host, final int port, final String password) {
        this(host, port, password, DEFAULT_MAX_CONNECTIONS);
    }

    public Redis(final String host, final int port) {
        this(host, port, null, DEFAULT_MAX_CONNECTIONS);
    }

    @Override
    public Mono<Void> connect() {
        return Mono.<Void>fromRunnable(() -> {
            final JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(maxConnections);
            this.pool = (password != null && !password.isEmpty())
                ? new JedisPool(poolConfig, host, port, 2000, password)
                : new JedisPool(poolConfig, host, port, 2000);
            this.accessController = new AccessController(maxConnections);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> disconnect() {
        return Mono.<Void>fromRunnable(() -> {
            if (pool != null && !pool.isClosed()) {
                pool.close();
            }
            this.accessController = null;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public boolean isConnected() {
        return pool != null && !pool.isClosed();
    }

    @Override
    public Mono<String> get(final String key) {
        return gate().guard(
            Mono.fromCallable(() -> {
                try (Jedis jedis = pool.getResource()) {
                    return jedis.get(key);
                }
            }).subscribeOn(Schedulers.boundedElastic())
        );
    }

    @Override
    public Mono<Void> set(final String key, final String value) {
        return gate().guard(
            Mono.<Void>fromRunnable(() -> {
                try (Jedis jedis = pool.getResource()) {
                    jedis.set(key, value);
                }
            }).subscribeOn(Schedulers.boundedElastic())
        );
    }

    @Override
    public Mono<Void> set(final String key, final String value, final long ttlSeconds) {
        return gate().guard(
            Mono.<Void>fromRunnable(() -> {
                try (Jedis jedis = pool.getResource()) {
                    jedis.setex(key, ttlSeconds, value);
                }
            }).subscribeOn(Schedulers.boundedElastic())
        );
    }

    @Override
    public Mono<Boolean> delete(final String key) {
        return gate().guard(
            Mono.fromCallable(() -> {
                try (Jedis jedis = pool.getResource()) {
                    return jedis.del(key) > 0;
                }
            }).subscribeOn(Schedulers.boundedElastic())
        );
    }

    @Override
    public Mono<Boolean> exists(final String key) {
        return gate().guard(
            Mono.fromCallable(() -> {
                try (Jedis jedis = pool.getResource()) {
                    return jedis.exists(key);
                }
            }).subscribeOn(Schedulers.boundedElastic())
        );
    }

    @Override
    public Mono<Long> expire(final String key, final long ttlSeconds) {
        return gate().guard(
            Mono.fromCallable(() -> {
                try (Jedis jedis = pool.getResource()) {
                    return jedis.expire(key, ttlSeconds);
                }
            }).subscribeOn(Schedulers.boundedElastic())
        );
    }

    private AccessController gate() {
        if (accessController == null) {
            throw new IllegalStateException("Not connected — call connect() before issuing commands.");
        }
        return accessController;
    }
}
