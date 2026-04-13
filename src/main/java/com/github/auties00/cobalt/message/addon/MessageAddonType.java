package com.github.auties00.cobalt.message.addon;

/**
 * Use-case types for add-on messages.
 *
 * <p>These are used in the HKDF info parameter to derive different
 * keys for different types of add-ons.  The {@link #value()} string
 * is concatenated into the HKDF info alongside the stanza ID,
 * original sender, and addon sender.
 *
 * @implNote WAUseCaseSecret.UseCaseSecretModificationType: the exact
 *           string values used for key derivation. WAWebAddonEncryption.g:
 *           maps MsgKind to spec and usecase pairs.
 */
public enum MessageAddonType {
    /**
     * Poll vote add-on.
     * Uses AAD: {@code stanzaId + "\0" + voterJid}.
     *
     * @implNote WAUseCaseSecret.UseCaseSecretModificationType.POLL_VOTE: {@code "Poll Vote"}.
     *           WAWebAddonEncryption.g: maps PollVoteEncrypted/PollVoteDecrypted to this usecase.
     */
    POLL_VOTE("Poll Vote", true),

    /**
     * Encrypted reaction add-on (for CAG groups).
     *
     * @implNote WAUseCaseSecret.UseCaseSecretModificationType.ENC_REACTION: {@code "Enc Reaction"}.
     *           WAWebAddonEncryption.g: maps ReactionEncrypted/ReactionDecrypted to this usecase.
     */
    ENC_REACTION("Enc Reaction", false),

    /**
     * Encrypted comment add-on.
     *
     * @implNote WAUseCaseSecret.UseCaseSecretModificationType.ENC_COMMENT: {@code "Enc Comment"}.
     *           WAWebAddonEncryption.g: maps CommentEncrypted/CommentDecrypted to this usecase.
     */
    ENC_COMMENT("Enc Comment", false),

    /**
     * Encrypted event response add-on (RSVP).
     * Uses AAD: {@code stanzaId + "\0" + responderJid}.
     *
     * @implNote WAUseCaseSecret.UseCaseSecretModificationType.EVENT_RESPONSE: {@code "Event Response"}.
     *           WAWebAddonEncryption.g: maps EventResponseEncrypted/EventResponseDecrypted to this usecase.
     */
    EVENT_RESPONSE("Event Response", true),

    /**
     * Encrypted event edit add-on.
     *
     * @implNote WAUseCaseSecret.UseCaseSecretModificationType.EVENT_EDIT_ENCRYPTED: {@code "Event Edit"}.
     *           WAWebAddonEncryption.g: maps EventEditEncrypted/EventEditDecrypted to this usecase.
     */
    EVENT_EDIT("Event Edit", false),

    /**
     * Encrypted poll edit add-on.
     *
     * @implNote WAUseCaseSecret.UseCaseSecretModificationType.POLL_EDIT_ENCRYPTED: {@code "Poll Edit"}.
     *           WAWebAddonEncryption.g: maps PollEditEncrypted/PollEditDecrypted to this usecase.
     */
    POLL_EDIT("Poll Edit", false),

    /**
     * Encrypted poll add option add-on.
     *
     * @implNote WAUseCaseSecret.UseCaseSecretModificationType.POLL_ADD_OPTION: {@code "Poll Add Option"}.
     *           WAWebAddonEncryption.g: maps PollAddOptionEncrypted/PollAddOptionDecrypted to this usecase.
     */
    POLL_ADD_OPTION("Poll Add Option", false),

    /**
     * Encrypted message edit add-on.
     *
     * @implNote WAUseCaseSecret.UseCaseSecretModificationType.MESSAGE_EDIT: {@code "Message Edit"}.
     *           WAWebAddonEncryption.g: maps MessageEditEncrypted/MessageEditDecrypted to this usecase.
     */
    MESSAGE_EDIT("Message Edit", false);

    /**
     * The string value used in the HKDF info parameter for key derivation.
     *
     * @implNote WAUseCaseSecret.UseCaseSecretModificationType: enum string values.
     */
    private final String value;

    /**
     * Whether this use-case type requires AAD in AES-GCM encryption.
     *
     * @implNote WAWebAddonEncryption.d: returns a non-null AAD string only for
     *           PollVote and EventResponse types.
     */
    private final boolean usesAad;

    /**
     * Constructs a new addon type with the specified HKDF info value and AAD requirement.
     *
     * @param value   the string value for HKDF info derivation
     * @param usesAad whether this type requires AAD in AES-GCM
     *
     * @implNote WAUseCaseSecret.UseCaseSecretModificationType: defines the value strings.
     *           WAWebAddonEncryption.d: defines which types use AAD.
     */
    MessageAddonType(String value, boolean usesAad) {
        this.value = value;
        this.usesAad = usesAad;
    }

    /**
     * Returns the string value used in the HKDF info parameter for key derivation.
     *
     * @return the use-case type string
     *
     * @implNote WAUseCaseSecret.UseCaseSecretModificationType: the {@code modificationType}
     *           parameter passed to {@code Binary.build}.
     */
    public String value() {
        return value;
    }

    /**
     * Returns whether this use-case type requires AAD in AES-GCM encryption.
     *
     * <p>Poll votes and event responses use AAD to cryptographically bind
     * the ciphertext to a specific stanza and sender, preventing
     * substitution attacks.
     *
     * @return {@code true} if AAD should be used
     *
     * @implNote WAWebAddonEncryption.d: returns {@code stanzaId + "\0" + addOnSenderJid}
     *           only for PollVoteEncrypted, PollVoteDecrypted, EventResponseEncrypted,
     *           and EventResponseDecrypted types; returns {@code undefined} for all others.
     */
    public boolean usesAad() {
        return usesAad;
    }
}
