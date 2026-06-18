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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            .then(addChecksumColumnIfAbsent())
            .then(loadApplied())
            .flatMap(storedChecksums -> {
                final Set<Integer> done = storedChecksums.keySet();

                for (final Migration m : sorted) {
                    if (!done.contains(m.version())) continue;
                    final String stored  = storedChecksums.get(m.version());
                    final String current = m.checksum();
                    if (stored != null && current != null && !stored.equals(current)) {
                        NoblLogger.warn("[NoblSQL] Migration v{} checksum mismatch (stored={}, current={}) — body may have been edited.",
                            m.version(), stored, current);
                    }
                }

                final List<Migration> pending = sorted.stream()
                    .filter(m -> !done.contains(m.version()))
                    .toList();

                if (pending.isEmpty()) {
                    NoblLogger.info("[NoblSQL] All migrations are up to date.");
                    return Mono.<Void>empty();
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
                    + " (" + q("version") + ", " + q("description") + ", " + q("applied_at") + ", " + q("checksum") + ")"
                    + " VALUES (?, ?, ?, ?)",
                migration.version(),
                migration.description(),
                Timestamp.from(Instant.now()),
                migration.checksum()
            );
            NoblLogger.info("[NoblSQL] Migration v{} applied.", migration.version());
            return null;
        }).then();
    }

    /**
     * Loads applied migration versions and their stored checksums.
     * Falls back to a version-only query (null checksums) if the checksum column is absent
     * on an older installation where {@link #addChecksumColumnIfAbsent()} could not add it.
     */
    private Mono<Map<Integer, String>> loadApplied() {
        return contract.queryManyRaw(
            "SELECT " + q("version") + ", " + q("checksum") + " FROM " + q(MIGRATION_TABLE)
        )
        .collectList()
        .onErrorResume(__ ->
            contract.queryMany(
                "SELECT " + q("version") + " FROM " + q(MIGRATION_TABLE), rs -> rs.getInt(1)
            ).collectList()
             .map(versions -> {
                 final List<Map<String, Object>> rows = new ArrayList<>();
                 for (final int ver : versions) {
                     final Map<String, Object> row = new HashMap<>();
                     row.put("version", ver);
                     row.put("checksum", null);
                     rows.add(row);
                 }
                 return rows;
             })
        )
        .map(rows -> {
            final Map<Integer, String> result = new HashMap<>();
            for (final Map<String, Object> row : rows) {
                result.put(((Number) row.get("version")).intValue(), (String) row.get("checksum"));
            }
            return result;
        });
    }

    /**
     * Rolls back applied migrations in descending version order until {@code toVersion} has
     * been reversed. Each migration runs in its own transaction; the first failure stops the
     * rollback and leaves subsequent (lower) versions untouched.
     *
     * <p>Requires that each targeted {@link Migration#down(app.simplexdev.noblsql.api.transaction.TransactionContext)}
     * be implemented — the default throws {@link UnsupportedOperationException}, which aborts
     * the rollback at that version.
     *
     * @param toVersion lowest version to reverse (inclusive); pass {@code 1} to roll back everything
     * @param migrations all known migrations; any version present in the DB but absent here
     *                   is skipped with a warning
     */
    public Mono<Void> rollback(final int toVersion, final List<Migration> migrations) {
        final Map<Integer, Migration> byVersion = new HashMap<>();
        for (final Migration m : migrations) byVersion.put(m.version(), m);

        return loadApplied()
            .flatMap(storedChecksums -> {
                final List<Integer> applied = new ArrayList<>(storedChecksums.keySet());
                applied.sort(Comparator.reverseOrder());

                final List<Migration> toRollback = new ArrayList<>();
                for (final int v : applied) {
                    if (v < toVersion) break;
                    final Migration m = byVersion.get(v);
                    if (m == null) {
                        NoblLogger.warn("[NoblSQL] Applied migration v{} not found in the provided list — skipping rollback for this version.", v);
                        continue;
                    }
                    toRollback.add(m);
                }

                if (toRollback.isEmpty()) {
                    NoblLogger.info("[NoblSQL] Nothing to roll back (no applied migrations with version >= {}).", toVersion);
                    return Mono.<Void>empty();
                }

                NoblLogger.info("[NoblSQL] Rolling back {} migration(s).", toRollback.size());
                return Flux.fromIterable(toRollback)
                    .concatMap(this::rollbackOne)
                    .then();
            });
    }

    private Mono<Void> rollbackOne(final Migration migration) {
        return contract.<Void>transaction(ctx -> {
            NoblLogger.info("[NoblSQL] Rolling back migration v{}: {}",
                migration.version(), migration.description());
            try {
                migration.down(ctx);
            } catch (final UnsupportedOperationException e) {
                throw new IllegalStateException(
                    "Cannot roll back migration v" + migration.version() + ": down() is not implemented.", e);
            }
            ctx.update(
                "DELETE FROM " + q(MIGRATION_TABLE) + " WHERE " + q("version") + " = ?",
                migration.version()
            );
            NoblLogger.info("[NoblSQL] Rolled back migration v{}.", migration.version());
            return null;
        }).then();
    }

    /** Best-effort: adds the {@code checksum} column to existing tracking tables. Swallows errors. */
    private Mono<Void> addChecksumColumnIfAbsent() {
        return contract.update(
            "ALTER TABLE " + q(MIGRATION_TABLE) + " ADD COLUMN " + q("checksum") + " TEXT"
        ).onErrorComplete().then();
    }

    private String createTableSql() {
        return "CREATE TABLE IF NOT EXISTS " + q(MIGRATION_TABLE) + " ("
            + "\n    " + q("version")     + " INT NOT NULL,"
            + "\n    " + q("description") + " TEXT,"
            + "\n    " + q("applied_at")  + " TIMESTAMP NOT NULL,"
            + "\n    " + q("checksum")    + " TEXT,"
            + "\n    PRIMARY KEY (" + q("version") + ")"
            + "\n)";
    }

    private String q(final String identifier) {
        return dialect.quoteIdentifier(identifier);
    }
}
