package app.simplexdev.noblsql.api.sql;

import reactor.core.publisher.Mono;

public interface RedisContract extends AutoCloseable {
    Mono<Void> connect();
    Mono<Void> disconnect();
    boolean isConnected();

    Mono<String> get(String key);
    Mono<Void> set(String key, String value);
    Mono<Void> set(String key, String value, long ttlSeconds);
    Mono<Boolean> delete(String key);
    Mono<Boolean> exists(String key);
    Mono<Long> expire(String key, long ttlSeconds);

    @Override
    default void close() {
        disconnect().subscribe();
    }
}
