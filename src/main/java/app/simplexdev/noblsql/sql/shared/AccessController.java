package app.simplexdev.noblsql.sql.shared;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fair, non-blocking access gate that prevents more queries from reaching the
 * connection pool than there are available connections.
 *
 * Fairness is provided by {@link Semaphore}'s FIFO waiting queue: the query
 * that has been waiting longest always receives the next available permit.
 * Permit release is guaranteed on completion, error, and cancellation — including
 * the case where a subscriber cancels while {@code acquire()} is still blocked.
 */
public final class AccessController {
    private final Semaphore semaphore;

    /**
     * @param permits maximum number of concurrently executing queries;
     *                should match the HikariCP / JedisPool max pool size.
     */
    public AccessController(final int permits) {
        this.semaphore = new Semaphore(permits, true);
    }

    /**
     * Guard a single-row query. The permit is held for the entire duration of the
     * query, including the time spent mapping the ResultSet to the return type.
     * 
     * @param <T>   the type of the result
     * @param query the mono representing the single-row query
     * @return a mono that acquires a permit before subscribing and releases it afterwards
     */
    public <T> Mono<T> guard(final Mono<T> query) {
        return Mono.usingWhen(
            acquire(),
            __ -> query,
            __ -> release()
        );
    }

    /**
     * Guard a multi-row query or stream of results. The permit is held for the
     * entire duration of the query, so the connection is not returned to the pool
     * until the last row is emitted.
     * 
     * @param <T>   the type of elements in the flux
     * @param query the flux representing the multi-row query
     * @return a flux that acquires a permit before subscribing and releases it afterwards
     */
    public <T> Flux<T> guard(final Flux<T> query) {
        return Flux.usingWhen(
            acquire(),
            __ -> query,
            __ -> release(),
            (__, err) -> release(),
            __ -> release()
        );
    }

    /** Number of permits currently available (for diagnostics / metrics). */
    public int availablePermits() {
        return semaphore.availablePermits();
    }

    /** Number of queries currently queued waiting for a permit. */
    public int queueLength() {
        return semaphore.getQueueLength();
    }

    private Mono<Boolean> acquire() {
        return Mono.<Boolean>create(sink -> {
            final Thread thread = Thread.currentThread();
            final AtomicBoolean cancelled = new AtomicBoolean(false);
            sink.onCancel(() -> {
                cancelled.set(true);
                thread.interrupt();
            });
            try {
                semaphore.acquire();
                if (cancelled.get()) {
                    semaphore.release();
                } else {
                    sink.success(Boolean.TRUE);
                }
            } catch (final InterruptedException e) {
                sink.error(e);
            } finally {
                Thread.interrupted();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Void> release() {
        return Mono.fromRunnable(semaphore::release);
    }
}
