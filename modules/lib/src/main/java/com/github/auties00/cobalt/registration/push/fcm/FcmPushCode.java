package com.github.auties00.cobalt.registration.push.fcm;

import java.io.IOException;

/**
 * Single-value, set-once synchronisation primitive that hands the
 * verification code received over the FCM MCS stream to the caller of
 * {@link FcmClient#getPushCode()}.
 *
 * @apiNote
 * The producer is {@link FcmMcsConnection}, which calls
 * {@link #deliver(String)} once a {@code registration_code} entry
 * appears in an incoming {@code app_data} payload. The consumer is
 * {@link FcmClient}, which blocks in {@link #waitForCode()} until the
 * value arrives or {@link #close()} is invoked.
 *
 * @implNote
 * This implementation uses plain {@code synchronized} plus
 * {@code wait}/{@code notifyAll} rather than
 * {@link java.util.concurrent.CompletableFuture} or
 * {@link java.util.concurrent.locks.ReentrantLock}; JEP 491 (JDK 24)
 * removed carrier-thread pinning on {@code synchronized}, which makes
 * wait/notify fully virtual-thread friendly and cheaper than the
 * lock-based alternatives.
 */
final class FcmPushCode {
    /**
     * Lock guarding {@link #code} and {@link #closed}.
     *
     * @apiNote
     * A dedicated monitor (rather than {@code synchronized (this)})
     * hides the locking strategy from callers that may inadvertently
     * synchronise on the holder.
     */
    private final Object lock;

    /**
     * The delivered verification code, or {@code null} until the first
     * {@link #deliver(String)} call lands.
     *
     * @apiNote
     * Stays set for the lifetime of the holder so a code arriving
     * before {@link #waitForCode()} is called is not lost.
     */
    private String code;

    /**
     * Closed flag flipped by {@link #close()}.
     *
     * @apiNote
     * Read by {@link #waitForCode()} on every wakeup so any thread
     * parked when the holder is closed unblocks and surfaces an
     * {@link IOException} rather than waiting forever.
     */
    private boolean closed;

    /**
     * Constructs an empty holder.
     *
     * @apiNote
     * Package-private; the only construction site is
     * {@link FcmClient}, which publishes the holder to its single
     * producer ({@link FcmMcsConnection}) and one or more consumers
     * via a {@code final} field on the containing client.
     */
    FcmPushCode() {
        this.lock = new Object();
    }

    /**
     * Blocks the calling thread until either {@link #deliver(String)}
     * stores a value or {@link #close()} releases the waiters.
     *
     * @apiNote
     * Returns immediately if a value was already delivered before the
     * call. Safe to call from multiple threads concurrently; every
     * caller observes the same delivered value.
     *
     * @return the delivered verification code
     * @throws InterruptedException if the caller is interrupted while
     *                              waiting
     * @throws IOException          if the holder was closed before any
     *                              code arrived
     */
    String waitForCode() throws InterruptedException, IOException {
        synchronized (lock) {
            while (code == null && !closed) {
                lock.wait();
            }
            if (code == null) {
                throw new IOException("FcmPushCode is closed");
            }
            return code;
        }
    }

    /**
     * Stores the first verification code seen and wakes every waiter
     * blocked in {@link #waitForCode()}.
     *
     * @apiNote
     * Subsequent invocations are no-ops: WhatsApp's registration flow
     * only ever sends one code per session, and replays after MCS
     * reconnect must surface the original value rather than overwrite
     * it. A {@code null} code (the {@code app_data} entry carried no
     * value) is silently dropped.
     *
     * @param code the verification code value, or {@code null} to drop
     */
    void deliver(String code) {
        if (code == null) {
            return;
        }
        synchronized (lock) {
            if (this.code == null) {
                this.code = code;
                lock.notifyAll();
            }
        }
    }

    /**
     * Marks the holder closed and wakes every pending waiter so they
     * can observe the close and throw.
     *
     * @apiNote
     * Idempotent; called by {@link FcmClient#close()} as part of the
     * lifecycle teardown.
     */
    void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            lock.notifyAll();
        }
    }
}
