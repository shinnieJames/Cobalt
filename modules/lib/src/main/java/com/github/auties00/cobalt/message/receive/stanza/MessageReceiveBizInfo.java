package com.github.auties00.cobalt.message.receive.stanza;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.util.Optional;

/**
 * The business-side metadata extracted from an incoming {@code <message>} stanza
 * by {@link MessageReceiveStanzaParser}.
 *
 * @apiNote
 * Surfaces every piece of state needed by the downstream receive pipeline to
 * render a business message correctly: the {@code verified_name} certificate
 * and serial used by the verified-business badge, the {@code verified_level}
 * tier shown on the chat header, the optional native-flow name and campaign
 * id that drive WhatsApp Business interactive surfaces (Flows, CTWA ads), and
 * the privacy-mode tuple ({@link #actualActors()},
 * {@link #hostStorage()}, {@link #privacyModeTs()}) that gates BSP hosted-vs
 * on-device behaviour. The three envelope booleans
 * ({@link #verifiedButtonsEnvelope()}, {@link #verifiedListEnvelope()},
 * {@link #verifiedHsmEnvelope()}) tell the protobuf parser which wrapper to
 * peel off the inner ciphertext after decryption.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleMsgParser")
public final class MessageReceiveBizInfo {
    /**
     * The serialized {@code VerifiedNameCertificate} protobuf bytes carried by
     * the {@code <verified_name>} child of the stanza.
     */
    private final byte[] verifiedNameCert;

    /**
     * The integer carried by the stanza's {@code verified_name} attribute, or
     * {@code -1} when the attribute is absent.
     *
     * @implNote
     * This implementation uses the WA Web sentinel {@code -1} rather than
     * wrapping the value in {@link Optional} so the field can stay primitive.
     */
    private final int verifiedNameSerial;

    /**
     * The raw {@code verified_level} attribute identifying the business
     * verification tier (HIGH, LOW, UNKNOWN in WA Web's enum).
     */
    private final String verifiedLevel;

    /**
     * The native-flow name extracted from either
     * {@code <biz><interactive><native_flow name="..."/></interactive></biz>}
     * or the {@code native_flow_name} attribute on the {@code <biz>} node.
     */
    private final String nativeFlowName;

    /**
     * The {@code campaign_id} attribute identifying the WhatsApp Business
     * campaign that produced this message.
     */
    private final String campaignId;

    /**
     * The {@code actual_actors} attribute parsed from the {@code <biz>} node,
     * one component of the privacy-mode triple.
     */
    private final Integer actualActors;

    /**
     * The {@code host_storage} attribute parsed from the {@code <biz>} node,
     * one component of the privacy-mode triple.
     */
    private final Integer hostStorage;

    /**
     * The {@code privacy_mode_ts} attribute parsed from the {@code <biz>}
     * node, the seconds-precision timestamp at which the current privacy mode
     * took effect.
     */
    private final Integer privacyModeTs;

    /**
     * {@code true} when the stanza's {@code <biz>} child carries a
     * {@code <buttons>} envelope.
     */
    private final boolean verifiedButtonsEnvelope;

    /**
     * {@code true} when the stanza's {@code <biz>} child carries a
     * {@code <list>} envelope.
     */
    private final boolean verifiedListEnvelope;

    /**
     * {@code true} when the stanza carries an {@code <hsm>} child alongside
     * a {@code <biz>} child, indicating a verified highly-structured-message
     * (template) envelope.
     */
    private final boolean verifiedHsmEnvelope;

    /**
     * Constructs a populated record from the values extracted by
     * {@link MessageReceiveStanzaParser}.
     *
     * @apiNote
     * Not intended for direct use outside the parser; callers consume
     * existing instances via {@link MessageReceiveStanza#bizInfo()}.
     *
     * @param verifiedNameCert        the certificate bytes, or {@code null}
     * @param verifiedNameSerial      the verified-name serial, or {@code -1}
     * @param verifiedLevel           the verification tier, or {@code null}
     * @param nativeFlowName          the resolved native-flow name, or {@code null}
     * @param campaignId              the business campaign id, or {@code null}
     * @param actualActors            the privacy-mode actors count, or {@code null}
     * @param hostStorage             the privacy-mode host storage, or {@code null}
     * @param privacyModeTs           the privacy-mode timestamp, or {@code null}
     * @param verifiedButtonsEnvelope whether a verified buttons envelope is present
     * @param verifiedListEnvelope    whether a verified list envelope is present
     * @param verifiedHsmEnvelope     whether a verified hsm envelope is present
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgParser", exports = "incomingMsgParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    public MessageReceiveBizInfo(
            byte[] verifiedNameCert,
            int verifiedNameSerial,
            String verifiedLevel,
            String nativeFlowName,
            String campaignId,
            Integer actualActors,
            Integer hostStorage,
            Integer privacyModeTs,
            boolean verifiedButtonsEnvelope,
            boolean verifiedListEnvelope,
            boolean verifiedHsmEnvelope
    ) {
        this.verifiedNameCert = verifiedNameCert;
        this.verifiedNameSerial = verifiedNameSerial;
        this.verifiedLevel = verifiedLevel;
        this.nativeFlowName = nativeFlowName;
        this.campaignId = campaignId;
        this.actualActors = actualActors;
        this.hostStorage = hostStorage;
        this.privacyModeTs = privacyModeTs;
        this.verifiedButtonsEnvelope = verifiedButtonsEnvelope;
        this.verifiedListEnvelope = verifiedListEnvelope;
        this.verifiedHsmEnvelope = verifiedHsmEnvelope;
    }

    /**
     * Returns the raw {@code VerifiedNameCertificate} bytes carried by the
     * stanza's {@code <verified_name>} child.
     *
     * @apiNote
     * Used by the chat-header rendering path to display the verified-business
     * badge and the BSP-signed display name; verify the embedded signature
     * before trusting the name.
     *
     * @return an {@link Optional} wrapping the certificate bytes
     */
    public Optional<byte[]> verifiedNameCert() {
        return Optional.ofNullable(verifiedNameCert);
    }

    /**
     * Returns the value of the stanza's {@code verified_name} attribute.
     *
     * @apiNote
     * Pairs with {@link #verifiedNameCert()} to identify a specific issued
     * certificate; the {@code -1} sentinel marks absence so the field can be
     * primitive.
     *
     * @return the serial number, or {@code -1} when the attribute was absent
     */
    public int verifiedNameSerial() {
        return verifiedNameSerial;
    }

    /**
     * Returns the {@code verified_level} attribute, when present.
     *
     * @apiNote
     * Drives the badge tier shown on chat headers and business cards; values
     * mirror WA Web's {@code MSG_VERIFIED_LEVEL} enum.
     *
     * @return an {@link Optional} wrapping the level identifier
     */
    public Optional<String> verifiedLevel() {
        return Optional.ofNullable(verifiedLevel);
    }

    /**
     * Returns the resolved native-flow name for WhatsApp Business
     * interactive content, when present.
     *
     * @apiNote
     * Tells the interactive-message renderer which Flow surface to launch
     * (for example {@code "shops"}, {@code "appointment_booking"}).
     *
     * @return an {@link Optional} wrapping the native flow name
     */
    public Optional<String> nativeFlowName() {
        return Optional.ofNullable(nativeFlowName);
    }

    /**
     * Returns the WhatsApp Business {@code campaign_id} attribute, when
     * present.
     *
     * @apiNote
     * Threaded into outgoing analytics so replies to a CTWA-driven
     * conversation can be attributed back to the originating ad campaign.
     *
     * @return an {@link Optional} wrapping the campaign identifier
     */
    public Optional<String> campaignId() {
        return Optional.ofNullable(campaignId);
    }

    /**
     * Returns the {@code actual_actors} component of the privacy-mode triple,
     * when present.
     *
     * @apiNote
     * Use {@link #hasPrivacyMode()} to decide whether the triple is complete
     * before consuming any individual component.
     *
     * @return an {@link Optional} wrapping the actor count
     */
    public Optional<Integer> actualActors() {
        return Optional.ofNullable(actualActors);
    }

    /**
     * Returns the {@code host_storage} component of the privacy-mode triple,
     * when present.
     *
     * @apiNote
     * Identifies whether the BSP is storing the conversation host-side or on
     * device; mirrors WA Web's {@code HostStorageEnumType}.
     *
     * @return an {@link Optional} wrapping the host storage value
     */
    public Optional<Integer> hostStorage() {
        return Optional.ofNullable(hostStorage);
    }

    /**
     * Returns the {@code privacy_mode_ts} component of the privacy-mode
     * triple, when present.
     *
     * @apiNote
     * The seconds-precision timestamp at which the current privacy-mode
     * configuration took effect; used by the privacy-mode banner to date the
     * change.
     *
     * @return an {@link Optional} wrapping the privacy-mode timestamp
     */
    public Optional<Integer> privacyModeTs() {
        return Optional.ofNullable(privacyModeTs);
    }

    /**
     * Returns whether the {@code <biz>} child contained a {@code <buttons>}
     * envelope.
     *
     * @apiNote
     * Signals to the protobuf parser that the inner ciphertext is a verified
     * buttons payload and must be unwrapped before display.
     *
     * @return {@code true} if a verified buttons envelope is present
     */
    public boolean verifiedButtonsEnvelope() {
        return verifiedButtonsEnvelope;
    }

    /**
     * Returns whether the {@code <biz>} child contained a {@code <list>}
     * envelope.
     *
     * @apiNote
     * Signals to the protobuf parser that the inner ciphertext is a verified
     * list payload.
     *
     * @return {@code true} if a verified list envelope is present
     */
    public boolean verifiedListEnvelope() {
        return verifiedListEnvelope;
    }

    /**
     * Returns whether an {@code <hsm>} child accompanied the {@code <biz>}
     * child.
     *
     * @apiNote
     * Signals that the message is a verified highly-structured-message
     * template; pair with {@link MessageReceiveStanza#hsmTag()} to identify
     * the template.
     *
     * @return {@code true} if a verified hsm envelope is present
     */
    public boolean verifiedHsmEnvelope() {
        return verifiedHsmEnvelope;
    }

    /**
     * Returns whether every component of the privacy-mode triple is present.
     *
     * @apiNote
     * Use this before reading {@link #actualActors()}, {@link #hostStorage()},
     * and {@link #privacyModeTs()} together; WA Web treats privacy mode as
     * defined only when all three attributes are present.
     *
     * @return {@code true} if the triple is fully populated
     */
    public boolean hasPrivacyMode() {
        return actualActors != null && hostStorage != null && privacyModeTs != null;
    }
}
