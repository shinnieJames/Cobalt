package com.github.auties00.cobalt.stream.control;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppConnectionException;
import com.github.auties00.cobalt.exception.WhatsAppException;
import com.github.auties00.cobalt.exception.WhatsAppServerRuntimeException;
import com.github.auties00.cobalt.exception.WhatsAppSessionException;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

/**
 * Handles {@code <failure>} stanzas that report session-fatal error
 * conditions during or after the post-handshake exchange.
 *
 * @apiNote
 * This handler is registered under the {@code "failure"} tag inside
 * {@link SocketStream}. Failure stanzas are the WA wire signal for
 * "this session cannot continue": the server pushes a numeric
 * {@code reason} drawn from {@link WhatsAppWebModule WAWebFailureErrorCodes}.FAILURE_REASON
 * and the appropriate recovery decision (logout, ban, upgrade required,
 * transient) is left to the configured {@link WhatsAppClient}. Cobalt
 * embedders react by handling the dispatched {@link WhatsAppException}
 * subtype in their {@code WhatsAppClientErrorHandler}.
 *
 * @implNote
 * This implementation deliberately departs from WA Web's inline recovery
 * logic ({@code clearCredentialsAndStoredData}, in-app updater calls,
 * temporary-ban backend event bus, {@code logPageLoadErrorForcedLogout}):
 * Cobalt instead instantiates one of the sealed
 * {@link WhatsAppSessionException} variants, the
 * {@link WhatsAppConnectionException} for version-mismatch reasons, or
 * the generic {@link WhatsAppServerRuntimeException} fallback, and hands
 * it to {@link WhatsAppClient#handleFailure(Throwable)} so the configured
 * error handler chooses the next action.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleFailure")
@WhatsAppWebModule(moduleName = "WAWebFailureErrorCodes")
public final class FailureStreamHandler implements SocketStream.Handler {

    /**
     * The system logger used to record every received failure stanza and
     * any reason codes that do not trigger an exception dispatch.
     */
    private static final System.Logger LOGGER = System.getLogger(FailureStreamHandler.class.getName());

    /**
     * The {@code reason=400} (generic failure) code; logged at
     * {@code WARNING} with no recovery dispatch.
     *
     * @apiNote
     * Matches {@code WAWebFailureErrorCodes.FAILURE_REASON.REASON_GENERIC_FAILURE}.
     */
    private static final int REASON_GENERIC_FAILURE = 400;

    /**
     * The {@code reason=401} (not authorized) code; dispatches a
     * {@link WhatsAppSessionException.LoggedOut}.
     *
     * @apiNote
     * Matches {@code WAWebFailureErrorCodes.FAILURE_REASON.REASON_NOT_AUTHORIZED}.
     * Surfaces a forced logout in WA Web ({@code Socket.clearCredentialsAndStoredData}).
     */
    private static final int REASON_NOT_AUTHORIZED = 401;

    /**
     * The {@code reason=402} (temporary ban) code; dispatches a
     * {@link WhatsAppSessionException.Banned} carrying the {@code code},
     * {@code expire}, {@code message} and {@code url} attributes when
     * present.
     *
     * @apiNote
     * Matches {@code WAWebFailureErrorCodes.FAILURE_REASON.REASON_TEMP_BANNED}.
     * WA Web surfaces this through {@code BackendEventBus.triggerTemporaryBan}
     * which drives the temp-ban screen; Cobalt is headless and only exposes
     * the metadata on the exception.
     */
    private static final int REASON_TEMP_BANNED = 402;

    /**
     * The {@code reason=403} (locked account) code; dispatches a
     * {@link WhatsAppSessionException.LoggedOut} carrying any logout
     * message header, subtext and locale attributes that the server
     * provided.
     *
     * @apiNote
     * Matches {@code WAWebFailureErrorCodes.FAILURE_REASON.REASON_LOCKED}.
     */
    private static final int REASON_LOCKED = 403;

    /**
     * The {@code reason=405} (client too old) code; dispatches a
     * {@link WhatsAppConnectionException} so the embedder updates the
     * pinned WA Web version.
     *
     * @apiNote
     * Matches {@code WAWebFailureErrorCodes.FAILURE_REASON.REASON_CLIENT_TOO_OLD}.
     * WA Web's {@code WAWebUpdater.Updater.update} call has no Cobalt
     * counterpart; the embedder upgrades by republishing with a newer
     * pinned bundle.
     */
    private static final int REASON_CLIENT_TOO_OLD = 405;

    /**
     * The {@code reason=406} (permanent ban) code; dispatches a
     * {@link WhatsAppSessionException.LoggedOut}.
     *
     * @apiNote
     * Matches {@code WAWebFailureErrorCodes.FAILURE_REASON.REASON_BANNED}.
     * WA Web treats the same way as {@code REASON_NOT_AUTHORIZED}: the
     * recovery path runs {@code clearCredentialsAndStoredData} then fires
     * {@code triggerLogout}. Cobalt collapses both into the same
     * {@link WhatsAppSessionException.LoggedOut} dispatch.
     */
    private static final int REASON_BANNED = 406;

    /**
     * The {@code reason=409} (bad user agent) code; dispatches a
     * {@link WhatsAppConnectionException}.
     *
     * @apiNote
     * Matches {@code WAWebFailureErrorCodes.FAILURE_REASON.REASON_BAD_USER_AGENT}.
     * Treated as a version-mismatch in WA Web; the embedder should update
     * the pinned WA Web bundle or the spoofed user agent string.
     */
    private static final int REASON_BAD_USER_AGENT = 409;

    /**
     * The {@code reason=500} (internal server error) code; logged at
     * {@code WARNING} with no recovery dispatch.
     *
     * @apiNote
     * Matches {@code WAWebFailureErrorCodes.FAILURE_REASON.REASON_INTERNAL_SERVER_ERROR}.
     */
    private static final int REASON_INTERNAL_SERVER_ERROR = 500;

    /**
     * The {@code reason=501} (experimental) code; logged at
     * {@code WARNING} with no recovery dispatch.
     *
     * @apiNote
     * Matches {@code WAWebFailureErrorCodes.FAILURE_REASON.REASON_EXPERIMENTAL}.
     */
    private static final int REASON_EXPERIMENTAL = 501;

    /**
     * The {@code reason=503} (service unavailable) code; logged at
     * {@code WARNING} with no recovery dispatch.
     *
     * @apiNote
     * Matches {@code WAWebFailureErrorCodes.FAILURE_REASON.REASON_SERVICE_UNAVAILABLE}.
     * WA Web also triggers a service-unavailable UI banner; Cobalt is
     * headless and only logs.
     */
    private static final int REASON_SERVICE_UNAVAILABLE = 503;

    /**
     * The {@link WhatsAppClient} used to dispatch the parsed failure
     * exception through the pluggable error handler.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a new failure stream handler bound to the given client.
     *
     * @apiNote
     * Cobalt embedders never call this constructor directly; the
     * dispatcher in {@link SocketStream} instantiates the handler once per
     * client.
     *
     * @param whatsapp the {@link WhatsAppClient} on which the parsed
     *                 failure exception is dispatched
     */
    public FailureStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Parses the {@code reason}, {@code location}, {@code code},
     * {@code expire}, {@code message}, {@code url} and three
     * {@code logout_message_*} attributes from the {@code <failure>}
     * stanza and dispatches the recovery exception that matches the
     * {@code reason} value. Logout-style reasons surface as
     * {@link WhatsAppSessionException.LoggedOut}; the temp-ban reason
     * surfaces as {@link WhatsAppSessionException.Banned}; version
     * mismatches surface as {@link WhatsAppConnectionException};
     * informational 4xx/5xx reasons are logged without dispatch; anything
     * else is wrapped in a {@link WhatsAppServerRuntimeException}.
     *
     * @implNote
     * This implementation does not implement WA Web's inline UI/storage
     * recovery (logout banners, updater modal, temp-ban screen,
     * service-unavailable banner); the exception subtype is the only
     * signal handed to the configured error handler. A missing or
     * out-of-range {@code reason} attribute is mapped to
     * {@link WhatsAppServerRuntimeException} rather than rejecting the
     * promise as WA Web's {@code WADeprecatedWapParser} does.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebHandleFailure", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public void handle(Node node) {
        var reason = node.getAttributeAsInt("reason", (Integer) null);
        var location = node.getAttributeAsString("location", null);
        var code = node.getAttributeAsInt("code", (Integer) null);
        var expire = node.getAttributeAsInt("expire", (Integer) null);
        var message = node.getAttributeAsString("message", null);
        var url = node.getAttributeAsString("url", null);
        var logoutMessageHeader = node.getAttributeAsString("logout_message_header", null);
        var logoutMessageSubtext = node.getAttributeAsString("logout_message_subtext", null);
        var logoutMessageLocale = node.getAttributeAsString("logout_message_locale", null);

        LOGGER.log(System.Logger.Level.WARNING,
                "Received failure stanza: reason={0}, location={1}, code={2}, expire={3}, message={4}, url={5}",
                reason,
                location,
                code,
                expire,
                message,
                url);

        if (reason == null) {
            whatsapp.handleFailure(new WhatsAppServerRuntimeException(
                    "Malformed failure stanza: missing reason attribute"));
            return;
        }

        switch (reason) {
            case REASON_LOCKED -> {
                whatsapp.handleFailure(new WhatsAppSessionException.LoggedOut(
                        "Account locked: reason=" + reason
                                + (logoutMessageHeader != null ? ", header=" + logoutMessageHeader : "")
                                + (logoutMessageSubtext != null ? ", subtext=" + logoutMessageSubtext : "")
                                + (logoutMessageLocale != null ? ", locale=" + logoutMessageLocale : "")));
            }
            case REASON_NOT_AUTHORIZED, REASON_BANNED -> {
                whatsapp.handleFailure(new WhatsAppSessionException.LoggedOut(
                        "Server reported logout, reason=" + reason));
            }
            case REASON_CLIENT_TOO_OLD, REASON_BAD_USER_AGENT -> {
                whatsapp.handleFailure(new WhatsAppConnectionException(
                        "Client version rejected by server, reason=" + reason));
            }
            case REASON_TEMP_BANNED -> {
                if (code != null && expire != null) {
                    whatsapp.handleFailure(new WhatsAppSessionException.Banned(
                            "Temporary ban: code=" + code + ", expire=" + expire
                                    + (message != null ? ", message=" + message : "")
                                    + (url != null ? ", url=" + url : "")));
                } else {
                    whatsapp.handleFailure(new WhatsAppServerRuntimeException(
                            "Incorrect temporary ban data: code=" + code + ", expire=" + expire));
                }
            }
            case REASON_GENERIC_FAILURE, REASON_INTERNAL_SERVER_ERROR, REASON_EXPERIMENTAL -> {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Server failure code {0}, no action taken", reason);
            }
            case REASON_SERVICE_UNAVAILABLE -> {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Service unavailable (reason {0})", reason);
            }
            default -> {
                whatsapp.handleFailure(new WhatsAppServerRuntimeException(
                        "Failure reason " + reason + " not implemented yet"));
            }
        }
    }
}
