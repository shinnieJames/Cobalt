package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.calls2.core.control.PrivacyToken;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Represents an {@code <offer>} signal: the initiating announcement of a one-to-one or group call.
 *
 * <p>The offer is the only signaling action with a rich nested tree; every other action is mostly
 * flat attributes. It announces a call to the peer's devices and carries everything the callee needs
 * to ring and to answer: the caller's identity hints, the negotiable audio and video codec sets, the
 * device capability advertisements, the negotiated media descriptor, the encryption options, and the
 * call-key distribution. For a one-to-one call the call key is fanned out per device inside a
 * {@code <destination>} block (or, on the server-delivered copy, as a bare {@code <enc>} directly
 * under the offer); a group call ships no call key in the offer (it arrives post-join through the
 * relay block).
 *
 * <p>Several offer children belong to subsystems outside the signaling-payload layer and are carried
 * as raw {@link Stanza} subtrees so this record stays faithful without duplicating their grammar: the
 * group roster ({@code <group_info>}, owned by the participant layer), the ADV device identity
 * ({@code <device-identity>}, owned by the identity store), the engine parameter bundles
 * ({@code <voip_settings>}, owned by the config layer), the one-to-one trusted-contact token
 * ({@code <privacy>}), the relay transport block ({@code <relay>}, owned by the relay model), the bot
 * descriptor ({@code <bot>}), and the caller A/B-test metadata.
 *
 * <p>On the wire the element is {@snippet lang="xml" :
 * <offer call-id="..." call-creator="..." caller_pn="..." group-jid="..." joinable="1">
 *   <group_info>...</group_info>
 *   <privacy>TCTOKEN</privacy>
 *   <audio enc="opus" rate="8000"/>
 *   <audio enc="opus" rate="16000"/>
 *   <video dec="H264" enc="h.264" device_orientation="0" screen_width="0" screen_height="0"/>
 *   <net medium="3"/>
 *   <capability ver="1">MASK</capability>
 *   <destination><to jid="<device>"><enc v="2" type="pkmsg" count="0">CT</enc></to>...</destination>
 *   <encopt keygen="2"/>
 *   <device-identity>ADV</device-identity>
 *   <voip_settings type="default">BLOB</voip_settings>
 * </offer>
 * }
 *
 * @implNote This implementation models the {@code <offer>} element parsed by {@code parse_xmpp_offer}
 * (fn11544) / {@code deserialize_offer} (fn11641) and built by {@code serialize_offer} (fn11648) in
 * the wa-voip WASM module {@code ff-tScznZ8P}, over the common header stamped by
 * {@code populate_common_call_attr} (fn11591). The flat attribute set follows {@code deserialize_offer}:
 * {@code caller_pn} (data offset {@code 0x552f8}), {@code user_pn} ({@code 0x552f0}), {@code username}
 * ({@code 0x79d43}), {@code group-jid} ({@code 0x875b0}), {@code scheduled-id} ({@code 0x8890d}),
 * {@code joinable} ({@code 0x7b100}), {@code is_lightweight} ({@code 0x220fc}), {@code lightweight-key}
 * ({@code 0x6027}), {@code device_class} ({@code 0x2adae}), and {@code rering_ts} ({@code 0x2a016}).
 * The network medium is emitted as a flat {@code <net medium>} child element (the live captures carry
 * {@code <net medium="3"/>}, never a {@code net} attribute), and the audio and video formats are
 * emitted as flat {@code <audio enc rate>} and {@code <video dec enc device_orientation screen_width
 * screen_height>} children, one element per format, rather than nested under wrapper blocks. The call
 * key rides per device through the reused Signal message pipeline (one
 * {@link CallKeyDistribution} per device). The {@code <group_info>}, {@code <device-identity>},
 * {@code <voip_settings>}, {@code <privacy>}, {@code <relay>}, {@code <bot>}, and A/B-test metadata
 * children are retained as raw subtrees because their grammar is owned by sibling layers.
 *
 * @param callId          the call identifier; never {@code null}
 * @param callCreator     the call creator's device JID; never {@code null}
 * @param callerPn        the caller's phone-number JID, or {@code null} when absent
 * @param userPn          the peer's phone-number JID, or {@code null} when absent
 * @param username        the caller's username, or {@code null} when absent
 * @param groupJid        the group JID for a group offer, or {@code null} for a one-to-one offer
 * @param scheduledId     the scheduled-call identifier, or {@code null} when absent
 * @param deviceClass     the caller device class, or {@code null} when absent
 * @param joinable        whether the call is joinable (a group call others may join)
 * @param lightweight     whether the offer is a lightweight offer
 * @param lightweightKey  the lightweight-offer key, or {@code null} when absent
 * @param reringTimestamp the re-ring timestamp, or {@code -1} when absent
 * @param netMedium       the caller network-medium classification, or {@code -1} when absent
 * @param capabilities    the caller's capability advertisements; never {@code null}, possibly empty
 * @param audioCodecs     the offered audio codec descriptors; never {@code null}, possibly empty
 * @param videoCodecs     the offered video codec descriptors; never {@code null}, possibly empty
 * @param keyDistribution the per-device call-key fanout; never {@code null}, empty for a group offer
 * @param media           the negotiated media descriptor, or {@code null} when absent
 * @param encOptions      the encryption options, or {@code null} when absent
 * @param groupInfo       the raw {@code <group_info>} roster subtree, or {@code null} when absent
 * @param deviceIdentity  the ADV device-identity bytes, or {@code null} when absent
 * @param privacyToken    the one-to-one trusted-contact {@link PrivacyToken}, or {@code null} when absent
 * @param relay           the raw {@code <relay>} transport subtree, or {@code null} when absent
 * @param bot             the raw {@code <bot>} descriptor subtree, or {@code null} when absent
 * @param voipSettings    the raw {@code <voip_settings>} parameter-bundle subtrees; never
 *                        {@code null}, possibly empty
 * @param callerMetadata  the raw caller A/B-test metadata subtree, or {@code null} when absent
 * @see Calls2SignalingType#OFFER
 * @see CallKeyDistribution
 */
public record OfferStanza(String callId, Jid callCreator, Jid callerPn, Jid userPn, String username, Jid groupJid,
                          String scheduledId, String deviceClass, boolean joinable, boolean lightweight,
                          byte[] lightweightKey, long reringTimestamp, int netMedium,
                          List<CallCapability> capabilities, List<CallCodecDescriptor> audioCodecs,
                          List<CallCodecDescriptor> videoCodecs, List<CallKeyDistribution> keyDistribution,
                          CallMediaDescriptor media, CallEncOptions encOptions, Stanza groupInfo, byte[] deviceIdentity,
                          PrivacyToken privacyToken, Stanza relay, Stanza bot, List<Stanza> voipSettings, Stanza callerMetadata)
        implements CallMessage {
    /**
     * The wire element tag for an offer signal.
     */
    public static final String ELEMENT = "offer";

    /**
     * The wire element tag for an audio-format advertisement.
     */
    private static final String AUDIO_ELEMENT = CallCodecDescriptor.AUDIO_ELEMENT;

    /**
     * The wire element tag for a video-format advertisement.
     */
    private static final String VIDEO_ELEMENT = CallCodecDescriptor.VIDEO_ELEMENT;

    /**
     * The wire element tag for the per-device key-fanout block.
     */
    private static final String DESTINATION_ELEMENT = "destination";

    /**
     * The wire element tag for the group roster block.
     */
    private static final String GROUP_INFO_ELEMENT = "group_info";

    /**
     * The wire element tag for the ADV device-identity block.
     */
    private static final String DEVICE_IDENTITY_ELEMENT = "device-identity";

    /**
     * The wire element tag for the one-to-one trusted-contact token block.
     */
    private static final String PRIVACY_ELEMENT = "privacy";

    /**
     * The wire element tag for the relay transport block.
     */
    private static final String RELAY_ELEMENT = "relay";

    /**
     * The wire element tag for the bot descriptor block.
     */
    private static final String BOT_ELEMENT = "bot";

    /**
     * The wire element tag for an engine parameter bundle.
     */
    private static final String VOIP_SETTINGS_ELEMENT = "voip_settings";

    /**
     * The wire element tag for the caller A/B-test metadata block.
     */
    private static final String METADATA_ELEMENT = "metadata";

    /**
     * The wire element tag for one bare key blob, used by the server-delivered receive shape.
     */
    private static final String ENC_ELEMENT = "enc";

    /**
     * The wire attribute naming the caller's phone-number JID.
     */
    private static final String CALLER_PN_ATTRIBUTE = "caller_pn";

    /**
     * The wire attribute naming the peer's phone-number JID.
     */
    private static final String USER_PN_ATTRIBUTE = "user_pn";

    /**
     * The wire attribute naming the caller's username.
     */
    private static final String USERNAME_ATTRIBUTE = "username";

    /**
     * The wire attribute naming the group JID on a group offer.
     */
    private static final String GROUP_JID_ATTRIBUTE = "group-jid";

    /**
     * The wire attribute naming the scheduled-call identifier.
     */
    private static final String SCHEDULED_ID_ATTRIBUTE = "scheduled-id";

    /**
     * The wire attribute naming the caller device class.
     */
    private static final String DEVICE_CLASS_ATTRIBUTE = "device_class";

    /**
     * The wire attribute marking a joinable call.
     */
    private static final String JOINABLE_ATTRIBUTE = "joinable";

    /**
     * The wire attribute marking a lightweight offer.
     */
    private static final String LIGHTWEIGHT_ATTRIBUTE = "is_lightweight";

    /**
     * The wire attribute naming the lightweight-offer key.
     */
    private static final String LIGHTWEIGHT_KEY_ATTRIBUTE = "lightweight-key";

    /**
     * The wire attribute naming the re-ring timestamp.
     */
    private static final String RERING_TS_ATTRIBUTE = "rering_ts";

    /**
     * The wire element tag for the caller network-medium block.
     */
    private static final String NET_ELEMENT = "net";

    /**
     * The wire attribute naming the network-medium classification on the {@code <net>} block.
     */
    private static final String MEDIUM_ATTRIBUTE = "medium";

    /**
     * The wire literal a boolean attribute carries when set; booleans on the call plane serialize as
     * {@code '1'}/{@code '0'} rather than {@code true}/{@code false}.
     */
    private static final String FLAG_TRUE = "1";

    /**
     * The wire literal a boolean attribute carries when clear.
     */
    private static final String FLAG_FALSE = "0";

    /**
     * Canonicalizes the record components, copying the lists and the binary payloads.
     *
     * @throws NullPointerException if {@code callId}, {@code callCreator}, {@code capabilities},
     *                              {@code audioCodecs}, {@code videoCodecs}, {@code keyDistribution},
     *                              or {@code voipSettings} is {@code null}, or if any of those lists
     *                              contains a {@code null} element
     */
    public OfferStanza {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
        Objects.requireNonNull(capabilities, "capabilities cannot be null");
        Objects.requireNonNull(audioCodecs, "audioCodecs cannot be null");
        Objects.requireNonNull(videoCodecs, "videoCodecs cannot be null");
        Objects.requireNonNull(keyDistribution, "keyDistribution cannot be null");
        Objects.requireNonNull(voipSettings, "voipSettings cannot be null");
        capabilities = List.copyOf(capabilities);
        audioCodecs = List.copyOf(audioCodecs);
        videoCodecs = List.copyOf(videoCodecs);
        keyDistribution = List.copyOf(keyDistribution);
        voipSettings = List.copyOf(voipSettings);
        lightweightKey = lightweightKey == null ? null : lightweightKey.clone();
        deviceIdentity = deviceIdentity == null ? null : deviceIdentity.clone();
    }

    /**
     * Returns whether this is a group offer.
     *
     * <p>A group offer carries a {@link #groupJid() group JID}; a one-to-one offer does not.
     *
     * @return {@code true} when the offer carries a group JID
     */
    public boolean isGroup() {
        return groupJid != null;
    }

    /**
     * Returns whether this offer announces a video call.
     *
     * <p>The offer advertises video when it carries at least one video codec descriptor.
     *
     * @return {@code true} when the offer carries video codecs
     */
    public boolean isVideo() {
        return !videoCodecs.isEmpty();
    }

    /**
     * Returns the caller's phone-number JID, if present.
     *
     * @return an {@link Optional} holding the caller phone-number JID, or empty when absent
     */
    public Optional<Jid> callerPnValue() {
        return Optional.ofNullable(callerPn);
    }

    /**
     * Returns the peer's phone-number JID, if present.
     *
     * @return an {@link Optional} holding the peer phone-number JID, or empty when absent
     */
    public Optional<Jid> userPnValue() {
        return Optional.ofNullable(userPn);
    }

    /**
     * Returns the caller's username, if present.
     *
     * @return an {@link Optional} holding the username, or empty when absent
     */
    public Optional<String> usernameValue() {
        return Optional.ofNullable(username);
    }

    /**
     * Returns the group JID, if this is a group offer.
     *
     * @return an {@link Optional} holding the group JID, or empty for a one-to-one offer
     */
    public Optional<Jid> groupJidValue() {
        return Optional.ofNullable(groupJid);
    }

    /**
     * Returns the scheduled-call identifier, if present.
     *
     * @return an {@link Optional} holding the scheduled-call identifier, or empty when absent
     */
    public Optional<String> scheduledIdValue() {
        return Optional.ofNullable(scheduledId);
    }

    /**
     * Returns the caller device class, if present.
     *
     * @return an {@link Optional} holding the device class, or empty when absent
     */
    public Optional<String> deviceClassValue() {
        return Optional.ofNullable(deviceClass);
    }

    /**
     * Returns the re-ring timestamp, if present.
     *
     * @return an {@link OptionalLong} holding the re-ring timestamp, or empty when absent
     */
    public OptionalLong reringTimestampValue() {
        return reringTimestamp < 0 ? OptionalLong.empty() : OptionalLong.of(reringTimestamp);
    }

    /**
     * Returns the caller network-medium classification, if present.
     *
     * @return an {@link OptionalInt} holding the network medium, or empty when absent
     */
    public OptionalInt netMediumValue() {
        return netMedium < 0 ? OptionalInt.empty() : OptionalInt.of(netMedium);
    }

    /**
     * Returns the negotiated media descriptor, if present.
     *
     * @return an {@link Optional} holding the media descriptor, or empty when absent
     */
    public Optional<CallMediaDescriptor> mediaDescriptor() {
        return Optional.ofNullable(media);
    }

    /**
     * Returns the encryption options, if present.
     *
     * @return an {@link Optional} holding the encryption options, or empty when absent
     */
    public Optional<CallEncOptions> encOptionsValue() {
        return Optional.ofNullable(encOptions);
    }

    /**
     * Returns the raw {@code <group_info>} roster subtree, if present.
     *
     * @return an {@link Optional} holding the group-info stanza, or empty for a one-to-one offer
     */
    public Optional<Stanza> groupInfoNode() {
        return Optional.ofNullable(groupInfo);
    }

    /**
     * Returns the ADV device-identity bytes, if present.
     *
     * <p>The returned array, when present, is a defensive copy.
     *
     * @return an {@link Optional} holding a copy of the device-identity bytes, or empty when absent
     */
    public Optional<byte[]> deviceIdentityBytes() {
        return Optional.ofNullable(deviceIdentity)
                .map(byte[]::clone);
    }

    /**
     * Returns the one-to-one trusted-contact token bytes, if present.
     *
     * <p>The returned array, when present, is a defensive copy extracted from the
     * {@link PrivacyToken}.
     *
     * @return an {@link Optional} holding a copy of the privacy-token bytes, or empty when absent
     */
    public Optional<byte[]> privacyTokenBytes() {
        return Optional.ofNullable(privacyToken)
                .map(PrivacyToken::value);
    }

    /**
     * Returns the raw {@code <relay>} transport subtree, if present.
     *
     * @return an {@link Optional} holding the relay stanza, or empty when absent
     */
    public Optional<Stanza> relayNode() {
        return Optional.ofNullable(relay);
    }

    /**
     * Returns the raw {@code <bot>} descriptor subtree, if present.
     *
     * @return an {@link Optional} holding the bot stanza, or empty when absent
     */
    public Optional<Stanza> botNode() {
        return Optional.ofNullable(bot);
    }

    /**
     * Returns the raw caller A/B-test metadata subtree, if present.
     *
     * @return an {@link Optional} holding the metadata stanza, or empty when absent
     */
    public Optional<Stanza> callerMetadataNode() {
        return Optional.ofNullable(callerMetadata);
    }

    /**
     * Returns the lightweight-offer key backing this offer.
     *
     * <p>This accessor overrides the implicit record accessor to return a defensive copy.
     *
     * @return a copy of the lightweight-offer key, or {@code null} when absent
     */
    @Override
    public byte[] lightweightKey() {
        return lightweightKey == null ? null : lightweightKey.clone();
    }

    /**
     * Returns the ADV device-identity bytes backing this offer.
     *
     * <p>This accessor overrides the implicit record accessor to return a defensive copy.
     *
     * @return a copy of the device-identity bytes, or {@code null} when absent
     */
    @Override
    public byte[] deviceIdentity() {
        return deviceIdentity == null ? null : deviceIdentity.clone();
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link Calls2SignalingType#OFFER}
     */
    @Override
    public Calls2SignalingType type() {
        return Calls2SignalingType.OFFER;
    }

    /**
     * Builds the {@code <offer>} action stanza with its flat attributes and nested tree.
     *
     * <p>Children are emitted in the order group roster, privacy token, audio formats, video formats,
     * network medium, capabilities, key fanout, media, encryption options, relay, bot, device
     * identity, parameter bundles, caller metadata, matching the live-capture child order on which the
     * server's offer validation depends. Each audio and video format is a flat {@code <audio>} or
     * {@code <video>} element and the network medium a {@code <net medium>} element. Absent attributes
     * and children are omitted. The key fanout is emitted as a {@code <destination>} block of
     * per-device {@code <to>} slots; a group offer carries no fanout.
     *
     * @return the offer action stanza
     */
    @Override
    public Stanza toStanza() {
        var children = new ArrayList<Stanza>();
        if (groupInfo != null) {
            children.add(groupInfo);
        }
        if (privacyToken != null) {
            children.add(new StanzaBuilder()
                    .description(PRIVACY_ELEMENT)
                    .content(privacyToken.value())
                    .build());
        }
        for (var codec : audioCodecs) {
            children.add(codec.toStanza());
        }
        for (var codec : videoCodecs) {
            children.add(codec.toStanza());
        }
        if (netMedium >= 0) {
            children.add(new StanzaBuilder()
                    .description(NET_ELEMENT)
                    .attribute(MEDIUM_ATTRIBUTE, netMedium)
                    .build());
        }
        for (var capability : capabilities) {
            children.add(capability.toStanza());
        }
        if (!keyDistribution.isEmpty()) {
            var toNodes = new ArrayList<Stanza>(keyDistribution.size());
            for (var slot : keyDistribution) {
                toNodes.add(slot.toStanza());
            }
            children.add(new StanzaBuilder()
                    .description(DESTINATION_ELEMENT)
                    .content(toNodes)
                    .build());
        }
        if (media != null) {
            children.add(media.toStanza());
        }
        if (encOptions != null) {
            children.add(encOptions.toStanza());
        }
        if (relay != null) {
            children.add(relay);
        }
        if (bot != null) {
            children.add(bot);
        }
        if (deviceIdentity != null) {
            children.add(new StanzaBuilder()
                    .description(DEVICE_IDENTITY_ELEMENT)
                    .content(deviceIdentity)
                    .build());
        }
        children.addAll(voipSettings);
        if (callerMetadata != null) {
            children.add(callerMetadata);
        }

        var builder = CallMessages.stampHeader(new StanzaBuilder().description(ELEMENT), callId, callCreator)
                .attribute(CALLER_PN_ATTRIBUTE, callerPn)
                .attribute(USER_PN_ATTRIBUTE, userPn)
                .attribute(USERNAME_ATTRIBUTE, username)
                .attribute(GROUP_JID_ATTRIBUTE, groupJid)
                .attribute(SCHEDULED_ID_ATTRIBUTE, scheduledId)
                .attribute(DEVICE_CLASS_ATTRIBUTE, deviceClass)
                .attribute(JOINABLE_ATTRIBUTE, FLAG_TRUE, joinable)
                .attribute(LIGHTWEIGHT_ATTRIBUTE, FLAG_TRUE, lightweight)
                .attribute(LIGHTWEIGHT_KEY_ATTRIBUTE, lightweightKey)
                .attribute(RERING_TS_ATTRIBUTE, reringTimestamp, reringTimestamp >= 0);
        if (!children.isEmpty()) {
            builder.content(children);
        }
        return builder.build();
    }

    /**
     * Decodes an {@code <offer>} action stanza into an {@link OfferStanza}.
     *
     * <p>The flat attributes, the capability children, the flat {@code <audio>} and {@code <video>}
     * format children, the {@code <net medium>} child, the call-key fanout, the {@code <media>}
     * descriptor, and the {@code <encopt>} options are decoded structurally. The call-key fanout
     * accepts both shapes: the {@code <destination>}
     * block of per-device {@code <to>} slots used on the send side, and a bare {@code <enc>} directly
     * under the offer used on the server-delivered receive side. The {@code <group_info>},
     * {@code <device-identity>}, {@code <voip_settings>}, {@code <privacy>}, {@code <relay>},
     * {@code <bot>}, and {@code <metadata>} children are retained as raw subtrees.
     *
     * @param stanza the {@code <offer>} stanza
     * @return the decoded offer signal
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id} or {@code call-creator} attribute
     *                                is absent
     */
    public static OfferStanza of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        var callerPn = stanza.getAttributeAsJid(CALLER_PN_ATTRIBUTE).orElse(null);
        var userPn = stanza.getAttributeAsJid(USER_PN_ATTRIBUTE).orElse(null);
        var username = stanza.getAttributeAsString(USERNAME_ATTRIBUTE, null);
        var groupJid = stanza.getAttributeAsJid(GROUP_JID_ATTRIBUTE).orElse(null);
        var scheduledId = stanza.getAttributeAsString(SCHEDULED_ID_ATTRIBUTE, null);
        var deviceClass = stanza.getAttributeAsString(DEVICE_CLASS_ATTRIBUTE, null);
        var joinable = FLAG_TRUE.equals(stanza.getAttributeAsString(JOINABLE_ATTRIBUTE, FLAG_FALSE));
        var lightweight = FLAG_TRUE.equals(stanza.getAttributeAsString(LIGHTWEIGHT_ATTRIBUTE, FLAG_FALSE));
        var lightweightKey = stanza.getAttributeAsBytes(LIGHTWEIGHT_KEY_ATTRIBUTE, null);
        var reringTimestamp = stanza.getAttributeAsLong(RERING_TS_ATTRIBUTE, -1L);
        var netMedium = stanza.getChild(NET_ELEMENT)
                .map(net -> net.getAttributeAsInt(MEDIUM_ATTRIBUTE, -1))
                .orElse(-1);

        var capabilities = stanza.streamChildren(CallCapability.ELEMENT)
                .flatMap(child -> CallCapability.of(child).stream())
                .toList();
        var audioCodecs = stanza.streamChildren(AUDIO_ELEMENT)
                .flatMap(audio -> CallCodecDescriptor.of(audio).stream())
                .toList();
        var videoCodecs = stanza.streamChildren(VIDEO_ELEMENT)
                .flatMap(video -> CallCodecDescriptor.of(video).stream())
                .toList();
        var keyDistribution = decodeKeyDistribution(stanza);
        var media = stanza.getChild(CallMediaDescriptor.ELEMENT).flatMap(CallMediaDescriptor::of).orElse(null);
        var encOptions = stanza.getChild(CallEncOptions.ELEMENT).flatMap(CallEncOptions::of).orElse(null);
        var groupInfo = stanza.getChild(GROUP_INFO_ELEMENT).orElse(null);
        var deviceIdentity = stanza.getChild(DEVICE_IDENTITY_ELEMENT)
                .flatMap(Stanza::toContentBytes)
                .orElse(null);
        var privacyToken = stanza.getChild(PRIVACY_ELEMENT)
                .flatMap(Stanza::toContentBytes)
                .map(PrivacyToken::new)
                .orElse(null);
        var relay = stanza.getChild(RELAY_ELEMENT).orElse(null);
        var bot = stanza.getChild(BOT_ELEMENT).orElse(null);
        var voipSettings = stanza.getChildren(VOIP_SETTINGS_ELEMENT)
                .stream()
                .toList();
        var callerMetadata = stanza.getChild(METADATA_ELEMENT).orElse(null);

        return new OfferStanza(callId, callCreator, callerPn, userPn, username, groupJid, scheduledId, deviceClass,
                joinable, lightweight, lightweightKey, reringTimestamp, netMedium, capabilities, audioCodecs,
                videoCodecs, keyDistribution, media, encOptions, groupInfo, deviceIdentity, privacyToken, relay, bot,
                voipSettings, callerMetadata);
    }

    /**
     * Decodes the call-key fanout from either offer shape.
     *
     * <p>The send-side shape nests one {@code <to>} slot per device inside a {@code <destination>}
     * block; the server-delivered receive shape places the device's {@code <enc>} directly under the
     * offer with no wrapper. This method prefers the {@code <destination>} block and falls back to a
     * single bare {@code <enc>} when no destination is present, modeling the second shape as one
     * fanout slot addressed to the call creator.
     *
     * @param stanza the {@code <offer>} stanza
     * @return the decoded per-device key fanout, possibly empty
     */
    private static List<CallKeyDistribution> decodeKeyDistribution(Stanza stanza) {
        var destination = stanza.getChild(DESTINATION_ELEMENT);
        if (destination.isPresent()) {
            return destination.get()
                    .streamChildren(CallKeyDistribution.TO_ELEMENT)
                    .flatMap(to -> CallKeyDistribution.of(to).stream())
                    .toList();
        }
        var enc = stanza.getChild(ENC_ELEMENT);
        if (enc.isPresent()) {
            var ciphertext = enc.get().toContentBytes();
            if (ciphertext.isPresent()) {
                var creator = stanza.getAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE).orElse(null);
                if (creator != null) {
                    var version = enc.get().getAttributeAsInt("v", -1);
                    var encType = enc.get().getAttributeAsString("type", null);
                    var count = enc.get().getAttributeAsInt("count", -1);
                    return List.of(new CallKeyDistribution(creator, version, encType, count, ciphertext.get()));
                }
            }
        }
        return List.of();
    }

    /**
     * Returns whether {@code obj} is an {@link OfferStanza} equal to this one by value.
     *
     * <p>The binary components ({@link #lightweightKey()}, {@link #deviceIdentity()},
     * {@link #privacyToken()}) compare by content rather than by array identity; every other
     * component compares by its own equality.
     *
     * @param obj the object to compare against
     * @return {@code true} when {@code obj} is a value-equal offer
     */
    @Override
    public boolean equals(Object obj) {
        return obj == this || (obj instanceof OfferStanza that
                && this.joinable == that.joinable
                && this.lightweight == that.lightweight
                && this.reringTimestamp == that.reringTimestamp
                && this.netMedium == that.netMedium
                && this.callId.equals(that.callId)
                && this.callCreator.equals(that.callCreator)
                && Objects.equals(this.callerPn, that.callerPn)
                && Objects.equals(this.userPn, that.userPn)
                && Objects.equals(this.username, that.username)
                && Objects.equals(this.groupJid, that.groupJid)
                && Objects.equals(this.scheduledId, that.scheduledId)
                && Objects.equals(this.deviceClass, that.deviceClass)
                && Arrays.equals(this.lightweightKey, that.lightweightKey)
                && this.capabilities.equals(that.capabilities)
                && this.audioCodecs.equals(that.audioCodecs)
                && this.videoCodecs.equals(that.videoCodecs)
                && this.keyDistribution.equals(that.keyDistribution)
                && Objects.equals(this.media, that.media)
                && Objects.equals(this.encOptions, that.encOptions)
                && Objects.equals(this.groupInfo, that.groupInfo)
                && Arrays.equals(this.deviceIdentity, that.deviceIdentity)
                && Objects.equals(this.privacyToken, that.privacyToken)
                && Objects.equals(this.relay, that.relay)
                && Objects.equals(this.bot, that.bot)
                && this.voipSettings.equals(that.voipSettings)
                && Objects.equals(this.callerMetadata, that.callerMetadata));
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * <p>The binary components contribute their content hash rather than their identity hash.
     *
     * @return the value hash of this offer
     */
    @Override
    public int hashCode() {
        var result = Objects.hash(callId, callCreator, callerPn, userPn, username, groupJid, scheduledId, deviceClass,
                joinable, lightweight, reringTimestamp, netMedium, capabilities, audioCodecs, videoCodecs,
                keyDistribution, media, encOptions, groupInfo, privacyToken, relay, bot, voipSettings, callerMetadata);
        result = 31 * result + Arrays.hashCode(lightweightKey);
        result = 31 * result + Arrays.hashCode(deviceIdentity);
        return result;
    }

    /**
     * Returns a debug-oriented string for this offer.
     *
     * <p>The binary components are summarized by length rather than dumped, and the output is not part
     * of the wire format.
     *
     * @return the debug string
     */
    @Override
    public String toString() {
        return "OfferStanza[callId=" + callId
                + ", callCreator=" + callCreator
                + ", group=" + isGroup()
                + ", video=" + isVideo()
                + ", joinable=" + joinable
                + ", deviceCount=" + keyDistribution.size()
                + ", deviceIdentityLen=" + (deviceIdentity == null ? -1 : deviceIdentity.length)
                + ", privacyTokenLen=" + (privacyToken == null ? -1 : privacyToken.length())
                + ']';
    }
}
