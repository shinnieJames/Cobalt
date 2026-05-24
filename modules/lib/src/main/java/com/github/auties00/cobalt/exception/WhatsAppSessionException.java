package com.github.auties00.cobalt.exception;

/**
 * Sealed root for failures that invalidate the active WhatsApp session
 * as a whole.
 *
 * @apiNote
 * A session in Cobalt is the encrypted Noise protocol channel that sits
 * on top of the WebSocket connection: it carries the registration
 * identity, the cipher state used for every frame, and the
 * authentication of the current device with the server. The nested
 * subtypes correspond to the distinct ways WA Web's
 * {@code WAWebHandleStreamError} aborts a logged-in stream (the parser
 * for {@code stream:error} stanzas dispatches on {@code conflict} type
 * {@code replaced} vs {@code device_removed}, on numeric error codes
 * 515 ({@link Reconnect}) and 516 ({@link LoggedOut}), and on
 * {@code xml-not-well-formed}). The configurable error handler decides
 * whether to reconnect, treat the account as locked out, or notify the
 * user.
 *
 * @implNote
 * This implementation always reports the failure as fatal: every
 * session-level subtype leaves the Noise channel in an unusable state.
 *
 * @see BadMac
 * @see Closed
 * @see Conflict
 * @see LoggedOut
 * @see Banned
 * @see Reconnect
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
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always returns {@code true}: every concrete
     * session exception terminates the active channel.
     */
    @Override
    public boolean isFatal() {
        return true;
    }

    /**
     * Thrown when the authentication code on a Noise frame did not
     * validate.
     *
     * @apiNote
     * Each Noise frame carries an AEAD authentication tag computed over
     * the ciphertext. A mismatch typically means the keys at the two
     * ends of the channel drifted out of sync (often after a brief
     * interruption that prevented a key rotation from completing) or
     * that the bytes were modified in transit.
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
     * Thrown when an operation is attempted on a session whose
     * underlying connection has already been closed.
     *
     * @apiNote
     * The closure may have been initiated by the application, by the
     * server (typically for maintenance or load balancing), or by the
     * keep-alive watchdog after the socket stopped responding.
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
     * Thrown when the server signals that another instance has taken
     * over the slot for this device kind.
     *
     * @apiNote
     * Maps to WA Web's {@code stream:error} {@code <conflict
     * type="replaced">} child handled by {@code WAWebHandleStreamError}.
     * WhatsApp allows only one active web client (or one active mobile
     * client) at a time. When a second one logs in, the server sends a
     * conflict stream error to the existing session and the local
     * credentials cannot be reused without re-pairing.
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

    /**
     * Thrown when the server reports that the account has been logged
     * out of this device.
     *
     * @apiNote
     * Maps to WA Web's {@code stream:error} {@code <conflict
     * type="device_removed">} child and to the numeric 516 path inside
     * {@code WAWebHandleStreamError} (which calls
     * {@code clearCredentialsAndStoredData} and triggers
     * {@code BackendEventBus.triggerLogout}). The server pushes this
     * notification when the user logs out from another device or when
     * WhatsApp itself revokes the session as part of an enforcement
     * flow. Credentials must be cleared before the user can pair the
     * device again.
     */
    public static final class LoggedOut extends WhatsAppSessionException {
        /**
         * Constructs a new logged-out exception with a default message.
         */
        public LoggedOut() {
            super("Session invalidated: logged out by server");
        }

        /**
         * Constructs a new logged-out exception with the specified detail message.
         *
         * @param message the detail message describing the logout condition
         */
        public LoggedOut(String message) {
            super(message);
        }

        /**
         * Constructs a new logged-out exception with the specified detail message
         * and cause.
         *
         * @param message the detail message describing the logout condition
         * @param cause   the underlying cause of the logout
         */
        public LoggedOut(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Thrown when the server refuses traffic for the account because it
     * has been banned.
     *
     * @apiNote
     * A ban is a terminal state. The client cannot reconnect until the
     * ban is lifted or appealed through WhatsApp's support channels.
     */
    public static final class Banned extends WhatsAppSessionException {
        /**
         * Constructs a new banned exception with a default message.
         */
        public Banned() {
            super("Session invalidated: account banned by server");
        }

        /**
         * Constructs a new banned exception with the specified detail message.
         *
         * @param message the detail message describing the ban condition
         */
        public Banned(String message) {
            super(message);
        }

        /**
         * Constructs a new banned exception with the specified detail message and cause.
         *
         * @param message the detail message describing the ban condition
         * @param cause   the underlying cause of the ban
         */
        public Banned(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Thrown when the server requests that the client drop the current
     * connection and immediately re-establish it.
     *
     * @apiNote
     * Maps to the numeric 515 path inside
     * {@code WAWebHandleStreamError} which calls
     * {@code WAComms.stopComms()} and {@code startLogin()}. Used during
     * load balancing, configuration rollouts, or scheduled maintenance.
     * Credentials remain valid and the next connection attempt should
     * succeed once the previous channel has been torn down.
     */
    public static final class Reconnect extends WhatsAppSessionException {
        /**
         * Constructs a new reconnect exception with a default message.
         */
        public Reconnect() {
            super("Session requires reconnect");
        }

        /**
         * Constructs a new reconnect exception with the specified detail message.
         *
         * @param message the detail message describing the reconnect request
         */
        public Reconnect(String message) {
            super(message);
        }

        /**
         * Constructs a new reconnect exception with the specified detail message and cause.
         *
         * @param message the detail message describing the reconnect request
         * @param cause   the underlying cause of the reconnect request
         */
        public Reconnect(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
