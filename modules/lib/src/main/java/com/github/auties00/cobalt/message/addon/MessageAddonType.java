package com.github.auties00.cobalt.message.addon;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Enumerates the use-case labels that WhatsApp mixes into the HKDF info
 * parameter when deriving a message-secret-derived encryption or MAC key.
 *
 * <p>Eight of the nine variants drive dual-encrypted addons (poll votes,
 * reactions inside CAG threads, encrypted comments, event responses, event
 * edits, poll edits, poll add-options, message edits): each binds its HKDF
 * derivation to a distinct label so that keys derived for different use cases
 * never collide even when the parent message, sender, and stanza id are
 * identical. The ninth variant, {@link #REPORT_TOKEN}, is used by the
 * reporting-token flow to derive the HMAC key that authenticates abuse
 * reports, not for AES-GCM encryption.
 *
 * <p>Each value also records whether the use case applies additional
 * authenticated data in AES-GCM. Only poll votes and event responses set
 * that flag, matching WA Web's inline check that only emits AAD for those
 * two {@code MsgKind} variants. The flag is always {@code false} for
 * {@link #REPORT_TOKEN} because reporting tokens do not use AES-GCM.
 *
 * @implNote WAUseCaseSecret.UseCaseSecretModificationType: enumerated as
 * {@code $InternalEnum({POLL_VOTE:"Poll Vote",ENC_REACTION:"Enc Reaction",
 * ENC_COMMENT:"Enc Comment",REPORT_TOKEN:"Report Token",
 * EVENT_RESPONSE:"Event Response",EVENT_EDIT_ENCRYPTED:"Event Edit",
 * POLL_EDIT_ENCRYPTED:"Poll Edit",POLL_ADD_OPTION:"Poll Add Option",
 * MESSAGE_EDIT:"Message Edit"})}. WAWebAddonEncryption.C maps each
 * {@code WAWebMsgType.MsgKind} to one of these labels and to the matching
 * protobuf spec. WAWebReportingTokenUtils.genReportingTokenKeyFromMessageSecret
 * passes {@link #REPORT_TOKEN} into the same {@code WABinary.Binary.build}
 * construction when deriving the reporting token key.
 * Cobalt simplifies {@code EVENT_EDIT_ENCRYPTED} to {@link #EVENT_EDIT} and
 * {@code POLL_EDIT_ENCRYPTED} to {@link #POLL_EDIT}; the enum constant names
 * drop the redundant {@code _ENCRYPTED} suffix but the associated wire strings
 * ({@code "Event Edit"}, {@code "Poll Edit"}) are preserved byte-for-byte.
 */
@WhatsAppWebModule(moduleName = "WAUseCaseSecret")
@WhatsAppWebModule(moduleName = "WAWebAddonEncryption")
public enum MessageAddonType {
    /**
     * Poll vote addon.
     *
     * <p>Produced when a user votes on a poll and dual-encrypted under a key
     * derived with this label. Uses AAD
     * ({@code stanzaId + "\0" + voterJid}) so that a malicious server cannot
     * reattribute a vote to a different user.
     *
     * @implNote WAUseCaseSecret.UseCaseSecretModificationType.POLL_VOTE:
     * {@code "Poll Vote"}. WAWebAddonEncryption.C maps
     * {@code MsgKind.PollVoteEncrypted} and
     * {@code MsgKind.PollVoteDecrypted} to this label.
     */
    @WhatsAppWebExport(moduleName = "WAUseCaseSecret", exports = "UseCaseSecretModificationType",
            adaptation = WhatsAppAdaptation.DIRECT)
    POLL_VOTE("Poll Vote", true),

    /**
     * Encrypted reaction addon, used for reactions posted inside CAG
     * (community / announcement group) threads where the default
     * non-encrypted reaction wire format would leak emoji content to the
     * server.
     *
     * @implNote WAUseCaseSecret.UseCaseSecretModificationType.ENC_REACTION:
     * {@code "Enc Reaction"}. WAWebAddonEncryption.C maps
     * {@code MsgKind.ReactionEncrypted} and
     * {@code MsgKind.ReactionDecrypted} to this label.
     */
    @WhatsAppWebExport(moduleName = "WAUseCaseSecret", exports = "UseCaseSecretModificationType",
            adaptation = WhatsAppAdaptation.DIRECT)
    ENC_REACTION("Enc Reaction", false),

    /**
     * Encrypted comment addon.
     *
     * <p>Used when a comment is attached to a message in a CAG thread: the
     * inner {@code MessageSpec} payload is dual-encrypted so the server can
     * route the comment without reading its body.
     *
     * @implNote WAUseCaseSecret.UseCaseSecretModificationType.ENC_COMMENT:
     * {@code "Enc Comment"}. WAWebAddonEncryption.C maps
     * {@code MsgKind.CommentEncrypted} and
     * {@code MsgKind.CommentDecrypted} to this label.
     */
    @WhatsAppWebExport(moduleName = "WAUseCaseSecret", exports = "UseCaseSecretModificationType",
            adaptation = WhatsAppAdaptation.DIRECT)
    ENC_COMMENT("Enc Comment", false),

    /**
     * Reporting token label.
     *
     * <p>Unlike the other variants, this label does not drive AES-GCM
     * encryption of an addon payload. It is mixed into the HKDF info when
     * deriving the 32-byte HMAC key used to compute franking tags that the
     * server verifies on abuse reports. The reporting-token flow lives in
     * {@code WAWebReportingTokenUtils} rather than {@code WAWebAddonEncryption}.
     *
     * @implNote WAUseCaseSecret.UseCaseSecretModificationType.REPORT_TOKEN:
     * {@code "Report Token"}. WAWebReportingTokenUtils.genReportingTokenKeyFromMessageSecret
     * passes this value as the fourth argument to {@code Binary.build}.
     */
    @WhatsAppWebExport(moduleName = "WAUseCaseSecret", exports = "UseCaseSecretModificationType",
            adaptation = WhatsAppAdaptation.DIRECT)
    REPORT_TOKEN("Report Token", false),

    /**
     * Event response addon (RSVP).
     *
     * <p>Uses AAD ({@code stanzaId + "\0" + responderJid}) so that a
     * malicious server cannot lift an encrypted RSVP emitted by one user and
     * replay it as if it came from a different user.
     *
     * @implNote WAUseCaseSecret.UseCaseSecretModificationType.EVENT_RESPONSE:
     * {@code "Event Response"}. WAWebAddonEncryption.C maps
     * {@code MsgKind.EventResponseEncrypted} and
     * {@code MsgKind.EventResponseDecrypted} to this label.
     */
    @WhatsAppWebExport(moduleName = "WAUseCaseSecret", exports = "UseCaseSecretModificationType",
            adaptation = WhatsAppAdaptation.DIRECT)
    EVENT_RESPONSE("Event Response", true),

    /**
     * Event edit addon, emitted when an event organiser updates the details
     * of a previously scheduled event.
     *
     * @implNote WAUseCaseSecret.UseCaseSecretModificationType.EVENT_EDIT_ENCRYPTED:
     * {@code "Event Edit"}. WAWebAddonEncryption.C maps
     * {@code MsgKind.EventEditEncrypted} and
     * {@code MsgKind.EventEditDecrypted} to this label.
     */
    @WhatsAppWebExport(moduleName = "WAUseCaseSecret", exports = "UseCaseSecretModificationType",
            adaptation = WhatsAppAdaptation.DIRECT)
    EVENT_EDIT("Event Edit", false),

    /**
     * Poll edit addon, emitted when a poll creator updates the poll
     * question, options, or end-time.
     *
     * @implNote WAUseCaseSecret.UseCaseSecretModificationType.POLL_EDIT_ENCRYPTED:
     * {@code "Poll Edit"}. WAWebAddonEncryption.C maps
     * {@code MsgKind.PollEditEncrypted} and
     * {@code MsgKind.PollEditDecrypted} to this label.
     */
    @WhatsAppWebExport(moduleName = "WAUseCaseSecret", exports = "UseCaseSecretModificationType",
            adaptation = WhatsAppAdaptation.DIRECT)
    POLL_EDIT("Poll Edit", false),

    /**
     * Poll add-option addon, emitted when a poll participant adds a new
     * option to a poll that allows open-ended contributions.
     *
     * @implNote WAUseCaseSecret.UseCaseSecretModificationType.POLL_ADD_OPTION:
     * {@code "Poll Add Option"}. WAWebAddonEncryption.C maps
     * {@code MsgKind.PollAddOptionEncrypted} and
     * {@code MsgKind.PollAddOptionDecrypted} to this label.
     */
    @WhatsAppWebExport(moduleName = "WAUseCaseSecret", exports = "UseCaseSecretModificationType",
            adaptation = WhatsAppAdaptation.DIRECT)
    POLL_ADD_OPTION("Poll Add Option", false),

    /**
     * Message edit addon, emitted when a user edits a previously sent
     * message. The edited payload is dual-encrypted so the server cannot
     * read the edit even though it still needs to route the stanza.
     *
     * @implNote WAUseCaseSecret.UseCaseSecretModificationType.MESSAGE_EDIT:
     * {@code "Message Edit"}. WAWebAddonEncryption.C maps
     * {@code MsgKind.MessageEditEncrypted} and
     * {@code MsgKind.MessageEditDecrypted} to this label.
     */
    @WhatsAppWebExport(moduleName = "WAUseCaseSecret", exports = "UseCaseSecretModificationType",
            adaptation = WhatsAppAdaptation.DIRECT)
    MESSAGE_EDIT("Message Edit", false);

    /**
     * Label mixed into the HKDF info parameter for this use case.
     *
     * @implNote WAUseCaseSecret.UseCaseSecretModificationType: the string
     * literal associated with each enum variant.
     */
    @WhatsAppWebExport(moduleName = "WAUseCaseSecret", exports = "UseCaseSecretModificationType",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final String value;

    /**
     * Whether this use case authenticates the stanza id and addon sender JID
     * as AAD during AES-GCM encryption and decryption.
     *
     * @implNote WAWebAddonEncryption.d: returns a non-null AAD only for
     * {@code MsgKind.PollVote*} and {@code MsgKind.EventResponse*}; returns
     * {@code undefined} for all other MsgKind values, which in
     * WACryptoAesGcm means no AAD is applied.
     */
    @WhatsAppWebExport(moduleName = "WAWebAddonEncryption", exports = {"encryptAddOn", "decryptAddOn"},
            adaptation = WhatsAppAdaptation.DIRECT)
    private final boolean usesAad;

    /**
     * Constructs an addon type bound to the given HKDF label and AAD flag.
     *
     * @param value   the HKDF info string associated with this use case
     * @param usesAad whether this use case authenticates stanza id and
     *                addon sender as AES-GCM AAD
     * @implNote WAUseCaseSecret.UseCaseSecretModificationType: defines the
     * label for each variant. WAWebAddonEncryption.d: selects the AAD flag.
     */
    MessageAddonType(String value, boolean usesAad) {
        this.value = value;
        this.usesAad = usesAad;
    }

    /**
     * Returns the label mixed into the HKDF info parameter for this use
     * case.
     *
     * @return the HKDF info string
     * @implNote WAUseCaseSecret.createUseCaseSecret: the
     * {@code modificationType} argument passed to {@code Binary.build}.
     */
    @WhatsAppWebExport(moduleName = "WAUseCaseSecret", exports = "UseCaseSecretModificationType",
            adaptation = WhatsAppAdaptation.DIRECT)
    public String value() {
        return value;
    }

    /**
     * Returns whether this use case binds the stanza id and addon sender as
     * AES-GCM AAD.
     *
     * <p>Only poll votes and event responses set the flag to {@code true}:
     * these addons require per-sender binding because the server otherwise
     * sees enough structural metadata to attempt cross-user rebinding of the
     * ciphertext.
     *
     * @return {@code true} if AAD should be applied during encrypt and
     *         decrypt
     * @implNote WAWebAddonEncryption.d: returns a non-null AAD only for
     * poll-vote and event-response {@code MsgKind} variants.
     */
    @WhatsAppWebExport(moduleName = "WAWebAddonEncryption", exports = {"encryptAddOn", "decryptAddOn"},
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean usesAad() {
        return usesAad;
    }
}
