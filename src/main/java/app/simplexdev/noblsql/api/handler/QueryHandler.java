package app.simplexdev.noblsql.api.handler;

public interface QueryHandler {
    void handle(QueryContext context);

    default int priority() {
        return 0;
    }
}
