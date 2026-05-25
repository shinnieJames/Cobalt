package com.github.auties00.cobalt.exception;

import com.github.auties00.cobalt.node.Node;

import java.util.Objects;

/**
 * Sealed root for problems detected in the WhatsApp protocol stream
 * carried over the WebSocket connection.
 *
 * <p>WhatsApp speaks an XMPP-flavored protocol where every message is a
 * node (a stanza with a tag, attributes, and child content). Stream
 * exceptions cover the layer that frames, encodes, and correlates those
 * nodes. Two concrete failure modes exist: a node that arrives in an
 * unparseable shape ({@link MalformedNode}) and a request whose response
 * never arrives ({@link NodeTimeout}).
 *
 * @apiNote
 * Raised by the node pipeline; {@link #isFatal()} reports {@code true}
 * for every subtype, so a configured {@code WhatsAppClientErrorHandler}
 * cannot meaningfully discard the event and should reconnect to clear the
 * in-flight protocol state.
 *
 * @implNote
 * This implementation classifies every stream fault as fatal because the
 * node pipeline is a shared resource: a single corrupted frame poisons
 * the in-flight protocol state and the connection has to be
 * re-established before traffic can resume.
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
     * This implementation always returns {@code true}: any stream-level
     * fault leaves the protocol pipeline in an unrecoverable state.
     */
    @Override
    public boolean isFatal() {
        return true;
    }

    /**
     * Thrown when a stanza received from the server is structurally
     * invalid.
     *
     * <p>The decoder raises this when a stanza is truncated, has a missing
     * required attribute, has the wrong content shape for its tag, or
     * otherwise cannot be parsed into a {@link Node}.
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
         * Constructs a new malformed node exception with no detail message.
         */
        public MalformedNode() {
            super();
        }

        /**
         * Constructs a new malformed node exception with the specified message.
         *
         * @param message the detail message describing why the node is malformed
         */
        public MalformedNode(String message) {
            super(message);
        }

        /**
         * Constructs a new malformed node exception with the specified message and cause.
         *
         * @param message the detail message describing why the node is malformed
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
     * @see Node
     */
    public static final class NodeTimeout extends WhatsAppStreamException {
        /**
         * The request stanza that did not receive a response.
         */
        private final Node node;

        /**
         * Constructs a new node timeout exception with the node that timed out.
         *
         * @param node the stanza that did not receive a response in time
         * @throws NullPointerException if {@code node} is {@code null}
         */
        public NodeTimeout(Node node) {
            super("Node timeout: " + Objects.requireNonNull(node, "node cannot be null"));
            this.node = node;
        }

        /**
         * Constructs a new node timeout exception with a custom message and the timed-out node.
         *
         * @param message the detail message describing the timeout condition
         * @param node    the stanza that did not receive a response in time
         * @throws NullPointerException if {@code node} is {@code null}
         */
        public NodeTimeout(String message, Node node) {
            super(message);
            this.node = Objects.requireNonNull(node, "node cannot be null");
        }

        /**
         * Constructs a new node timeout exception with a message, cause, and the timed-out node.
         *
         * @param message the detail message describing the timeout condition
         * @param node    the stanza that did not receive a response in time
         * @param cause   the underlying cause of the timeout
         * @throws NullPointerException if {@code node} is {@code null}
         */
        public NodeTimeout(String message, Node node, Throwable cause) {
            super(message, cause);
            this.node = Objects.requireNonNull(node, "node cannot be null");
        }

        /**
         * Returns the stanza that did not receive a response within the
         * timeout.
         *
         * @return the timed-out request stanza, never {@code null}
         */
        public Node node() {
            return node;
        }
    }
}
