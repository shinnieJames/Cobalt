package com.github.auties00.cobalt.net;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.net.bindings.linux.Netlink;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Native {@link NetworkConnectivityMonitor} for Linux backed by a
 * {@code NETLINK_ROUTE} socket and {@code getifaddrs}.
 *
 * <p>The event thread blocks in {@code recv} on a netlink route socket
 * subscribed to link and address change groups; every kernel message (a link
 * going up or down, an address added or removed) wakes it, after which it
 * re-evaluates connectivity with {@code getifaddrs}. The host is considered
 * online when some interface is up and running, is not loopback, and carries a
 * non-loopback IPv4 or IPv6 address. All calls go through libc, so no extra
 * shared library is loaded.
 *
 * @implNote {@link #stopEventSource()} closes the netlink descriptor to unblock
 * the {@code recv}, so {@link #close()} returns promptly. Link-local-only
 * interfaces are still counted as online; the connect attempt remains the
 * source of truth, so a false positive only costs one failed attempt and a
 * backoff. The downcall handles are resolved eagerly in the constructor so an
 * unexpected libc mismatch surfaces to {@link NetworkConnectivityMonitors},
 * which then falls back to the no-op monitor.
 */
@WhatsAppWebModule(moduleName = "WAWebNetworkStatus")
final class LinuxNetworkConnectivityMonitor extends AbstractNativeConnectivityMonitor {
    /**
     * {@code AF_NETLINK} address family.
     */
    private static final int AF_NETLINK = 16;

    /**
     * {@code SOCK_RAW} socket type.
     */
    private static final int SOCK_RAW = 3;

    /**
     * {@code NETLINK_ROUTE} netlink protocol.
     */
    private static final int NETLINK_ROUTE = 0;

    /**
     * Multicast group mask: {@code RTMGRP_LINK | RTMGRP_IPV4_IFADDR |
     * RTMGRP_IPV6_IFADDR} ({@code 0x1 | 0x10 | 0x100}).
     */
    private static final int RTMGRP_MASK = 0x1 | 0x10 | 0x100;

    /**
     * Size in bytes of {@code struct sockaddr_nl}.
     */
    private static final long SOCKADDR_NL_SIZE = 12;

    /**
     * Offset of {@code nl_groups} within {@code struct sockaddr_nl}.
     */
    private static final long NL_GROUPS_OFFSET = 8;

    /**
     * Size in bytes of {@code struct ifaddrs} on an LP64 target.
     */
    private static final long IFADDRS_SIZE = 56;

    /**
     * Offset of {@code ifa_next} within {@code struct ifaddrs}.
     */
    private static final long IFA_NEXT_OFFSET = 0;

    /**
     * Offset of {@code ifa_flags} within {@code struct ifaddrs}.
     */
    private static final long IFA_FLAGS_OFFSET = 16;

    /**
     * Offset of {@code ifa_addr} within {@code struct ifaddrs}.
     */
    private static final long IFA_ADDR_OFFSET = 24;

    /**
     * {@code IFF_UP} interface flag.
     */
    private static final int IFF_UP = 0x1;

    /**
     * {@code IFF_RUNNING} interface flag.
     */
    private static final int IFF_RUNNING = 0x40;

    /**
     * {@code IFF_LOOPBACK} interface flag.
     */
    private static final int IFF_LOOPBACK = 0x8;

    /**
     * {@code AF_INET} address family.
     */
    private static final int AF_INET = 2;

    /**
     * {@code AF_INET6} address family.
     */
    private static final int AF_INET6 = 10;

    /**
     * Bytes read per netlink {@code recv}; messages are only used as a change
     * signal, so the buffer just needs to drain one datagram.
     */
    private static final long RECV_BUFFER_SIZE = 8192;

    /**
     * The open netlink descriptor, or {@code -1} when none; written by the
     * event thread and read by {@link #stopEventSource()}.
     */
    private volatile int netlinkFd = -1;

    /**
     * Constructs the monitor, eagerly resolving the libc downcall handles.
     *
     * @throws UnsatisfiedLinkError if any required libc symbol cannot be resolved
     */
    LinuxNetworkConnectivityMonitor() {
        super("cobalt-connectivity-linux");
        Netlink.socket$handle();
        Netlink.bind$handle();
        Netlink.recv$handle();
        Netlink.close$handle();
        Netlink.getifaddrs$handle();
        Netlink.freeifaddrs$handle();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void runEventLoop() throws IOException {
        try (var arena = Arena.ofConfined()) {
            var fd = Netlink.socket(AF_NETLINK, SOCK_RAW, NETLINK_ROUTE);
            if (fd < 0) {
                throw new IOException("netlink socket() failed");
            }
            netlinkFd = fd;
            try {
                var addr = arena.allocate(SOCKADDR_NL_SIZE);
                addr.set(ValueLayout.JAVA_SHORT, 0, (short) AF_NETLINK);
                addr.set(ValueLayout.JAVA_INT, NL_GROUPS_OFFSET, RTMGRP_MASK);
                if (Netlink.bind(fd, addr, (int) SOCKADDR_NL_SIZE) < 0) {
                    throw new IOException("netlink bind() failed");
                }
                setOnline(queryOnline(arena));
                var buffer = arena.allocate(RECV_BUFFER_SIZE);
                while (!isClosed()) {
                    var read = Netlink.recv(fd, buffer, RECV_BUFFER_SIZE, 0);
                    if (isClosed() || read < 0) {
                        break;
                    }
                    setOnline(queryOnline(arena));
                }
            } finally {
                netlinkFd = -1;
                Netlink.close(fd);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Closes the netlink descriptor so the blocking {@code recv} returns.
     */
    @Override
    protected void stopEventSource() {
        var fd = netlinkFd;
        if (fd >= 0) {
            Netlink.close(fd);
        }
    }

    /**
     * Walks the interface list and decides whether the host is online.
     *
     * @param arena the confined arena for the out-pointer
     * @return {@code true} if a usable interface address is present, or if the
     *         query fails (assumed online so reconnection is never wedged)
     */
    private boolean queryOnline(Arena arena) {
        var listPointer = arena.allocate(ValueLayout.ADDRESS);
        if (Netlink.getifaddrs(listPointer) != 0) {
            return true;
        }
        var head = listPointer.get(ValueLayout.ADDRESS, 0);
        try {
            var node = head;
            while (node.address() != 0) {
                var entry = node.reinterpret(IFADDRS_SIZE);
                var flags = entry.get(ValueLayout.JAVA_INT, IFA_FLAGS_OFFSET);
                var address = entry.get(ValueLayout.ADDRESS, IFA_ADDR_OFFSET);
                var next = entry.get(ValueLayout.ADDRESS, IFA_NEXT_OFFSET);
                if ((flags & IFF_LOOPBACK) == 0
                        && (flags & (IFF_UP | IFF_RUNNING)) == (IFF_UP | IFF_RUNNING)
                        && address.address() != 0) {
                    var family = address.reinterpret(2).get(ValueLayout.JAVA_SHORT, 0) & 0xFFFF;
                    if (family == AF_INET || family == AF_INET6) {
                        return true;
                    }
                }
                node = next;
            }
            return false;
        } finally {
            if (head.address() != 0) {
                Netlink.freeifaddrs(head);
            }
        }
    }
}
