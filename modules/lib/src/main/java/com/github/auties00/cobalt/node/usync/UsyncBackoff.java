package com.github.auties00.cobalt.node.usync;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

/**
 * Per-protocol backoff timer for USync.
 *
 * <p>When the relay returns a per-protocol error with an
 * {@code error_backoff} attribute, the client is supposed to suppress further
 * USync requests for that protocol until the backoff window has elapsed.
 * WhatsApp Web stores the backoff state in a module-level {@code Map} keyed
 * by protocol name; this class is the Cobalt equivalent.
 *
 * <p>Backoff is consulted in {@link UsyncQuery#execute}: before sending the
 * request, the query waits for every active backoff associated with one of
 * its protocols, except that {@link UsyncContext#INTERACTIVE} skips the wait
 * entirely and {@link UsyncContext#MESSAGE} / {@link UsyncContext#VOIP}
 * exempt the {@code devices} protocol because the resulting send would
 * otherwise be impossible to encrypt.
 *
 * @implNote WAWebUsyncBackoff: in the JS module the map and the wait
 *     function are module-private; Cobalt promotes them to a final class
 *     with a thread-safe {@link ConcurrentHashMap} so multiple virtual
 *     threads can register backoffs concurrently.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncBackoff")
public final class UsyncBackoff {
    /**
     * Logger used for the backoff lifecycle messages WhatsApp Web emits via
     * {@code WALogger.LOG}.
     */
    private static final Logger LOGGER = Logger.getLogger(UsyncBackoff.class.getName());

    /**
     * Map of protocol name to the {@link Instant} at which the backoff
     * window expires.
     *
     * @implNote {@code WAWebUsyncBackoff.c}: a {@code Map<string, Promise>}.
     *     Cobalt stores the absolute expiry instant rather than a pending
     *     promise because virtual threads make timed waits trivial.
     */
    private final ConcurrentMap<String, Instant> backoffs;

    /**
     * Creates a new, empty backoff registry.
     */
    public UsyncBackoff() {
        this.backoffs = new ConcurrentHashMap<>();
    }

    /**
     * Records a backoff window for the given protocol.
     *
     * @param protocolName the protocol name (e.g. {@code "devices"},
     *                     {@code "contact"})
     * @param backoffMs    the duration of the backoff window, in
     *                     milliseconds
     * @implNote WAWebUsyncBackoff.setProtocolBackoffMs: schedules a
     *     {@code setTimeout(_, ms)} and stores the resulting promise in the
     *     module map. Cobalt records the absolute expiry instant instead.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncBackoff",
            exports = "setProtocolBackoffMs", adaptation = WhatsAppAdaptation.ADAPTED)
    public void setProtocolBackoffMs(String protocolName, long backoffMs) {
        var expiry = Instant.now().plusMillis(backoffMs);
        backoffs.put(protocolName, expiry);
        LOGGER.fine(() -> "usync: " + protocolName + " protocol: " + backoffMs + "ms backoff started");
    }

    /**
     * Blocks the current thread until every backoff window relevant to the
     * given query has elapsed.
     *
     * <p>Three cases short-circuit the wait:
     * <ul>
     *   <li>{@link UsyncContext#INTERACTIVE} skips backoff entirely (the
     *       user is waiting on the result).</li>
     *   <li>For {@link UsyncContext#MESSAGE} or {@link UsyncContext#VOIP},
     *       the {@code devices} protocol is exempt because failing here
     *       would block message encryption.</li>
     *   <li>Protocols whose backoff windows have already elapsed are
     *       removed from the map and skipped.</li>
     * </ul>
     *
     * @param query the query about to be dispatched
     * @throws InterruptedException if the current thread is interrupted
     *                              while sleeping
     * @implNote WAWebUsyncBackoff.waitForBackoff. The JS function returns a
     *     {@code Promise.all} of the per-protocol promises; Cobalt iterates
     *     and sleeps inline because virtual threads make
     *     {@code Thread.sleep} cheap.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncBackoff",
            exports = "waitForBackoff", adaptation = WhatsAppAdaptation.ADAPTED)
    public void waitForBackoff(UsyncQuery query) throws InterruptedException {
        if (query.context() == UsyncContext.INTERACTIVE) {
            return;
        }

        for (var protocol : query.protocols()) {
            var name = protocol.name();
            if ((query.context() == UsyncContext.MESSAGE || query.context() == UsyncContext.VOIP)
                    && "devices".equals(name)) {
                continue;
            }
            var expiry = backoffs.get(name);
            if (expiry == null) {
                continue;
            }
            var remaining = Duration.between(Instant.now(), expiry);
            if (remaining.isZero() || remaining.isNegative()) {
                backoffs.remove(name, expiry);
                LOGGER.fine(() -> "usync: " + name + " protocol backoff ended");
                continue;
            }
            Thread.sleep(remaining);
            backoffs.remove(name, expiry);
            LOGGER.fine(() -> "usync: " + name + " protocol backoff ended");
        }
    }

    /**
     * Removes any active backoff for the given protocol. Visible for testing
     * and for explicit invalidation paths.
     *
     * @param protocolName the protocol name
     */
    public void clear(String protocolName) {
        backoffs.remove(protocolName);
    }

    /**
     * Removes every active backoff. Visible for testing and for the
     * "logout / reconnect" reset path.
     */
    public void clearAll() {
        backoffs.clear();
    }
}
