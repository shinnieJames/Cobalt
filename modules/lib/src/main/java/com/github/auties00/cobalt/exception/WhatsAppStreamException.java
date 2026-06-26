package com.github.auties00.cobalt.exception;

import com.github.auties00.cobalt.client.linked.WhatsAppLinkedClientErrorResult;
import com.github.auties00.cobalt.stanza.Stanza;

import java.util.Objects;

/**
 * Sealed root for problems detected in the WhatsApp protocol stream
 * carried over the WebSocket connection.
 *
 * <p>WhatsApp speaks an XMPP-flavored protocol where every message is a
 * stanza (a stanza with a tag, attributes, and child content). Stream
 * exceptions cover the layer that frames, encodes, and correlates those
 * nodes. Two concrete failure modes exist: a stanza that arrives in an
 * unparseable shape ({@link MalformedNode}) and a request whose response
 * never arrives ({@link NodeTimeout}).
 *
 * @apiNote
 * Raised by the stanza pipeline; {@link #toErrorResult()} reports
 * {@link WhatsAppLinkedClientErrorResult#RECONNECT} for every subtype, so a
 * configured {@code WhatsAppClientErrorHandler} cannot meaningfully discard
 * the event and instead reconnects to clear the in-flight protocol state.
 *
 * @implNote
 * This implementation classifies every stream fault as
 * {@link WhatsAppLinkedClientErrorResult#RECONNECT} because the stanza pipeline is a
 * shared resource: a single corrupted frame poisons the in-flight protocol
 * state and the connection has to be re-established before traffic can
 * resume.
 *
 * @see MalformedNode
 * @see NodeTimeout
 */
public sealed class WhatsAppStreamException extends WhatsAppException
        permits WhatsAppStreamException.MalformedNode, WhatsAppStreamException.NodeTimeout {

    /**
     * Constructs a new stream exception with no detail message.
     */
    public WhatsAppStreamException() {
        super();
    }

    /**
     * Constructs a new stream exception with the specified detail message.
     *
     * @param message the detail message describing the stream error
     */
    public WhatsAppStreamException(String message) {
        super(message);
    }

    /**
     * Constructs a new stream exception with the specified detail message and cause.
     *
     * @param message the detail message describing the stream error
     * @param cause   the underlying cause of this exception
     */
    public WhatsAppStreamException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new stream exception wrapping the specified cause.
     *
     * @param cause the underlying cause of this exception
     */
    public WhatsAppStreamException(Throwable cause) {
        super(cause);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always returns
     * {@link WhatsAppLinkedClientErrorResult#RECONNECT}: a single corrupted frame
     * poisons the in-flight protocol state, so the connection is dropped and
     * re-opened with fresh Noise state, collapsing to WhatsApp Web's
     * {@code CLOSE_SOCKET} resolution.
     */
    @Override
    public WhatsAppLinkedClientErrorResult toErrorResult() {
        return WhatsAppLinkedClientErrorResult.RECONNECT;
    }

    /**
     * Thrown when a stanza received from the server is structurally
     * invalid.
     *
     * <p>The decoder raises this when a stanza is truncated, has a missing
     * required attribute, has the wrong content shape for its tag, or
     * otherwise cannot be parsed into a {@link Stanza}.
     *
     * @apiNote
     * Raised locally on a decode failure; a configured
     * {@code WhatsAppClientErrorHandler} decides whether to NACK the
     * offending stanza or reconnect.
     *
     * @implNote
     * This implementation raises the exception rather than emitting an
     * inline NACK, leaving the recovery policy to the configurable error
     * handler.
     */
    public static final class MalformedNode extends WhatsAppStreamException {
        /**
         * Constructs a new malformed stanza exception with no detail message.
         */
        public MalformedNode() {
            super();
        }

        /**
         * Constructs a new malformed stanza exception with the specified message.
         *
         * @param message the detail message describing why the stanza is malformed
         */
        public MalformedNode(String message) {
            super(message);
        }

        /**
         * Constructs a new malformed stanza exception with the specified message and cause.
         *
         * @param message the detail message describing why the stanza is malformed
         * @param cause   the underlying cause
         */
        public MalformedNode(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Thrown when a request stanza never receives the matching response
     * within the expected window.
     *
     * <p>WhatsApp uses a request-response pattern where each outgoing
     * stanza is tagged with an id and the server eventually returns a
     * stanza carrying the same id. This exception marks an id whose
     * response did not arrive before the timeout fired.
     *
     * @apiNote
     * Raised on a request timeout; {@link #node()} returns the original
     * request stanza so the caller can log or retry the operation.
     *
     * @see Stanza
     */
    public static final class NodeTimeout extends WhatsAppStreamException {
        /**
         * The request stanza that did not receive a response.
         */
        private final Stanza stanza;

        /**
         * Constructs a new stanza timeout exception with the stanza that timed out.
         *
         * @param stanza the stanza that did not receive a response in time
         * @throws NullPointerException if {@code stanza} is {@code null}
         */
        public NodeTimeout(Stanza stanza) {
            super("Stanza timeout: " + Objects.requireNonNull(stanza, "stanza cannot be null"));
            this.stanza = stanza;
        }

        /**
         * Constructs a new stanza timeout exception with a custom message and the timed-out stanza.
         *
         * @param message the detail message describing the timeout condition
         * @param stanza    the stanza that did not receive a response in time
         * @throws NullPointerException if {@code stanza} is {@code null}
         */
        public NodeTimeout(String message, Stanza stanza) {
            super(message);
            this.stanza = Objects.requireNonNull(stanza, "stanza cannot be null");
        }

        /**
         * Constructs a new stanza timeout exception with a message, cause, and the timed-out stanza.
         *
         * @param message the detail message describing the timeout condition
         * @param stanza    the stanza that did not receive a response in time
         * @param cause   the underlying cause of the timeout
         * @throws NullPointerException if {@code stanza} is {@code null}
         */
        public NodeTimeout(String message, Stanza stanza, Throwable cause) {
            super(message, cause);
            this.stanza = Objects.requireNonNull(stanza, "stanza cannot be null");
        }

        /**
         * Returns the stanza that did not receive a response within the
         * timeout.
         *
         * @return the timed-out request stanza, never {@code null}
         */
        public Stanza node() {
            return stanza;
        }
    }
}
