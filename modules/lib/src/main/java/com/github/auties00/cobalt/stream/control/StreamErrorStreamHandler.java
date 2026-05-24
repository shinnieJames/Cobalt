package com.github.auties00.cobalt.stream.control;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppSessionException;
import com.github.auties00.cobalt.exception.WhatsAppStreamException;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

/**
 * Handles {@code <stream:error>} stanzas that report protocol-level
 * failures requiring the socket to close.
 *
 * @apiNote
 * This handler is registered under the {@code "stream:error"} tag inside
 * {@link SocketStream} and classifies the inbound stanza into one of
 * three buckets: a {@code <conflict/>} child (another session took over
 * or the device was removed), a numeric {@code code} attribute (5xx
 * server stream error, with {@code 515} meaning "reconnect" and
 * {@code 516} meaning "logout") or an XML-validity child
 * ({@code <ack/>} or {@code <xml-not-well-formed/>}). Each bucket is
 * dispatched as the appropriate {@link WhatsAppSessionException}
 * subtype to the configured error handler.
 *
 * @implNote
 * This implementation departs from WA Web's inline recovery
 * ({@code WAComms.stopComms}, {@code startLogin}, {@code startLogout},
 * {@code clearCredentialsAndStoredData}, {@code triggerLogout}) and
 * instead surfaces the failure as a {@link WhatsAppSessionException}
 * subtype on {@link WhatsAppClient#handleFailure(Throwable)}: the
 * configured error handler decides between {@code DISCARD},
 * {@code DISCONNECT}, {@code RECONNECT}, {@code LOG_OUT} or
 * {@code BAN}. The malformed-XML branch surfaces as a
 * {@link WhatsAppStreamException.MalformedNode} so the error handler can
 * distinguish a server-reported bad-XML close from a generic session
 * close.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleStreamError")
public final class StreamErrorStreamHandler implements SocketStream.Handler {

    /**
     * The system logger used to surface unrecognised stream errors at
     * {@code WARNING} severity before dispatch.
     */
    private static final System.Logger LOGGER = System.getLogger(StreamErrorStreamHandler.class.getName());

    /**
     * The {@code code=515} stream error code that asks the client to
     * tear down the current socket and re-login.
     *
     * @apiNote
     * Matches the hardcoded {@code a.code===515} branch in WA Web's
     * {@code WAWebHandleStreamError.default} which runs
     * {@code WAComms.stopComms} then {@code startLogin}. Cobalt surfaces
     * this as {@link WhatsAppSessionException.Reconnect}.
     */
    private static final int STREAM_ERROR_RESTART_LOGIN = 515;

    /**
     * The {@code code=516} stream error code that forces the companion
     * device to log out.
     *
     * @apiNote
     * Matches the hardcoded {@code a.code===516} branch in WA Web's
     * {@code WAWebHandleStreamError.default} which calls
     * {@code commitDeviceLinkEvent(516)} on the WAM device-link reporter
     * and then runs {@code startLogout}. Cobalt surfaces this as
     * {@link WhatsAppSessionException.LoggedOut}.
     */
    private static final int STREAM_ERROR_LOGOUT = 516;

    /**
     * The {@link WhatsAppClient} used to dispatch the parsed failure
     * exception through the pluggable error handler.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a new stream error handler bound to the given client.
     *
     * @apiNote
     * Cobalt embedders never call this constructor directly; the
     * dispatcher in {@link SocketStream} instantiates the handler once per
     * client.
     *
     * @param whatsapp the {@link WhatsAppClient} on which the parsed
     *                 failure exception is dispatched
     */
    public StreamErrorStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Classifies the {@code <stream:error>} stanza using WA Web's parser
     * priority ({@code <conflict/>} child first, then {@code code}
     * attribute, then {@code <ack/>} or {@code <xml-not-well-formed/>}
     * child, otherwise unrecognised) and dispatches the matching
     * {@link WhatsAppSessionException} subtype.
     *
     * @implNote
     * This implementation never returns the {@code "CLOSE_SOCKET"}
     * sentinel that WA Web's {@code WAWebHandleStreamError.default}
     * resolves with: the socket close is left to the configured error
     * handler reacting to the dispatched exception.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebHandleStreamError", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public void handle(Node node) {
        var conflict = node.getChild("conflict").orElse(null);
        if (conflict != null) {
            handleConflict(conflict.getAttributeAsString("type", null));
            return;
        }

        var code = node.getAttributeAsInt("code", (Integer) null);
        if (code != null) {
            handleCode(code);
            return;
        }

        var ack = node.getChild("ack").orElse(null);
        if (ack != null) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Received stream:error ack for id={0}",
                    ack.getAttributeAsString("id", null));
            whatsapp.handleFailure(new WhatsAppSessionException.Closed("Stream error: ack"));
            return;
        }

        if (node.hasChild("xml-not-well-formed")) {
            whatsapp.handleFailure(new WhatsAppStreamException.MalformedNode("Server reported xml-not-well-formed"));
            return;
        }

        LOGGER.log(System.Logger.Level.WARNING, "Received unrecognized stream:error stanza: {0}", node);
        whatsapp.handleFailure(new WhatsAppSessionException.Closed("Stream error: unrecognized"));
    }

    /**
     * Dispatches the appropriate {@link WhatsAppSessionException} subtype
     * for a {@code <stream:error><conflict type=".../></stream:error>}
     * stanza.
     *
     * @apiNote
     * WA Web's parser maps {@code type="replaced"} to "another tab took
     * over the mutex" (logged at {@code ERROR} when the current tab still
     * holds it) and folds {@code type="device_removed"} together with
     * unknown values into the device-removal branch that calls
     * {@code clearCredentialsAndStoredData}. Cobalt mirrors the split
     * exactly: {@code "replaced"} surfaces as
     * {@link WhatsAppSessionException.Conflict} and any other value
     * surfaces as {@link WhatsAppSessionException.LoggedOut}.
     *
     * @implNote
     * This implementation has no equivalent of WA Web's
     * {@code WAWebUserPrefsTabMutex.currentTabHasMutex()} check because
     * Cobalt has no tab concept; the {@code "replaced"} branch is always
     * surfaced verbatim.
     *
     * @param type the {@code type} attribute of the {@code <conflict/>}
     *             child, or {@code null} when absent
     */
    private void handleConflict(String type) {
        if ("replaced".equals(type)) {
            whatsapp.handleFailure(new WhatsAppSessionException.Conflict("Stream replaced by another active session"));
            return;
        }

        whatsapp.handleFailure(new WhatsAppSessionException.LoggedOut("Server removed or invalidated this device"));
    }

    /**
     * Dispatches the appropriate {@link WhatsAppSessionException} subtype
     * for a {@code <stream:error code="..."/>} stanza.
     *
     * @apiNote
     * Codes in the 500-599 range follow WA Web's
     * {@code WAWebHandleStreamError.default} switch:
     * {@link #STREAM_ERROR_RESTART_LOGIN} drives a
     * {@link WhatsAppSessionException.Reconnect},
     * {@link #STREAM_ERROR_LOGOUT} drives a
     * {@link WhatsAppSessionException.LoggedOut}, and other 5xx values
     * surface as {@link WhatsAppSessionException.Closed}. Codes outside
     * the 5xx range also surface as {@link WhatsAppSessionException.Closed}.
     *
     * @implNote
     * This implementation does not emit the
     * {@code WAWebWamDeviceLinkReporter.commitDeviceLinkEvent(516)} call
     * that WA Web fires before the forced logout; the WAM event is
     * already covered when pairing fails through
     * {@code IqStreamHandler.emitMdLinkDeviceCompanionStage}.
     *
     * @param code the {@code code} attribute on the stanza
     */
    private void handleCode(int code) {
        LOGGER.log(System.Logger.Level.WARNING, "Received stream:error code={0}", code);
        if (code >= 500 && code < 600) {
            if (code == STREAM_ERROR_RESTART_LOGIN) {
                whatsapp.handleFailure(new WhatsAppSessionException.Reconnect("Server requested reconnect"));
                return;
            }

            if (code == STREAM_ERROR_LOGOUT) {
                whatsapp.handleFailure(new WhatsAppSessionException.LoggedOut("Server requested logout"));
                return;
            }

            whatsapp.handleFailure(new WhatsAppSessionException.Closed("Server stream error " + code));
            return;
        }

        whatsapp.handleFailure(new WhatsAppSessionException.Closed("Server stream error code " + code));
    }
}
