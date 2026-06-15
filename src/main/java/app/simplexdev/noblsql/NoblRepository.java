package app.simplexdev.noblsql;

import app.simplexdev.noblsql.api.sql.ResultSetMapper;
import app.simplexdev.noblsql.api.sql.SQLContract;
import app.simplexdev.noblsql.api.transaction.TransactionWork;
import app.simplexdev.noblsql.bukkit.BukkitSchedulers;
import app.simplexdev.noblsql.sql.orm.Dialect;
import app.simplexdev.noblsql.sql.orm.EntityMapper;
import app.simplexdev.noblsql.sql.orm.SchemaGenerator;
import app.simplexdev.noblsql.sql.query.QueryBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Bridges {@link SQLContract}, {@link EntityMapper}, and {@link SchemaGenerator} into a
 * single user-facing surface. Obtain an instance via {@link NoblSQL#createRepository}.
 *
 * @param <T> the entity type — must be annotated with {@code @Table}, have at least one
 *            {@code @Column @Id} field, and provide a public no-arg constructor.
 */
public class NoblRepository<T> {

    private final Class<T> entityClass;
    private final SQLContract contract;
    private final EntityMapper<T> mapper;
    private final SchemaGenerator schemaGen;

    NoblRepository(final Class<T> entityClass, final SQLContract contract, final Dialect dialect) {
        this.entityClass = entityClass;
        this.contract = contract;
        this.mapper = new EntityMapper<>(entityClass, dialect);
        this.schemaGen = new SchemaGenerator(dialect);
    }

    /** Creates the table and any indexes declared on the entity class. */
    public Flux<Integer> createSchema() {
        return schemaGen.execute(contract, entityClass);
    }

    /**
     * Finds the row whose primary key matches the supplied id value(s).
     * For composite keys pass values in the same order the {@code @Id} fields are declared.
     *
     * <p>Raw data is fetched on the I/O thread, then the pipeline switches to the Bukkit
     * main thread before ORM mapping, so TypeHandlers that call Bukkit API are safe.
     *
     * @throws IllegalArgumentException if the wrong number of id values is supplied
     */
    public Mono<T> findById(final Object... ids) {
        if (ids.length != mapper.idCount()) {
            throw new IllegalArgumentException(
                "Entity has " + mapper.idCount() + " @Id field(s) but " + ids.length + " value(s) were supplied");
        }
        return contract.queryRaw(mapper.selectById(), ids)
            .publishOn(BukkitSchedulers.mainThread())
            .mapNotNull(mapper::fromRawRow);
    }

    /** Returns every row in the entity's table. See {@link #findById} for threading semantics. */
    public Flux<T> findAll() {
        return contract.queryManyRaw(mapper.selectAll())
            .publishOn(BukkitSchedulers.mainThread())
            .map(mapper::fromRawRow);
    }

    /**
     * Returns a fluent {@link QueryBuilder} for constructing parameterized
     * SELECT / DELETE queries with WHERE / ORDER BY / LIMIT / OFFSET clauses.
     *
     * <pre>{@code
     * repo.query()
     *     .where("name", "=", "Steve")
     *     .where("health", ">", 10)
     *     .orderBy("health", Order.DESC)
     *     .limit(10)
     *     .findAll();
     * }</pre>
     */
    public QueryBuilder<T> query() {
        return new QueryBuilder<>(mapper, contract);
    }

    /**
     * Inserts the entity, omitting {@code @Id} fields so the database can auto-generate
     * them. Use {@link #saveWithId} when you control the primary key.
     */
    public Mono<Integer> save(final T entity) {
        return contract.update(mapper.insert(), mapper.valuesFor(entity));
    }

    /**
     * Inserts the entity (omitting {@code @Id} fields) and returns the generated key.
     * Returns {@code -1} if the driver did not return a generated key.
     */
    public Mono<Long> saveAndReturnId(final T entity) {
        return contract.updateReturnKey(mapper.insert(), mapper.valuesFor(entity));
    }

    /** Inserts the entity including its {@code @Id} field value(s). */
    public Mono<Integer> saveWithId(final T entity) {
        return contract.update(mapper.insertWithId(), mapper.valuesWithIdFor(entity));
    }

    /**
     * Inserts or updates the row identified by the entity's {@code @Id} field(s).
     * The exact SQL depends on the dialect:
     * MySQL / MariaDB — {@code ON DUPLICATE KEY UPDATE};
     * PostgreSQL / H2 / SQLite — {@code ON CONFLICT (...) DO UPDATE SET}.
     */
    public Mono<Integer> upsert(final T entity) {
        return contract.update(mapper.upsert(), mapper.valuesWithIdFor(entity));
    }

    /** Updates all non-{@code @Id} columns for the row matching the entity's id(s). */
    public Mono<Integer> update(final T entity) {
        return contract.update(mapper.updateById(), mapper.valuesForUpdate(entity));
    }

    /** Deletes the row(s) whose primary key matches the entity's {@code @Id} field(s). */
    public Mono<Integer> delete(final T entity) {
        return contract.update(mapper.deleteById(), mapper.idFor(entity));
    }

    /**
     * Batch-inserts a list of entities in a single JDBC batch call, omitting {@code @Id}
     * fields. More efficient than calling {@link #save} in a loop.
     */
    public Mono<int[]> saveAll(final List<T> entities) {
        final List<Object[]> paramSets = entities.stream().map(mapper::valuesFor).toList();
        return contract.executeBatch(mapper.insert(), paramSets);
    }

    /**
     * Batch-updates a list of entities by their id(s) in a single JDBC batch call.
     */
    public Mono<int[]> updateAllById(final List<T> entities) {
        final List<Object[]> paramSets = entities.stream().map(mapper::valuesForUpdate).toList();
        return contract.executeBatch(mapper.updateById(), paramSets);
    }

    /**
     * Batch-deletes a list of entities by their id(s) in a single JDBC batch call.
     */
    public Mono<int[]> deleteAllById(final List<T> entities) {
        final List<Object[]> paramSets = entities.stream().map(mapper::idFor).toList();
        return contract.executeBatch(mapper.deleteById(), paramSets);
    }

    /**
     * Runs {@code work} inside a single JDBC transaction. Committed on normal return,
     * rolled back on any exception. All operations inside the callback share the same
     * underlying connection.
     *
     * <pre>{@code
     * repo.transaction(ctx -> {
     *     ctx.update("INSERT INTO ...", ...);
     *     ctx.update("UPDATE ...  SET ...", ...);
     *     return null;
     * }).subscribe();
     * }</pre>
     */
    public <R> Mono<R> transaction(final TransactionWork<R> work) {
        return contract.transaction(work);
    }

    /** Runs a custom SELECT and maps the first row to T via the two-phase pipeline. */
    public Mono<T> query(final String sql, final Object... params) {
        return contract.queryRaw(sql, params)
            .publishOn(BukkitSchedulers.mainThread())
            .mapNotNull(mapper::fromRawRow);
    }

    /** Runs a custom SELECT and maps every row to T via the two-phase pipeline. */
    public Flux<T> queryMany(final String sql, final Object... params) {
        return contract.queryManyRaw(sql, params)
            .publishOn(BukkitSchedulers.mainThread())
            .map(mapper::fromRawRow);
    }

    /** Runs an arbitrary SELECT with a custom row mapper (runs on the I/O thread). */
    public <R> Mono<R> rawQuery(final String sql, final ResultSetMapper<R> resultMapper, final Object... params) {
        return contract.query(sql, resultMapper, params);
    }

    /** Runs an arbitrary SELECT with a custom row mapper for every row. */
    public <R> Flux<R> rawQueryMany(final String sql, final ResultSetMapper<R> rowMapper, final Object... params) {
        return contract.queryMany(sql, rowMapper, params);
    }

    /** Executes an arbitrary DML or DDL statement. */
    public Mono<Integer> rawUpdate(final String sql, final Object... params) {
        return contract.update(sql, params);
    }

    /** Returns the underlying {@link SQLContract} for advanced use-cases. */
    public SQLContract contract() {
        return contract;
    }
}
