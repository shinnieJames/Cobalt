package com.github.auties00.cobalt.exception;

/**
 * Exception thrown when a session-level error occurs with the WhatsApp server.
 * <p>
 * This sealed class hierarchy represents critical errors at the session layer of the WhatsApp
 * protocol. Session exceptions indicate that the current connection is no longer viable and
 * must be terminated. All session exceptions are fatal.
 *
 * <h2>Session Architecture</h2>
 * WhatsApp sessions operate on top of a WebSocket connection using the Noise protocol for
 * encryption. Each session maintains:
 * <ul>
 *   <li>Noise protocol cipher state for message encryption/decryption</li>
 *   <li>Session authentication state</li>
 *   <li>Active connection to a specific server endpoint</li>
 * </ul>
 *
 * <h2>Exception Hierarchy</h2>
 * <ul>
 *   <li>{@link BadMac} - Message authentication code validation failed</li>
 *   <li>{@link Closed} - Operation attempted on a closed session</li>
 *   <li>{@link Conflict} - Session conflict with another device</li>
 * </ul>
 *
 * <h2>Recovery</h2>
 * Session exceptions require:
 * <ol>
 *   <li>Closing the current WebSocket connection</li>
 *   <li>Discarding the Noise protocol state</li>
 *   <li>Establishing a new connection (if desired)</li>
 *   <li>For conflicts: Re-authentication may be required</li>
 * </ol>
 *
 * @see BadMac
 * @see Closed
 * @see Conflict
 */
public sealed abstract class WhatsAppSessionException
        extends WhatsAppException
        permits WhatsAppSessionException.BadMac,
                WhatsAppSessionException.Banned,
                WhatsAppSessionException.Closed,
                WhatsAppSessionException.Conflict,
                WhatsAppSessionException.LoggedOut,
                WhatsAppSessionException.Reconnect {

    /**
     * Constructs a new session exception with the specified detail message.
     *
     * @param message the detail message describing the session error
     */
    protected WhatsAppSessionException(String message) {
        super(message);
    }

    /**
     * Constructs a new session exception with the specified detail message and cause.
     *
     * @param message the detail message describing the session error
     * @param cause   the underlying cause of this exception
     */
    protected WhatsAppSessionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Returns whether this exception represents a fatal error.
     * <p>
     * All session exceptions are fatal as they indicate the session state is
     * compromised or no longer valid.
     *
     * @return {@code true} for all session exceptions
     */
    @Override
    public boolean isFatal() {
        return true;
    }

    /**
     * Exception thrown when message authentication code (MAC) validation fails at the session level.
     * <p>
     * The Noise protocol used by WhatsApp includes MAC authentication on every frame to ensure
     * message integrity. This exception occurs when the server sends a {@code bad-mac} stream
     * error, indicating that the cryptographic integrity check of a frame failed.
     *
     * <h2>Possible Causes</h2>
     * <ul>
     *   <li><b>Key desynchronization:</b> The encryption keys are out of sync between client
     *       and server, typically after connection interruption during key rotation</li>
     *   <li><b>Data corruption:</b> The message was corrupted during transmission due to
     *       network issues or intermediary manipulation</li>
     *   <li><b>State corruption:</b> The Noise protocol session state has become corrupted,
     *       possibly due to a software bug or memory corruption</li>
     *   <li><b>Security attack:</b> A potential man-in-the-middle attack was detected where
     *       an attacker attempted to modify encrypted traffic</li>
     * </ul>
     *
     * <h2>Noise Protocol Context</h2>
     * WhatsApp uses Noise_XX pattern for session establishment:
     * <ul>
     *   <li>Each message is authenticated with AEAD (AES-GCM)</li>
     *   <li>MAC failures indicate the ciphertext or associated data was modified</li>
     *   <li>The protocol does not allow recovery from MAC failures</li>
     * </ul>
     *
     * <h2>Recovery</h2>
     * When this exception is thrown:
     * <ol>
     *   <li>Close the current WebSocket connection immediately</li>
     *   <li>Clear all Noise protocol state</li>
     *   <li>Establish a new connection with fresh cryptographic state</li>
     *   <li>If the error persists, investigate for potential attacks or bugs</li>
     * </ol>
     */
    public static final class BadMac extends WhatsAppSessionException {
        /**
         * Constructs a new bad MAC exception with a default message.
         */
        public BadMac() {
            super("Bad MAC: message authentication code validation failed");
        }

        /**
         * Constructs a new bad MAC exception with the specified message.
         *
         * @param message the detail message describing the MAC failure
         */
        public BadMac(String message) {
            super(message);
        }

        /**
         * Constructs a new bad MAC exception with the specified message and cause.
         *
         * @param message the detail message describing the MAC failure
         * @param cause   the underlying cause of the MAC failure
         */
        public BadMac(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when an operation is attempted on a closed session.
     * <p>
     * This occurs when the client attempts to send data through a WebSocket connection
     * that has already been closed. The closure may have been initiated by either side
     * or due to network conditions.
     *
     * <h2>Possible Causes</h2>
     * <ul>
     *   <li><b>Server termination:</b> The server closed the connection due to maintenance,
     *       load balancing, or session timeout</li>
     *   <li><b>Network interruption:</b> A network issue caused the socket to close
     *       unexpectedly (e.g., WiFi disconnect, carrier switch)</li>
     *   <li><b>Dead socket detection:</b> The keep-alive mechanism detected the socket
     *       is no longer responsive and triggered a reset</li>
     *   <li><b>Explicit disconnect:</b> The client explicitly called disconnect</li>
     *   <li><b>Protocol error:</b> A protocol error caused the connection to be terminated
     *       (e.g., stream error from server)</li>
     * </ul>
     *
     * <h2>Recovery</h2>
     * When this exception is thrown:
     * <ol>
     *   <li>Recognize that the session is terminated</li>
     *   <li>Attempt to reconnect before retrying the operation</li>
     *   <li>Queue pending operations for retry after reconnection</li>
     * </ol>
     */
    public static final class Closed extends WhatsAppSessionException {
        /**
         * Constructs a new session closed exception with a default message.
         */
        public Closed() {
            super("Session closed: cannot perform operation on closed connection");
        }

        /**
         * Constructs a new session closed exception with the specified message.
         *
         * @param message the detail message describing why the session is closed
         */
        public Closed(String message) {
            super(message);
        }

        /**
         * Constructs a new session closed exception with the specified message and cause.
         *
         * @param message the detail message describing why the session is closed
         * @param cause   the underlying cause of the session closure
         */
        public Closed(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when a session conflict is detected.
     * <p>
     * This occurs when the server sends a {@code conflict} stream error, indicating that
     * another device or session has taken over this connection. WhatsApp allows only one
     * active session per device type at a time.
     *
     * <h2>Conflict Scenarios</h2>
     * <ul>
     *   <li><b>Same account login:</b> The user logged into WhatsApp from another device
     *       of the same type (e.g., another web client)</li>
     *   <li><b>Session takeover:</b> A reconnection from another network established a new
     *       session, invalidating this one</li>
     *   <li><b>Device limit:</b> The account has reached its device limit and this device
     *       was evicted</li>
     * </ul>
     *
     * <h2>Recovery</h2>
     * When this exception is thrown:
     * <ol>
     *   <li>The current session is invalidated and cannot be resumed</li>
     *   <li>Re-authentication is typically required</li>
     *   <li>For companion devices, a new QR code scan or pairing may be needed</li>
     *   <li>Consider informing the user that another session took over</li>
     * </ol>
     */
    public static final class Conflict extends WhatsAppSessionException {
        /**
         * Constructs a new session conflict exception with a default message.
         */
        public Conflict() {
            super("Session conflict: another device has taken over the connection");
        }

        /**
         * Constructs a new session conflict exception with the specified message.
         *
         * @param message the detail message describing the conflict
         */
        public Conflict(String message) {
            super(message);
        }

        /**
         * Constructs a new session conflict exception with the specified message and cause.
         *
         * @param message the detail message describing the conflict
         * @param cause   the underlying cause of the conflict
         */
        public Conflict(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class LoggedOut extends WhatsAppSessionException {
        public LoggedOut() {
            super("Session invalidated: logged out by server");
        }

        public LoggedOut(String message) {
            super(message);
        }

        public LoggedOut(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class Banned extends WhatsAppSessionException {
        public Banned() {
            super("Session invalidated: account banned by server");
        }

        public Banned(String message) {
            super(message);
        }

        public Banned(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class Reconnect extends WhatsAppSessionException {
        public Reconnect() {
            super("Session requires reconnect");
        }

        public Reconnect(String message) {
            super(message);
        }

        public Reconnect(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
