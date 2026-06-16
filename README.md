# NoblSQL

A reactive, annotation-driven SQL and Redis ORM library for [Paper](https://papermc.io/) Minecraft plugins.

NoblSQL is consumed as a server-side plugin dependency. Drop the JAR into your server's `plugins/` folder
alongside your own plugin, declare the dependency, and call `NoblSQL#createRepository` to get a type-safe,
non-blocking data accessor. You never write SQL by hand for standard CRUD - the ORM handles it.

Supports SQLite, MySQL, MariaDB, PostgreSQL, and an embedded H2 server. Optional Redis caching layer included.

> Copyright (c) 2026 Paldiu. See [LICENSE.md](LICENSE.md) for terms.

---

## How It Works

1. NoblSQL starts first (it is a `depend`, so Paper loads it before your plugin).
2. It reads `plugins/NoblSQL/config.yml`, connects to the configured database, and exposes a shared
   `SQLContract` backed by a HikariCP connection pool.
3. Your plugin calls `createRepository(MyEntity.class)` to get a `NoblRepository<MyEntity>`. The
   repository inspects your entity's annotations via reflection and generates all SQL at construction time.
4. Every method on `NoblRepository` returns a Reactor `Mono<T>` or `Flux<T>`. I/O runs on
   `boundedElastic` (never the main thread). Entity mapping is automatically published back to the Bukkit
   main thread so TypeHandlers that call Bukkit API (e.g. resolving a `World`) are always safe.
5. Subscribe to the returned publisher to execute. Use `.block()` only inside an async task or
   `CompletableFuture`; never on the main thread.

---

## Adding NoblSQL as a Dependency

NoblSQL is published to the SimplexDev Maven repository. Add the repository and dependency to your
plugin's `build.gradle`:

```groovy
repositories {
    mavenCentral()
    maven { url = 'https://repo.papermc.io/repository/maven-public/' }
    maven { url = 'https://oss.simplexdev.app/releases' }
}

dependencies {
    compileOnly 'io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT'
    compileOnly 'app.simplexdev.noblsql:noblsql:0.1.0'
}
```

Then declare the runtime dependency in `plugin.yml` so Paper loads NoblSQL before your plugin:

```yaml
name: MyPlugin
version: '1.0.0'
main: com.example.myplugin.MyPlugin
api-version: '1.21'

depend:
  - NoblSQL
```

---

## Defining an Entity

Annotate a plain Java class. Every entity must have:
- A public no-arg constructor
- At least one field annotated with `@PrimaryKey` (or `@Id`)
- `@Table(name = "...")` on the class

```java
import app.simplexdev.noblsql.api.data.*;
import app.simplexdev.noblsql.api.data.type.*;
import app.simplexdev.noblsql.bukkit.UUIDTypeHandler;

import java.util.UUID;

@Table(name = "players")
public class PlayerData {

    @Column("id")
    @Varchar(36)
    @PrimaryKey
    @NotNull
    @Handles(UUIDTypeHandler.class)
    UUID id;

    @Column("name")
    @Varchar(64)
    @NotNull
    String name;

    @Column("balance")
    @Decimal(precision = 10, scale = 2)
    @Default("0.00")
    double balance;

    @Column("join_date")
    @Timestamp
    @NotNull
    String joinDate;

    // Required - NoblSQL instantiates entities via reflection
    public PlayerData() {}
}
```

### SQL type annotations

| Annotation          | SQL type                                                    |
|---------------------|-------------------------------------------------------------|
| `@Varchar(n)`       | `VARCHAR(n)` (default 255)                                  |
| `@Text`             | `TEXT`                                                      |
| `@Int`              | `INTEGER`                                                   |
| `@BigInt`           | `BIGINT`                                                    |
| `@Decimal(p, s)`    | `DECIMAL(p, s)`                                             |
| `@Bool`             | `BOOLEAN` / `TINYINT(1)` (dialect-dependent)                |
| `@Blob`             | `BLOB`                                                      |
| `@Json`             | `JSONB` (PostgreSQL) / `JSON` (MySQL, H2) / `TEXT` (SQLite) |
| `@Timestamp`        | `TIMESTAMP`                                                 |

### Constraint annotations

| Annotation               | Effect                                                      |
|--------------------------|-------------------------------------------------------------|
| `@NotNull`               | `NOT NULL` constraint                                       |
| `@Unique`                | `UNIQUE` constraint                                         |
| `@Indexed`               | Creates a separate `CREATE INDEX` statement                 |
| `@Default("value")`      | `DEFAULT value` constraint                                  |
| `@AutoIncrement`         | Auto-increment on integer primary keys                      |
| `@ForeignKey`            | Foreign key with configurable `ON DELETE` / `ON UPDATE`     |

---

## Initializing in Your Plugin

```java
public class MyPlugin extends JavaPlugin {

    private NoblRepository<PlayerData> playerRepo;

    @Override
    public void onEnable() {
        // Obtain the NoblSQL plugin instance
        NoblSQL noblSQL = (NoblSQL) getServer().getPluginManager().getPlugin("NoblSQL");
        if (noblSQL == null) {
            getLogger().severe("NoblSQL not found = disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Create a repository backed by the shared connection pool and dialect.
        // The entity's annotations are inspected once here; all SQL is generated at this point.
        playerRepo = noblSQL.createRepository(PlayerData.class);

        // Create the table and any declared indexes if they don't exist yet.
        // This is non-blocking - subscribe to kick it off.
        playerRepo.createSchema()
            .doOnError(e -> getLogger().severe("Schema creation failed: " + e.getMessage()))
            .subscribe();
    }

    @Override
    public void onDisable() {
        // NoblSQL manages its own connection shutdown - nothing to clean up here.
    }
}
```

If you need an isolated connection (e.g. a separate pool from the shared one), pass your own
`SQLContract` and `Dialect` directly:

```java
// Custom isolated connection - your plugin manages its own pool
SQLContract myContract = new MySQL(new ConnectionDetails(host, port, db, user, pass, ssl));
NoblRepository<PlayerData> isolated = noblSQL.createRepository(PlayerData.class, myContract, Dialect.MYSQL);
```

---

## CRUD

All methods return `Mono<T>` or `Flux<T>`. Call `.subscribe()` for fire-and-forget. If you need
the result synchronously (e.g. inside a `CompletableFuture` or Folia async task), call `.block()` -
but never on the Bukkit main thread.

```java
// --- INSERT ---

// Insert, letting the database generate the primary key
playerRepo.save(playerData).subscribe();

// Insert and receive the generated key
playerRepo.saveAndReturnId(playerData)
    .subscribe(generatedId -> getLogger().info("Inserted row " + generatedId));

// Insert including the entity's own primary key value
playerRepo.saveWithId(playerData).subscribe();


// --- READ ---

// Find by primary key - mapping runs on the Bukkit main thread
playerRepo.findById(player.getUniqueId())
    .subscribe(data -> {
        if (data != null) {
            player.sendMessage("Balance: " + data.balance);
        }
    });

// Find all rows
playerRepo.findAll()
    .subscribe(data -> getLogger().info(data.name + " - " + data.balance));


// --- UPDATE ---

// Update all non-PK columns for the row matching the entity's PK
playerData.balance += 100.0;
playerRepo.update(playerData).subscribe();

// Insert or update on conflict (dialect-appropriate SQL generated automatically)
playerRepo.upsert(playerData).subscribe();


// --- DELETE ---

playerRepo.delete(playerData).subscribe();
```

---

## Fluent Query Builder

Use `repo.query()` for filtered, sorted, and paginated lookups without writing SQL.

```java
// Find the first player named "Steve"
playerRepo.query()
    .where("name", "=", "Steve")
    .findFirst()
    .subscribe(data -> { /* runs on Bukkit main thread */ });

// Paginated leaderboard
playerRepo.query()
    .where("balance", ">", 0.0)
    .orderBy("balance", Order.DESC)
    .limit(10)
    .offset(0)
    .findAll()
    .subscribe(data -> getLogger().info(data.name + ": " + data.balance));

// Count inactive players
playerRepo.query()
    .where("join_date", "<", "2024-01-01 00:00:00")
    .count()
    .subscribe(n -> getLogger().info("Inactive: " + n));

// IN clause - match a set of UUIDs
playerRepo.query()
    .whereIn("id", uuids.stream().map(UUID::toString).toList())
    .findAll()
    .subscribe();

// NULL check
playerRepo.query()
    .whereNull("join_date")
    .findAll()
    .subscribe();

// OR condition
playerRepo.query()
    .where("name", "=", "Steve")
    .orWhere("name", "=", "Alex")
    .findAll()
    .subscribe();

// Delete matching rows
playerRepo.query()
    .where("balance", "=", 0.0)
    .deleteAll()
    .subscribe();

// Set a single column across matching rows
playerRepo.query()
    .where("name", "LIKE", "Griefer%")
    .updateSet("balance", 0.0)
    .subscribe();
```

---

## Batch Operations

Sends a single JDBC batch call rather than individual statements in a loop.

```java
List<PlayerData> batch = List.of(alice, bob, charlie);

playerRepo.saveAll(batch).subscribe();        // batch INSERT (auto-PK)
playerRepo.updateAllById(batch).subscribe();  // batch UPDATE WHERE id = ?
playerRepo.deleteAllById(batch).subscribe();  // batch DELETE WHERE id = ?
```

---

## Transactions

All operations inside the callback share one JDBC connection. The transaction commits on normal
return and rolls back on any exception. Savepoints let you partially roll back nested work.

```java
playerRepo.transaction(ctx -> {
    ctx.update(
        "UPDATE players SET balance = balance - ? WHERE id = ?",
        amount, senderId.toString()
    );
    ctx.update(
        "UPDATE players SET balance = balance + ? WHERE id = ?",
        amount, receiverId.toString()
    );

    // Savepoint - rolling back to here undoes the ledger insert but keeps the balance changes
    ctx.savepoint("after_transfer");

    ctx.update(
        "INSERT INTO ledger (from_id, to_id, amount) VALUES (?, ?, ?)",
        senderId.toString(), receiverId.toString(), amount
    );

    return null;
}).subscribe();
```

If you need typed results from within a transaction:

```java
playerRepo.transaction(ctx -> {
    List<Map<String, Object>> rows = ctx.queryMany(
        "SELECT * FROM players WHERE balance > ?", 1000.0
    );
    for (Map<String, Object> row : rows) {
        ctx.update("UPDATE players SET rank = 'vip' WHERE id = ?", row.get("id"));
    }
    return rows.size();
}).subscribe(promoted -> getLogger().info("Promoted " + promoted + " players"));
```

---

## Migrations

Define versioned schema changes and apply them at startup. Each migration runs exactly once;
applied versions are tracked in an internal `_noblsql_migrations` table.

```java
public class V1_AddRankColumn implements Migration {

    @Override
    public int version() { return 1; }

    @Override
    public String description() { return "Add rank column to players table"; }

    @Override
    public void up(TransactionContext ctx) throws Exception {
        ctx.update("ALTER TABLE players ADD COLUMN rank VARCHAR(32) DEFAULT 'member'");
    }
}

public class V2_AddDiscordId implements Migration {

    @Override
    public int version() { return 2; }

    @Override
    public String description() { return "Add discord_id column"; }

    @Override
    public void up(TransactionContext ctx) throws Exception {
        ctx.update("ALTER TABLE players ADD COLUMN discord_id VARCHAR(64)");
    }
}
```

Register all migrations at startup. Pending ones are applied in ascending version order:

```java
MigrationRunner runner = noblSQL.createMigrationRunner();
runner.run(List.of(new V1_AddRankColumn(), new V2_AddDiscordId()))
    .doOnError(e -> getLogger().severe("Migration failed: " + e.getMessage()))
    .subscribe();
```

---

## Custom Type Handlers

Implement `TypeHandler<T>` and attach it to a field with `@Handles` to control serialization for
any Java type not natively handled by JDBC.

```java
public class RankTypeHandler implements TypeHandler<Rank> {

    @Override
    public Object toSql(Rank value) {
        return value == null ? null : value.name();
    }

    @Override
    public Rank fromSql(Object value) {
        return value == null ? null : Rank.valueOf(value.toString());
    }
}
```

```java
@Column("rank")
@Varchar(32)
@Default("MEMBER")
@Handles(RankTypeHandler.class)
Rank rank;
```

### Built-in Bukkit type handlers

| Handler                    | Column type    | Notes                                                           |
|----------------------------|----------------|-----------------------------------------------------------------|
| `UUIDTypeHandler`          | `VARCHAR(36)`  | Thread-safe                                                     |
| `LocationTypeHandler`      | `TEXT`         | Stores as `world:x:y:z:yaw:pitch`; world reference resolved later on main thread via `BukkitTypeConverters.resolveLocation()` |
| `ItemStackTypeHandler`     | `BLOB`         | Uses Paper's `serializeAsBytes` / `deserializeBytes`            |
| `OfflinePlayerTypeHandler` | `VARCHAR(36)`  | `fromSql` calls Bukkit API - must run on main thread (it does by default via two-phase pipeline) |

---

## Query Interceptors (Handler Chain)

Every query routed through the shared contract passes through a priority-ordered handler chain.
Register a `QueryHandler` to audit, modify, or cancel queries before they reach the database.

```java
import app.simplexdev.noblsql.api.handler.*;

@QueryInterceptor(priority = 50, intercepts = { QueryType.SELECT, QueryType.UPDATE })
public class AuditHandler implements QueryHandler {

    private final Logger logger;

    public AuditHandler(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void handle(QueryContext ctx) {
        logger.info("[Audit] " + ctx.queryType() + " -> " + ctx.sql());

        // Optionally cancel - query will not reach the database
        // ctx.cancel();
    }
}
```

```java
// Register in onEnable - handlers added here apply to all repos sharing the contract
noblSQL.registerHandler(new AuditHandler(getLogger()));

// Unregister when your plugin disables (good practice, NoblSQL cleans up on server stop anyway)
noblSQL.unregisterHandler(auditHandler);
```

Higher priority values run first. Built-in handlers: `ValidationHandler` at `Integer.MAX_VALUE`,
`LoggingHandler` at `Integer.MAX_VALUE - 1`.

---

## Redis

Enable in `config.yml` (`redis.enabled: true`). The contract is `null` if Redis is disabled or
failed to connect, so always null-check before use.

```java
RedisContract redis = noblSQL.getRedisContract();
if (redis == null) return; // Redis not available

// Cache a value with a 5-minute TTL
redis.set("session:" + uuid, token)
    .then(redis.expire("session:" + uuid, 300))
    .subscribe();

// Read from cache
redis.get("session:" + uuid)
    .subscribe(token -> {
        if (token != null) {
            // cache hit - skip database lookup
        }
    });

// Check existence
redis.exists("session:" + uuid)
    .subscribe(exists -> {
        if (!exists) refreshSession(uuid);
    });

// Delete
redis.delete("session:" + uuid).subscribe();
```

---

## Configuration (NoblSQL/config.yml)

This file lives in the NoblSQL plugin folder, not yours. Server operators configure the database
connection here. Your plugin only needs the `depend` declaration in `plugin.yml`.

```yaml
# Driver: sqlite | mysql | mariadb | postgresql
sql_type: sqlite

# Set true to launch a bundled embedded H2 server instead of an external database.
# Useful for development or single-server setups that don't need a separate DB process.
use_internal: false
internal:
  port: 9092
  database: noblsql
  username: noblsql
  password: 'changeme'   # must be non-empty when use_internal is true

# External database (used when sql_type != sqlite and use_internal is false)
address: localhost
port: 3306
database: noblsql
username: root
password: ''
require_ssl: false

redis:
  enabled: false
  host: localhost
  port: 6379
  password: ''
  max_connections: 10
```

---

## In-game Admin Commands

Requires the `noblsql.admin` permission.

| Command              | Description                                              |
|----------------------|----------------------------------------------------------|
| `/noblsql status`    | Displays driver, dialect, HikariCP pool stats, Redis     |
| `/noblsql reload`    | Tears down and rebuilds the connection pool              |

---

## License

Copyright (c) 2026 Paldiu. Distributed under the NoblSQL Source-Available License - see
[LICENSE.md](LICENSE.md). You may view, study, and use the compiled plugin and contribute patches
back. Redistribution, sale, and incorporation into proprietary software require written permission.
