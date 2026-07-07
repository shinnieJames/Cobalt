package com.github.auties00.cobalt.calls2.common;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;

import java.util.Objects;

/**
 * Represents a callable JID together with its voip domain-type classification.
 *
 * <p>This is the foundational value object the wa-voip engine builds every callable
 * address on: a user, group, call-link, newsletter, bot, or hosted device address.
 * It pairs an existing {@link Jid} (which already carries the parsed user, server,
 * agent, and device components) with the {@link CallJidDomainType} discriminator the
 * native engine stores alongside every JID and uses to drive its
 * {@linkplain CallJidDomainType#isLid() lid}, {@linkplain CallJidDomainType#isBot()
 * bot}, and {@linkplain CallJidDomainType#isHosted() hosted} predicates. The native
 * struct additionally renders a {@code <identifier>@<domain>} full-jid string and an
 * isolated identifier (local part); both are recoverable from the wrapped {@code Jid}
 * via {@link Jid#toString()} and {@link Jid#user()}, so they are not duplicated here.
 *
 * <p>The wrapped {@code Jid} is the single source of truth for the addressing
 * components; {@link #domainType()} is the engine-side classification derived at
 * construction from the {@code Jid}'s {@link JidServer.Type} by matching it against the
 * {@linkplain CallJidDomainType#serverType() server type} of each
 * {@link CallJidDomainType}. Instances are immutable.
 *
 * @implNote This implementation composes the {@code wa_call_identifier_jid} value object
 *           (0x30 bytes) from {@code call_jid.cc}: use-case (+0x0), the rendered full-jid
 *           string (+0x8), the identifier local part (+0x18), and the domain-type
 *           discriminator (+0x28). The use-case offset is taken from
 *           {@code wa_call_identifier_jid_initialize} (fn10519) in module
 *           {@code ff-tScznZ8P}, which stores its use-case argument at memarg offset
 *           {@code 0} and the domain-type at offset {@code 0x28}; the clone fn13843
 *           corroborates by copying words {@code +0x0} and {@code +0x28}. The string
 *           components are delegated to the wrapped {@link Jid} instead of being stored
 *           separately.
 * @param jid        the wrapped addressing JID; never {@code null}
 * @param domainType the voip domain-type classification of {@code jid}; never
 *                   {@code null}
 * @param useCase    the structural addressing use-case the engine attaches to the JID;
 *                   never {@code null}
 * @see CallJidDomainType
 * @see CallJidUseCase
 * @see Jid
 * @see CallDeviceJid
 * @see CallParticipantJid
 */
public record CallJid(Jid jid, CallJidDomainType domainType, CallJidUseCase useCase) {
    /**
     * The default addressing use-case applied when none is specified.
     *
     * <p>This is {@link CallJidUseCase#USER}, the use-case the native engine attaches to a
     * bare user JID and the value the default-constructed {@link CallJid} carries.
     */
    public static final CallJidUseCase DEFAULT_USE_CASE = CallJidUseCase.USER;

    /**
     * Validates the record components during construction.
     *
     * @throws NullPointerException if {@code jid}, {@code domainType}, or {@code useCase}
     *                              is {@code null}
     */
    public CallJid {
        Objects.requireNonNull(jid, "jid cannot be null");
        Objects.requireNonNull(domainType, "domainType cannot be null");
        Objects.requireNonNull(useCase, "useCase cannot be null");
    }

    /**
     * Returns a {@code CallJid} wrapping the given {@link Jid}, deriving both its
     * domain-type and its use-case from the JID.
     *
     * <p>The domain-type is resolved from the {@linkplain Jid#server() server}'s
     * {@link JidServer#type() type} via {@link #domainOf(JidServer.Type)}. The
     * {@link CallJidUseCase use-case} is then classified from the domain-type and the
     * presence of a {@linkplain Jid#hasDevice() device} component via
     * {@link #useCaseOf(Jid, CallJidDomainType)}: a {@link CallJidDomainType#GROUP group}
     * JID yields {@link CallJidUseCase#GROUP}, a {@link CallJidDomainType#NEWSLETTER
     * newsletter} JID yields {@link CallJidUseCase#NEWSLETTER}, a device-callable JID
     * carrying a device yields {@link CallJidUseCase#DEVICE}, and every other user-family
     * JID yields {@link CallJidUseCase#USER}. This is the canonical factory for adapting a
     * model {@code Jid} into the call layer.
     *
     * @param jid the addressing JID to wrap; never {@code null}
     * @return a {@code CallJid} for the given JID with its classified use-case
     * @throws NullPointerException     if {@code jid} is {@code null}
     * @throws IllegalArgumentException if the JID's server domain does not map to a
     *                                  callable {@link CallJidDomainType}
     */
    public static CallJid of(Jid jid) {
        Objects.requireNonNull(jid, "jid cannot be null");
        var domainType = domainOf(jid.server().type());
        return new CallJid(jid, domainType, useCaseOf(jid, domainType));
    }

    /**
     * Returns a {@code CallJid} wrapping the given {@link Jid} with the given use-case,
     * deriving its domain-type from the JID's server domain.
     *
     * @param jid     the addressing JID to wrap; never {@code null}
     * @param useCase the structural addressing use-case; never {@code null}
     * @return a {@code CallJid} for the given JID and use-case
     * @throws NullPointerException     if {@code jid} or {@code useCase} is {@code null}
     * @throws IllegalArgumentException if the JID's server domain does not map to a
     *                                  callable {@link CallJidDomainType}
     */
    public static CallJid of(Jid jid, CallJidUseCase useCase) {
        Objects.requireNonNull(jid, "jid cannot be null");
        var domainType = domainOf(jid.server().type());
        return new CallJid(jid, domainType, useCase);
    }

    /**
     * Resolves the voip {@link CallJidDomainType} for the given Cobalt server type.
     *
     * <p>The match is the first {@link CallJidDomainType} whose
     * {@linkplain CallJidDomainType#serverType() server type} equals {@code type}. Because
     * the domain-type constants are declared in ascending engine-code order and the
     * reserved {@code s.whatsapp.net} variant slots follow the canonical
     * {@link CallJidDomainType#USER} constant, a {@link JidServer.Type#USER} resolves to
     * {@link CallJidDomainType#USER} rather than to a variant slot.
     *
     * @param type the Cobalt server type to map; never {@code null}
     * @return the matching voip domain-type
     * @throws NullPointerException     if {@code type} is {@code null}
     * @throws IllegalArgumentException if {@code type} does not correspond to any callable
     *                                  voip domain-type
     */
    static CallJidDomainType domainOf(JidServer.Type type) {
        Objects.requireNonNull(type, "type cannot be null");
        for (var domainType : CallJidDomainType.values()) {
            if (domainType.serverType() == type) {
                return domainType;
            }
        }
        throw new IllegalArgumentException("server type " + type + " is not a callable voip domain");
    }

    /**
     * Classifies the {@link CallJidUseCase} the native engine attaches to a parsed JID.
     *
     * <p>This reproduces the use-case branch the engine takes in
     * {@code wa_call_jid_from_string}: a {@link CallJidDomainType#GROUP group} JID is a
     * {@link CallJidUseCase#GROUP group} address, a {@link CallJidDomainType#NEWSLETTER
     * newsletter} JID is a {@link CallJidUseCase#NEWSLETTER newsletter} address, a
     * device-callable JID that carries a {@linkplain Jid#hasDevice() device} identifier is
     * a {@link CallJidUseCase#DEVICE device} address, and every other user-family JID is a
     * bare {@link CallJidUseCase#USER user} address. The {@link CallJidUseCase#GROUP_CALL}
     * and {@link CallJidUseCase#EXTENSION} use-cases are never produced here: the engine
     * assigns those only through its explicit call-session and call-link constructors, not
     * by parsing an address string.
     *
     * @implNote This implementation gates the {@link CallJidUseCase#DEVICE} result on the
     *           {@code 0xa28} device-domain mask (domain-types {@code 3} call, {@code 5}
     *           lid, {@code 9} bot, {@code 0xb} hosted.lid) that
     *           {@code wa_call_device_jid_create} enforces, so a {@code s.whatsapp.net}
     *           JID stays {@link CallJidUseCase#USER} even when it carries a device. A
     *           primary device (device id {@code 0}) is indistinguishable from a bare
     *           account in the Cobalt {@link Jid} model, so it classifies as
     *           {@link CallJidUseCase#USER}.
     * @param jid        the addressing JID being classified; never {@code null}
     * @param domainType the already-resolved domain-type of {@code jid}; never
     *                   {@code null}
     * @return the classified use-case
     */
    private static CallJidUseCase useCaseOf(Jid jid, CallJidDomainType domainType) {
        return switch (domainType) {
            case GROUP -> CallJidUseCase.GROUP;
            case NEWSLETTER -> CallJidUseCase.NEWSLETTER;
            default -> jid.hasDevice() && isDeviceCallable(domainType)
                    ? CallJidUseCase.DEVICE
                    : CallJidUseCase.USER;
        };
    }

    /**
     * Returns whether a JID in the given domain may be addressed as a specific device.
     *
     * @implNote This implementation enumerates the {@code 0xa28} device-domain mask of
     *           {@code wa_call_device_jid_create} (domain-types {@code 3} call, {@code 5}
     *           lid, {@code 9} bot, {@code 0xb} hosted.lid).
     * @param domainType the domain-type to test; never {@code null}
     * @return {@code true} if the domain is device-callable
     */
    private static boolean isDeviceCallable(CallJidDomainType domainType) {
        return domainType == CallJidDomainType.CALL
                || domainType == CallJidDomainType.LID
                || domainType == CallJidDomainType.BOT
                || domainType == CallJidDomainType.HOSTED_LID;
    }

    /**
     * Returns the identifier (local part) of this JID.
     *
     * <p>This is the {@linkplain Jid#user() user component} of the wrapped JID: the
     * phone number for a {@linkplain CallJidDomainType#USER PN user}, the opaque
     * numeric identifier for a {@linkplain CallJidDomainType#LID LID}, the group id for
     * a {@linkplain CallJidDomainType#GROUP group}, the call-link id for a
     * {@linkplain CallJidDomainType#CALL call extension}, and so on. It is {@code null}
     * for a server-only JID.
     *
     * @return the identifier string, or {@code null} if the wrapped JID has no user
     *         component
     */
    public String identifier() {
        return jid.user();
    }

    /**
     * Returns the rendered {@code <identifier>@<domain>} string form of this JID.
     *
     * <p>The rendering is delegated to {@link Jid#toString()}, which omits default
     * agent, device, and user components and emits a bare domain for server-only JIDs.
     *
     * @return the full JID string
     */
    public String fullJid() {
        return jid.toString();
    }

    /**
     * Returns whether this JID belongs to the Linked Identity family.
     *
     * <p>This delegates to {@link CallJidDomainType#isLid()}, which the native engine
     * computes as {@code domain_type == 5}.
     *
     * @return {@code true} if the domain-type is {@link CallJidDomainType#LID}
     */
    public boolean isLid() {
        return domainType.isLid();
    }

    /**
     * Returns whether this JID identifies a bot.
     *
     * <p>This delegates to {@link CallJidDomainType#isBot()}, which the native engine
     * computes as {@code domain_type == 9}.
     *
     * @return {@code true} if the domain-type is {@link CallJidDomainType#BOT}
     */
    public boolean isBot() {
        return domainType.isBot();
    }

    /**
     * Returns whether this JID identifies a business-hosted device.
     *
     * <p>This delegates to {@link CallJidDomainType#isHosted()}, which the native engine
     * computes as {@code domain_type == 0xb}.
     *
     * @return {@code true} if the domain-type is {@link CallJidDomainType#HOSTED_LID}
     */
    public boolean isHosted() {
        return domainType.isHosted();
    }
}
