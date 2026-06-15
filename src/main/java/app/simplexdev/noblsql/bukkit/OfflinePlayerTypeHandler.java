package app.simplexdev.noblsql.bukkit;

import app.simplexdev.noblsql.api.data.TypeHandler;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

/**
 * Stores an {@link OfflinePlayer} as a {@code VARCHAR(36)} UUID string.
 *
 * <h3>Threading</h3>
 * <p>{@link #toSql} reads the player's UUID and is safe from any thread.
 * {@link #fromSql} calls {@link Bukkit#getOfflinePlayer(UUID)} which is a
 * Bukkit API call — <strong>this must execute on the Bukkit main thread</strong>.
 *
 * <p>Because NoblSQL's JDBC layer processes ResultSets on an async I/O thread,
 * do <em>not</em> use this handler for fields that must be populated during
 * standard repository queries. Instead, store the UUID string (see
 * {@link UUIDTypeHandler}) and resolve the player in a
 * {@code .publishOn(BukkitSchedulers.mainThread())} stage:
 *
 * <pre>{@code
 * repo.findById(id)
 *     .publishOn(BukkitSchedulers.mainThread())
 *     .subscribe(record -> {
 *         OfflinePlayer player = Bukkit.getOfflinePlayer(record.getPlayerId());
 *     });
 * }</pre>
 *
 * <p>This handler is provided for manual mapping contexts where the caller
 * guarantees main-thread execution.
 */
public final class OfflinePlayerTypeHandler implements TypeHandler<OfflinePlayer> {
    @Override
    public Object toSql(final OfflinePlayer value) {
        return value == null ? null : value.getUniqueId().toString();
    }

    @Override
    public OfflinePlayer fromSql(final Object value) {
        if (value == null) return null;
        return Bukkit.getOfflinePlayer(UUID.fromString(value.toString()));
    }
}
