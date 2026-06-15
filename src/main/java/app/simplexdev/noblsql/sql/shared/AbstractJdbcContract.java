package app.simplexdev.noblsql.sql.shared;

import app.simplexdev.noblsql.api.sql.PoolStats;
import app.simplexdev.noblsql.api.sql.ResultSetMapper;
import app.simplexdev.noblsql.api.sql.SQLContract;
import app.simplexdev.noblsql.api.transaction.TransactionContext;
import app.simplexdev.noblsql.api.transaction.TransactionWork;
import app.simplexdev.noblsql.sql.transaction.JdbcTransactionContext;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public abstract class AbstractJdbcContract implements SQLContract {
    private volatile HikariDataSource dataSource;
    private volatile AccessController accessController;

    /** Populate the {@link HikariConfig} with driver-specific settings (URL, driver class, pool size, etc.). */
    protected abstract void configure(HikariConfig config);

    @Override
    public Mono<Void> connect() {
        return Mono.<Void>fromRunnable(() -> {
            final HikariConfig config = new HikariConfig();
            configure(config);
            this.dataSource = new HikariDataSource(config);
            this.accessController = new AccessController(dataSource.getMaximumPoolSize());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> disconnect() {
        return Mono.<Void>fromRunnable(() -> {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
            this.accessController = null;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    @Override
    public <T> Mono<T> query(final String sql, final ResultSetMapper<T> mapper, final Object... params) {
        return gate().guard(
            Mono.fromCallable(() -> {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    bindParams(stmt, params);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) return null;
                        return mapper.map(rs);
                    }
                }
            }).subscribeOn(Schedulers.boundedElastic())
        );
    }

    @Override
    public <T> Flux<T> queryMany(final String sql, final ResultSetMapper<T> rowMapper, final Object... params) {
        return gate()
            .guard(Mono.fromCallable(() -> {
                final List<T> rows = new ArrayList<>();
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    bindParams(stmt, params);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) rows.add(rowMapper.map(rs));
                    }
                }
                return rows;
            }).subscribeOn(Schedulers.boundedElastic()))
            .flatMapIterable(rows -> rows);
    }

    @Override
    public Mono<Integer> update(final String sql, final Object... params) {
        return gate().guard(
            Mono.fromCallable(() -> {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    bindParams(stmt, params);
                    return stmt.executeUpdate();
                }
            }).subscribeOn(Schedulers.boundedElastic())
        );
    }

    @Override
    public Mono<Long> updateReturnKey(final String sql, final Object... params) {
        return gate().guard(
            Mono.fromCallable(() -> {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    bindParams(stmt, params);
                    stmt.executeUpdate();
                    try (ResultSet keys = stmt.getGeneratedKeys()) {
                        return keys.next() ? keys.getLong(1) : -1L;
                    }
                }
            }).subscribeOn(Schedulers.boundedElastic())
        );
    }

    @Override
    public Mono<Map<String, Object>> queryRaw(final String sql, final Object... params) {
        return gate().guard(
            Mono.fromCallable(() -> {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    bindParams(stmt, params);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) return null;
                        return snapRow(rs);
                    }
                }
            }).subscribeOn(Schedulers.boundedElastic())
        );
    }

    @Override
    public Flux<Map<String, Object>> queryManyRaw(final String sql, final Object... params) {
        return gate()
            .guard(Mono.fromCallable(() -> {
                final List<Map<String, Object>> rows = new ArrayList<>();
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    bindParams(stmt, params);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) rows.add(snapRow(rs));
                    }
                }
                return rows;
            }).subscribeOn(Schedulers.boundedElastic()))
            .flatMapIterable(rows -> rows);
    }

    @Override
    public <T> Mono<T> transaction(final TransactionWork<T> work) {
        return gate().guard(
            Mono.fromCallable(() -> {
                try (Connection conn = dataSource.getConnection()) {
                    conn.setAutoCommit(false);
                    final TransactionContext ctx = new JdbcTransactionContext(conn);
                    try {
                        final T result = work.execute(ctx);
                        conn.commit();
                        return result;
                    } catch (final Exception e) {
                        conn.rollback();
                        throw e;
                    } finally {
                        conn.setAutoCommit(true);
                    }
                }
            }).subscribeOn(Schedulers.boundedElastic())
        );
    }

    @Override
    public Mono<int[]> executeBatch(final String sql, final List<Object[]> paramSets) {
        return gate().guard(
            Mono.fromCallable(() -> {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    for (final Object[] params : paramSets) {
                        bindParams(stmt, params);
                        stmt.addBatch();
                    }
                    return stmt.executeBatch();
                }
            }).subscribeOn(Schedulers.boundedElastic())
        );
    }

    @Override
    public PoolStats poolStats() {
        if (dataSource == null) return null;
        final var mxBean = dataSource.getHikariPoolMXBean();
        if (mxBean == null) return null;
        return new PoolStats(
            mxBean.getActiveConnections(),
            mxBean.getIdleConnections(),
            mxBean.getTotalConnections(),
            mxBean.getThreadsAwaitingConnection()
        );
    }

    private AccessController gate() {
        if (accessController == null) {
            throw new IllegalStateException("Not connected — call connect() before issuing queries.");
        }
        return accessController;
    }

    private static void bindParams(final PreparedStatement stmt, final Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
    }

    private static Map<String, Object> snapRow(final ResultSet rs) throws SQLException {
        final ResultSetMetaData meta = rs.getMetaData();
        final Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            row.put(meta.getColumnLabel(i).toLowerCase(Locale.ROOT), rs.getObject(i));
        }
        return row;
    }
}
