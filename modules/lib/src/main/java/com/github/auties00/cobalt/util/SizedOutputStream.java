package com.github.auties00.cobalt.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Marker interface for {@link OutputStream} implementations that can
 * accept an up-front size hint for the next logical message they are
 * about to receive.
 *
 * <p>Length-prefixed wire formats (the WhatsApp {@code int24} datagram
 * envelope, RFC 6455 WebSocket frames, and similar) need to know the
 * payload size before emitting the frame header. Without this hint
 * they would either have to buffer the entire payload to discover its
 * size or use fragmentation to defer the size announcement. With this
 * hint they can emit their header immediately and forward subsequent
 * write calls straight to the wire — true pure streaming with no
 * intermediate buffer.
 *
 * <p>Producers that know the size of their next message ahead of time
 * (such as the WhatsApp node encoder, which computes
 * {@code NodeSizer.sizeOf(node)} before writing the first byte) should
 * check whether their target stream implements this interface and, if
 * so, invoke {@link #beginMessage(int)} before any write calls. The
 * subsequent stream-of-bytes is the body of that single message; the
 * message ends implicitly when {@code payloadSize} bytes have been
 * written. {@link OutputStream#flush()} after that pushes the frame
 * downstream.
 *
 * <p>Implementations may layer freely: when an outer
 * {@code SizedOutputStream} receives a {@code beginMessage} hint, it
 * may transform the size (for example, an encrypting layer would add
 * 16 bytes for the GCM tag, a framing layer would add its header
 * size) and cascade {@code beginMessage} to its own downstream if
 * that downstream is also a {@code SizedOutputStream}.
 */
public interface SizedOutputStream {

    /**
     * Signals that the next {@code payloadSize} bytes written to this
     * stream constitute one logical message.
     *
     * <p>The stream typically uses this hint to emit its length-prefixed
     * frame header immediately and forward subsequent writes straight
     * to its downstream.
     *
     * @param payloadSize the exact number of body bytes the caller is
     *                    about to write; must be non-negative
     * @throws IOException              if the header-emit fails
     * @throws IllegalArgumentException if {@code payloadSize} is
     *                                  negative
     * @throws IllegalStateException    if the stream is already in
     *                                  the middle of a previously
     *                                  begun message
     */
    void beginMessage(int payloadSize) throws IOException;
}
