package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Represents an {@code <accept>} signal: the callee answers the call.
 *
 * <p>An accept is the callee's answer to an offer. For a relay call it carries, as its first child, the
 * server-allocated {@code <relay>} block (the caller's relay credentials the server delivered in the
 * offer ack), which the server consumes to complete relay allocation before forwarding the accept; an
 * accept that omits it is torn down with {@code setup_failed}. It also carries the chosen network medium,
 * the offered audio codecs, and a video codec for a video call. The data-channel cert-fingerprint HMAC,
 * e2e public-key {@code <enc>}
 * blobs, the {@code <media>} descriptor, and the embedded {@code <transport>} block belong to the
 * conditional Web-P2P/DTLS data-channel path and are absent from a plain relay accept; the relay block is
 * modeled by {@link RelayInfo}, while the key blobs and transport block are held as raw subtrees because
 * their grammar is owned by the participant-crypto and transport layers.
 *
 * <p>On the wire the element is {@snippet lang="xml" :
 * <accept call-id="..." call-creator="...">
 *   <relay self_pid="..." peer_pid="..." uuid="...">CREDENTIALS</relay>
 *   <capability ver="1">MASK</capability>
 *   <audio enc="opus" rate="16000"/>
 *   <net medium="2"/>
 *   <enc>KEY_BLOB</enc>
 *   <media enc="N" rate="16000"/>
 *   <encopt keygen="2"/>
 *   <transport .../>
 * </accept>
 * }
 *
 * @implNote This implementation models the {@code <accept>} element (data offset {@code 0x1a352})
 * built by {@code make_and_send_accept} (fn11450) and {@code serialize_accept}/{@code deserialize_accept}
 * in the wa-voip WASM module {@code ff-tScznZ8P}: the callee audio capabilities (msg offset
 * {@code 0x64}), the voip capability version (fn11774), local candidates (fn11464), the data-channel
 * cert-fingerprint HMAC and e2e public-key blobs, the video codec capability (fn11451), and the relay
 * candidate (fn11468), over the common header stamped by {@code populate_common_call_attr} (fn11591).
 * The {@code <enc>} key blobs and the embedded {@code <transport>} block are retained as raw subtrees
 * because their layouts are owned by the participant-crypto and transport layers respectively. The
 * accept also receives an ack or nack like the offer; that ack is parsed by the ack layer, not by this
 * record.
 *
 * @param callId        the call identifier; never {@code null}
 * @param callCreator   the call creator's device JID; never {@code null}
 * @param netMedium     the chosen network-medium classification, or {@code -1} when absent
 * @param capabilities  the callee's capability advertisements; never {@code null}, possibly empty
 * @param audioCodecs   the offered audio codec descriptors; never {@code null}, possibly empty
 * @param videoCodecs   the offered video codec descriptors; never {@code null}, possibly empty
 * @param encKeys       the raw {@code <enc>} key-blob subtrees; never {@code null}, possibly empty
 * @param media         the negotiated media descriptor, or {@code null} when absent
 * @param encOptions    the encryption options, or {@code null} when absent
 * @param transport     the embedded {@code <transport>} subtree, or {@code null} when absent
 * @param relay         the parsed server-allocated {@link RelayInfo relay block} (the caller's relay
 *                      credentials) echoed as the accept's first child for a relay call, or {@code null}
 *                      when absent
 * @see Calls2SignalingType#ACCEPT
 */
public record AcceptStanza(String callId, Jid callCreator, int netMedium, List<CallCapability> capabilities,
                           List<CallCodecDescriptor> audioCodecs, List<CallCodecDescriptor> videoCodecs,
                           List<Stanza> encKeys, CallMediaDescriptor media, CallEncOptions encOptions,
                           Stanza transport, RelayInfo relay) implements CallMessage {
    /**
     * The wire element tag for an accept signal.
     */
    public static final String ELEMENT = "accept";

    /**
     * The wire element tag for the chosen network-medium block.
     */
    private static final String NET_ELEMENT = "net";

    /**
     * The wire attribute naming the network-medium classification on the {@code <net>} block.
     */
    private static final String MEDIUM_ATTRIBUTE = "medium";

    /**
     * The wire element tag for an audio-format advertisement.
     */
    private static final String AUDIO_ELEMENT = CallCodecDescriptor.AUDIO_ELEMENT;

    /**
     * The wire element tag for a video-format advertisement.
     */
    private static final String VIDEO_ELEMENT = CallCodecDescriptor.VIDEO_ELEMENT;

    /**
     * The wire element tag for a callee key-material blob.
     */
    private static final String ENC_ELEMENT = "enc";

    /**
     * The wire element tag for the embedded transport block.
     */
    private static final String TRANSPORT_ELEMENT = "transport";

    /**
     * The wire element tag for the server-allocated relay block.
     */
    private static final String RELAY_ELEMENT = "relay";

    /**
     * Canonicalizes the record components, copying the capability, codec, and key lists.
     *
     * @throws NullPointerException if {@code callId}, {@code callCreator}, {@code capabilities},
     *                              {@code audioCodecs}, {@code videoCodecs}, or {@code encKeys} is
     *                              {@code null}, or if any of those lists contains a {@code null} element
     */
    public AcceptStanza {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
        Objects.requireNonNull(capabilities, "capabilities cannot be null");
        Objects.requireNonNull(audioCodecs, "audioCodecs cannot be null");
        Objects.requireNonNull(videoCodecs, "videoCodecs cannot be null");
        Objects.requireNonNull(encKeys, "encKeys cannot be null");
        capabilities = List.copyOf(capabilities);
        audioCodecs = List.copyOf(audioCodecs);
        videoCodecs = List.copyOf(videoCodecs);
        encKeys = List.copyOf(encKeys);
    }

    /**
     * Returns the chosen network-medium classification, if present.
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
     * Returns the embedded {@code <transport>} subtree, if present.
     *
     * @return an {@link Optional} holding the transport stanza, or empty when absent
     */
    public Optional<Stanza> transportNode() {
        return Optional.ofNullable(transport);
    }

    /**
     * Returns the parsed server-allocated {@code <relay>} block, if present.
     *
     * @return an {@link Optional} holding the relay info, or empty when absent
     */
    public Optional<RelayInfo> relayInfo() {
        return Optional.ofNullable(relay);
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link Calls2SignalingType#ACCEPT}
     */
    @Override
    public Calls2SignalingType type() {
        return Calls2SignalingType.ACCEPT;
    }

    /**
     * Builds the {@code <accept>} action stanza with its relay, capability, audio, video, key, media,
     * encryption, and transport children.
     *
     * <p>Children are emitted in the order relay, capabilities, audio formats, video formats, network medium,
     * key blobs, media, encryption options, transport; the relay block leads when present, each audio format
     * is a flat {@code <audio>} element and each video format a flat {@code <video>} element, the network
     * medium a {@code <net medium>} element emitted only when present, and absent optional children are
     * omitted.
     *
     * @return the accept action stanza
     */
    @Override
    public Stanza toStanza() {
        var children = new ArrayList<Stanza>();
        if (relay != null) {
            children.add(relay.toNode());
        }
        for (var capability : capabilities) {
            children.add(capability.toStanza());
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
        children.addAll(encKeys);
        if (media != null) {
            children.add(media.toStanza());
        }
        if (encOptions != null) {
            children.add(encOptions.toStanza());
        }
        if (transport != null) {
            children.add(transport);
        }
        var builder = CallMessages.stampHeader(new StanzaBuilder().description(ELEMENT), callId, callCreator);
        if (!children.isEmpty()) {
            builder.content(children);
        }
        return builder.build();
    }

    /**
     * Decodes an {@code <accept>} action stanza into an {@link AcceptStanza}.
     *
     * <p>The {@code <relay>} block, capability children, the flat {@code <audio>} and {@code <video>} format
     * children, the {@code <net medium>} child, the {@code <enc>} key blobs, the {@code <media>} descriptor,
     * the {@code <encopt>} options, and the embedded {@code <transport>} subtree are each decoded when
     * present; the relay block is parsed into a {@link RelayInfo}, and the key blobs and transport subtree
     * are retained verbatim.
     *
     * @param stanza the {@code <accept>} stanza
     * @return the decoded accept signal
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id} or {@code call-creator} attribute
     *                                is absent
     */
    public static AcceptStanza of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
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
        var encKeys = stanza.streamChildren(ENC_ELEMENT)
                .toList();
        var media = stanza.getChild(CallMediaDescriptor.ELEMENT).flatMap(CallMediaDescriptor::of).orElse(null);
        var encOptions = stanza.getChild(CallEncOptions.ELEMENT).flatMap(CallEncOptions::of).orElse(null);
        var transport = stanza.getChild(TRANSPORT_ELEMENT).orElse(null);
        var relay = stanza.getChild(RELAY_ELEMENT).flatMap(RelayInfo::of).orElse(null);
        return new AcceptStanza(callId, callCreator, netMedium, capabilities, audioCodecs, videoCodecs, encKeys,
                media, encOptions, transport, relay);
    }
}
