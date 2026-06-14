package com.github.auties00.cobalt.net;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.net.bindings.windows.Iphlpapi;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Native {@link NetworkConnectivityMonitor} for Windows backed by the IP Helper
 * API in {@code Iphlpapi.dll}.
 *
 * <p>The event thread blocks in {@code NotifyAddrChange(NULL, NULL)}, which
 * returns synchronously on every IPv4 address-table change, then re-reads the
 * current connectivity level with {@code GetNetworkConnectivityHint}; the host
 * is considered online when that level is at least
 * {@code NetworkConnectivityLevelHintInternetAccess}. Both calls require no
 * special privilege and are available on Windows 10 version 2004 and later.
 *
 * @implNote The synchronous form of {@code NotifyAddrChange} cannot be
 * cancelled, so {@link #stopEventSource()} is a no-op and the daemon event
 * thread exits on its next natural wakeup after {@link #close()}; this is
 * harmless because the thread is a daemon. The downcall handles are resolved
 * eagerly in the constructor so an unavailable API (an older Windows build)
 * surfaces to {@link NetworkConnectivityMonitors}, which then falls back to the
 * no-op monitor, instead of failing later on the event thread.
 */
@WhatsAppWebModule(moduleName = "WAWebNetworkStatus")
final class WindowsNetworkConnectivityMonitor extends AbstractNativeConnectivityMonitor {
    /**
     * {@code NO_ERROR} returned by the IP Helper calls on success.
     */
    private static final int NO_ERROR = 0;

    /**
     * {@code NetworkConnectivityLevelHintInternetAccess} from
     * {@code NL_NETWORK_CONNECTIVITY_LEVEL_HINT}; levels at or above this mean
     * the host has internet connectivity.
     */
    private static final int CONNECTIVITY_LEVEL_INTERNET_ACCESS = 3;

    /**
     * Bytes allocated for the {@code NL_NETWORK_CONNECTIVITY_HINT} out-parameter;
     * its {@code ConnectivityLevel} field sits at offset zero. Sixteen bytes
     * comfortably covers the struct (two 4-byte enums plus three booleans and
     * padding).
     */
    private static final long HINT_BYTES = 16;

    /**
     * Constructs the monitor, eagerly resolving the IP Helper downcall handles.
     *
     * @throws UnsatisfiedLinkError if {@code Iphlpapi.dll} or either entry point cannot be
     *         resolved on this host
     */
    WindowsNetworkConnectivityMonitor() {
        super("cobalt-connectivity-windows");
        System.loadLibrary("Iphlpapi");
        Iphlpapi.NotifyAddrChange$handle();
        Iphlpapi.GetNetworkConnectivityHint$handle();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void runEventLoop() {
        try (var arena = Arena.ofConfined()) {
            var hint = arena.allocate(HINT_BYTES);
            setOnline(queryOnline(hint));
            while (!isClosed()) {
                var rc = Iphlpapi.NotifyAddrChange(MemorySegment.NULL, MemorySegment.NULL);
                if (rc != NO_ERROR || isClosed()) {
                    break;
                }
                setOnline(queryOnline(hint));
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>No-op: the synchronous {@code NotifyAddrChange} has no cancellation
     * handle, so the daemon thread is left to exit on its next wakeup.
     */
    @Override
    protected void stopEventSource() {
        // Nothing to unblock: synchronous NotifyAddrChange cannot be cancelled
    }

    /**
     * Reads the current connectivity level into the reusable hint buffer and
     * decides whether the host is online.
     *
     * @param hint the reusable {@code NL_NETWORK_CONNECTIVITY_HINT} buffer
     * @return {@code true} if the level indicates internet access, or if the
     *         query fails (assumed online so reconnection is never wedged)
     */
    private boolean queryOnline(MemorySegment hint) {
        hint.fill((byte) 0);
        var rc = Iphlpapi.GetNetworkConnectivityHint(hint);
        if (rc != NO_ERROR) {
            return true;
        }
        var level = hint.get(ValueLayout.JAVA_INT, 0);
        return level >= CONNECTIVITY_LEVEL_INTERNET_ACCESS;
    }
}
