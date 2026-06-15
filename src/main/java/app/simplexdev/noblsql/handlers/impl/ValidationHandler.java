package app.simplexdev.noblsql.handlers.impl;

import app.simplexdev.noblsql.api.handler.QueryContext;
import app.simplexdev.noblsql.api.handler.QueryHandler;
import app.simplexdev.noblsql.api.handler.QueryInterceptor;
import app.simplexdev.noblsql.api.handler.QueryType;
import app.simplexdev.noblsql.util.NoblLogger;

@QueryInterceptor(priority = Integer.MAX_VALUE, intercepts = { QueryType.SELECT, QueryType.UPDATE, QueryType.DDL })
public final class ValidationHandler implements QueryHandler {
    @Override
    public void handle(final QueryContext context) {
        if (context.getSql() == null || context.getSql().isBlank()) {
            NoblLogger.warn("[NoblSQL] Blank or null SQL intercepted — cancelling query.");
            context.cancel();
        }
    }
}
