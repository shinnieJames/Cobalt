package com.github.auties00.cobalt.store.persistent.mdbx;

import java.lang.foreign.MemorySegment;
import java.util.function.Function;

/**
 * A single unit of work submitted by a producer thread to the {@code PersistentMessageStore} writer
 * thread.
 *
 * <p>Every mutation of the libmdbx env (chat, newsletter and status puts and deletes) is funnelled
 * through an {@code MdbxWriteOp} so that the dedicated writer can batch many of them into one libmdbx
 * write transaction and pay a single {@code fsync} per batch. Producers enqueue the op and block in
 * {@link #await()} until the writer has committed the batch the op belongs to, which preserves the
 * synchronous-durable contract of the accessor that created it.
 *
 * @implNote
 * This implementation signals completion through the op's own intrinsic monitor
 * ({@code synchronized} plus {@link Object#wait()} / {@link Object#notifyAll()}) rather than a
 * {@code CompletableFuture}. On a virtual thread {@link #await()} unmounts the carrier while parked
 * (monitor waits no longer pin since JEP 491), so a blocked producer does not hold a scheduler
 * carrier. The mutation is carried as a {@link Function} the writer applies inside an active write
 * transaction; it must be safe to re-run on a fresh transaction because the writer retries it per-op
 * when a batched commit overflows with {@code MDBX_TXN_FULL}.
 */
public final class MdbxWriteOp {
    /**
     * The mutation to apply, taking the active write transaction handle and returning the accessor's
     * result (or {@code null} for {@code void} accessors).
     */
    public final Function<MemorySegment, Object> action;

    /**
     * The result the writer stores after applying {@link #action}; read by {@link #await()} once the
     * owning batch commits.
     */
    public Object result;

    /**
     * The failure that aborted this op, or {@code null} if it committed successfully; guarded by this
     * op's monitor.
     */
    private RuntimeException error;

    /**
     * Whether the writer has finished this op (committed or failed); guarded by this op's monitor.
     */
    private boolean done;

    /**
     * Constructs a write op around the given mutation.
     *
     * @param action the mutation to apply inside the writer's transaction
     */
    public MdbxWriteOp(Function<MemorySegment, Object> action) {
        this.action = action;
    }

    /**
     * Blocks the calling producer thread until the writer has durably committed or failed this op,
     * then returns the result or rethrows the failure.
     *
     * @apiNote
     * The wait is uninterruptible in the sense that an interrupt does not abandon a write already in
     * flight: it is remembered and re-asserted on the calling thread before returning.
     *
     * @return the result produced by {@link #action}
     * @throws RuntimeException the failure recorded by the writer, if the op did not commit
     */
    public synchronized Object await() {
        var interrupted = false;
        while (!done) {
            try {
                wait();
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        if (error != null) {
            throw error;
        }
        return result;
    }

    /**
     * Marks this op durably committed and wakes the producer parked in {@link #await()}.
     *
     * @apiNote
     * Called by the writer after the owning batch commits; {@link #result} must already be set.
     */
    public synchronized void complete() {
        done = true;
        notifyAll();
    }

    /**
     * Marks this op failed with {@code error} and wakes the producer parked in {@link #await()}.
     *
     * @param error the failure to rethrow from {@link #await()}
     */
    public synchronized void fail(RuntimeException error) {
        this.error = error;
        done = true;
        notifyAll();
    }
}
