package com.github.auties00.cobalt.message.addon;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Use-case label that WhatsApp mixes into the HKDF info parameter when it
 * derives a message-secret-bound encryption or MAC key.
 *
 * @apiNote Mirrors WA Web's {@code UseCaseSecretModificationType} enum in
 * {@code WAUseCaseSecret}. Each variant binds an HKDF derivation to a
 * distinct label so keys derived for different use cases never collide even
 * when the parent message, sender, and stanza id are otherwise identical.
 * Eight variants drive dual-encrypted addons (poll votes, encrypted
 * reactions, encrypted comments, event responses, event edits, poll edits,
 * poll add-options, message edits); the ninth, {@link #REPORT_TOKEN}, drives
 * the HMAC franking tag the server verifies on abuse reports.
 *
 * @implNote This implementation also records, on each variant, whether the
 * use case authenticates additional data through AES-GCM AAD. Only
 * {@link #POLL_VOTE} and {@link #EVENT_RESPONSE} set the flag.
 */
@WhatsAppWebModule(moduleName = "WAUseCaseSecret")
@WhatsAppWebModule(moduleName = "WAWebAddonEncryption")
public enum MessageAddonType {
    /**
     * Poll vote addon, dual-encrypted under a key derived with this label.
     *
     * @apiNote Drives {@link EncMessageFactory#encryptPollVote}. Sets the
     * AAD flag so the AES-GCM cipher binds {@code stanzaId + "\0" + voterJid}
     * into the tag, preventing the server from reattributing a vote to a
     * different user.
     */
    @WhatsAppWebExport(moduleName = "WAUseCaseSecret", exports = "UseCaseSecretModificationType",
            adaptation = WhatsAppAdaptation.DIRECT)
    POLL_VOTE("Poll Vote", true),

    /**
     * Encrypted reaction addon, used in community / announcement group
     * threads.
     *
     * @apiNote Drives {@link EncMessageFactory#encryptReaction}. The default
     * non-encrypted reaction wire format leaks the emoji content to the
     * server, which is unacceptable on CAG threads; this label selects the
     * dual-encrypted variant.
     */
    @WhatsAppWebExport(moduleName = "WAUseCaseSecret", exports = "UseCaseSecretModificationType",
            adaptation = WhatsAppAdaptation.DIRECT)
    ENC_REACTION("Enc Reaction", false),

    /**
     * Encrypted comment addon, used in community / announcement group
     * threads.
     *
     * @apiNote Drives {@link EncMessageFactory#encryptComment}. The inner
     * {@code MessageSpec} payload is dual-encrypted so the server can route
     * the comment without reading its body.
     */
    @WhatsAppWebExport(moduleName = "WAUseCaseSecret", exports = "UseCaseSecretModificationType",
            adaptation = WhatsAppAdaptation.DIRECT)
    ENC_COMMENT("Enc Comment", false),

    /**
     * Reporting-token label.
     *
     * @apiNote Unlike the other variants, this label does not drive AES-GCM
     * encryption of an addon payload. It is mixed into the HKDF info when
     * deriving the 32-byte HMAC key used to compute the franking tag that
     * the server verifies on abuse reports.
     */
    @WhatsAppWebExport(moduleName = "WAUseCaseSecret", exports = "UseCaseSecretModificationType",
            adaptation = WhatsAppAdaptation.DIRECT)
    REPORT_TOKEN("Report Token", false),

    /**
     * Event response addon (RSVP).
     *
     * @apiNote Sets the AAD flag so the AES-GCM cipher binds
     * {@code stanzaId + "\0" + responderJid} into the tag, preventing the
     * server from lifting an encrypted RSVP emitted by one user and
     * replaying it as if it came from a different user.
     */
    @WhatsAppWebExport(moduleName = "WAUseCaseSecret", exports = "UseCaseSecretModificationType",
            adaptation = WhatsAppAdaptation.DIRECT)
    EVENT_RESPONSE("Event Response", true),

    /**
     * Event edit addon, emitted when an event organiser updates the details
     * of a previously scheduled event.
     *
     * @apiNote The edited payload is dual-encrypted so the server can route
     * the edit without reading its body. Maps to WA Web's
     * {@code EVENT_EDIT_ENCRYPTED} enum entry.
     */
    @WhatsAppWebExport(moduleName = "WAUseCaseSecret", exports = "UseCaseSecretModificationType",
            adaptation = WhatsAppAdaptation.DIRECT)
    EVENT_EDIT("Event Edit", false),

    /**
     * Poll edit addon, emitted when a poll creator updates the poll
     * question, options, or end-time.
     *
     * @apiNote Maps to WA Web's {@code POLL_EDIT_ENCRYPTED} enum entry.
     */
    @WhatsAppWebExport(moduleName = "WAUseCaseSecret", exports = "UseCaseSecretModificationType",
            adaptation = WhatsAppAdaptation.DIRECT)
    POLL_EDIT("Poll Edit", false),

    /**
     * Poll add-option addon, emitted when a poll participant adds a new
     * option to a poll that allows open-ended contributions.
     */
    @WhatsAppWebExport(moduleName = "WAUseCaseSecret", exports = "UseCaseSecretModificationType",
            adaptation = WhatsAppAdaptation.DIRECT)
    POLL_ADD_OPTION("Poll Add Option", false),

    /**
     * Message edit addon, emitted when a user edits a previously sent
     * message.
     *
     * @apiNote The edited payload is dual-encrypted so the server can route
     * the stanza without reading the new content.
     */
    @WhatsAppWebExport(moduleName = "WAUseCaseSecret", exports = "UseCaseSecretModificationType",
            adaptation = WhatsAppAdaptation.DIRECT)
    MESSAGE_EDIT("Message Edit", false);

    /**
     * Label mixed into the HKDF info parameter for this use case.
     */
    @WhatsAppWebExport(moduleName = "WAUseCaseSecret", exports = "UseCaseSecretModificationType",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final String value;

    /**
     * Whether this use case binds the stanza id and addon sender JID as
     * AES-GCM AAD.
     */
    @WhatsAppWebExport(moduleName = "WAWebAddonEncryption", exports = {"encryptAddOn", "decryptAddOn"},
            adaptation = WhatsAppAdaptation.DIRECT)
    private final boolean usesAad;

    /**
     * Constructs an addon type bound to the given HKDF label and AAD flag.
     *
     * @param value   the HKDF info string associated with this use case
     * @param usesAad whether this use case binds stanza id and addon sender
     *                as AES-GCM AAD
     */
    MessageAddonType(String value, boolean usesAad) {
        this.value = value;
        this.usesAad = usesAad;
    }

    /**
     * Returns the HKDF info label associated with this use case.
     *
     * @apiNote Consumed inside
     * {@link MessageAddonEncryption#encrypt} when assembling the HKDF info
     * parameter; never exposed to callers of the high-level factory in
     * {@link EncMessageFactory}.
     *
     * @return the HKDF info string
     */
    @WhatsAppWebExport(moduleName = "WAUseCaseSecret", exports = "UseCaseSecretModificationType",
            adaptation = WhatsAppAdaptation.DIRECT)
    public String value() {
        return value;
    }

    /**
     * Returns whether this use case binds the stanza id and addon sender JID
     * as AES-GCM AAD.
     *
     * @apiNote Only {@link #POLL_VOTE} and {@link #EVENT_RESPONSE} set the
     * flag; those addons need per-sender binding because the server
     * otherwise sees enough structural metadata to attempt cross-user
     * rebinding of the ciphertext.
     *
     * @return {@code true} when AAD should be applied during encrypt and
     *         decrypt
     */
    @WhatsAppWebExport(moduleName = "WAWebAddonEncryption", exports = {"encryptAddOn", "decryptAddOn"},
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean usesAad() {
        return usesAad;
    }
}
