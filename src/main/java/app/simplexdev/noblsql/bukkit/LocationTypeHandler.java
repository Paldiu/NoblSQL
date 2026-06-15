package app.simplexdev.noblsql.bukkit;

import app.simplexdev.noblsql.api.data.TypeHandler;
import org.bukkit.Location;

/**
 * Stores a {@link Location} as a compact {@code TEXT} string:
 * {@code "worldName:x:y:z:yaw:pitch"}.
 *
 * <h3>Threading</h3>
 * <p>{@link #toSql} reads the world name from the Location object and is safe
 * from any thread. {@link #fromSql} deserializes the numeric components without
 * calling any Bukkit API — it returns a {@code Location} with a {@code null} world
 * reference so it can be constructed safely on the async I/O thread.
 *
 * <p>To obtain a fully resolved Location with a live {@link org.bukkit.World}
 * reference, call {@code BukkitTypeConverters.resolveLocation} from
 * within a {@code .publishOn(BukkitSchedulers.mainThread())} stage:
 *
 * <pre>{@code
 * repo.findById(id)
 *     .publishOn(BukkitSchedulers.mainThread())
 *     .subscribe(record -> {
 *         Location full = BukkitTypeConverters.resolveLocation(record.getLocation(), record.getRawSpawn());
 *         player.teleport(full);
 *     });
 * }</pre>
 *
 * <p>Map the field to {@code @Text} in your entity:
 *
 * <pre>{@code
 * @Column("spawn")
 * @Text
 * @Handles(LocationTypeHandler.class)
 * Location spawn;
 * }</pre>
 */
public final class LocationTypeHandler implements TypeHandler<Location> {
    private static final String SEP = ":";

    @Override
    public Object toSql(final Location value) {
        if (value == null) return null;
        final String worldName = value.getWorld() != null ? value.getWorld().getName() : "null";
        return worldName + SEP
            + value.getX() + SEP
            + value.getY() + SEP
            + value.getZ() + SEP
            + value.getYaw() + SEP
            + value.getPitch();
    }

    @Override
    public Location fromSql(final Object value) {
        if (value == null) return null;
        final String[] parts = value.toString().split(SEP, 6);
        if (parts.length != 6) {
            throw new IllegalArgumentException("Invalid serialized Location: '" + value + "'");
        }
        // World is intentionally null — resolve via BukkitTypeConverters on the main thread.
        return new Location(
            null,
            Double.parseDouble(parts[1]),
            Double.parseDouble(parts[2]),
            Double.parseDouble(parts[3]),
            Float.parseFloat(parts[4]),
            Float.parseFloat(parts[5])
        );
    }

    /** Extracts the world name from a serialized location string without constructing a Location. */
    public static String worldNameFrom(final Object serialized) {
        if (serialized == null) return null;
        return serialized.toString().split(SEP, 2)[0];
    }
}
