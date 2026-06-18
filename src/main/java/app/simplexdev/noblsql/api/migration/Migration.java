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

    /**
     * An optional content fingerprint for this migration (e.g. a SHA-256 of the SQL body).
     * When non-null, {@link app.simplexdev.noblsql.sql.orm.MigrationRunner} will warn if the
     * stored checksum differs from the current value, indicating that a previously-applied
     * migration's body has been silently edited.
     * Returns {@code null} by default (no check performed).
     */
    default String checksum() {
        return null;
    }
}
