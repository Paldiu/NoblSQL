package app.simplexdev.noblsql.bukkit;

import app.simplexdev.noblsql.api.data.TypeHandler;
import org.bukkit.inventory.ItemStack;

/**
 * Stores an {@link ItemStack} as a raw byte array using Paper's built-in
 * binary serialization ({@code ItemStack#serializeAsBytes} /
 * {@code ItemStack#deserializeBytes}).
 *
 * <p>This handler operates on item data only and does not call any
 * server-state Bukkit API, making it safe to use from any thread.
 * Map the field to {@code @Blob} in your entity:
 *
 * <pre>{@code
 * @Column("item_data")
 * @Blob
 * @Handles(ItemStackTypeHandler.class)
 * ItemStack item;
 * }</pre>
 */
public final class ItemStackTypeHandler implements TypeHandler<ItemStack> {
    @Override
    public Object toSql(final ItemStack value) {
        return value == null ? null : value.serializeAsBytes();
    }

    @Override
    public ItemStack fromSql(final Object value) {
        if (value == null) return null;
        if (value instanceof byte[] bytes) return ItemStack.deserializeBytes(bytes);
        throw new IllegalArgumentException(
            "ItemStackTypeHandler expects a byte[] from the ResultSet, got: " + value.getClass().getName()
        );
    }
}
