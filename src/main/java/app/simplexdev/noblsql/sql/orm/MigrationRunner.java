package app.simplexdev.noblsql.sql.orm;

import app.simplexdev.noblsql.api.migration.Migration;
import app.simplexdev.noblsql.api.sql.SQLContract;
import app.simplexdev.noblsql.util.NoblLogger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies pending {@link Migration}s in version order, each in its own transaction.
 * Applied versions are recorded in a {@code _noblsql_migrations} tracking table so
 * the same migration is never run twice.
 *
 * <p>Obtain an instance via {@link app.simplexdev.noblsql.NoblSQL#createMigrationRunner()}.
 */
public final class MigrationRunner {

    private static final String MIGRATION_TABLE = "_noblsql_migrations";

    private final SQLContract contract;
    private final Dialect dialect;

    public MigrationRunner(final SQLContract contract, final Dialect dialect) {
        this.contract = contract;
        this.dialect = dialect;
    }

    /**
     * Creates the tracking table if absent, queries which versions are already applied,
     * then runs each pending migration in ascending version order.
     *
     * @param migrations all known migrations; duplicates / already-applied ones are skipped
     */
    public Mono<Void> run(final List<Migration> migrations) {
        final List<Migration> sorted = new ArrayList<>(migrations);
        sorted.sort(Comparator.comparingInt(Migration::version));

        return contract.update(createTableSql())
            .then(contract.queryMany(
                "SELECT " + q("version") + " FROM " + q(MIGRATION_TABLE),
                rs -> rs.getInt(1)
            ).collectList())
            .flatMap(applied -> {
                final Set<Integer> done = new HashSet<>(applied);
                final List<Migration> pending = sorted.stream()
                    .filter(m -> !done.contains(m.version()))
                    .toList();

                if (pending.isEmpty()) {
                    NoblLogger.info("[NoblSQL] All migrations are up to date.");
                    return Mono.empty();
                }

                NoblLogger.info("[NoblSQL] Applying {} pending migration(s).", pending.size());
                return Flux.fromIterable(pending)
                    .concatMap(this::applyOne)
                    .then();
            });
    }

    private Mono<Void> applyOne(final Migration migration) {
        return contract.<Void>transaction(ctx -> {
            NoblLogger.info("[NoblSQL] Applying migration v{}: {}",
                migration.version(), migration.description());
            migration.up(ctx);
            ctx.update(
                "INSERT INTO " + q(MIGRATION_TABLE)
                    + " (" + q("version") + ", " + q("description") + ", " + q("applied_at") + ")"
                    + " VALUES (?, ?, ?)",
                migration.version(),
                migration.description(),
                Timestamp.from(Instant.now())
            );
            NoblLogger.info("[NoblSQL] Migration v{} applied.", migration.version());
            return null;
        }).then();
    }

    private String createTableSql() {
        return "CREATE TABLE IF NOT EXISTS " + q(MIGRATION_TABLE) + " ("
            + "\n    " + q("version")     + " INT NOT NULL,"
            + "\n    " + q("description") + " TEXT,"
            + "\n    " + q("applied_at")  + " TIMESTAMP NOT NULL,"
            + "\n    PRIMARY KEY (" + q("version") + ")"
            + "\n)";
    }

    private String q(final String identifier) {
        return dialect.quoteIdentifier(identifier);
    }
}
