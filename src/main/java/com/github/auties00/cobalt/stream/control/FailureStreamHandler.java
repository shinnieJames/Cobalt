package com.github.auties00.cobalt.stream.control;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppConnectionException;
import com.github.auties00.cobalt.exception.WhatsAppSessionException;
import com.github.auties00.cobalt.exception.WhatsAppServerRuntimeException;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

/**
 * Handles incoming {@code <failure>} stanzas from the WhatsApp server.
 * <p>
 * A failure stanza indicates a session-level error condition that requires the client
 * to take specific action based on the reason code. The handler parses the stanza
 * attributes and dispatches the appropriate exception through the client's error handler.
 *
 * <h2>Reason Codes</h2>
 * <ul>
 *   <li>{@code 400} - Generic failure: logged as warning, no exception</li>
 *   <li>{@code 401} - Not authorized: triggers logout</li>
 *   <li>{@code 402} - Temporary ban: triggers ban with code, expire, message, and url</li>
 *   <li>{@code 403} - Locked account: triggers logout with optional custom message</li>
 *   <li>{@code 405} - Client too old: triggers client version rejection</li>
 *   <li>{@code 406} - Banned: triggers logout</li>
 *   <li>{@code 409} - Bad user agent: triggers client version rejection</li>
 *   <li>{@code 500} - Internal server error: logged as warning, no exception</li>
 *   <li>{@code 501} - Experimental: logged as warning, no exception</li>
 *   <li>{@code 503} - Service unavailable: logged as warning, no exception</li>
 * </ul>
 *
 * @implNote WAWebHandleFailure.default
 */
public final class FailureStreamHandler implements SocketStream.Handler {

    /**
     * Logger instance for diagnostic output related to failure stanza handling.
     *
     * @implNote WAWebHandleFailure.default (WALogger usage)
     */
    private static final System.Logger LOGGER = System.getLogger(FailureStreamHandler.class.getName());

    /**
     * Failure reason code indicating a generic server-side failure.
     *
     * @implNote WAWebFailureErrorCodes.FAILURE_REASON.REASON_GENERIC_FAILURE
     */
    private static final int REASON_GENERIC_FAILURE = 400;

    /**
     * Failure reason code indicating the client is not authorized.
     *
     * @implNote WAWebFailureErrorCodes.FAILURE_REASON.REASON_NOT_AUTHORIZED
     */
    private static final int REASON_NOT_AUTHORIZED = 401;

    /**
     * Failure reason code indicating a temporary ban with code and expiry.
     *
     * @implNote WAWebFailureErrorCodes.FAILURE_REASON.REASON_TEMP_BANNED
     */
    private static final int REASON_TEMP_BANNED = 402;

    /**
     * Failure reason code indicating the account has been locked.
     *
     * @implNote WAWebFailureErrorCodes.FAILURE_REASON.REASON_LOCKED
     */
    private static final int REASON_LOCKED = 403;

    /**
     * Failure reason code indicating the client version is too old.
     *
     * @implNote WAWebFailureErrorCodes.FAILURE_REASON.REASON_CLIENT_TOO_OLD
     */
    private static final int REASON_CLIENT_TOO_OLD = 405;

    /**
     * Failure reason code indicating the account has been permanently banned.
     *
     * @implNote WAWebFailureErrorCodes.FAILURE_REASON.REASON_BANNED
     */
    private static final int REASON_BANNED = 406;

    /**
     * Failure reason code indicating a bad user agent string.
     *
     * @implNote WAWebFailureErrorCodes.FAILURE_REASON.REASON_BAD_USER_AGENT
     */
    private static final int REASON_BAD_USER_AGENT = 409;

    /**
     * Failure reason code indicating an internal server error.
     *
     * @implNote WAWebFailureErrorCodes.FAILURE_REASON.REASON_INTERNAL_SERVER_ERROR
     */
    private static final int REASON_INTERNAL_SERVER_ERROR = 500;

    /**
     * Failure reason code indicating an experimental server condition.
     *
     * @implNote WAWebFailureErrorCodes.FAILURE_REASON.REASON_EXPERIMENTAL
     */
    private static final int REASON_EXPERIMENTAL = 501;

    /**
     * Failure reason code indicating the service is temporarily unavailable.
     *
     * @implNote WAWebFailureErrorCodes.FAILURE_REASON.REASON_SERVICE_UNAVAILABLE
     */
    private static final int REASON_SERVICE_UNAVAILABLE = 503;

    /**
     * The WhatsApp client instance used to dispatch failure exceptions through
     * the pluggable error handler.
     *
     * @implNote WAWebHandleFailure.default (implicit via WAWebSocketModel, WAWebBackendEventBus)
     */
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a new failure stream handler with the specified client.
     *
     * @param whatsapp the WhatsApp client used to dispatch failure exceptions
     * @implNote WAWebHandleFailure.default
     */
    public FailureStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    /**
     * Handles an incoming {@code <failure>} stanza by parsing its attributes and
     * dispatching the appropriate exception based on the reason code.
     * <p>
     * The handler extracts the {@code reason}, {@code location}, {@code code},
     * {@code expire}, {@code message}, {@code url}, {@code logout_message_header},
     * {@code logout_message_subtext}, and {@code logout_message_locale} attributes
     * from the stanza. The {@code reason} attribute determines which exception type
     * is thrown:
     * <ul>
     *   <li>{@code REASON_LOCKED} (403): throws {@link WhatsAppSessionException.LoggedOut}</li>
     *   <li>{@code REASON_NOT_AUTHORIZED} (401) and {@code REASON_BANNED} (406):
     *       throw {@link WhatsAppSessionException.LoggedOut}</li>
     *   <li>{@code REASON_CLIENT_TOO_OLD} (405) and {@code REASON_BAD_USER_AGENT} (409):
     *       throw {@link WhatsAppConnectionException}</li>
     *   <li>{@code REASON_TEMP_BANNED} (402): throws {@link WhatsAppSessionException.Banned}
     *       if code and expire are present, otherwise throws {@link WhatsAppServerRuntimeException}</li>
     *   <li>{@code REASON_GENERIC_FAILURE} (400), {@code REASON_INTERNAL_SERVER_ERROR} (500),
     *       {@code REASON_EXPERIMENTAL} (501): logged as warning only</li>
     *   <li>{@code REASON_SERVICE_UNAVAILABLE} (503): logged as warning only</li>
     *   <li>Unrecognized reasons: throws {@link WhatsAppServerRuntimeException}</li>
     * </ul>
     *
     * @param node the {@code <failure>} stanza node received from the server
     * @implNote WAWebHandleFailure.default
     */
    @Override
    public void handle(Node node) {
        // WAWebHandleFailure.default - parse stanza attributes
        var reason = node.getAttributeAsInt("reason", (Integer) null);
        var location = node.getAttributeAsString("location", null); // WAWebHandleFailure.default
        var code = node.getAttributeAsInt("code", (Integer) null);
        var expire = node.getAttributeAsInt("expire", (Integer) null);
        var message = node.getAttributeAsString("message", null);
        var url = node.getAttributeAsString("url", null);
        var logoutMessageHeader = node.getAttributeAsString("logout_message_header", null);
        var logoutMessageSubtext = node.getAttributeAsString("logout_message_subtext", null);
        var logoutMessageLocale = node.getAttributeAsString("logout_message_locale", null); // WAWebHandleFailure.default

        LOGGER.log(System.Logger.Level.WARNING,
                "Received failure stanza: reason={0}, location={1}, code={2}, expire={3}, message={4}, url={5}",
                reason,
                location,
                code,
                expire,
                message,
                url);

        if (reason == null) {
            return;
        }

        // WAWebHandleFailure.default - switch on reason code
        switch (reason) {
            case REASON_LOCKED -> {
                // WAWebHandleFailure.default - REASON_LOCKED: logout with optional custom message
                whatsapp.handleFailure(new WhatsAppSessionException.LoggedOut(
                        "Account locked: reason=" + reason
                                + (logoutMessageHeader != null ? ", header=" + logoutMessageHeader : "")
                                + (logoutMessageSubtext != null ? ", subtext=" + logoutMessageSubtext : "")));
            }
            case REASON_NOT_AUTHORIZED, REASON_BANNED -> {
                // WAWebHandleFailure.default - REASON_NOT_AUTHORIZED/REASON_BANNED: logout
                whatsapp.handleFailure(new WhatsAppSessionException.LoggedOut(
                        "Server reported logout, reason=" + reason));
            }
            case REASON_CLIENT_TOO_OLD, REASON_BAD_USER_AGENT -> {
                // WAWebHandleFailure.default - REASON_CLIENT_TOO_OLD/REASON_BAD_USER_AGENT: client version rejected
                whatsapp.handleFailure(new WhatsAppConnectionException(
                        "Client version rejected by server, reason=" + reason));
            }
            case REASON_TEMP_BANNED -> {
                // WAWebHandleFailure.default - REASON_TEMP_BANNED: requires code+expire tuple
                if (code != null && expire != null) {
                    whatsapp.handleFailure(new WhatsAppSessionException.Banned(
                            "Temporary ban: code=" + code + ", expire=" + expire
                                    + (message != null ? ", message=" + message : "")
                                    + (url != null ? ", url=" + url : "")));
                } else {
                    // WAWebHandleFailure.default - malformed temp ban data: throw error
                    whatsapp.handleFailure(new WhatsAppServerRuntimeException(
                            "Incorrect temporary ban data: code=" + code + ", expire=" + expire));
                }
            }
            case REASON_GENERIC_FAILURE, REASON_INTERNAL_SERVER_ERROR, REASON_EXPERIMENTAL -> {
                // WAWebHandleFailure.default - warning only, no exception
                LOGGER.log(System.Logger.Level.WARNING,
                        "Server failure code {0}, no action taken", reason);
            }
            case REASON_SERVICE_UNAVAILABLE -> {
                // WAWebHandleFailure.default - warning only, no exception
                LOGGER.log(System.Logger.Level.WARNING,
                        "Service unavailable (reason {0})", reason);
            }
            default -> {
                // WAWebHandleFailure.default - unrecognized reason: throw error
                whatsapp.handleFailure(new WhatsAppServerRuntimeException(
                        "Failure reason " + reason + " not implemented yet"));
            }
        }
    }
}
