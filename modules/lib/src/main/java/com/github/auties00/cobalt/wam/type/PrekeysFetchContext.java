package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;
import com.github.auties00.cobalt.wam.event.PrekeysDepletionEvent;

/**
 * Enumerates the call-site contexts under which a client requests one-time
 * prekey bundles from the server; the identifier is forwarded to WAM on the
 * {@link PrekeysDepletionEvent#prekeysFetchReason()} property whenever the
 * response shows that the receiving peer has run out of prekeys.
 *
 * <p>Each constant corresponds to a distinct upstream operation that triggered
 * the prekey fetch (a regular end-to-end message send, a peer-message sync, a
 * vname-certificate lookup, a live-location retry or key rotation, a multi-
 * device call attempt, a peer-E2E-failure repair, an identity-change
 * notification, a Signal backoff retry, a background user-intent prefetch, a
 * client-initiated resend/retry, a status-prefetch pass, or a group
 * SenderKey-distribution message). The bare integer value is transmitted on
 * the wire so server operators can triage prekey depletions by originating
 * flow rather than by aggregate count.
 *
 * @implNote WAWebWamEnumPrekeysFetchContext: the module default-exports a
 *     frozen namespace object {@code PREKEYS_FETCH_CONTEXT} whose keys are
 *     the context names and whose values are the integer identifiers. Cobalt
 *     mirrors the full enumeration with {@link WamEnumConstant} preserving
 *     each numeric value so that the Cobalt-side WAM event carries the same
 *     opaque context code as the real WhatsApp client.
 */
@WamEnum
@WhatsAppWebModule(moduleName = "WAWebWamEnumPrekeysFetchContext")
public enum PrekeysFetchContext {
    /**
     * Prekeys were fetched while dispatching a regular end-to-end encrypted
     * one-to-one or fanout message.
     *
     * @implNote WAWebWamEnumPrekeysFetchContext.PREKEYS_FETCH_CONTEXT.SEND_MESSAGE: {@code 1}.
     */
    @WamEnumConstant(1)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumPrekeysFetchContext",
            exports = "PREKEYS_FETCH_CONTEXT",
            adaptation = WhatsAppAdaptation.DIRECT)
    SEND_MESSAGE,

    /**
     * Prekeys were fetched while resolving a verified-name certificate for a
     * business contact.
     *
     * @implNote WAWebWamEnumPrekeysFetchContext.PREKEYS_FETCH_CONTEXT.GET_VNAME_CERTIFICATE: {@code 2}.
     */
    @WamEnumConstant(2)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumPrekeysFetchContext",
            exports = "PREKEYS_FETCH_CONTEXT",
            adaptation = WhatsAppAdaptation.DIRECT)
    GET_VNAME_CERTIFICATE,

    /**
     * Prekeys were fetched while retrying a live-location update after a
     * prior delivery failure.
     *
     * @implNote WAWebWamEnumPrekeysFetchContext.PREKEYS_FETCH_CONTEXT.SEND_LIVE_LOCATION_RETRY: {@code 3}.
     */
    @WamEnumConstant(3)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumPrekeysFetchContext",
            exports = "PREKEYS_FETCH_CONTEXT",
            adaptation = WhatsAppAdaptation.DIRECT)
    SEND_LIVE_LOCATION_RETRY,

    /**
     * Prekeys were fetched while rotating or distributing a live-location
     * encryption key to a recipient device.
     *
     * @implNote WAWebWamEnumPrekeysFetchContext.PREKEYS_FETCH_CONTEXT.SEND_LIVE_LOCATION_KEY: {@code 4}.
     */
    @WamEnumConstant(4)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumPrekeysFetchContext",
            exports = "PREKEYS_FETCH_CONTEXT",
            adaptation = WhatsAppAdaptation.DIRECT)
    SEND_LIVE_LOCATION_KEY,

    /**
     * Prekeys were fetched while sending a multi-device peer-sync message
     * (e.g. app-state, history, or device pair broadcast to a companion).
     *
     * @implNote WAWebWamEnumPrekeysFetchContext.PREKEYS_FETCH_CONTEXT.SEND_PEER_MESSAGE: {@code 5}.
     */
    @WamEnumConstant(5)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumPrekeysFetchContext",
            exports = "PREKEYS_FETCH_CONTEXT",
            adaptation = WhatsAppAdaptation.DIRECT)
    SEND_PEER_MESSAGE,

    /**
     * Prekeys were fetched while establishing a multi-device call session
     * with a remote peer.
     *
     * @implNote WAWebWamEnumPrekeysFetchContext.PREKEYS_FETCH_CONTEXT.MULTI_DEVICE_CALL: {@code 6}.
     */
    @WamEnumConstant(6)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumPrekeysFetchContext",
            exports = "PREKEYS_FETCH_CONTEXT",
            adaptation = WhatsAppAdaptation.DIRECT)
    MULTI_DEVICE_CALL,

    /**
     * Prekeys were fetched in response to a peer-to-peer end-to-end
     * encryption failure during a call, as part of the repair pass.
     *
     * @implNote WAWebWamEnumPrekeysFetchContext.PREKEYS_FETCH_CONTEXT.CALL_PEER_E2E_FAIL: {@code 7}.
     */
    @WamEnumConstant(7)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumPrekeysFetchContext",
            exports = "PREKEYS_FETCH_CONTEXT",
            adaptation = WhatsAppAdaptation.DIRECT)
    CALL_PEER_E2E_FAIL,

    /**
     * Prekeys were fetched after receiving an identity-change notification,
     * to rebuild a Signal session with the peer under its new identity key.
     *
     * @implNote WAWebWamEnumPrekeysFetchContext.PREKEYS_FETCH_CONTEXT.IDENTITY_CHANGE_NOTIFICATION: {@code 8}.
     */
    @WamEnumConstant(8)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumPrekeysFetchContext",
            exports = "PREKEYS_FETCH_CONTEXT",
            adaptation = WhatsAppAdaptation.DIRECT)
    IDENTITY_CHANGE_NOTIFICATION,

    /**
     * Prekeys were fetched after a Signal backoff window elapsed, when the
     * client retries a previously deferred prekey request.
     *
     * @implNote WAWebWamEnumPrekeysFetchContext.PREKEYS_FETCH_CONTEXT.BACK_OFF: {@code 9}.
     */
    @WamEnumConstant(9)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumPrekeysFetchContext",
            exports = "PREKEYS_FETCH_CONTEXT",
            adaptation = WhatsAppAdaptation.DIRECT)
    BACK_OFF,

    /**
     * Prekeys were fetched proactively during a user-intent prefetch pass
     * (e.g. the user opened a chat, so the client eagerly establishes an
     * end-to-end session in anticipation of a send).
     *
     * @implNote WAWebWamEnumPrekeysFetchContext.PREKEYS_FETCH_CONTEXT.USER_INTENT_PREFETCH: {@code 10}.
     */
    @WamEnumConstant(10)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumPrekeysFetchContext",
            exports = "PREKEYS_FETCH_CONTEXT",
            adaptation = WhatsAppAdaptation.DIRECT)
    USER_INTENT_PREFETCH,

    /**
     * Prekeys were fetched while resending a message that had previously
     * failed to deliver.
     *
     * @implNote WAWebWamEnumPrekeysFetchContext.PREKEYS_FETCH_CONTEXT.RESEND_MESSAGE: {@code 11}.
     */
    @WamEnumConstant(11)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumPrekeysFetchContext",
            exports = "PREKEYS_FETCH_CONTEXT",
            adaptation = WhatsAppAdaptation.DIRECT)
    RESEND_MESSAGE,

    /**
     * Prekeys were fetched while servicing a server-side retry request (a
     * {@code <receipt type="retry">} stanza from the recipient).
     *
     * @implNote WAWebWamEnumPrekeysFetchContext.PREKEYS_FETCH_CONTEXT.RETRY_MESSAGE: {@code 12}.
     */
    @WamEnumConstant(12)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumPrekeysFetchContext",
            exports = "PREKEYS_FETCH_CONTEXT",
            adaptation = WhatsAppAdaptation.DIRECT)
    RETRY_MESSAGE,

    /**
     * Prekeys were fetched during a user-intent status prefetch pass (e.g.
     * opening the status tray eagerly establishes sessions with status
     * viewers in anticipation of posting).
     *
     * @implNote WAWebWamEnumPrekeysFetchContext.PREKEYS_FETCH_CONTEXT.USER_INTENT_STATUS_PREFETCH: {@code 13}.
     */
    @WamEnumConstant(13)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumPrekeysFetchContext",
            exports = "PREKEYS_FETCH_CONTEXT",
            adaptation = WhatsAppAdaptation.DIRECT)
    USER_INTENT_STATUS_PREFETCH,

    /**
     * Prekeys were fetched while distributing a group SenderKey to a new
     * or re-synced participant device.
     *
     * @implNote WAWebWamEnumPrekeysFetchContext.PREKEYS_FETCH_CONTEXT.SEND_SENDERKEY: {@code 14}.
     */
    @WamEnumConstant(14)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumPrekeysFetchContext",
            exports = "PREKEYS_FETCH_CONTEXT",
            adaptation = WhatsAppAdaptation.DIRECT)
    SEND_SENDERKEY
}
