package app.simplexdev.noblsql.handlers.impl;

import app.simplexdev.noblsql.api.handler.QueryContext;
import app.simplexdev.noblsql.api.handler.QueryHandler;
import app.simplexdev.noblsql.api.handler.QueryInterceptor;
import app.simplexdev.noblsql.api.handler.QueryType;
import app.simplexdev.noblsql.util.NoblLogger;

@QueryInterceptor(priority = Integer.MAX_VALUE - 1, intercepts = { QueryType.SELECT, QueryType.UPDATE, QueryType.DDL })
public final class LoggingHandler implements QueryHandler {
    @Override
    public void handle(final QueryContext context) {
        NoblLogger.debug("[NoblSQL] {} → {}", context.getType(), context.getSql());
    }
}
