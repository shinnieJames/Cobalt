package com.github.auties00.cobalt.calls2.common;

import com.github.auties00.cobalt.model.jid.JidServer;

import java.util.Optional;

/**
 * Enumerates the voip JID domain-type codes the wa-voip engine assigns to a callable
 * JID.
 *
 * <p>The engine classifies every JID it handles into a small domain-type code stored
 * alongside the JID; that code, not the textual server string, is what the call layer
 * branches on when deciding whether a JID is a phone-number user, a group, a LID, a bot,
 * a hosted device, and so on. This enum reproduces the engine's twelve-slot domain-type
 * table and binds each code to its {@linkplain #domainString() canonical server string}
 * and to the equivalent Cobalt {@link JidServer.Type}.
 *
 * <p>The table has twelve consecutive codes {@code 0..11}. Several codes share the
 * {@code s.whatsapp.net} server string: code {@code 0} is the phone-number user domain,
 * and codes {@code 2}, {@code 4}, {@code 6}, {@code 7}, and {@code 10} are reserved
 * variant slots that the engine also resolves to {@code s.whatsapp.net}. The remaining
 * codes are distinct domains: {@code 1} group, {@code 3} call (extension and link),
 * {@code 5} LID user or device, {@code 8} newsletter, {@code 9} bot, and {@code 11}
 * hosted LID device. The engine's domain predicates read directly off these codes:
 * a JID is a LID when its code is {@code 5}, a bot when its code is {@code 9}, and a
 * hosted device when its code is {@code 11}; a device JID counts as LID-addressed when
 * its code is {@code 5}, {@code 9}, or {@code 11}, and resolving a hosted-LID device to
 * its owning user maps code {@code 11} back to code {@code 5}.
 *
 * @implNote This implementation ports the twelve-entry domain-string table at data
 * segment offset {@code 0x125960} of the wa-voip WASM module {@code ff-tScznZ8P} (the
 * array indexed by the JID domain-type field). The recovered table resolves codes
 * {@code 0,2,4,6,7,10} to {@code s.whatsapp.net}, {@code 1} to {@code g.us}, {@code 3}
 * to {@code call}, {@code 5} to {@code lid}, {@code 8} to {@code newsletter}, {@code 9}
 * to {@code bot}, and {@code 11} to {@code hosted.lid}; the LID/bot/hosted predicates
 * ({@code is_lid==5}, {@code is_bot==9}, {@code is_hosted==0xb}) and the device-LID and
 * hosted-to-user mappings are taken from the JID classification helpers in the same
 * module.
 */
public enum CallJidDomainType {
    /**
     * The phone-number user domain ({@code s.whatsapp.net}).
     */
    USER(0, "s.whatsapp.net", JidServer.Type.USER),

    /**
     * The group and community domain ({@code g.us}).
     */
    GROUP(1, "g.us", JidServer.Type.GROUP_OR_COMMUNITY),

    /**
     * The first reserved phone-number user variant slot, resolved to
     * {@code s.whatsapp.net}.
     */
    USER_VARIANT_2(2, "s.whatsapp.net", JidServer.Type.USER),

    /**
     * The call domain ({@code call}), used for call extensions and call links.
     */
    CALL(3, "call", JidServer.Type.CALL),

    /**
     * The second reserved phone-number user variant slot, resolved to
     * {@code s.whatsapp.net}.
     */
    USER_VARIANT_4(4, "s.whatsapp.net", JidServer.Type.USER),

    /**
     * The Linked Identity domain ({@code lid}), used for LID users and devices.
     */
    LID(5, "lid", JidServer.Type.LID),

    /**
     * The third reserved phone-number user variant slot, resolved to
     * {@code s.whatsapp.net}.
     */
    USER_VARIANT_6(6, "s.whatsapp.net", JidServer.Type.USER),

    /**
     * The fourth reserved phone-number user variant slot, resolved to
     * {@code s.whatsapp.net}.
     */
    USER_VARIANT_7(7, "s.whatsapp.net", JidServer.Type.USER),

    /**
     * The newsletter (Channel) domain ({@code newsletter}).
     */
    NEWSLETTER(8, "newsletter", JidServer.Type.NEWSLETTER),

    /**
     * The bot domain ({@code bot}), used for AI assistant and automated agent JIDs.
     */
    BOT(9, "bot", JidServer.Type.BOT),

    /**
     * The fifth reserved phone-number user variant slot, resolved to
     * {@code s.whatsapp.net}.
     */
    USER_VARIANT_10(10, "s.whatsapp.net", JidServer.Type.USER),

    /**
     * The business-hosted LID device domain ({@code hosted.lid}).
     */
    HOSTED_LID(11, "hosted.lid", JidServer.Type.HOSTED_LID);

    /**
     * Caches the constant array so the {@link #ofCode(int)} decode scan does not pay the
     * defensive-clone cost of {@link #values()} on every domain-code lookup.
     */
    private static final CallJidDomainType[] VALUES = values();

    /**
     * The integer domain-type code the wa-voip engine stores for this domain.
     */
    private final int code;

    /**
     * The canonical server string the engine maps this domain code to.
     */
    private final String domainString;

    /**
     * The equivalent Cobalt {@link JidServer.Type} for this domain.
     */
    private final JidServer.Type serverType;

    /**
     * Constructs a domain-type constant bound to its engine code, server string, and
     * Cobalt server type.
     *
     * @param code         the integer domain-type code the engine stores
     * @param domainString the canonical server string for this domain
     * @param serverType   the equivalent Cobalt {@link JidServer.Type}
     */
    CallJidDomainType(int code, String domainString, JidServer.Type serverType) {
        this.code = code;
        this.domainString = domainString;
        this.serverType = serverType;
    }

    /**
     * Returns the integer domain-type code the wa-voip engine stores for this domain.
     *
     * @return the engine domain-type code
     */
    public int code() {
        return code;
    }

    /**
     * Returns the canonical server string the engine maps this domain code to.
     *
     * <p>Multiple codes share the {@code s.whatsapp.net} string, so this value does not
     * uniquely identify a constant; use {@link #code()} for that.
     *
     * @return the canonical server string, such as {@code "s.whatsapp.net"} or
     *         {@code "g.us"}
     */
    public String domainString() {
        return domainString;
    }

    /**
     * Returns the equivalent Cobalt {@link JidServer.Type} for this domain.
     *
     * @return the matching Cobalt server type
     */
    public JidServer.Type serverType() {
        return serverType;
    }

    /**
     * Returns whether this domain is the Linked Identity user or device domain.
     *
     * @return {@code true} if this is {@link #LID}, {@code false} otherwise
     */
    public boolean isLid() {
        return this == LID;
    }

    /**
     * Returns whether this domain is the bot domain.
     *
     * @return {@code true} if this is {@link #BOT}, {@code false} otherwise
     */
    public boolean isBot() {
        return this == BOT;
    }

    /**
     * Returns whether this domain is a business-hosted device domain.
     *
     * @return {@code true} if this is {@link #HOSTED_LID}, {@code false} otherwise
     */
    public boolean isHosted() {
        return this == HOSTED_LID;
    }

    /**
     * Returns whether a device JID in this domain is Linked-Identity-addressed.
     *
     * <p>The engine treats {@link #LID}, {@link #BOT}, and {@link #HOSTED_LID} devices as
     * LID-addressed.
     *
     * @return {@code true} if this is {@link #LID}, {@link #BOT}, or {@link #HOSTED_LID},
     *         {@code false} otherwise
     */
    public boolean isDeviceLid() {
        return this == LID || this == BOT || this == HOSTED_LID;
    }

    /**
     * Returns the domain of the user that owns a device in this domain.
     *
     * <p>This mirrors the engine's device-to-user resolution: a {@link #HOSTED_LID}
     * device maps to a {@link #LID} user, and every other domain maps to itself.
     *
     * @return the owning user's domain
     */
    public CallJidDomainType userFromDevice() {
        return this == HOSTED_LID ? LID : this;
    }

    /**
     * Returns the domain whose {@linkplain #code() code} equals the given value.
     *
     * @param code the engine domain-type code to resolve
     * @return the matching domain, or {@link Optional#empty()} if no domain matches
     */
    public static Optional<CallJidDomainType> ofCode(int code) {
        for (var domain : VALUES) {
            if (domain.code == code) {
                return Optional.of(domain);
            }
        }
        return Optional.empty();
    }
}
