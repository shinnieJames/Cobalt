package com.github.auties00.cobalt.socket.context;

import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractSocketClientContext {
    private static final int INT24_BYTE_SIZE = 3;
    private static final int WRITES_CHUNK_CAPACITY = 64;

    /**
     * Whether the underlying channel is connected and registered with the
     * selector.
     *
     * <p> Set to {@code true} by the connecting thread after the handshake
     * completes; set to {@code false} by the selector thread on disconnect
     * or I/O failure.  Read by any thread via
     * {@code SocketClient.isConnected()}.
     */
    public final AtomicBoolean connected;

    /**
     * Monitor used to block the connecting thread until the selector
     * completes the non-blocking {@link SocketChannel#connect
     * connect} operation.
     *
     * <p> The connecting thread calls {@code connectionLock.wait()} after
     * initiating a non-blocking connect.  The selector thread calls
     * {@code connectionLock.notifyAll()} when
     * {@link SocketChannel#finishConnect finishConnect}
     * succeeds.
     */
    public final Object connectionLock;

    /**
     * Whether the connection has completed any proxy negotiation and is
     * ready for the WhatsApp datagram protocol.
     *
     * <p> For direct connections this is {@code true} from construction.
     * For proxied connections it transitions from {@code false} to
     * {@code true} exactly once, after the proxy handshake succeeds.
     * Once {@code true} it never reverts.
     *
     * <p> Read and written exclusively by the selector thread.
     */
    public boolean tunnelled;

    /**
     * The pending binary read request, or {@code null} if no read is
     * in progress.
     *
     * <p> Used only during the pre-tunnel phase.  The proxy handshake is
     * sequential -- each request is sent and its response fully read before
     * the next request -- so at most one read is outstanding at any time.
     * A nullable field is therefore sufficient; no collection is needed.
     *
     * <p> Written by the thread calling {@code SocketClient.readBinary};
     * read and cleared by the selector thread.
     */
    public volatile SocketPendingRead pendingBinaryRead;

    /**
     * Lock-free MPSC queue of outbound buffers waiting to be written to
     * the channel.
     *
     * <p> Producers (any thread calling {@code SocketClient.sendBinary})
     * enqueue buffers via {@link SocketPendingWrites#offer offer}.  The
     * selector thread drains the queue via
     * {@link SocketPendingWrites#claim() claim} and
     * {@link SocketPendingWrites#release(int) release}, passing the
     * backing array directly to
     * {@link GatheringByteChannel#write(ByteBuffer[], int, int)
     * GatheringByteChannel.write}.
     *
     * <p> Used in both phases.
     */
    public final SocketPendingWrites pendingWrites;

    /**
     * The virtual executor for listener events
     */
    public volatile ExecutorService datagramListenerVirtualExecutor;

    /**
     * The lock to create/destroy the virtual executor for listener events
     */
    public final Object datagramListenerVirtualExecutorLock;

    /**
     * Buffer for reading the 3-byte length prefix of the current inbound
     * WhatsApp datagram.
     *
     * <p> Used only during the post-tunnel phase.  Accessed exclusively
     * by the selector thread.  Cleared and reused after each complete
     * datagram delivery.
     */
    public final ByteBuffer datagramLengthBuffer;

    /**
     * Buffer for accumulating the payload of the current inbound WhatsApp
     * datagram, or {@code null} if the length prefix has not yet been
     * fully read.
     *
     * <p> Allocated by the selector thread once the 3-byte length prefix
     * is complete, sized to the decoded length.  Set back to {@code null}
     * after the completed datagram is handed off.
     * Accessed exclusively by the selector thread.
     */
    public ByteBuffer datagramBuffer;

    /**
     * Creates a context for a new connection.
     */
    public AbstractSocketClientContext() {
        this.connected = new AtomicBoolean(false);
        this.connectionLock = new Object();
        this.pendingWrites = new SocketPendingWrites(WRITES_CHUNK_CAPACITY);
        this.datagramListenerVirtualExecutorLock = new Object();
        this.datagramLengthBuffer = ByteBuffer.allocate(INT24_BYTE_SIZE);
    }

    /**
     * Starts the virtual listener executor if one doesn't already exist
     */
    public void startListenerExecutor() {
        if(datagramListenerVirtualExecutor == null || datagramListenerVirtualExecutor.isShutdown()) {
            synchronized (datagramListenerVirtualExecutorLock) {
                if(datagramListenerVirtualExecutor == null || datagramListenerVirtualExecutor.isShutdown()) {
                    datagramListenerVirtualExecutor = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
                }
            }
        }
    }

    /**
     * Stops the virtual listener executor if one exists
     */
    public void stopListenerExecutor() {
        if(datagramListenerVirtualExecutor != null && !datagramListenerVirtualExecutor.isShutdown()) {
            synchronized (datagramListenerVirtualExecutorLock) {
                if(datagramListenerVirtualExecutor != null && !datagramListenerVirtualExecutor.isShutdown()) {
                    datagramListenerVirtualExecutor.shutdownNow();
                    datagramListenerVirtualExecutor = null;
                }
            }
        }
    }

    public abstract void onDatagram(ByteBuffer datagram);

    public abstract void onClose();
}
