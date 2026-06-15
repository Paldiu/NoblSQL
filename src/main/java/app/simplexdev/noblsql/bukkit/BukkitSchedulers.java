package app.simplexdev.noblsql.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

public final class BukkitSchedulers {
    private static Scheduler mainThread;

    private BukkitSchedulers() {}

    public static void init(final Plugin plugin) {
        init(Schedulers.fromExecutor(Bukkit.getScheduler().getMainThreadExecutor(plugin)));
    }

    /**
     * Initialises with an explicit {@link Scheduler}. Useful for testing or embedding
     * NoblSQL outside of a Bukkit server where a real plugin instance is unavailable.
     */
    public static void init(final Scheduler scheduler) {
        mainThread = scheduler;
    }

    /**
     * Reactor {@link Scheduler} backed by Bukkit's main-thread executor.
     *
     * <p>Any operation that touches the Paper/Bukkit API <em>must</em> run here —
     * the server enforces main-thread access for most of its API surface. Use
     * {@code .publishOn(BukkitSchedulers.mainThread())} to hop back after async
     * database or network work:
     *
     * <pre>{@code
     * repo.findById(uuid)
     *     .publishOn(BukkitSchedulers.mainThread())
     *     .subscribe(data -> player.sendMessage(data.toString()));
     * }</pre>
     *
     * <p>Non-Bukkit work (JDBC, Redis, pure-Java logic) must <em>not</em> use this
     * scheduler — keep those operations on {@code Schedulers.boundedElastic()}.
     */
    public static Scheduler mainThread() {
        if (mainThread == null) {
            throw new IllegalStateException(
                "BukkitSchedulers has not been initialised. Call BukkitSchedulers.init(plugin) in onEnable()."
            );
        }
        return mainThread;
    }
}
