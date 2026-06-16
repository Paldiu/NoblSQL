package app.simplexdev.noblsql.api.migration;

import app.simplexdev.noblsql.api.transaction.TransactionContext;

/**
 * A single versioned schema change. Implement this and register instances with
 * {@link app.simplexdev.noblsql.sql.orm.MigrationRunner#run} to apply pending migrations
 * in version order, each within its own transaction.
 */
public interface Migration {
    /** Monotonically increasing version number. Each version must be unique. */
    int version();

    /** Human-readable label stored in the migrations tracking table. */
    String description();

    /** Apply this migration. Runs inside a transaction — throw to trigger rollback. */
    void up(TransactionContext ctx) throws Exception;

    /** Reverse this migration. Throws {@link UnsupportedOperationException} by default. */
    default void down(TransactionContext ctx) throws Exception {
        throw new UnsupportedOperationException("down() not implemented for migration v" + version());
    }
}
