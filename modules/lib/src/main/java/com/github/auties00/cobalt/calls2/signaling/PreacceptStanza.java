package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a {@code <preaccept>} signal: the callee's device is alerting but the user has not
 * answered yet.
 *
 * <p>A preaccept is the early ring-acknowledgement the callee sends after the offer, before the user
 * answers, so the caller can begin early media preparation. It echoes a light version of the call
 * profile back to the caller: the callee's capability advertisements, the offered audio and video codecs,
 * the negotiated media descriptor, and the encryption options. It carries no transport block and no
 * per-device key fanout; the full key and transport material arrive with the accept.
 *
 * <p>On the wire the element is {@snippet lang="xml" :
 * <preaccept call-id="..." call-creator="...">
 *   <capability ver="1">MASK</capability>
 *   <audio enc="opus" rate="16000"/>
 *   <video dec="H264" device_orientation="0" screen_width="0" screen_height="0"/>
 *   <media enc="N" rate="16000"/>
 *   <encopt keygen="2"/>
 * </preaccept>
 * }
 *
 * @implNote This implementation models the {@code <preaccept>} element (data offset {@code 0x1a133})
 * built by {@code make_and_send_preaccept} (fn11453) and {@code serialize_preaccept} in the wa-voip
 * WASM module {@code ff-tScznZ8P}: the callee audio capabilities, the voip capability advertisement
 * (fn11452), and the negotiated media, over the common header stamped by
 * {@code populate_common_call_attr} (fn11591). Each audio format is a flat {@code <audio>} element, and a
 * video call's preaccept also carries the negotiated video codec as a flat {@code <video>} element.
 * Group preaccepts additionally embed an end-to-end key and a
 * video codec capability; those are carried by the key-distribution and capability subsystems rather
 * than enumerated as preaccept fields here.
 *
 * @param callId       the call identifier; never {@code null}
 * @param callCreator  the call creator's device JID; never {@code null}
 * @param capabilities the callee's capability advertisements; never {@code null}, possibly empty
 * @param audioCodecs  the offered audio codec descriptors; never {@code null}, possibly empty
 * @param videoCodecs  the offered video codec descriptors; never {@code null}, possibly empty
 * @param media        the negotiated media descriptor, or {@code null} when absent
 * @param encOptions   the encryption options, or {@code null} when absent
 * @see Calls2SignalingType#PREACCEPT
 */
public record PreacceptStanza(String callId, Jid callCreator, List<CallCapability> capabilities,
                              List<CallCodecDescriptor> audioCodecs, List<CallCodecDescriptor> videoCodecs,
                              CallMediaDescriptor media, CallEncOptions encOptions) implements CallMessage {
    /**
     * The wire element tag for a preaccept signal.
     */
    public static final String ELEMENT = "preaccept";

    /**
     * The wire element tag for an audio-format advertisement.
     */
    private static final String AUDIO_ELEMENT = CallCodecDescriptor.AUDIO_ELEMENT;

    /**
     * The wire element tag for a video-format advertisement.
     */
    private static final String VIDEO_ELEMENT = CallCodecDescriptor.VIDEO_ELEMENT;

    /**
     * The wire element tag for a capability child, taken from {@link CallCapability#ELEMENT}.
     */
    private static final String CAPABILITY_ELEMENT = CallCapability.ELEMENT;

    /**
     * The wire element tag for the media descriptor child, taken from {@link CallMediaDescriptor#ELEMENT}.
     */
    private static final String MEDIA_ELEMENT = CallMediaDescriptor.ELEMENT;

    /**
     * The wire element tag for the encryption-options child, taken from {@link CallEncOptions#ELEMENT}.
     */
    private static final String ENCOPT_ELEMENT = CallEncOptions.ELEMENT;

    /**
     * Canonicalizes the record components, copying the capability and codec lists.
     *
     * @throws NullPointerException if {@code callId}, {@code callCreator}, {@code capabilities},
     *                              {@code audioCodecs}, or {@code videoCodecs} is {@code null}, or if any
     *                              of those lists contains a {@code null} element
     */
    public PreacceptStanza {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
        Objects.requireNonNull(capabilities, "capabilities cannot be null");
        Objects.requireNonNull(audioCodecs, "audioCodecs cannot be null");
        Objects.requireNonNull(videoCodecs, "videoCodecs cannot be null");
        capabilities = List.copyOf(capabilities);
        audioCodecs = List.copyOf(audioCodecs);
        videoCodecs = List.copyOf(videoCodecs);
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
     * {@inheritDoc}
     *
     * @return {@link Calls2SignalingType#PREACCEPT}
     */
    @Override
    public Calls2SignalingType type() {
        return Calls2SignalingType.PREACCEPT;
    }

    /**
     * Builds the {@code <preaccept>} action stanza with its capability, audio, video, media, and encryption
     * children.
     *
     * <p>Children are emitted in the order capabilities, audio formats, video formats, media, encryption
     * options; each audio format is a flat {@code <audio>} element and each video format a flat
     * {@code <video>} element, and absent media and encryption options are omitted.
     *
     * @return the preaccept action stanza
     */
    @Override
    public Stanza toStanza() {
        var children = new ArrayList<Stanza>();
        for (var capability : capabilities) {
            children.add(capability.toStanza());
        }
        for (var codec : audioCodecs) {
            children.add(codec.toStanza());
        }
        for (var codec : videoCodecs) {
            children.add(codec.toStanza());
        }
        if (media != null) {
            children.add(media.toStanza());
        }
        if (encOptions != null) {
            children.add(encOptions.toStanza());
        }
        var builder = CallMessages.stampHeader(new StanzaBuilder().description(ELEMENT), callId, callCreator);
        if (!children.isEmpty()) {
            builder.content(children);
        }
        return builder.build();
    }

    /**
     * Decodes a {@code <preaccept>} action stanza into a {@link PreacceptStanza}.
     *
     * <p>Capability children, the flat {@code <audio>} and {@code <video>} format children, the
     * {@code <media>} descriptor, and the {@code <encopt>} options are each decoded when present.
     *
     * @param stanza the {@code <preaccept>} stanza
     * @return the decoded preaccept signal
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id} or {@code call-creator} attribute
     *                                is absent
     */
    public static PreacceptStanza of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        var capabilities = stanza.streamChildren(CAPABILITY_ELEMENT)
                .flatMap(child -> CallCapability.of(child).stream())
                .toList();
        var audioCodecs = stanza.streamChildren(AUDIO_ELEMENT)
                .flatMap(audio -> CallCodecDescriptor.of(audio).stream())
                .toList();
        var videoCodecs = stanza.streamChildren(VIDEO_ELEMENT)
                .flatMap(video -> CallCodecDescriptor.of(video).stream())
                .toList();
        var media = stanza.getChild(MEDIA_ELEMENT).flatMap(CallMediaDescriptor::of).orElse(null);
        var encOptions = stanza.getChild(ENCOPT_ELEMENT).flatMap(CallEncOptions::of).orElse(null);
        return new PreacceptStanza(callId, callCreator, capabilities, audioCodecs, videoCodecs, media, encOptions);
    }
}
