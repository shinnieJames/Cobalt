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
 * Owns usrsctp's process-global state as a singleton: it loads the native library, initialises the stack, drives the
 * timer wheel, and routes outbound packets to the owning {@link SctpAssociation}.
 *
 * <p>usrsctp is a user-mode SCTP stack: it implements the SCTP wire protocol but owns no underlying transport, exposing
 * instead two halves of an asynchronous I/O contract. On the outbound side, when usrsctp wants to ship a packet it
 * calls a single global C callback registered at init; this engine funnels that callback to
 * {@link SctpAssociation#deliverOutbound(byte[])} of whichever association registered the matching {@code conn_id}
 * pointer, from where the bytes travel out the call's DTLS-SRTP transport. On the inbound side, callers invoke
 * {@link SctpAssociation#feedInboundPacket(byte[])} after the DTLS transport hands a record up the stack, which
 * delegates to {@code usrsctp_conninput} and drives the SCTP state machine until the per-socket receive callback fires.
 *
 * <p>The engine is thread-safe and lazily initialised: the first {@link #instance()} call loads the library, allocates
 * the global upcall stub, runs the stack initialiser, and starts the timer thread, while subsequent callers see the
 * already-initialised engine.
 *
 * @implNote This implementation uses {@code usrsctp_init_nothreads} rather than {@code usrsctp_init} so usrsctp runs no
 * internal timer thread; the timer wheel is instead advanced from a single daemon thread this engine owns. WebRTC stacks
 * universally drive usrsctp from one application thread to avoid the locking-order pitfalls of usrsctp's own threading
 * model. {@code usrsctp_finish} is never called, so the stack and its upcall stub live for the lifetime of the JVM.
 */
public final class SctpEngine {
    /**
     * Holds the lazily-initialised singleton, deferring native-library loading and stack initialisation until first
     * use.
     *
     * @implNote This implementation relies on the JVM's class-initialisation guarantee for thread-safe one-time
     * construction, so loading libusrsctp and running {@code usrsctp_init_nothreads} happen on the first call that
     * actually needs SCTP.
     */
    private static final class Holder {
        /**
         * Holds the single engine instance for the process.
         */
        static final SctpEngine INSTANCE = new SctpEngine();
    }

    /**
     * Returns the lazily-created process singleton, initialising it on the first call.
     *
     * @return the engine
     */
    public static SctpEngine instance() {
        return Holder.INSTANCE;
    }

    /**
     * Maps each association's {@code conn_id} pointer to the owning {@link SctpAssociation}.
     *
     * <p>The map is keyed by the raw pointer address rather than by {@link MemorySegment} so the lookup in
     * {@link #connOutput(MemorySegment, MemorySegment, long, byte, byte)}, which runs on every outbound packet, is
     * allocation-free.
     */
    private final ConcurrentHashMap<Long, SctpAssociation> associations = new ConcurrentHashMap<>();

    /**
     * Holds the process-lifetime arena that owns the conn-output upcall stub.
     *
     * @implNote This implementation uses {@link Arena#global()} because the stub must outlive usrsctp, which is never
     * finished, so a global arena is the only home with a matching lifetime.
     */
    private final Arena arena = Arena.global();

    /**
     * Holds the period, in milliseconds, of the automatic ticker thread.
     *
     * @implNote This implementation ticks every 10 ms because usrsctp's internal timer wheel is granular to 10 ms;
     * ticking any slower loses precision on the T3 retransmit windows.
     */
    private static final long TICK_PERIOD_MILLIS = 10L;

    /**
     * Holds the daemon thread that periodically drives {@code usrsctp_handle_timers}.
     *
     * <p>The thread lives for the JVM's lifetime because usrsctp is initialised once globally and never finished.
     */
    private final Thread ticker;

    /**
     * Constructs the engine, initialising the usrsctp stack and starting the timer thread.
     *
     * <p>Construction loads libusrsctp into the global arena, allocates the global conn-output upcall, runs
     * {@code usrsctp_init_nothreads(0, &cb, NULL)}, then starts the daemon ticker thread that advances usrsctp's timer
     * wheel at the {@link #TICK_PERIOD_MILLIS} cadence.
     *
     * @throws WhatsAppCallException.Sctp if {@code usrsctp_init_nothreads} fails
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
     * Runs the daemon ticker loop until the thread is interrupted.
     *
     * <p>Each iteration sleeps {@link #TICK_PERIOD_MILLIS} and then calls {@code usrsctp_handle_timers} with the actual
     * elapsed milliseconds, capped at {@link Integer#MAX_VALUE}. Any exception from the native call is swallowed so the
     * ticker never dies; usrsctp's own logging surfaces internal errors.
     *
     * @implNote This implementation ticks unconditionally for the JVM's lifetime rather than gating on the association
     * count: when no associations are registered the call is cheap (usrsctp walks an empty timer wheel), and gating
     * would race with associations registered between the emptiness check and their first timer.
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
            }
        }
    }

    /**
     * Registers an association so usrsctp routes its outbound packets back to it.
     *
     * <p>Called from {@link SctpAssociation}'s constructor and paired with {@link #unregister(MemorySegment)}. The
     * association is recorded under its {@code conn_id} address and the address is registered with usrsctp; if the
     * native registration fails the recorded entry is rolled back before the failure is rethrown.
     *
     * @param connId the association's unique {@code conn_id} segment
     * @param assoc  the association to dispatch outbound packets to
     * @throws NullPointerException       if either argument is {@code null}
     * @throws WhatsAppCallException.Sctp if {@code usrsctp_register_address} fails
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
     * Reverses {@link #register(MemorySegment, SctpAssociation)} for an association being torn down.
     *
     * <p>Called from {@link SctpAssociation#close()} before the association destroys its socket. usrsctp guarantees no
     * further outbound packets are routed to the deregistered address once this returns. A {@code null} conn id is
     * ignored, and any error from the native deregistration is swallowed so teardown always completes.
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
     * Allocates the global conn-output upcall and returns its stub address for passing to
     * {@code usrsctp_init_nothreads}.
     *
     * <p>Binds the {@link #connOutput(MemorySegment, MemorySegment, long, byte, byte)} instance method to this engine
     * and wraps it in a native upcall stub backed by the global {@link #arena}.
     *
     * @return the upcall stub address
     * @throws WhatsAppCallException.Sctp if the {@code connOutput} method handle cannot be located
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
     * Dispatches one outbound-packet firing from usrsctp's global conn-output callback to the owning association.
     *
     * <p>This is the single global callback usrsctp invokes whenever it has an SCTP packet to ship. It routes by the
     * {@code addr} pointer the firing association registered: it looks the {@link SctpAssociation} up in
     * {@link #associations} by raw address and forwards a fresh copy of the packet bytes to
     * {@link SctpAssociation#deliverOutbound(byte[])}. A non-positive length, an unknown address, and an exception
     * while copying or delivering are all handled without propagating into usrsctp's call stack.
     *
     * @param addr   the {@code conn_id} segment whose owner produced this packet
     * @param buffer the pointer to {@code length} bytes of SCTP wire payload
     * @param length the number of bytes at {@code buffer}
     * @param tos    the SCTP-level type-of-service (ignored)
     * @param setDf  the SCTP-level don't-fragment hint (ignored)
     * @return {@code 0} on success; {@code 1} when delivery throws, which usrsctp treats as a signal to retry
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
     * Drives one immediate timer-wheel tick with the canonical {@link #TICK_PERIOD_MILLIS} elapsed window.
     *
     * <p>The background ticker started in the constructor is what production paths rely on; an explicit call here is
     * additive, since the timer wheel tolerates over-frequency ticks. Tests use it to exercise SCTP retransmit and
     * heartbeat logic deterministically without waiting for the background thread.
     *
     * @throws WhatsAppCallException.Sctp if {@code usrsctp_handle_timers} fails
     */
    public void scheduleTick() {
        try {
            UsrSctp.usrsctp_handle_timers((int) TICK_PERIOD_MILLIS);
        } catch (Throwable t) {
            throw new WhatsAppCallException.Sctp("usrsctp_handle_timers failed", t);
        }
    }
}
