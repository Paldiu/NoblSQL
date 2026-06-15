package app.simplexdev.noblsql.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Static helpers for resolving Bukkit objects that require main-thread API calls.
 *
 * <p>All methods in this class call Bukkit API and <strong>must</strong> be invoked
 * from the Bukkit main thread. Use them inside a
 * {@code .publishOn(BukkitSchedulers.mainThread())} stage after async database work:
 *
 * <pre>{@code
 * repo.findById(id)
 *     .publishOn(BukkitSchedulers.mainThread())
 *     .subscribe(record -> {
 *         Location full = BukkitTypeConverters.resolveLocation(record.getSpawn(), record.getRawSpawn());
 *         player.teleport(full);
 *     });
 * }</pre>
 */
public final class BukkitTypeConverters {
    private BukkitTypeConverters() {}

    /**
     * Resolves the {@link World} reference on a Location that was deserialized
     * from SQL with a {@code null} world (as produced by {@link LocationTypeHandler}).
     *
     * @param partial   a Location with potentially null world
     * @param worldName the world name embedded in the serialized value
     * @return the same Location with its world set, or the original if the world is not found
     */
    public static Location resolveLocation(final Location partial, final String worldName) {
        if (partial == null) return null;
        if (partial.getWorld() != null) return partial;
        final World world = Bukkit.getWorld(worldName);
        if (world != null) partial.setWorld(world);
        return partial;
    }

    /**
     * Resolves the world reference using the world name encoded in the raw serialized
     * string (stored by {@link LocationTypeHandler}).
     *
     * @param partial         the deserialized Location with null world
     * @param serializedValue the original SQL string value, e.g. {@code "world:1.0:64.0:2.0:0.0:0.0"}
     */
    public static Location resolveLocation(final Location partial, final Object serializedValue) {
        return resolveLocation(partial, LocationTypeHandler.worldNameFrom(serializedValue));
    }

    /** Returns the online {@link Player} for the given UUID, or {@code null} if offline. */
    public static Player resolveOnlinePlayer(final UUID uuid) {
        return Bukkit.getPlayer(uuid);
    }

    /** Returns the online {@link Player} for the given UUID string, or {@code null} if offline. */
    public static Player resolveOnlinePlayer(final String uuid) {
        return Bukkit.getPlayer(UUID.fromString(uuid));
    }

    /**
     * Returns the {@link OfflinePlayer} record for the given UUID.
     * This may trigger a disk lookup if the player has never joined; prefer
     * {@link #resolveOnlinePlayer} when you only care about currently connected players.
     */
    public static OfflinePlayer resolveOfflinePlayer(final UUID uuid) {
        return Bukkit.getOfflinePlayer(uuid);
    }

    /** Returns the {@link OfflinePlayer} record for the given UUID string. */
    public static OfflinePlayer resolveOfflinePlayer(final String uuid) {
        return Bukkit.getOfflinePlayer(UUID.fromString(uuid));
    }

    /** Returns the {@link World} with the given name, or {@code null} if not loaded. */
    public static World resolveWorld(final String name) {
        return Bukkit.getWorld(name);
    }

    /** Returns the {@link World} with the given UID, or {@code null} if not loaded. */
    public static World resolveWorld(final UUID uid) {
        return Bukkit.getWorld(uid);
    }
}
