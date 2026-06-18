package app.simplexdev.noblsql.api.sql;

import app.simplexdev.noblsql.api.transaction.TransactionContext;
import app.simplexdev.noblsql.api.transaction.TransactionWork;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface SQLContract extends AutoCloseable {
    Logger LOGGER = LoggerFactory.getLogger(SQLContract.class);
    Mono<Void> connect();
    Mono<Void> disconnect();
    boolean isConnected();

    /**
     * Executes a SELECT and maps the first row via the provided {@link ResultSetMapper}.
     * The mapper is called on the I/O thread while the {@link java.sql.ResultSet} is still open.
     * For ORM entity mapping that may invoke Bukkit API, prefer {@link #queryRaw} instead.
     */
    <T> Mono<T> query(String sql, ResultSetMapper<T> mapper, Object... params);

    /**
     * Executes a SELECT and maps every row via the provided {@link ResultSetMapper}.
     * The mapper is called on the I/O thread. For ORM entity mapping prefer {@link #queryManyRaw}.
     */
    <T> Flux<T> queryMany(String sql, ResultSetMapper<T> rowMapper, Object... params);

    Mono<Integer> update(String sql, Object... params);

    /**
     * Executes an INSERT (or any statement that generates a key) and returns the
     * first generated key as a {@code long}. Returns {@code -1} if no key was generated.
     * Use this instead of {@link #update} when you need the auto-incremented id after
     * {@code @AutoIncrement} inserts.
     */
    Mono<Long> updateReturnKey(String sql, Object... params);

    /**
     * Executes a SELECT and returns the first row as a raw column-value snapshot
     * ({@code column_label → value}) collected entirely on the I/O thread.
     * The caller can then {@code .publishOn(BukkitSchedulers.mainThread())} before
     * mapping the snapshot to a domain object, ensuring any Bukkit API calls in
     * {@link app.simplexdev.noblsql.api.data.TypeHandler#fromSql} run on the correct thread.
     */
    Mono<Map<String, Object>> queryRaw(String sql, Object... params);

    /**
     * Executes a SELECT and returns every row as a raw column-value snapshot.
     * See {@link #queryRaw} for the threading rationale.
     */
    Flux<Map<String, Object>> queryManyRaw(String sql, Object... params);

    /**
     * Executes a SELECT and streams rows one at a time using a JDBC server-side cursor.
     * Unlike {@link #queryManyRaw}, this never materializes the entire result set in
     * memory — heap usage is proportional to {@code fetchSize}, not the number of rows.
     * Use for unbounded queries on large tables where {@link #queryManyRaw} would risk OOM.
     *
     * <p>The underlying {@link java.sql.Connection} and {@link java.sql.ResultSet} remain
     * open until the Flux completes, errors, or is cancelled.
     *
     * @param fetchSize JDBC fetch-size hint (typical: 50–500). MySQL requires
     *                  {@code Integer.MIN_VALUE} to enable true server-side streaming.
     */
    Flux<Map<String, Object>> queryStream(String sql, int fetchSize, Object... params);

    /**
     * Runs {@code work} inside a single JDBC transaction on a dedicated I/O thread.
     * The transaction is committed when {@code work} returns normally and rolled back
     * if it throws. Use {@link TransactionContext} for all database operations inside
     * the callback; they share the same underlying connection.
     */
    <T> Mono<T> transaction(TransactionWork<T> work);

    /**
     * Executes the same prepared statement repeatedly with different parameter sets,
     * using JDBC batch execution. More efficient than issuing individual updates when
     * inserting or updating many rows at once.
     *
     * @return per-row affected-row counts in the same order as {@code paramSets}
     */
    Mono<int[]> executeBatch(String sql, List<Object[]> paramSets);

    /**
     * Returns a live snapshot of the underlying connection pool metrics.
     * Returns {@code null} if the contract is not yet connected or does not use a pool.
     */
    PoolStats poolStats();

    @Override
    default void close() {
        disconnect().subscribe(null, e -> LOGGER.warn("SQL disconnect error", e));
    }
}
