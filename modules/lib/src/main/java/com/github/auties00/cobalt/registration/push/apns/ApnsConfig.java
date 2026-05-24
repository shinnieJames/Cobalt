package com.github.auties00.cobalt.registration.push.apns;

import com.github.auties00.cobalt.registration.push.apns.courier.ApnsPayloadTag;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.List;

/**
 * The immutable APNS settings that pin an {@link ApnsClient} to a
 * fixed set of iOS bundle identifiers.
 *
 * @apiNote
 * Used to bind a session to the topics whose pushes the courier
 * connection will deliver: the courier filters by SHA-1 hashes of
 * these strings (sent in the {@link ApnsPayloadTag#FILTER}
 * subscription) so the topics must be known up front. The canonical
 * values for WhatsApp impersonation are {@link #WHATSAPP_PERSONAL}
 * and {@link #WHATSAPP_BUSINESS}; the {@link #of(String...)} factory
 * is provided for callers driving a non-WhatsApp APNS surface.
 *
 * @implNote
 * This implementation is stored inside {@link ApnsSession} so a
 * serialised session round-trips its topic list along with the
 * credentials.
 */
@ProtobufMessage(name = "ApnsConfig")
public final class ApnsConfig {
    /**
     * The configuration for WhatsApp's consumer iOS app
     * ({@code net.whatsapp.WhatsApp}).
     *
     * @apiNote
     * The first entry is the messaging topic surfaced by
     * {@link ApnsClient#getPushToken()}; the {@code .voip} entry is
     * also subscribed so the courier delivers the VoIP-flavoured
     * silent pushes WhatsApp's registration server uses during
     * phone-number verification.
     */
    public static final ApnsConfig WHATSAPP_PERSONAL = new ApnsConfig(List.of(
            "net.whatsapp.WhatsApp",
            "net.whatsapp.WhatsApp.voip"));

    /**
     * The configuration for WhatsApp Business' iOS app
     * ({@code net.whatsapp.WhatsAppSMB}).
     *
     * @apiNote
     * The Business-flavoured counterpart of {@link #WHATSAPP_PERSONAL};
     * same shape, pinned to the business bundle identifiers.
     */
    public static final ApnsConfig WHATSAPP_BUSINESS = new ApnsConfig(List.of(
            "net.whatsapp.WhatsAppSMB",
            "net.whatsapp.WhatsAppSMB.voip"));

    /**
     * The bundle identifiers this config subscribes to.
     *
     * @apiNote
     * The wire protocol filters pushes by the SHA-1 hash of these
     * strings, so they must match the iOS app's
     * {@code CFBundleIdentifier} exactly (case-sensitive). The first
     * entry is treated as the primary messaging topic by
     * {@link ApnsClient#getPushToken()}.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    List<String> topics;

    /**
     * Package-private constructor for the protobuf builder.
     *
     * @apiNote
     * Reachable by the generated {@code ApnsConfigBuilder} and by
     * the {@link #of(String...)} factory; defensively copies the
     * incoming list so the caller cannot mutate the topic set after
     * construction, and tolerates a {@code null} input by storing
     * the empty list.
     *
     * @param topics the bundle identifiers to subscribe to, or
     *               {@code null} for an empty subscription
     */
    ApnsConfig(List<String> topics) {
        this.topics = topics == null ? List.of() : List.copyOf(topics);
    }

    /**
     * Creates a configuration from a varargs topic list.
     *
     * @apiNote
     * Convenience for callers driving a non-WhatsApp APNS surface;
     * for the WhatsApp registration flow prefer the
     * {@link #WHATSAPP_PERSONAL} / {@link #WHATSAPP_BUSINESS}
     * presets.
     *
     * @param topics one or more bundle identifiers
     * @return a new immutable config
     */
    public static ApnsConfig of(String... topics) {
        return new ApnsConfig(List.of(topics));
    }

    /**
     * Returns the configured bundle identifiers.
     *
     * @apiNote
     * The returned list is immutable; callers needing a mutable view
     * must copy it.
     *
     * @return the configured bundle identifiers, never {@code null}
     */
    public List<String> topics() {
        return topics;
    }
}
