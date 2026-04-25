package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

/**
 * Enumerates the reasons an incoming WhatsApp message may be dropped before
 * being delivered to the local client, as reported by WAM telemetry.
 *
 * <p>Each constant maps to a fixed integer identifier that is serialized on
 * the wire; values must never be renumbered or reused.
 *
 * @implNote WAWebWamEnumMessageDropReasonType: the module default-exports a
 *     frozen object whose keys are the reason names and whose values are the
 *     integer identifiers; Cobalt mirrors the full enumeration with
 *     {@link WamEnumConstant} preserving each numeric value.
 */
@WamEnum
@WhatsAppWebModule(moduleName = "WAWebWamEnumMessageDropReasonType")
public enum MessageDropReasonType {
    /**
     * The message was dropped because it was tombstoned by SyncD.
     *
     * @implNote WAWebWamEnumMessageDropReasonType.MESSAGE_DROP_REASON_TYPE: {@code SYNCD_DELETION = 1}.
     */
    @WamEnumConstant(1)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMessageDropReasonType",
            exports = "MESSAGE_DROP_REASON_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    SYNCD_DELETION,

    /**
     * The message was a group admin revoke but the sender did not have the
     * admin-revoke capability enabled.
     *
     * @implNote WAWebWamEnumMessageDropReasonType.MESSAGE_DROP_REASON_TYPE: {@code ADMIN_REVOKE_NOT_ENABLED = 2}.
     */
    @WamEnumConstant(2)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMessageDropReasonType",
            exports = "MESSAGE_DROP_REASON_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ADMIN_REVOKE_NOT_ENABLED,

    /**
     * The message arrived with a Signal counter older than the one last
     * processed for the sender's session.
     *
     * @implNote WAWebWamEnumMessageDropReasonType.MESSAGE_DROP_REASON_TYPE: {@code RECEIVED_WITH_OLD_COUNTER = 3}.
     */
    @WamEnumConstant(3)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMessageDropReasonType",
            exports = "MESSAGE_DROP_REASON_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    RECEIVED_WITH_OLD_COUNTER,

    /**
     * The message stanza failed structural validation.
     *
     * @implNote WAWebWamEnumMessageDropReasonType.MESSAGE_DROP_REASON_TYPE: {@code INVALID_STANZA = 4}.
     */
    @WamEnumConstant(4)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMessageDropReasonType",
            exports = "MESSAGE_DROP_REASON_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    INVALID_STANZA,

    /**
     * The decoded protobuf payload was malformed.
     *
     * @implNote WAWebWamEnumMessageDropReasonType.MESSAGE_DROP_REASON_TYPE: {@code INVALID_PROTOBUF = 5}.
     */
    @WamEnumConstant(5)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMessageDropReasonType",
            exports = "MESSAGE_DROP_REASON_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    INVALID_PROTOBUF,

    /**
     * The associated per-message secret lookup or derivation failed.
     *
     * @implNote WAWebWamEnumMessageDropReasonType.MESSAGE_DROP_REASON_TYPE: {@code MESSAGE_SECRET_ERROR = 6}.
     */
    @WamEnumConstant(6)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMessageDropReasonType",
            exports = "MESSAGE_DROP_REASON_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    MESSAGE_SECRET_ERROR,

    /**
     * The stanza was addressed to a LID that the local client cannot resolve
     * or accept.
     *
     * @implNote WAWebWamEnumMessageDropReasonType.MESSAGE_DROP_REASON_TYPE: {@code INVALID_LID_ADDRESSED_MESSAGE = 7}.
     */
    @WamEnumConstant(7)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMessageDropReasonType",
            exports = "MESSAGE_DROP_REASON_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    INVALID_LID_ADDRESSED_MESSAGE,

    /**
     * The inner message payload was of an unknown type the client cannot
     * process.
     *
     * @implNote WAWebWamEnumMessageDropReasonType.MESSAGE_DROP_REASON_TYPE: {@code UNKNOWN_MESSAGE_TYPE = 8}.
     */
    @WamEnumConstant(8)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMessageDropReasonType",
            exports = "MESSAGE_DROP_REASON_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    UNKNOWN_MESSAGE_TYPE,

    /**
     * The message could not be persisted because an underlying database
     * operation failed.
     *
     * @implNote WAWebWamEnumMessageDropReasonType.MESSAGE_DROP_REASON_TYPE: {@code DB_OPERATION_FAILED = 9}.
     */
    @WamEnumConstant(9)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMessageDropReasonType",
            exports = "MESSAGE_DROP_REASON_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    DB_OPERATION_FAILED,

    /**
     * The message was dropped due to a generic internal client error.
     *
     * @implNote WAWebWamEnumMessageDropReasonType.MESSAGE_DROP_REASON_TYPE: {@code INTERNAL_ERROR = 10}.
     */
    @WamEnumConstant(10)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMessageDropReasonType",
            exports = "MESSAGE_DROP_REASON_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    INTERNAL_ERROR,

    /**
     * The message was dropped because its ephemeral expiry had already
     * elapsed.
     *
     * @implNote WAWebWamEnumMessageDropReasonType.MESSAGE_DROP_REASON_TYPE: {@code EXPIRED = 11}.
     */
    @WamEnumConstant(11)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMessageDropReasonType",
            exports = "MESSAGE_DROP_REASON_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    EXPIRED,

    /**
     * A stanza from a hosted-companion device failed validation and was
     * dropped.
     *
     * @implNote WAWebWamEnumMessageDropReasonType.MESSAGE_DROP_REASON_TYPE: {@code INVALID_HOSTED_COMPANION_STANZA = 12}.
     */
    @WamEnumConstant(12)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMessageDropReasonType",
            exports = "MESSAGE_DROP_REASON_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    INVALID_HOSTED_COMPANION_STANZA,

    /**
     * The message had already been revoked by the sender.
     *
     * @implNote WAWebWamEnumMessageDropReasonType.MESSAGE_DROP_REASON_TYPE: {@code MESSAGE_REVOKED = 13}.
     */
    @WamEnumConstant(13)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMessageDropReasonType",
            exports = "MESSAGE_DROP_REASON_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    MESSAGE_REVOKED,

    /**
     * A payment message had already been revoked by the sender.
     *
     * @implNote WAWebWamEnumMessageDropReasonType.MESSAGE_DROP_REASON_TYPE: {@code PAYMENT_MESSAGE_REVOKED = 14}.
     */
    @WamEnumConstant(14)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMessageDropReasonType",
            exports = "MESSAGE_DROP_REASON_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    PAYMENT_MESSAGE_REVOKED,

    /**
     * The incoming stanza was a duplicate of a message already processed.
     *
     * @implNote WAWebWamEnumMessageDropReasonType.MESSAGE_DROP_REASON_TYPE: {@code DUPLICATE_MESSAGE = 15}.
     */
    @WamEnumConstant(15)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMessageDropReasonType",
            exports = "MESSAGE_DROP_REASON_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    DUPLICATE_MESSAGE,

    /**
     * The stanza was a duplicate delivery receipt for a message already
     * acknowledged.
     *
     * @implNote WAWebWamEnumMessageDropReasonType.MESSAGE_DROP_REASON_TYPE: {@code DUPLICATE_DELIVERY = 16}.
     */
    @WamEnumConstant(16)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMessageDropReasonType",
            exports = "MESSAGE_DROP_REASON_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    DUPLICATE_DELIVERY,

    /**
     * The message referenced another message that could not be resolved.
     *
     * @implNote WAWebWamEnumMessageDropReasonType.MESSAGE_DROP_REASON_TYPE: {@code INVALID_MESSAGE_REFERENCE = 17}.
     */
    @WamEnumConstant(17)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMessageDropReasonType",
            exports = "MESSAGE_DROP_REASON_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    INVALID_MESSAGE_REFERENCE,

    /**
     * The message type is not supported on this client build.
     *
     * @implNote WAWebWamEnumMessageDropReasonType.MESSAGE_DROP_REASON_TYPE: {@code UNSUPPORTED_MESSAGE = 18}.
     */
    @WamEnumConstant(18)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMessageDropReasonType",
            exports = "MESSAGE_DROP_REASON_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    UNSUPPORTED_MESSAGE,

    /**
     * A duplicate message was received that looked like a malicious replay
     * attempt (for example diverging body or key).
     *
     * @implNote WAWebWamEnumMessageDropReasonType.MESSAGE_DROP_REASON_TYPE: {@code MALICIOUS_DUPLICATE_MESSAGE = 19}.
     */
    @WamEnumConstant(19)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMessageDropReasonType",
            exports = "MESSAGE_DROP_REASON_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    MALICIOUS_DUPLICATE_MESSAGE,

    /**
     * A peer-protocol message was received from a JID other than the local
     * user's own account.
     *
     * @implNote WAWebWamEnumMessageDropReasonType.MESSAGE_DROP_REASON_TYPE: {@code PEER_MESSAGE_FROM_OTHER_USER = 20}.
     */
    @WamEnumConstant(20)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMessageDropReasonType",
            exports = "MESSAGE_DROP_REASON_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    PEER_MESSAGE_FROM_OTHER_USER,

    /**
     * A peer-protocol message was structurally invalid.
     *
     * @implNote WAWebWamEnumMessageDropReasonType.MESSAGE_DROP_REASON_TYPE: {@code INVALID_PEER_MESSAGE = 21}.
     */
    @WamEnumConstant(21)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMessageDropReasonType",
            exports = "MESSAGE_DROP_REASON_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    INVALID_PEER_MESSAGE,

    /**
     * The attached reporting token failed validation.
     *
     * @implNote WAWebWamEnumMessageDropReasonType.MESSAGE_DROP_REASON_TYPE: {@code INVALID_REPORTING_TOKEN = 22}.
     */
    @WamEnumConstant(22)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMessageDropReasonType",
            exports = "MESSAGE_DROP_REASON_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    INVALID_REPORTING_TOKEN,

    /**
     * The stanza was missing a required reporting token.
     *
     * @implNote WAWebWamEnumMessageDropReasonType.MESSAGE_DROP_REASON_TYPE: {@code MISSING_REPORTING_TOKEN = 23}.
     */
    @WamEnumConstant(23)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMessageDropReasonType",
            exports = "MESSAGE_DROP_REASON_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    MISSING_REPORTING_TOKEN
}
