package app.simplexdev.noblsql.bukkit;

import app.simplexdev.noblsql.api.data.TypeHandler;

import java.util.UUID;

/**
 * Stores a {@link UUID} as a {@code VARCHAR(36)} string.
 *
 * <p>This handler calls no Bukkit API and is safe to use from any thread.
 *
 * <pre>{@code
    @Column("player_id")
    @Varchar(36)
    @NotNull
    @Handles(UUIDTypeHandler.class)
    UUID playerId;
  }</pre>
 */
public final class UUIDTypeHandler implements TypeHandler<UUID> {
    @Override
    public Object toSql(final UUID value) {
        return value == null ? null : value.toString();
    }

    @Override
    public UUID fromSql(final Object value) {
        return value == null ? null : UUID.fromString(value.toString());
    }
}
