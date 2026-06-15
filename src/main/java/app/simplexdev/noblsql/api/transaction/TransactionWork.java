package app.simplexdev.noblsql.api.transaction;

@FunctionalInterface
public interface TransactionWork<T> {
    T execute(TransactionContext ctx) throws Exception;
}
