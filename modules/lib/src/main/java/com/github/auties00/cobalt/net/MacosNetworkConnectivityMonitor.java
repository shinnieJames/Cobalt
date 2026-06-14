package com.github.auties00.cobalt.net;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.net.bindings.macos.SCReachability;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Native {@link NetworkConnectivityMonitor} for macOS backed by
 * {@code SCNetworkReachability} from the SystemConfiguration framework.
 *
 * <p>The event thread creates a reachability reference for the default route
 * ({@code 0.0.0.0}), registers a callback through an FFM upcall stub, schedules
 * it on the thread's CoreFoundation run loop, and runs the loop. The framework
 * then invokes the callback on every reachability change; the host is considered
 * online when the reachable flag is set and the connection-required flag is not.
 * The SystemConfiguration and CoreFoundation frameworks are loaded in the
 * constructor so the loader lookup resolves the symbols.
 *
 * @implNote This is genuinely event-driven: it does not poll. The callback is a
 * {@link Linker#upcallStub(MethodHandle, FunctionDescriptor, Arena, Linker.Option...) upcall stub}
 * allocated in the event thread's confined arena and is only ever invoked by the
 * run loop on that same thread, so the simulated flags read needs no extra
 * synchronisation beyond what {@link AbstractNativeConnectivityMonitor#setOnline(boolean)}
 * already provides. {@link #close()} calls {@code CFRunLoopStop} to unblock
 * {@code CFRunLoopRun}. An initial {@code SCNetworkReachabilityGetFlags} probe
 * seeds the state because the callback only fires on subsequent changes. The
 * downcall handles are resolved eagerly in the constructor so a framework-load
 * failure surfaces to {@link NetworkConnectivityMonitors}, which then falls back
 * to the no-op monitor.
 */
@WhatsAppWebModule(moduleName = "WAWebNetworkStatus")
final class MacosNetworkConnectivityMonitor extends AbstractNativeConnectivityMonitor {
    /**
     * Absolute path to the CoreFoundation framework binary.
     */
    private static final String CORE_FOUNDATION = "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation";

    /**
     * Absolute path to the SystemConfiguration framework binary.
     */
    private static final String SYSTEM_CONFIGURATION = "/System/Library/Frameworks/SystemConfiguration.framework/SystemConfiguration";

    /**
     * {@code AF_INET} address family on macOS.
     */
    private static final byte AF_INET = 2;

    /**
     * Size in bytes of {@code struct sockaddr_in}, used as the reachability
     * target for the default route.
     */
    private static final long SOCKADDR_IN_SIZE = 16;

    /**
     * {@code kSCNetworkReachabilityFlagsReachable}.
     */
    private static final int FLAG_REACHABLE = 0x2;

    /**
     * {@code kSCNetworkReachabilityFlagsConnectionRequired}.
     */
    private static final int FLAG_CONNECTION_REQUIRED = 0x4;

    /**
     * C signature of {@code SCNetworkReachabilityCallBack}:
     * {@code void (*)(SCNetworkReachabilityRef, SCNetworkReachabilityFlags, void *)}.
     */
    private static final FunctionDescriptor CALLBACK_DESCRIPTOR =
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

    /**
     * Method handle to {@link #onReachabilityChanged(MemorySegment, int, MemorySegment)},
     * bound per instance to build the upcall stub.
     */
    private static final MethodHandle CALLBACK_HANDLE;

    static {
        try {
            CALLBACK_HANDLE = MethodHandles.lookup().findVirtual(
                    MacosNetworkConnectivityMonitor.class,
                    "onReachabilityChanged",
                    MethodType.methodType(void.class, MemorySegment.class, int.class, MemorySegment.class));
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    /**
     * The run loop running the reachability callback, or {@code null} when the
     * loop is not running; written by the event thread and read by
     * {@link #stopEventSource()}.
     */
    private volatile MemorySegment runLoop;

    /**
     * Constructs the monitor, loading the required frameworks and eagerly
     * resolving the downcall handles.
     *
     * @throws UnsatisfiedLinkError if a framework or symbol cannot be resolved on this host
     */
    MacosNetworkConnectivityMonitor() {
        super("cobalt-connectivity-macos");
        System.load(CORE_FOUNDATION);
        System.load(SYSTEM_CONFIGURATION);
        SCReachability.SCNetworkReachabilityCreateWithAddress$handle();
        SCReachability.SCNetworkReachabilityGetFlags$handle();
        SCReachability.SCNetworkReachabilitySetCallback$handle();
        SCReachability.SCNetworkReachabilityScheduleWithRunLoop$handle();
        SCReachability.SCNetworkReachabilityUnscheduleFromRunLoop$handle();
        SCReachability.CFRunLoopGetCurrent$handle();
        SCReachability.CFRunLoopRun$handle();
        SCReachability.CFRunLoopStop$handle();
        SCReachability.CFRelease$handle();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void runEventLoop() throws IOException {
        try (var arena = Arena.ofConfined()) {
            var sockaddr = arena.allocate(SOCKADDR_IN_SIZE);
            sockaddr.set(ValueLayout.JAVA_BYTE, 0, (byte) SOCKADDR_IN_SIZE);
            sockaddr.set(ValueLayout.JAVA_BYTE, 1, AF_INET);
            var reachability = SCReachability.SCNetworkReachabilityCreateWithAddress(MemorySegment.NULL, sockaddr);
            if (reachability.address() == 0) {
                throw new IOException("SCNetworkReachabilityCreateWithAddress returned null");
            }
            try {
                var callback = Linker.nativeLinker().upcallStub(CALLBACK_HANDLE.bindTo(this), CALLBACK_DESCRIPTOR, arena);
                if (SCReachability.SCNetworkReachabilitySetCallback(reachability, callback, MemorySegment.NULL) == 0) {
                    throw new IOException("SCNetworkReachabilitySetCallback failed");
                }
                var loop = SCReachability.CFRunLoopGetCurrent();
                var mode = SCReachability.kCFRunLoopDefaultMode();
                if (SCReachability.SCNetworkReachabilityScheduleWithRunLoop(reachability, loop, mode) == 0) {
                    throw new IOException("SCNetworkReachabilityScheduleWithRunLoop failed");
                }
                runLoop = loop;
                // Seed the initial state: the callback only fires on later changes.
                setOnline(probe(reachability, arena));
                if (!isClosed()) {
                    SCReachability.CFRunLoopRun();
                }
                SCReachability.SCNetworkReachabilityUnscheduleFromRunLoop(reachability, loop, mode);
            } finally {
                runLoop = null;
                SCReachability.CFRelease(reachability);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Stops the run loop so {@code CFRunLoopRun} returns and the event thread
     * unwinds.
     */
    @Override
    protected void stopEventSource() {
        var loop = runLoop;
        if (loop != null) {
            SCReachability.CFRunLoopStop(loop);
        }
    }

    /**
     * Reachability callback invoked by the run loop on every connectivity
     * change; updates the online state from the supplied flags.
     *
     * @param target the reachability reference (unused)
     * @param flags  the current {@code SCNetworkReachabilityFlags}
     * @param info   the caller context (unused)
     */
    private void onReachabilityChanged(MemorySegment target, int flags, MemorySegment info) {
        setOnline((flags & FLAG_REACHABLE) != 0 && (flags & FLAG_CONNECTION_REQUIRED) == 0);
    }

    /**
     * Reads the current reachability flags for the initial state seed.
     *
     * @param reachability the reachability reference
     * @param arena        the confined arena for the flags out-parameter
     * @return {@code true} if reachable without a required connection, or if the
     *         query fails (assumed online so reconnection is never wedged)
     */
    private boolean probe(MemorySegment reachability, Arena arena) {
        var flags = arena.allocate(ValueLayout.JAVA_INT);
        var ok = SCReachability.SCNetworkReachabilityGetFlags(reachability, flags);
        if (ok == 0) {
            return true;
        }
        var value = flags.get(ValueLayout.JAVA_INT, 0);
        return (value & FLAG_REACHABLE) != 0 && (value & FLAG_CONNECTION_REQUIRED) == 0;
    }
}
