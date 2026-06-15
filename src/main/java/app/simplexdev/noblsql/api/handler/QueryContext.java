package app.simplexdev.noblsql.api.handler;

public final class QueryContext {
    private final String originalSql;
    private final Object[] originalParams;
    private final QueryType type;
    private String sql;
    private Object[] params;
    private boolean cancelled;

    public QueryContext(final String sql, final Object[] params, final QueryType type) {
        this.originalSql = sql;
        this.originalParams = params.clone();
        this.sql = sql;
        this.params = params.clone();
        this.type = type;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(final String sql) {
        this.sql = sql;
    }

    public Object[] getParams() {
        return params;
    }

    public void setParams(final Object[] params) {
        this.params = params;
    }

    public QueryType getType() {
        return type;
    }

    public String getOriginalSql() {
        return originalSql;
    }

    public Object[] getOriginalParams() {
        return originalParams;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        this.cancelled = true;
    }
}
