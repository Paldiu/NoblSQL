package app.simplexdev.noblsql.handlers;

import app.simplexdev.noblsql.api.handler.QueryContext;
import app.simplexdev.noblsql.api.handler.QueryType;
import app.simplexdev.noblsql.api.sql.PoolStats;
import app.simplexdev.noblsql.api.sql.ResultSetMapper;
import app.simplexdev.noblsql.api.sql.SQLContract;
import app.simplexdev.noblsql.api.transaction.TransactionWork;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HandlerAwareSQLContract implements SQLContract {
    private final SQLContract delegate;
    private final HandlerChain chain;

    public HandlerAwareSQLContract(final SQLContract delegate, final HandlerChain chain) {
        this.delegate = delegate;
        this.chain = chain;
    }

    @Override
    public Mono<Void> connect() {
        return delegate.connect();
    }

    @Override
    public Mono<Void> disconnect() {
        return delegate.disconnect();
    }

    @Override
    public boolean isConnected() {
        return delegate.isConnected();
    }

    @Override
    public <T> Mono<T> query(final String sql, final ResultSetMapper<T> mapper, final Object... params) {
        final QueryContext ctx = chain.process(sql, params, QueryType.SELECT);
        if (ctx.isCancelled()) return Mono.empty();
        return delegate.query(ctx.getSql(), mapper, ctx.getParams());
    }

    @Override
    public <T> Flux<T> queryMany(final String sql, final ResultSetMapper<T> rowMapper, final Object... params) {
        final QueryContext ctx = chain.process(sql, params, QueryType.SELECT);
        if (ctx.isCancelled()) return Flux.empty();
        return delegate.queryMany(ctx.getSql(), rowMapper, ctx.getParams());
    }

    @Override
    public Mono<Integer> update(final String sql, final Object... params) {
        final QueryContext ctx = chain.process(sql, params, detectType(sql));
        if (ctx.isCancelled()) return Mono.just(0);
        return delegate.update(ctx.getSql(), ctx.getParams());
    }

    @Override
    public Mono<Long> updateReturnKey(final String sql, final Object... params) {
        final QueryContext ctx = chain.process(sql, params, detectType(sql));
        if (ctx.isCancelled()) return Mono.just(-1L);
        return delegate.updateReturnKey(ctx.getSql(), ctx.getParams());
    }

    @Override
    public Mono<Map<String, Object>> queryRaw(final String sql, final Object... params) {
        final QueryContext ctx = chain.process(sql, params, QueryType.SELECT);
        if (ctx.isCancelled()) return Mono.empty();
        return delegate.queryRaw(ctx.getSql(), ctx.getParams());
    }

    @Override
    public Flux<Map<String, Object>> queryManyRaw(final String sql, final Object... params) {
        final QueryContext ctx = chain.process(sql, params, QueryType.SELECT);
        if (ctx.isCancelled()) return Flux.empty();
        return delegate.queryManyRaw(ctx.getSql(), ctx.getParams());
    }

    @Override
    public Flux<Map<String, Object>> queryStream(final String sql, final int fetchSize, final Object... params) {
        final QueryContext ctx = chain.process(sql, params, QueryType.SELECT);
        if (ctx.isCancelled()) return Flux.empty();
        return delegate.queryStream(ctx.getSql(), fetchSize, ctx.getParams());
    }

    @Override
    public PoolStats poolStats() {
        return delegate.poolStats();
    }

    /**
     * NOTE: SQL executed inside the {@link TransactionWork} callback bypasses the handler
     * chain entirely. Handlers such as {@code ValidationHandler} and {@code LoggingHandler}
     * will not be invoked for queries issued through the {@link app.simplexdev.noblsql.api.transaction.TransactionContext}.
     */
    @Override
    public <T> Mono<T> transaction(final TransactionWork<T> work) {
        return delegate.transaction(work);
    }

    /**
     * NOTE: The handler chain sees the batch SQL but receives an empty params array.
     * Individual batch parameter sets are not forwarded to handlers.
     */
    @Override
    public Mono<int[]> executeBatch(final String sql, final List<Object[]> paramSets) {
        final QueryContext ctx = chain.process(sql, new Object[0], detectType(sql));
        if (ctx.isCancelled()) return Mono.just(new int[0]);
        return delegate.executeBatch(ctx.getSql(), paramSets);
    }

    private static QueryType detectType(final String sql) {
        final String upper = sql.stripLeading().toUpperCase(Locale.ROOT);
        if (upper.startsWith("CREATE") || upper.startsWith("DROP") || upper.startsWith("ALTER")
                || upper.startsWith("TRUNCATE")) {
            return QueryType.DDL;
        }
        if (upper.startsWith("SELECT")) {
            return QueryType.SELECT;
        }
        return QueryType.UPDATE;
    }
}
