package com.github.auties00.cobalt.call.signaling;

import com.github.auties00.cobalt.call.CallService;
import com.github.auties00.cobalt.model.call.CallEndReason;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryptedPayload;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Builds the {@code <call>} stanzas the VoIP signalling layer sends to the server.
 *
 * <p>Every factory method returns a {@link NodeBuilder} for the {@code <call to="...">...</call>}
 * envelope; the {@code id} attribute is left unset for the dispatcher to assign.
 *
 * <p>The receive-side counterpart that parses these stanzas is {@link CallReceiver}.
 *
 * @implNote The outgoing offer is "thin": the caller emits only the codec advertisement, the
 * per-device Signal-encrypted call-key fanout, the ADV device-identity envelope, and the privacy
 * and capability bitfields. The server enriches the offer with the {@code <relay>} transport
 * block, {@code <rte>}, {@code <metadata>}, {@code <voip_settings>}, and {@code <uploadfieldstat/>}
 * before forwarding to the recipient.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSendSignalingXmpp")
public final class CallStanza {
    /**
     * Canonical 7-byte capability advertisement observed in every captured offer/preaccept on
     * WA Web revision 1040316331. The byte sequence encodes the device's supported call features
     * (audio codecs, hop-by-hop FEC, etc.); the captured value covers the standard Opus + DTLS-SCTP
     * profile.
     *
     * @implNote The exact bit layout of this advertisement is not yet reverse-engineered; the
     * captured corpus byte string is used verbatim as a sane default. A future capture against a
     * differently-configured peer may reveal the field layout and let this become a computed value.
     */
    private static final byte[] CAPABILITY_BYTES = {
            (byte) 0x01, (byte) 0x05, (byte) 0xF7, (byte) 0x09,
            (byte) 0xE4, (byte) 0xBB, (byte) 0x13
    };

    /**
     * Net medium advertised in outgoing offers (corresponds to the call placer's network
     * classification before the relay election runs).
     */
    private static final String OFFER_NET_MEDIUM = "3";

    /**
     * Net medium advertised in outgoing accept stanzas (corresponds to the chosen relay's
     * network classification after the local relay-latency election).
     */
    private static final String ACCEPT_NET_MEDIUM = "2";

    /**
     * Canonical sample rate selected by an outgoing preaccept/accept once the call profile is
     * locked. Outgoing offers advertise both {@code 8000} and {@code 16000}; the callee picks one.
     */
    private static final String CHOSEN_AUDIO_RATE = "16000";

    /**
     * Lower sample rate advertised on the offer's first {@code <audio>} child.
     */
    private static final String OFFERED_AUDIO_RATE_LOW = "8000";

    /**
     * Higher sample rate advertised on the offer's second {@code <audio>} child.
     */
    private static final String OFFERED_AUDIO_RATE_HIGH = "16000";

    /**
     * The {@code keygen} attribute value carried by every {@code <encopt>} child in the captured
     * corpus. The value selects the SRTP master-key-derivation scheme; {@code "2"} is the
     * standard scheme used by every WA Web revision observed so far.
     */
    private static final String ENCOPT_KEYGEN = "2";

    /**
     * Cipher version attribute value emitted on every {@code <enc>} child inside the call offer's
     * per-device fanout. Matches the standard Signal {@code v="2"} envelope used by WA's regular
     * message fanout.
     */
    private static final String ENC_CIPHERTEXT_VERSION = "2";

    /**
     * Prevents instantiation of this static helper.
     *
     * @throws AssertionError always, since this class is not instantiable
     */
    private CallStanza() {
        throw new AssertionError("CallStanza is not instantiable");
    }

    /**
     * Builds an outgoing offer stanza carrying the full per-device Signal fanout and the ADV
     * device-identity envelope.
     *
     * <p>The returned envelope wraps an
     * {@snippet lang="xml" :
     * <call to="<peer-user-lid>" id="...">
     *   <offer call-id="..." call-creator="<self-lid:device@lid>" [group-jid="..."]>
     *     <privacy>[11 bytes]</privacy>
     *     <audio enc="opus" rate="8000"/>
     *     <audio enc="opus" rate="16000"/>
     *     <net medium="3"/>
     *     <capability ver="1">[7 bytes]</capability>
     *     [<group_info><participant jid="..."/>...</group_info>]
     *     <destination>
     *       <to jid="<peer-device-lid>"><enc v="2" type="msg|pkmsg" count="0">[ciphertext]</enc></to>
     *       ...
     *     </destination>
     *     [<video/>]
     *     <encopt keygen="2"/>
     *     <device-identity>[185 bytes]</device-identity>
     *   </offer>
     * </call>
     * }
     *
     * <p>The order of children mirrors the captured corpus. The {@code <destination>} block carries
     * one {@code <to><enc>} per peer device, with the {@code <enc>} content being the Signal-encrypted
     * {@code Message{ call: Call { callKey: <bytes> } }} payload computed via
     * {@link com.github.auties00.cobalt.message.send.crypto.MessageEncryption#encryptForDevice(Jid, byte[])}.
     * The {@code <device-identity>} bytes come from {@code WAWebAdvSignatureApi.getADVEncodedIdentity()}'s
     * Cobalt equivalent in the ADV identity store.
     *
     * <p>The {@code group-jid} attribute and {@code <group_info>} child are emitted only for group calls
     * (when {@code groupJid} is non-{@code null} and {@code groupParticipants} is non-empty).
     *
     * @apiNote Callers must precompute every byte buffer this method takes; the builder does no
     * Signal encryption, no ADV signing, and no privacy/capability bitfield assembly. See
     * {@link CallService#placeCall(com.github.auties00.cobalt.model.jid.Jid, com.github.auties00.cobalt.call.stream.AudioOutputStream, com.github.auties00.cobalt.call.stream.AudioInputStream, com.github.auties00.cobalt.call.stream.VideoOutputStream, com.github.auties00.cobalt.call.stream.VideoInputStream)
     * CallService.placeCall} for the production caller wiring.
     *
     * @param target               the peer user LID, placed on {@code <call to>}
     * @param creator              the local self device JID with device suffix, placed on
     *                             {@code call-creator}
     * @param callId               the locally-generated call identifier
     * @param video                whether to advertise this as a video call (emits {@code <video/>})
     * @param privacy              the 11-byte privacy bitfield bytes
     * @param capability           the 7-byte capability bitfield bytes; {@code null} substitutes
     *                             {@link #CAPABILITY_BYTES}
     * @param destinationPayloads  one {@link MessageEncryptedPayload} per peer device receiving the call key
     * @param deviceIdentity       the ADV-encoded device-identity envelope bytes
     * @param groupJid             the group JID for group calls, or {@code null} for one-to-one
     * @param groupParticipants    the participants for a group call; ignored when {@code groupJid} is
     *                             {@code null}
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code target}, {@code creator}, {@code callId},
     *                              {@code privacy}, {@code destinationPayloads}, or
     *                              {@code deviceIdentity} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static NodeBuilder offer(Jid target,
                                    Jid creator,
                                    String callId,
                                    boolean video,
                                    byte[] privacy,
                                    byte[] capability,
                                    List<MessageEncryptedPayload> destinationPayloads,
                                    byte[] deviceIdentity,
                                    Jid groupJid,
                                    Collection<Jid> groupParticipants) {
        return offer(target, creator, callId, video, privacy, capability, destinationPayloads,
                deviceIdentity, groupJid, groupParticipants, null);
    }

    /**
     * Builds a call offer, with an optional caller phone-number JID for group calls.
     *
     * <p>For a group offer (non-{@code null} {@code groupJid}) the stanza targets {@code <group>@call},
     * adds {@code caller_pn} (the caller's phone-number JID) and {@code joinable="1"}, and omits the 1:1
     * {@code <privacy>} and per-device {@code <destination>} fanout: a group call does not carry a
     * per-member-encrypted call key (the relay block and per-participant keys arrive after joining), and
     * the server builds the {@code <group_info>} member/device tree, so the caller does not send it.
     *
     * @param callerPn the caller's phone-number JID for {@code caller_pn} on a group offer, or {@code null}
     * @see #offer(Jid, Jid, String, boolean, byte[], byte[], List, byte[], Jid, Collection)
     */
    public static NodeBuilder offer(Jid target,
                                    Jid creator,
                                    String callId,
                                    boolean video,
                                    byte[] privacy,
                                    byte[] capability,
                                    List<MessageEncryptedPayload> destinationPayloads,
                                    byte[] deviceIdentity,
                                    Jid groupJid,
                                    Collection<Jid> groupParticipants,
                                    Jid callerPn) {
        return offer(target, creator, callId, video, privacy, capability, destinationPayloads,
                deviceIdentity, groupJid, groupParticipants, callerPn, null);
    }

    /**
     * Builds a call offer, with an optional caller phone-number JID and a prebuilt {@code <group_info>}
     * child for group calls.
     *
     * <p>For a group call, the WhatsApp voip engine emits a {@code <group_info>} child that enumerates
     * the invited participants and their devices so the server knows whom to fan the offer out to; an
     * offer that omits it is silently dropped by the server. The caller therefore builds the
     * {@code <group_info>} from the resolved participant device lists and passes it here, where it is
     * inserted as the first child of {@code <offer>} (matching the captured wire order). For a 1:1 call
     * the argument is {@code null}.
     *
     * @param target               the call target JID; must not be {@code null}
     * @param creator              the call creator JID; must not be {@code null}
     * @param callId               the call identifier; must not be {@code null}
     * @param video                whether the offer announces a video call
     * @param privacy              the 1:1 trusted-contact token bytes, or {@code null} for a group call
     * @param capability           the capability bytes, or {@code null} to use the default
     * @param destinationPayloads  one {@link MessageEncryptedPayload} per peer device receiving the call key
     * @param deviceIdentity       the encoded signed device identity authenticating the caller
     * @param groupJid             the group JID for a group call, or {@code null} for a 1:1 call
     * @param groupParticipants    unused; retained for source-mapping compatibility
     * @param callerPn             the caller's phone-number JID for {@code caller_pn} on a group offer, or {@code null}
     * @param groupInfo            the prebuilt {@code <group_info>} child for a group offer, or {@code null}
     * @return the {@code <call>} stanza builder ready for dispatch
     * @throws NullPointerException if any required reference argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static NodeBuilder offer(Jid target,
                                    Jid creator,
                                    String callId,
                                    boolean video,
                                    byte[] privacy,
                                    byte[] capability,
                                    List<MessageEncryptedPayload> destinationPayloads,
                                    byte[] deviceIdentity,
                                    Jid groupJid,
                                    Collection<Jid> groupParticipants,
                                    Jid callerPn,
                                    Node groupInfo) {
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(creator, "creator cannot be null");
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(destinationPayloads, "destinationPayloads cannot be null");
        Objects.requireNonNull(deviceIdentity, "deviceIdentity cannot be null");

        var capabilityBytes = capability != null ? capability : CAPABILITY_BYTES;

        var offerChildren = new ArrayList<Node>(11);
        // The group offer leads with the <group_info> participant/device tree the server fans out on.
        if (groupInfo != null) {
            offerChildren.add(groupInfo);
        }
        // The 1:1 offer carries the peer's trusted-contact token in <privacy>; a group offer has no
        // such per-peer token and omits the element entirely (the inbound group offer parsed by
        // WAWebHandleVoipCall carries no privacy child), so a null privacy skips it.
        if (privacy != null) {
            offerChildren.add(new NodeBuilder()
                    .description("privacy")
                    .content(privacy)
                    .build());
        }
        offerChildren.add(new NodeBuilder()
                .description("audio")
                .attribute("enc", "opus")
                .attribute("rate", OFFERED_AUDIO_RATE_LOW)
                .build());
        offerChildren.add(new NodeBuilder()
                .description("audio")
                .attribute("enc", "opus")
                .attribute("rate", OFFERED_AUDIO_RATE_HIGH)
                .build());
        offerChildren.add(new NodeBuilder()
                .description("net")
                .attribute("medium", OFFER_NET_MEDIUM)
                .build());
        // A 1:1 offer carries offer-level <capability> and <encopt>; a captured real group offer carries
        // neither at offer level (capability lives inside each <group_info> device), so the group offer
        // omits both to match the wire shape the server accepts.
        if (groupJid == null) {
            offerChildren.add(new NodeBuilder()
                    .description("capability")
                    .attribute("ver", "1")
                    .content(capabilityBytes)
                    .build());
        }

        // A group offer carries no per-device <destination> fanout (the call key is not shipped in the
        // offer for groups); the 1:1 offer always does. Skip it when there are no payloads.
        if (!destinationPayloads.isEmpty()) {
            offerChildren.add(buildDestination(destinationPayloads));
        }

        if (video) {
            offerChildren.add(new NodeBuilder()
                    .description("video")
                    .build());
        }

        if (groupJid == null) {
            offerChildren.add(new NodeBuilder()
                    .description("encopt")
                    .attribute("keygen", ENCOPT_KEYGEN)
                    .build());
        }
        // The 1:1 offer carries <device-identity> for the server to authenticate the caller; a captured
        // WA Web group offer carries no device-identity at all (only audio/net/group_info), so the group
        // offer omits it.
        if (groupJid == null) {
            offerChildren.add(new NodeBuilder()
                    .description("device-identity")
                    .content(deviceIdentity)
                    .build());
        }

        var offerBuilder = new NodeBuilder()
                .description(CallSignalingType.OFFER.wireTag())
                .attribute("call-id", callId)
                .attribute("call-creator", creator);
        // A captured WA Web group offer carries ONLY group-jid (no caller_pn, no joinable -- those are
        // server-added on the member-fanned-out copy, not sent by the caller).
        if (groupJid != null) {
            offerBuilder.attribute("group-jid", groupJid);
        }
        offerBuilder.content(offerChildren);
        return wrap(target, offerBuilder.build());
    }

    /**
     * Builds the {@code <destination>} child carrying one {@code <to>} per peer device.
     *
     * <p>Each {@link MessageEncryptedPayload} with a ciphertext produces a {@code <to jid="<device>">
     * <enc v="2" type="msg|pkmsg" count="0">[ciphertext]</enc></to>} child. A
     * {@link MessageEncryptedPayload#bareDestination(Jid) bare destination} marker (null ciphertext)
     * produces a keyless {@code <to jid="<device>"/>} child instead, matching WhatsApp Web's behaviour
     * of stripping every {@code <enc>} and addressing each device bare when per-device encryption fails
     * for any device, so the call still rings. Payloads with a {@code null}
     * {@link MessageEncryptedPayload#recipientJid()} are skipped.
     *
     * @param payloads the per-device call-key fanout, or per-device bare destination markers
     * @return the {@code <destination>} {@link Node}, never {@code null}
     */
    private static Node buildDestination(List<MessageEncryptedPayload> payloads) {
        var toNodes = new ArrayList<Node>(payloads.size());
        for (var payload : payloads) {
            if (payload.recipientJid() == null) {
                continue;
            }
            var toBuilder = new NodeBuilder()
                    .description("to")
                    .attribute("jid", payload.recipientJid());
            if (payload.ciphertext() != null) {
                toBuilder.content(new NodeBuilder()
                        .description("enc")
                        .attribute("v", ENC_CIPHERTEXT_VERSION)
                        .attribute("type", payload.type().protocolValue())
                        .attribute("count", "0")
                        .content(payload.ciphertext())
                        .build());
            }
            toNodes.add(toBuilder.build());
        }
        return new NodeBuilder()
                .description("destination")
                .content(toNodes)
                .build();
    }

    /**
     * Builds a pre-accept stanza echoing the chosen call profile back to the caller.
     *
     * <p>The returned envelope wraps a
     * {@snippet lang="xml" :
     * <preaccept call-id="..." call-creator="<peer-creator>">
     *   <audio enc="opus" rate="16000"/>
     *   <encopt keygen="2"/>
     *   <capability ver="1">[7 bytes]</capability>
     * </preaccept>
     * }
     *
     * <p>The pre-accept is sent once the device decides to alert the user, before they answer.
     *
     * @param caller the JID of the call creator; routed back as both {@code <call to>} and
     *               {@code call-creator}; must not be {@code null}
     * @param callId the call identifier from the original offer; must not be {@code null}
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code caller} or {@code callId} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static NodeBuilder preaccept(Jid caller, String callId) {
        return preaccept(caller, callId, caller);
    }

    /**
     * Builds a pre-accept stanza addressed to an explicit target.
     *
     * <p>Identical payload to {@link #preaccept(Jid, String)} but lets the caller route the
     * {@code <call to>} envelope independently of the {@code call-creator}. A group-call callee
     * addresses its pre-accept to the call MUC address {@code <callId>@call} (the join is mediated by
     * the call address, not the creator's device), whereas a one-to-one callee routes it back to the
     * creator. The {@code call-creator} attribute always carries the original offer's creator.
     *
     * @param creator the offer's {@code call-creator}, placed on the {@code call-creator} attribute;
     *                must not be {@code null}
     * @param callId  the call identifier from the original offer; must not be {@code null}
     * @param target  the JID placed on {@code <call to>}; must not be {@code null}
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code creator}, {@code callId}, or {@code target} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static NodeBuilder preaccept(Jid creator, String callId, Jid target) {
        Objects.requireNonNull(creator, "creator cannot be null");
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        var payload = new NodeBuilder()
                .description(CallSignalingType.PREACCEPT.wireTag())
                .attribute("call-id", callId)
                .attribute("call-creator", creator)
                .content(
                        new NodeBuilder()
                                .description("audio")
                                .attribute("enc", "opus")
                                .attribute("rate", CHOSEN_AUDIO_RATE)
                                .build(),
                        new NodeBuilder()
                                .description("encopt")
                                .attribute("keygen", ENCOPT_KEYGEN)
                                .build(),
                        new NodeBuilder()
                                .description("capability")
                                .attribute("ver", "1")
                                .content(CAPABILITY_BYTES)
                                .build())
                .build();
        return wrap(target, payload);
    }

    /**
     * Builds an accept stanza confirming the user answered.
     *
     * <p>The returned envelope wraps an
     * {@snippet lang="xml" :
     * <accept call-id="..." call-creator="<peer-creator>">
     *   <audio enc="opus" rate="16000"/>
     *   <net medium="2"/>
     *   <encopt keygen="2"/>
     * </accept>
     * }
     *
     * <p>The captured corpus also includes a {@code <metadata peer_abtest_bucket=... id_list=.../>}
     * child carrying A/B-test telemetry. That child is currently omitted because Cobalt does not
     * mirror WA Web's A/B-test bucket assignments; the server tolerates its absence on the
     * accept path.
     *
     * @param caller the JID of the call creator; routed back as both {@code <call to>} and
     *               {@code call-creator}; must not be {@code null}
     * @param callId the call identifier; must not be {@code null}
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code caller} or {@code callId} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static NodeBuilder accept(Jid caller, String callId) {
        return accept(caller, callId, caller);
    }

    /**
     * Builds an accept stanza addressed to an explicit target.
     *
     * <p>Identical payload to {@link #accept(Jid, String)} but lets the caller route the
     * {@code <call to>} envelope independently of the {@code call-creator}. A group-call callee
     * addresses its accept to the call MUC address {@code <callId>@call} (the join is mediated by the
     * call address); a one-to-one callee routes it back to the creator. The {@code call-creator}
     * attribute always carries the original offer's creator.
     *
     * @param creator the offer's {@code call-creator}, placed on the {@code call-creator} attribute;
     *                must not be {@code null}
     * @param callId  the call identifier; must not be {@code null}
     * @param target  the JID placed on {@code <call to>}; must not be {@code null}
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code creator}, {@code callId}, or {@code target} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static NodeBuilder accept(Jid creator, String callId, Jid target) {
        Objects.requireNonNull(creator, "creator cannot be null");
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        var payload = new NodeBuilder()
                .description(CallSignalingType.ACCEPT.wireTag())
                .attribute("call-id", callId)
                .attribute("call-creator", creator)
                .content(
                        new NodeBuilder()
                                .description("audio")
                                .attribute("enc", "opus")
                                .attribute("rate", CHOSEN_AUDIO_RATE)
                                .build(),
                        new NodeBuilder()
                                .description("net")
                                .attribute("medium", ACCEPT_NET_MEDIUM)
                                .build(),
                        new NodeBuilder()
                                .description("encopt")
                                .attribute("keygen", ENCOPT_KEYGEN)
                                .build())
                .build();
        return wrap(target, payload);
    }

    /**
     * Builds a reject stanza.
     *
     * <p>The returned envelope wraps a {@code <reject call-id call-creator/>} payload.
     *
     * @param caller the JID of the call creator; must not be {@code null}
     * @param callId the call identifier; must not be {@code null}
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code caller} or {@code callId} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static NodeBuilder reject(Jid caller, String callId) {
        return simple(caller, callId, CallSignalingType.REJECT.wireTag());
    }

    /**
     * Builds a terminate stanza without a per-device {@code <destination>} block.
     *
     * <p>Used for peer-initiated-style terminates where the server fans out to every peer
     * device automatically. Cobalt's outbound hangup path prefers the richer
     * {@link #terminate(Jid, Jid, String, CallEndReason, Collection)} overload that explicitly
     * lists the peer devices the terminate must reach.
     *
     * @param target  the JID of the other party, placed on {@code <call to>}; must not be {@code null}
     * @param creator the JID of the call creator, placed on {@code call-creator}; must not be
     *                {@code null}
     * @param callId  the call identifier; must not be {@code null}
     * @param reason  the end reason; must not be {@code null}
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static NodeBuilder terminate(Jid target, Jid creator, String callId, CallEndReason reason) {
        return terminate(target, creator, callId, reason, null);
    }

    /**
     * Builds a terminate stanza, optionally fanning out per peer device.
     *
     * <p>The returned envelope wraps a
     * {@snippet lang="xml" :
     * <terminate reason="..." call-id="..." call-creator="...">
     *   [<destination>
     *     <to jid="<peer-device-1>"/>
     *     <to jid="<peer-device-2>"/>
     *     ...
     *   </destination>]
     * </terminate>
     * }
     *
     * <p>The {@code <destination>} block is emitted when {@code peerDevices} is non-{@code null}
     * and non-empty. The fanout entries carry no encrypted payload (only the bare {@code jid}
     * marker): unlike the offer fanout, the terminate is a server-mediated broadcast.
     *
     * @param target      the JID of the other party, placed on {@code <call to>}; must not be {@code null}
     * @param creator     the JID of the call creator, placed on {@code call-creator}; must not be
     *                    {@code null}
     * @param callId      the call identifier; must not be {@code null}
     * @param reason      the end reason; must not be {@code null}
     * @param peerDevices the peer device JIDs to fan out to, or {@code null}/empty to omit the
     *                    {@code <destination>} block
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code target}, {@code creator}, {@code callId}, or
     *                              {@code reason} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static NodeBuilder terminate(Jid target, Jid creator, String callId, CallEndReason reason,
                                        Collection<Jid> peerDevices) {
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(creator, "creator cannot be null");
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");

        var terminateBuilder = new NodeBuilder()
                .description(CallSignalingType.TERMINATE.wireTag())
                .attribute("reason", reason.wireValue())
                .attribute("call-id", callId)
                .attribute("call-creator", creator);

        if (peerDevices != null && !peerDevices.isEmpty()) {
            var toNodes = new ArrayList<Node>(peerDevices.size());
            for (var device : peerDevices) {
                toNodes.add(new NodeBuilder()
                        .description("to")
                        .attribute("jid", device)
                        .build());
            }
            terminateBuilder.content(new NodeBuilder()
                    .description("destination")
                    .content(toNodes)
                    .build());
        }

        return wrap(target, terminateBuilder.build());
    }

    /**
     * Builds a mute-state stanza announcing that the local user has muted or unmuted their microphone.
     *
     * <p>The returned envelope wraps a {@code <mute_v2 call-id call-creator mute-state="0|1"/>} payload,
     * with {@code mute-state} set to {@code "1"} for a mute and {@code "0"} for an unmute.
     *
     * @implNote This implementation emits the {@code mute-state} attribute on a {@code mute_v2} payload
     * rather than the legacy {@code state="muted"|"unmuted"} attribute on a {@code mute} payload; the
     * captured {@code fixtures/call/1to1/mute-toggle.*.jsonl} corpus confirms the current shape.
     *
     * @param target  the JID of the other party; must not be {@code null}
     * @param creator the JID of the call creator; must not be {@code null}
     * @param callId  the call identifier; must not be {@code null}
     * @param muted   {@code true} to announce a mute, {@code false} to announce an unmute
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code target}, {@code creator}, or {@code callId} is
     *                              {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static NodeBuilder mute(Jid target, Jid creator, String callId, boolean muted) {
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(creator, "creator cannot be null");
        Objects.requireNonNull(callId, "callId cannot be null");
        var payload = new NodeBuilder()
                .description(CallSignalingType.MUTE.wireTag())
                .attribute("call-id", callId)
                .attribute("call-creator", creator)
                .attribute("mute-state", muted ? "1" : "0")
                .build();
        return wrap(target, payload);
    }

    /**
     * Builds a raise-hand stanza announcing that the local user raised or lowered their hand.
     *
     * <p>The returned envelope wraps a {@code <user_action call-id call-creator action="raise_hand">}
     * payload whose single {@code <raise_hand raise-hand-state="0|1"/>} child carries the new state:
     * {@code "1"} for a raised hand, {@code "0"} for a lowered hand. The server relays the stanza to
     * every other participant of the group call.
     *
     * @implNote This implementation mirrors the live-captured shape from a WhatsApp Web sender on the
     * {@code enable_new_user_action_stanza_for_raise_hand_sender} path: a {@code <user_action>} wrapper
     * tagged {@code action="raise_hand"} around a {@code <raise_hand raise-hand-state="N"/>} child. It
     * replaces the earlier empirical RTP-shaped data-plane packet, which never round-tripped against a
     * live peer.
     *
     * @param target  the JID placed on {@code <call to>}; the group call address
     *                {@code <callId>@call} for a group call; must not be {@code null}
     * @param creator the JID of the call creator; must not be {@code null}
     * @param callId  the call identifier; must not be {@code null}
     * @param raised  {@code true} to raise the hand, {@code false} to lower it
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code target}, {@code creator}, or {@code callId} is
     *                              {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static NodeBuilder raiseHand(Jid target, Jid creator, String callId, boolean raised) {
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(creator, "creator cannot be null");
        Objects.requireNonNull(callId, "callId cannot be null");
        var raiseHandChild = new NodeBuilder()
                .description("raise_hand")
                .attribute("raise-hand-state", raised ? "1" : "0")
                .build();
        var payload = new NodeBuilder()
                .description("user_action")
                .attribute("call-id", callId)
                .attribute("call-creator", creator)
                .attribute("action", "raise_hand")
                .content(raiseHandChild)
                .build();
        return wrap(target, payload);
    }

    /**
     * Builds a peer-mute request stanza asking another participant to mute their microphone.
     *
     * <p>The returned envelope wraps a {@code <mute_v2 call-id call-creator request-state="1"/>}
     * payload: the same {@code mute_v2} element used for a self-mute announcement, but carrying the
     * {@code request-state} attribute instead of {@code mute-state}. The server routes the request to
     * the targeted participant addressed by {@code target}.
     *
     * @implNote This implementation mirrors the live-captured shape from a WhatsApp Web sender invoking
     * {@code requestPeerMute}: the {@code mute_v2} payload with {@code request-state="1"}, matching the
     * WhatsApp voip engine deserializer that accepts either {@code request-state} or {@code mute-state}
     * on the {@code mute_v2} element.
     *
     * @param target  the JID placed on {@code <call to>}; the participant being asked to mute, or the
     *                group call address {@code <callId>@call}; must not be {@code null}
     * @param creator the JID of the call creator; must not be {@code null}
     * @param callId  the call identifier; must not be {@code null}
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code target}, {@code creator}, or {@code callId} is
     *                              {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static NodeBuilder peerMute(Jid target, Jid creator, String callId) {
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(creator, "creator cannot be null");
        Objects.requireNonNull(callId, "callId cannot be null");
        var payload = new NodeBuilder()
                .description(CallSignalingType.MUTE.wireTag())
                .attribute("call-id", callId)
                .attribute("call-creator", creator)
                .attribute("request-state", "1")
                .build();
        return wrap(target, payload);
    }

    /**
     * Video-state code announcing that local video is enabled (camera on, or an audio-to-video upgrade).
     *
     * @implNote This implementation uses 11, the live-captured {@code state} a WhatsApp Web caller emits when it turns
     * video on or upgrades an audio call to video; the {@code <video state="11" dec="H264" voip_settings="video">} shape
     * appears verbatim in the captured {@code fixtures/call/1to1/video-upgrade-accept.*.jsonl} corpus.
     */
    private static final int VIDEO_STATE_ON = 11;

    /**
     * Video-state code announcing that local video is disabled (camera off, or a video-to-audio downgrade).
     *
     * @implNote This implementation uses 0, the live-captured {@code state} a WhatsApp Web caller emits when it turns
     * video off; the transient {@code state="1"}/{@code "4"}/{@code "6"} values seen in the corpus are intermediate peer
     * states the caller does not originate for a plain disable.
     */
    private static final int VIDEO_STATE_OFF = 0;

    /**
     * Video-state code announcing an active video stream that carries a source resolution (screenshare or hi-res video).
     *
     * @implNote This implementation uses 4, the live-captured resolution-carrying {@code state}; both 4 and
     * {@link #VIDEO_STATE_ON} are video-active states, but 4 is the variant the corpus pairs with
     * {@code screen_width}/{@code screen_height}.
     */
    private static final int VIDEO_STATE_ACTIVE_WITH_RESOLUTION = 4;

    /**
     * The video codec advertised on every outgoing {@code <video>} stanza.
     *
     * @implNote This implementation uses {@code "H264"}, the live-negotiated web video codec; a different peer may
     * negotiate VP8, but the WhatsApp Web sender advertises {@code dec="H264"} in every captured {@code <video>} stanza.
     */
    private static final String VIDEO_DECODER = "H264";

    /**
     * The {@code voip_settings} marker emitted on the video-enabled {@code <video>} variant.
     */
    private static final String VIDEO_VOIP_SETTINGS = "video";

    /**
     * The default {@code device_orientation} emitted on outgoing {@code <video>} stanzas (portrait).
     */
    private static final String VIDEO_DEVICE_ORIENTATION_DEFAULT = "0";

    /**
     * Builds a video-state stanza announcing that the local user has turned video on or off.
     *
     * <p>The returned envelope wraps a {@code <video>} payload (the live wire tag, not {@code <video_state>}). Enabling
     * video emits {@snippet lang="xml" :
     * <video call-id="..." call-creator="..." state="11" device_orientation="0" dec="H264" voip_settings="video"/>
     * } and disabling video emits {@snippet lang="xml" :
     * <video call-id="..." call-creator="..." state="0" device_orientation="0"/>
     * }
     * matching the live-captured caller shape. The same {@code <video>} mechanism announces an audio-to-video upgrade
     * (state {@code 11}) and a video-to-audio downgrade (state {@code 0}); there is no re-offer.
     *
     * @implNote This implementation maps {@code enabled} to the numeric {@code state} ({@link #VIDEO_STATE_ON} or
     * {@link #VIDEO_STATE_OFF}) and emits the full attribute set the WhatsApp Web caller sends, replacing the earlier
     * {@code state="on"|"off"} shape, which never matched the wire.
     *
     * @param target  the JID of the other party; must not be {@code null}
     * @param creator the JID of the call creator; must not be {@code null}
     * @param callId  the call identifier; must not be {@code null}
     * @param enabled {@code true} to announce a video-on, {@code false} to announce a video-off
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code target}, {@code creator}, or {@code callId} is
     *                              {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static NodeBuilder videoState(Jid target, Jid creator, String callId, boolean enabled) {
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(creator, "creator cannot be null");
        Objects.requireNonNull(callId, "callId cannot be null");
        var builder = new NodeBuilder()
                .description("video")
                .attribute("call-id", callId)
                .attribute("call-creator", creator)
                .attribute("state", String.valueOf(enabled ? VIDEO_STATE_ON : VIDEO_STATE_OFF))
                .attribute("device_orientation", VIDEO_DEVICE_ORIENTATION_DEFAULT);
        if (enabled) {
            builder.attribute("dec", VIDEO_DECODER)
                    .attribute("voip_settings", VIDEO_VOIP_SETTINGS);
        }
        return wrap(target, builder.build());
    }

    /**
     * Builds a screen-share {@code <video>} stanza announcing an active video stream that carries the shared source
     * resolution.
     *
     * <p>Screen sharing is not a dedicated {@code <call>} child: the live WhatsApp Web stack announces it through the
     * same {@code <video>} state, distinguished by the {@code screen_width}/{@code screen_height} attributes carrying the
     * source screen dimensions. The returned envelope wraps {@snippet lang="xml" :
     * <video call-id="..." call-creator="..." state="4" device_orientation="0"
     *        screen_width="1920" screen_height="1080" dec="H264" voip_settings="video"/>
     * }
     *
     * @param target       the JID of the other party; must not be {@code null}
     * @param creator      the JID of the call creator; must not be {@code null}
     * @param callId       the call identifier; must not be {@code null}
     * @param screenWidth  the shared source width in pixels
     * @param screenHeight the shared source height in pixels
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code target}, {@code creator}, or {@code callId} is
     *                              {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static NodeBuilder screenShare(Jid target, Jid creator, String callId, int screenWidth, int screenHeight) {
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(creator, "creator cannot be null");
        Objects.requireNonNull(callId, "callId cannot be null");
        var payload = new NodeBuilder()
                .description("video")
                .attribute("call-id", callId)
                .attribute("call-creator", creator)
                .attribute("state", String.valueOf(VIDEO_STATE_ACTIVE_WITH_RESOLUTION))
                .attribute("device_orientation", VIDEO_DEVICE_ORIENTATION_DEFAULT)
                .attribute("screen_width", String.valueOf(screenWidth))
                .attribute("screen_height", String.valueOf(screenHeight))
                .attribute("dec", VIDEO_DECODER)
                .attribute("voip_settings", VIDEO_VOIP_SETTINGS)
                .build();
        return wrap(target, payload);
    }

    /**
     * Builds a group-update stanza that adds or removes participants in an in-progress group call.
     *
     * <p>The returned envelope wraps a {@code <group_update call-id call-creator action="add|remove">}
     * payload whose {@code <group_info>} child lists one {@code <participant jid="..."/>} entry per
     * supplied participant. The {@code action} attribute is {@code "add"} when {@code add} is
     * {@code true} and {@code "remove"} otherwise.
     *
     * @param target       the group JID; must not be {@code null}
     * @param creator      the JID of the call creator; must not be {@code null}
     * @param callId       the call identifier; must not be {@code null}
     * @param add          {@code true} to announce additions, {@code false} to announce removals
     * @param participants the participants to add or remove; must not be {@code null} or empty
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException     if any reference argument is {@code null}
     * @throws IllegalArgumentException if {@code participants} is empty
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static NodeBuilder groupUpdate(Jid target, Jid creator, String callId, boolean add, Collection<Jid> participants) {
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(creator, "creator cannot be null");
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(participants, "participants cannot be null");
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("participants cannot be empty");
        }
        var participantNodes = new ArrayList<Node>(participants.size());
        for (var participant : participants) {
            participantNodes.add(new NodeBuilder()
                    .description("participant")
                    .attribute("jid", participant)
                    .build());
        }
        var groupInfo = new NodeBuilder()
                .description("group_info")
                .content(participantNodes)
                .build();
        var payload = new NodeBuilder()
                .description(CallSignalingType.GROUP_UPDATE.wireTag())
                .attribute("call-id", callId)
                .attribute("call-creator", creator)
                .attribute("action", add ? "add" : "remove")
                .content(List.of(groupInfo))
                .build();
        return wrap(target, payload);
    }

    /**
     * Builds a ringing stanza confirming the device is alerting the local user.
     *
     * <p>The returned envelope wraps a {@code <ringing call-id call-creator/>} payload.
     *
     * @param caller the JID of the call creator; must not be {@code null}
     * @param callId the call identifier; must not be {@code null}
     * @return the {@code <call>} stanza ready for dispatch
     * @throws NullPointerException if {@code caller} or {@code callId} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSendSignalingXmpp", exports = "sendWAWebVoipSignalingXmpp",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static NodeBuilder ringing(Jid caller, String callId) {
        return simple(caller, callId, "ringing");
    }

    /**
     * Builds a content-less call payload carrying only the call-id and call-creator attributes.
     *
     * <p>The returned envelope wraps a {@code <tag call-id call-creator/>} payload, where {@code tag}
     * is the supplied wire tag, and routes it to {@code caller} as the {@code <call to>} attribute.
     *
     * @param caller the JID of the call creator, also used as the {@code <call to>} target
     * @param callId the call identifier
     * @param tag    the wire tag for the payload, for example {@code "reject"}
     * @return the wrapped {@code <call>} stanza
     * @throws NullPointerException if {@code caller} or {@code callId} is {@code null}
     */
    private static NodeBuilder simple(Jid caller, String callId, String tag) {
        Objects.requireNonNull(caller, "caller cannot be null");
        Objects.requireNonNull(callId, "callId cannot be null");
        var payload = new NodeBuilder()
                .description(tag)
                .attribute("call-id", callId)
                .attribute("call-creator", caller)
                .build();
        return wrap(caller, payload);
    }

    /**
     * Wraps a payload in a {@code <call to="...">...</call>} envelope builder.
     *
     * <p>Returns the {@link NodeBuilder} unbuilt so the caller can either
     * {@link NodeBuilder#build() build} it for a fire-and-forget
     * {@link com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient#sendNodeWithNoResponse(Node)
     * sendNodeWithNoResponse} or pass it directly to
     * {@link com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient#sendNode(NodeBuilder)
     * sendNode} when the IQ ACK must be captured (for example, the outgoing offer whose ACK
     * carries the relay tokens).
     *
     * @param target  the value of the {@code to} attribute
     * @param payload the inner payload node
     * @return the wrapped {@code <call>} stanza builder
     */
    private static NodeBuilder wrap(Jid target, Node payload) {
        return new NodeBuilder()
                .description("call")
                .attribute("to", target)
                .content(payload);
    }

}
