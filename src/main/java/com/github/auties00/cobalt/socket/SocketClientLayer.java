package com.github.auties00.cobalt.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public interface SocketClientLayer {
    void connect(InetSocketAddress address, SocketClientLayerListener listener) throws IOException;
    void disconnect();
    boolean isConnected();

    /**
     * Sends one logical binary payload represented by the provided buffers.
     *
     * <p>Implementations may enqueue these buffers for asynchronous write and
     * may transform their content in-place (for example framing, masking, or
     * encryption). Callers should treat each supplied buffer as transferred
     * ownership and avoid mutating it after this call.
     *
     * @param buffers payload buffers in send order
     */
    void sendBinary(ByteBuffer... buffers) throws IOException;

    /**
     * Reads binary bytes into {@code buffer}.
     *
     * <p>The destination buffer stays in write mode after the call; callers
     * that need to read from it should invoke {@link ByteBuffer#flip()}
     * explicitly.
     *
     * @param buffer the destination buffer, in write mode
     * @param fully  {@code true} to fill the buffer, {@code false} to return
     *               after the first successful read
     * @return bytes read, or {@code -1} on end-of-stream
     * @throws IOException if reading fails
     */
    int readBinary(ByteBuffer buffer, boolean fully) throws IOException;
}
