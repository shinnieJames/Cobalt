package com.github.auties00.cobalt.exception;

/**
 * Base exception for all WhatsApp-related errors in the Cobalt library.
 * <p>
 * This is a sealed abstract class that serves as the root of the exception hierarchy
 * for all WhatsApp protocol and client errors. The sealed hierarchy enables exhaustive
 * pattern matching on exception types and ensures all exception categories are explicitly
 * defined.
 *
 * <h2>Exception Hierarchy</h2>
 * The exception hierarchy is organized by error domain:
 * <ul>
 *   <li><b>Connection errors:</b>
 *     <ul>
 *       <li>{@link WhatsAppConnectionException} - Initial connection failures</li>
 *       <li>{@link WhatsAppReconnectionException} - Reconnection attempt failures</li>
 *     </ul>
 *   </li>
 *   <li><b>Session errors:</b>
 *     <ul>
 *       <li>{@link WhatsAppSessionException} - Session-level protocol errors (bad MAC, conflict, closed)</li>
 *     </ul>
 *   </li>
 *   <li><b>Stream errors:</b>
 *     <ul>
 *       <li>{@link WhatsAppStreamException} - Protocol stream errors (malformed nodes, timeouts)</li>
 *     </ul>
 *   </li>
 *   <li><b>Message errors:</b>
 *     <ul>
 *       <li>{@link WhatsAppMessageException} - Message decryption and parsing failures</li>
 *     </ul>
 *   </li>
 *   <li><b>Media errors:</b>
 *     <ul>
 *       <li>{@link WhatsAppMediaException} - Media upload, download, and processing failures</li>
 *     </ul>
 *   </li>
 *   <li><b>Sync errors:</b>
 *     <ul>
 *       <li>{@link WhatsAppWebAppStateSyncException} - Web app state synchronization failures</li>
 *       <li>{@link WhatsAppHistorySyncException} - Message history synchronization failures</li>
 *     </ul>
 *   </li>
 *   <li><b>Device errors:</b>
 *     <ul>
 *       <li>{@link WhatsAppAdvCheckException} - Periodic ADV check failures</li>
 *       <li>{@link WhatsAppAdvValidationException} - Account device verification failures</li>
 *       <li>{@link WhatsAppOwnDeviceListExpiredException} - Device list staleness errors</li>
 *       <li>{@link WhatsAppLidMigrationException} - LID migration failures</li>
 *     </ul>
 *   </li>
 *   <li><b>Registration errors:</b>
 *     <ul>
 *       <li>{@link WhatsAppRegistrationException} - Mobile registration failures</li>
 *     </ul>
 *   </li>
 *   <li><b>Other errors:</b>
 *     <ul>
 *       <li>{@link WhatsAppMalformedJidException} - Invalid JID format</li>
 *       <li>{@link WhatsAppABPropTypeMismatchException} - Configuration type mismatches</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>Fatal vs Non-Fatal Errors</h2>
 * Each exception indicates whether it is fatal via the {@link #isFatal()} method:
 * <ul>
 *   <li><b>Fatal errors:</b> Require the client to disconnect. The session cannot be recovered
 *       and a new connection must be established. Examples include session conflicts, bad MAC
 *       errors, and critical protocol violations.</li>
 *   <li><b>Non-fatal errors:</b> The client can continue operating. These errors affect individual
 *       operations (like a single message or media transfer) but don't compromise the session.
 *       The client should handle the error and continue processing.</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * try {
 *     client.sendMessage(message);
 * } catch (WhatsAppException e) {
 *     if (e.isFatal()) {
 *         // Must reconnect
 *         client.reconnect();
 *     } else {
 *         // Can continue with error handling
 *         logger.warn("Non-fatal error: " + e.getMessage());
 *     }
 * }
 * }</pre>
 *
 * @see #isFatal()
 */
public abstract sealed class WhatsAppException extends RuntimeException
        permits WhatsAppABPropTypeMismatchException, WhatsAppAdvCheckException, WhatsAppAdvValidationException, WhatsAppConnectionException, WhatsAppCorruptedStoreException, WhatsAppDeviceSyncException, WhatsAppHistorySyncException, WhatsAppLidMigrationException, WhatsAppMalformedJidException, WhatsAppMediaException, WhatsAppMessageException, WhatsAppOwnDeviceListExpiredException, WhatsAppReconnectionException, WhatsAppRegistrationException, WhatsAppServerRuntimeException, WhatsAppSessionException, WhatsAppStreamException, WhatsAppWebAppStateSyncException {

    /**
     * Constructs a new WhatsApp exception with no detail message.
     */
    protected WhatsAppException() {
        super();
    }

    /**
     * Constructs a new WhatsApp exception with the specified detail message.
     *
     * @param message the detail message describing the error condition
     */
    protected WhatsAppException(String message) {
        super(message);
    }

    /**
     * Constructs a new WhatsApp exception with the specified detail message and cause.
     *
     * @param message the detail message describing the error condition
     * @param cause   the underlying cause of this exception
     */
    protected WhatsAppException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new WhatsApp exception wrapping the specified cause.
     *
     * @param cause the underlying cause of this exception
     */
    protected WhatsAppException(Throwable cause) {
        super(cause);
    }

    /**
     * Returns whether this exception represents a fatal error that requires client disconnection.
     * <p>
     * Fatal errors indicate that the current session is no longer viable and the client must
     * disconnect and potentially re-authenticate. Non-fatal errors can be handled locally
     * without affecting the overall session.
     *
     * <h2>Fatal Error Examples</h2>
     * <ul>
     *   <li>Session conflict (another device logged in)</li>
     *   <li>Bad MAC (cryptographic integrity failure)</li>
     *   <li>Stream protocol errors</li>
     *   <li>ADV validation failures</li>
     * </ul>
     *
     * <h2>Non-Fatal Error Examples</h2>
     * <ul>
     *   <li>Individual message decryption failures</li>
     *   <li>Media download/upload failures</li>
     *   <li>Malformed JID parsing errors</li>
     *   <li>Configuration type mismatches</li>
     * </ul>
     *
     * @return {@code true} if this is a fatal error requiring disconnection,
     *         {@code false} if the client can continue operating
     */
    public abstract boolean isFatal();
}
