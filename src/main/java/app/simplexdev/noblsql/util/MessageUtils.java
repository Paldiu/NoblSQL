package app.simplexdev.noblsql.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class MessageUtils {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private MessageUtils() {}

    public static Component parse(final String miniMessage) {
        return MM.deserialize(miniMessage);
    }

    public static String serialize(final Component component) {
        return MM.serialize(component);
    }

    public static Component empty() {
        return Component.empty();
    }
}
