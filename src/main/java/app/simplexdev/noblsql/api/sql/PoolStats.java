package app.simplexdev.noblsql.api.sql;

/**
 * Snapshot of HikariCP pool metrics at the moment of the call.
 *
 * @param active  connections currently checked out by application threads
 * @param idle    connections sitting unused in the pool
 * @param total   active + idle (may be less than the configured maximum)
 * @param waiting threads blocked waiting for a connection
 */
public record PoolStats(int active, int idle, int total, int waiting) {}
