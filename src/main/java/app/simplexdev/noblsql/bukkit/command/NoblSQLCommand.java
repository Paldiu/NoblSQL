package app.simplexdev.noblsql.bukkit.command;

import app.simplexdev.noblsql.NoblSQL;
import app.simplexdev.noblsql.api.sql.PoolStats;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class NoblSQLCommand {

    private NoblSQLCommand() {}

    public static void register(final Commands registrar, final NoblSQL plugin) {
        registrar.register(
            Commands.literal("noblsql")
                .requires(source -> source.getSender().hasPermission("noblsql.admin"))
                .then(Commands.literal("status")
                    .executes(ctx -> status(ctx, plugin)))
                .then(Commands.literal("reload")
                    .executes(ctx -> reload(ctx, plugin)))
                .build(),
            "NoblSQL management commands.",
            List.of("nsql")
        );
    }

    private static int status(final CommandContext<CommandSourceStack> ctx, final NoblSQL plugin) {
        final CommandSender sender = ctx.getSource().getSender();

        sender.sendMessage(Component.text("--- NoblSQL Status ---", NamedTextColor.GOLD));

        final boolean connected = plugin.getSharedContract().isConnected();
        sender.sendMessage(Component.text()
            .append(Component.text("SQL  ", NamedTextColor.YELLOW))
            .append(connected
                ? Component.text("Connected", NamedTextColor.GREEN)
                : Component.text("Disconnected", NamedTextColor.RED))
            .append(Component.text("  |  dialect: ", NamedTextColor.GRAY))
            .append(Component.text(plugin.getSharedDialect().name(), NamedTextColor.WHITE))
            .build());

        final PoolStats stats = plugin.getSharedContract().poolStats();
        if (stats != null) {
            sender.sendMessage(Component.text()
                .append(Component.text("Pool ", NamedTextColor.YELLOW))
                .append(Component.text(
                    "active=" + stats.active() + "  idle=" + stats.idle()
                        + "  total=" + stats.total() + "  waiting=" + stats.waiting(),
                    NamedTextColor.WHITE))
                .build());
        }

        final boolean redisUp = plugin.getRedisContract() != null
            && plugin.getRedisContract().isConnected();
        final boolean redisEnabled = plugin.getConfig().getBoolean("redis.enabled", false);
        sender.sendMessage(Component.text()
            .append(Component.text("Redis", NamedTextColor.YELLOW))
            .append(Component.text("  "))
            .append(!redisEnabled
                ? Component.text("Disabled", NamedTextColor.GRAY)
                : redisUp
                    ? Component.text("Connected", NamedTextColor.GREEN)
                    : Component.text("Disconnected", NamedTextColor.RED))
            .build());

        return Command.SINGLE_SUCCESS;
    }

    private static int reload(final CommandContext<CommandSourceStack> ctx, final NoblSQL plugin) {
        final CommandSender sender = ctx.getSource().getSender();
        sender.sendMessage(Component.text("Reconnecting SQL pool...", NamedTextColor.YELLOW));

        final boolean ok = plugin.reconnect();
        if (ok) {
            sender.sendMessage(Component.text("SQL pool reconnected.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(
                "Reconnect failed — check the server log for details.", NamedTextColor.RED));
        }
        return Command.SINGLE_SUCCESS;
    }
}
