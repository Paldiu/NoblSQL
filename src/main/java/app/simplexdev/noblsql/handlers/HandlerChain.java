package app.simplexdev.noblsql.handlers;

import app.simplexdev.noblsql.api.handler.QueryContext;
import app.simplexdev.noblsql.api.handler.QueryHandler;
import app.simplexdev.noblsql.api.handler.QueryInterceptor;
import app.simplexdev.noblsql.api.handler.QueryType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class HandlerChain {
    private volatile List<QueryHandler> handlers = List.of();

    public synchronized void register(final QueryHandler handler) {
        final List<QueryHandler> next = new ArrayList<>(handlers);
        next.add(handler);
        next.sort(Comparator.comparingInt(this::resolvedPriority).reversed());
        handlers = List.copyOf(next);
    }

    public synchronized void unregister(final QueryHandler handler) {
        final List<QueryHandler> next = new ArrayList<>(handlers);
        next.remove(handler);
        handlers = List.copyOf(next);
    }

    public QueryContext process(final String sql, final Object[] params, final QueryType type) {
        final List<QueryHandler> snapshot = handlers;
        final QueryContext ctx = new QueryContext(sql, params, type);
        for (final QueryHandler handler : snapshot) {
            if (ctx.isCancelled()) break;
            if (!accepts(handler, type)) continue;
            handler.handle(ctx);
        }
        return ctx;
    }

    private int resolvedPriority(final QueryHandler handler) {
        final QueryInterceptor ann = handler.getClass().getAnnotation(QueryInterceptor.class);
        return ann != null ? ann.priority() : handler.priority();
    }

    private boolean accepts(final QueryHandler handler, final QueryType type) {
        final QueryInterceptor ann = handler.getClass().getAnnotation(QueryInterceptor.class);
        if (ann == null) return true;
        return Arrays.asList(ann.intercepts()).contains(type);
    }
}
