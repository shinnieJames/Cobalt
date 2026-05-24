package com.github.auties00.cobalt.call.internal.transport.sctp;

import com.github.auties00.cobalt.call.internal.transport.sctp.bindings.UsrSctp;
import com.github.auties00.cobalt.exception.WhatsAppCallException;
import com.github.auties00.cobalt.util.NativeLibLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-level singleton wrapping usrsctp's global state. Loads
 * libusrsctp on first access, drives {@code usrsctp_init_nothreads},
 * and owns the global outbound-packet upcall that dispatches to the
 * right {@link SctpAssociation}.
 *
 * <p>usrsctp is a user-mode SCTP stack: it implements the SCTP wire
 * protocol but does not own any underlying transport. Instead it
 * exposes two halves of an asynchronous I/O contract:
 *
 * <ul>
 *   <li><b>Outbound:</b> when usrsctp wants to ship an SCTP packet, it
 *       calls a single global C callback registered at init. We funnel
 *       that callback to {@link SctpAssociation#deliverOutbound} of
 *       whichever association registered the matching {@code conn_id}
 *       pointer. The bytes then travel out the call's DTLS-SRTP
 *       transport.</li>
 *   <li><b>Inbound:</b> applications call
 *       {@link SctpAssociation#feedInboundPacket} after the DTLS
 *       transport hands a record up the stack; that delegates to
 *       {@code usrsctp_conninput}, which drives the SCTP state
 *       machine and ultimately fires the per-socket receive callback.</li>
 * </ul>
 *
 * <p>Why {@code usrsctp_init_nothreads} and not {@code usrsctp_init}:
 * the {@code _nothreads} variant disables usrsctp's internal timer
 * thread, leaving timer ticks to whoever calls
 * {@link #scheduleTick}. WebRTC implementations universally drive
 * usrsctp from a single application thread to avoid the locking-order
 * pitfalls of usrsctp's own threading model.
 *
 * <p>This class is thread-safe and lazily initialised. The first
 * {@link #instance()} call loads the library, allocates the global
 * upcall stub, and runs {@code usrsctp_init_nothreads}. Subsequent
 * callers see an already-initialised engine.
 */
public final class SctpEngine {
    /**
     * Lazy-init holder so that loading libusrsctp and invoking
     * {@code usrsctp_init_nothreads} is deferred until the first
     * call that actually needs SCTP. Idiomatically thread-safe via
     * the JVM's class-init guarantee.
     */
    private static final class Holder {
        static final SctpEngine INSTANCE = new SctpEngine();
    }

    /**
     * Returns the lazily-created process singleton.
     *
     * @return the engine
     */
    public static SctpEngine instance() {
        return Holder.INSTANCE;
    }

    /**
     * Registry mapping each association's {@code conn_id} pointer
     * (the {@code void *addr} usrsctp routes by) to the owning
     * {@link SctpAssociation}. Keyed by raw address rather than
     * {@link MemorySegment} so the lookup in
     * {@link #connOutput(MemorySegment, MemorySegment, long, byte, byte)}
     * is allocation-free.
     */
    private final ConcurrentHashMap<Long, SctpAssociation> associations = new ConcurrentHashMap<>();

    /**
     * Process-lifetime arena that owns the upcall stub passed to
     * {@code usrsctp_init_nothreads}. The stub itself must live as
     * long as usrsctp does, which is "forever" since
     * {@code usrsctp_finish} is never called by Cobalt — so the
     * global arena is the right home.
     */
    private final Arena arena = Arena.global();

    /**
     * Period of the automatic ticker thread, in milliseconds. usrsctp's
     * internal timer wheel is granular to 10 ms; ticking any slower
     * loses precision on T3 retransmit windows.
     */
    private static final long TICK_PERIOD_MILLIS = 10L;

    /**
     * Daemon thread that periodically drives
     * {@code usrsctp_handle_timers}. Lives for the JVM's lifetime
     * because usrsctp is initialised once globally and never finished.
     */
    private final Thread ticker;

    /**
     * Constructs the engine: loads libusrsctp, allocates the global
     * conn-output upcall, runs {@code usrsctp_init_nothreads(0, &cb,
     * NULL)}, and starts the daemon ticker thread that drives usrsctp's
     * internal timer wheel at {@link #TICK_PERIOD_MILLIS} cadence.
     */
    private SctpEngine() {
        NativeLibLoader.load("usrsctp", arena);
        var connOutputStub = installConnOutputUpcall();
        try {
            UsrSctp.usrsctp_init_nothreads((short) 0, connOutputStub, MemorySegment.NULL);
        } catch (Throwable t) {
            throw new WhatsAppCallException.Sctp("usrsctp_init_nothreads failed", t);
        }
        this.ticker = Thread.ofVirtual()
                .name("sctp-ticker")
                .unstarted(this::runTickerLoop);
        this.ticker.setDaemon(true);
        this.ticker.start();
    }

    /**
     * Body of the daemon ticker thread. Loops forever sleeping
     * {@link #TICK_PERIOD_MILLIS} and then calling
     * {@code usrsctp_handle_timers} with the actual elapsed
     * milliseconds. Returns immediately on interrupt.
     *
     * <p>Note: when no associations are registered the call is cheap
     * (usrsctp walks an empty timer wheel). The ticker therefore runs
     * unconditionally for the JVM's lifetime rather than gating on
     * association count — gating would race with new associations
     * registered between the "empty" check and their first timer.
     */
    private void runTickerLoop() {
        var previous = System.currentTimeMillis();
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(TICK_PERIOD_MILLIS);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                return;
            }
            var now = System.currentTimeMillis();
            var elapsed = now - previous;
            previous = now;
            try {
                UsrSctp.usrsctp_handle_timers((int) Math.min(elapsed, Integer.MAX_VALUE));
            } catch (Throwable _) {
                // Swallow — the ticker must never die. usrsctp's own
                // logging will surface any internal errors.
            }
        }
    }

    /**
     * Registers an association so it receives outbound packets routed
     * by usrsctp to its {@code conn_id} pointer. Called from
     * {@link SctpAssociation}'s constructor, paired with
     * {@link #unregister}.
     *
     * @param connId the association's unique {@code conn_id} segment
     * @param assoc  the association to dispatch to
     */
    void register(MemorySegment connId, SctpAssociation assoc) {
        Objects.requireNonNull(connId, "connId cannot be null");
        Objects.requireNonNull(assoc, "assoc cannot be null");
        associations.put(connId.address(), assoc);
        try {
            UsrSctp.usrsctp_register_address(connId);
        } catch (Throwable t) {
            associations.remove(connId.address());
            throw new WhatsAppCallException.Sctp("usrsctp_register_address failed", t);
        }
    }

    /**
     * Reverses {@link #register}. Called from
     * {@link SctpAssociation#close()} before the association tears
     * down its socket — usrsctp guarantees no further outbound calls
     * will be routed to the deregistered address once this returns.
     *
     * @param connId the association's {@code conn_id} segment
     */
    void unregister(MemorySegment connId) {
        if (connId == null) {
            return;
        }
        try {
            UsrSctp.usrsctp_deregister_address(connId);
        } catch (Throwable _) {
        }
        associations.remove(connId.address());
    }

    /**
     * Allocates the global conn-output upcall and returns its stub
     * address for passing to {@code usrsctp_init_nothreads}.
     *
     * @return the upcall stub address
     */
    private MemorySegment installConnOutputUpcall() {
        var descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_BYTE,
                ValueLayout.JAVA_BYTE);
        MethodHandle target;
        try {
            target = MethodHandles.lookup().findVirtual(
                    SctpEngine.class,
                    "connOutput",
                    MethodType.methodType(
                            int.class, MemorySegment.class, MemorySegment.class,
                            long.class, byte.class, byte.class));
        } catch (ReflectiveOperationException e) {
            throw new WhatsAppCallException.Sctp("could not locate conn_output method", e);
        }
        target = target.bindTo(this);
        return Linker.nativeLinker().upcallStub(target, descriptor, arena);
    }

    /**
     * Global outbound-packet callback installed in usrsctp at init.
     * Routes by the {@code addr} pointer that the firing association
     * registered: looks up the {@link SctpAssociation} in
     * {@link #associations} and forwards the packet bytes to it.
     *
     * @param addr   the {@code conn_id} segment whose owner produced
     *               this packet
     * @param buffer pointer to {@code length} bytes of SCTP wire
     *               payload
     * @param length number of bytes at {@code buffer}
     * @param tos    SCTP-level type-of-service (ignored)
     * @param setDf  SCTP-level don't-fragment hint (ignored)
     * @return 0 on success — usrsctp will retry on non-zero
     */
    @SuppressWarnings("unused")
    private int connOutput(MemorySegment addr, MemorySegment buffer, long length, byte tos, byte setDf) {
        if (length <= 0) {
            return 0;
        }
        var assoc = associations.get(addr.address());
        if (assoc == null) {
            return 0;
        }
        try {
            var packet = buffer.reinterpret(length).toArray(ValueLayout.JAVA_BYTE);
            assoc.deliverOutbound(packet);
            return 0;
        } catch (Throwable t) {
            return 1;
        }
    }

    /**
     * Drives one immediate timer-wheel tick with the canonical
     * {@link #TICK_PERIOD_MILLIS} elapsed-window. Used by tests that
     * want to exercise SCTP retransmit/heartbeat logic deterministically
     * without waiting for the background ticker thread.
     *
     * <p>The background ticker started in the constructor is what
     * production paths rely on; explicit calls here are additive (the
     * timer wheel tolerates over-frequency ticks).
     */
    public void scheduleTick() {
        try {
            UsrSctp.usrsctp_handle_timers((int) TICK_PERIOD_MILLIS);
        } catch (Throwable t) {
            throw new WhatsAppCallException.Sctp("usrsctp_handle_timers failed", t);
        }
    }
}
