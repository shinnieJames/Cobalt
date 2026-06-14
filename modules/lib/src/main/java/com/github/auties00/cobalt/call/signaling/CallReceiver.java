package com.github.auties00.cobalt.call.signaling;

import com.github.auties00.cobalt.stream.SocketStreamHandler;
import com.github.auties00.cobalt.ack.AckClass;
import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.call.CallRuntime;
import com.github.auties00.cobalt.model.call.CallInteraction;
import com.github.auties00.cobalt.call.CallService;
import com.github.auties00.cobalt.model.call.CallPeerState;
import com.github.auties00.cobalt.model.call.IncomingCall;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.call.CallOfferMessage;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.listener.linked.LinkedCallEndedListener;
import com.github.auties00.cobalt.listener.linked.LinkedCallInteractionListener;
import com.github.auties00.cobalt.listener.linked.LinkedCallListener;
import com.github.auties00.cobalt.listener.linked.LinkedCallMuteChangedListener;
import com.github.auties00.cobalt.listener.linked.LinkedCallOfferNoticeListener;
import com.github.auties00.cobalt.listener.linked.LinkedCallParticipantsChangedListener;
import com.github.auties00.cobalt.listener.linked.LinkedCallPeerStateChangedListener;
import com.github.auties00.cobalt.listener.linked.LinkedCallPreacceptListener;
import com.github.auties00.cobalt.listener.linked.LinkedCallVideoStateChangedListener;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import com.github.auties00.cobalt.model.call.CallEndReason;

/**
 * Handles inbound VoIP call stanzas received from the WhatsApp server.
 *
 * <p>This handler processes call signaling stanzas (tag {@code "call"}) arriving on the socket stream.
 * It parses each stanza into its constituent fields (peer {@link Jid}, call creator, call identifier,
 * timestamp, and similar), persists the LID-to-phone-number mappings and caller push names the stanza
 * carries, updates the local call model in the
 * {@link com.github.auties00.cobalt.store.LinkedWhatsAppStore}, and sends the receipt or acknowledgement the
 * payload type requires back to the server. The supported payload tags are {@code offer},
 * {@code accept}, {@code reject}, {@code preaccept}, {@code enc_rekey}, {@code terminate},
 * {@code mute_v2}, {@code video_state}, {@code peer_state}, {@code group_update}, {@code offer_notice},
 * and a set of media-plane tags that are acknowledged and otherwise ignored; any other tag falls
 * through to a plain acknowledgement.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleVoipCall")
@WhatsAppWebModule(moduleName = "WAWebVoipLidUtils")
public final class CallReceiver extends SocketStreamHandler.Concurrent {
    /**
     * Logs parse errors and signaling traces for malformed or ignored stanzas.
     */
    private static final System.Logger LOGGER = System.getLogger(CallReceiver.class.getName());

    /**
     * Holds the WhatsApp client used to send stanzas and access the store.
     */
    private final LinkedWhatsAppClient whatsapp;

    /**
     * Holds the call engine that dispatches peer-side accept, reject, and terminate transitions to the
     * matching {@link CallRuntime}.
     */
    private final CallService engine;

    /**
     * Holds the {@link AckSender} used to emit the {@code <ack class="call" type="...">} stanza that
     * echoes the parsed VoIP payload tag back to the server.
     */
    private final AckSender ackSender;

    /**
     * Holds the {@link MessageService} used to Signal-decrypt the caller's 32-byte call key from the
     * inbound offer's per-device {@code <enc>} fanout before stashing it with the engine.
     */
    private final MessageService messageService;

    /**
     * Constructs a call receiver bound to its client, call engine, message service, and acknowledgement
     * sender.
     *
     * @param whatsapp       the WhatsApp client used to send stanzas and access the store
     * @param engine         the call engine the receiver routes peer-side events to
     * @param messageService the {@link MessageService} used to decrypt the offer's per-device call-key
     *                       {@code <enc>}
     * @param ackSender      the {@link AckSender} used to emit the outbound {@code <ack class="call">}
     *                       stanza
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleVoipCall", exports = "handleCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public CallReceiver(LinkedWhatsAppClient whatsapp, CallService engine, MessageService messageService,
                        AckSender ackSender) {
        this.whatsapp = whatsapp;
        this.engine = engine;
        this.messageService = messageService;
        this.ackSender = ackSender;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates the inbound call stanza to {@link #handleCall(Node)} for parsing and dispatch.
     *
     * @param node the inbound call stanza node
     */
    @Override
    public void handle(Node node) {
        handleCall(node);
    }

    /**
     * Parses the inbound call stanza and dispatches it to the receipt, acknowledgement, or no-op path
     * its payload tag requires.
     *
     * <p>The call stanza has the structure:
     * {@snippet lang="xml" :
     * <call from="..." id="..." [offline] [t="..."] [sender_lid="..."]>
     *   <offer|accept|reject|enc_rekey|terminate|... call-id="..." call-creator="..."
     *          [group-jid="..."] [caller_pn="..."] [notify="..."]>
     *     [<video/>]
     *     [<group_info>
     *       <participant jid="..." [user_pn="..."] [username="..."]/> ...
     *     </group_info>]
     *   </...>
     * </call>
     * }
     *
     * <p>The stanza is ignored when the {@code from} attribute or the payload child is missing, and
     * also when the payload lacks a {@code call-id} or {@code call-creator} attribute. When the stanza
     * carries a {@code sender_lid} attribute, the mapping between the sender LID and the {@code from}
     * phone number is persisted via
     * {@link com.github.auties00.cobalt.store.ContactStore#registerLidMapping(Jid, Jid)}. The payload
     * attributes and the optional {@code group_info} participants are persisted through
     * {@link #persistAttributesAndLidMappingsForCall(Jid, Jid, String, Node, boolean)}: the caller push
     * name is written to the contact, the caller LID-to-phone mapping is registered when applicable,
     * and each {@code group_info} participant receives the same treatment.
     *
     * <p>Dispatch by payload tag is:
     * <ul>
     *   <li>{@code offer}: build the {@link IncomingCall}, send a receipt, register the call, and
     *       notify listeners.</li>
     *   <li>{@code accept}: send a receipt and route the peer acceptance to the engine.</li>
     *   <li>{@code reject}: send a receipt, route the peer rejection to the engine, drop the call from
     *       the store, and notify listeners that the call ended.</li>
     *   <li>{@code preaccept}: send an acknowledgement and notify listeners of the pre-accept.</li>
     *   <li>{@code enc_rekey}: send a receipt for the group-call key re-exchange.</li>
     *   <li>{@code terminate}: route the peer termination to the engine, drop the call from the store,
     *       send an acknowledgement, and notify listeners that the call ended.</li>
     *   <li>{@code mute_v2}: send an acknowledgement and notify listeners of the microphone-mute
     *       change, reading {@code mute-state} as {@code "1"} muted and {@code "0"} unmuted.</li>
     *   <li>{@code video_state}: send an acknowledgement and notify listeners of the video on/off
     *       change.</li>
     *   <li>{@code peer_state}: send an acknowledgement and notify listeners of the peer-state
     *       update.</li>
     *   <li>{@code group_update}: send an acknowledgement and notify listeners that participants were
     *       added or removed.</li>
     *   <li>{@code offer_notice}: send an acknowledgement and notify listeners of the missed-call
     *       offer notice.</li>
     *   <li>any media-plane or unrecognized tag: send an acknowledgement.</li>
     * </ul>
     *
     * @param node the inbound call stanza node
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleVoipCall", exports = "handleCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleCall(Node node) {
        var from = node.getAttributeAsJid("from", null);
        var payload = node.getChild().orElse(null);
        if (from == null || payload == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring malformed call stanza: {0}", node);
            return;
        }

        var senderLid = node.getAttributeAsJid("sender_lid", null);

        var callId = payload.getAttributeAsString("call-id", null);
        var callCreator = payload.getAttributeAsJid("call-creator", null);
        if (callId == null || callCreator == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring call stanza with missing call-id or call-creator: {0}", node);
            return;
        }

        var offline = node.hasAttribute("offline");
        var callerPn = payload.getAttributeAsJid("caller_pn", null);
        var groupJid = payload.getAttributeAsJid("group-jid", null);
        var callerPushName = payload.getAttributeAsString("notify", null);

        if (senderLid != null) {
            attemptPersistLidMappingAndUserAttributes(senderLid, from, null);
        }

        persistAttributesAndLidMappingsForCall(callCreator, callerPn, callerPushName, payload, offline);

        switch (payload.description()) {
            case "offer" -> handleOffer(node, payload, from, callId, callCreator, groupJid, offline);
            case "accept" -> {
                sendCallReceipt(node, from, callId, callCreator, "accept");
                // Record the answering device JID before draining the deferred media-plane bring-up:
                // the peer keys its end-to-end media (Family B) with this device JID, so the inbound
                // SRTP master must be derived from it rather than the relay roster's bare-user peer_pid.
                var acceptRuntime = engine.find(callId);
                if (acceptRuntime != null) {
                    acceptRuntime.setPeerDeviceJid(from);
                    // The accept stanza carries the finalized relay block whose edgeray credentials the
                    // relay actually honors, unlike the offer-ACK block allocated before the callee
                    // answered; store it so the caller's web ICE leg keys from it.
                    payload.getChild("relay")
                            .flatMap(CallRelay::parse)
                            .ifPresent(acceptRuntime::setAcceptRelay);
                }
                engine.onPeerAccept(callId);
            }
            case "reject" -> {
                sendCallReceipt(node, from, callId, callCreator, "reject");
                // A reject from one peer device does not end an outgoing call that has not yet been
                // accepted: a multi-device peer rings every device, and one device declining (for
                // example the phone reporting "busy" while the web companion is the intended answerer)
                // must not collapse the call before another device answers. The call still ends on a
                // canonical terminate or its own ring timeout. Only end immediately when the local side
                // is the callee, or the call was already accepted (a post-accept reject is terminal).
                var rejectRuntime = engine.find(callId);
                var keepRinging = rejectRuntime != null
                        && rejectRuntime.call().isOutgoing()
                        && !rejectRuntime.peerAccepted();
                if (!keepRinging) {
                    engine.onPeerReject(callId, "reject");
                    whatsapp.store().chatStore().removeCall(callId);
                    notifyEnded(callId, from, "reject");
                }
            }
            case "preaccept" -> {
                sendCallAck(node, payload.description());
                // The preaccept is the first signal from the answering device; capture its device JID
                // now so the media plane keys the inbound Family-B SRTP master from it even if the
                // bring-up runs before the accept arrives.
                var preacceptRuntime = engine.find(callId);
                if (preacceptRuntime != null) {
                    preacceptRuntime.setPeerDeviceJid(from);
                }
                notifyPreaccept(callId, from);
            }
            case "enc_rekey" -> {
                sendCallReceipt(node, from, callId, callCreator, "enc_rekey");
                handleEncRekey(callId, payload, from);
            }
            case "terminate" -> {
                var reason = payload.getAttributeAsString("reason", null);
                engine.onPeerTerminate(callId, reason);
                whatsapp.store().chatStore().removeCall(callId);
                sendCallAck(node, payload.description());
                notifyEnded(callId, from, reason);
            }
            case "mute_v2" -> {
                // Wire shape verified against captured fixtures: a self mute announcement carries
                // `mute-state` ("1" muted, "0" unmuted); a peer-mute request from another participant
                // carries `request-state` instead (the voip engine accepts either on the mute_v2
                // element). The request surfaces as a PeerMuteRequest interaction, the announcement as
                // a mute-changed event.
                sendCallAck(node, payload.description());
                var requestState = payload.getAttributeAsString("request-state", null);
                if (requestState != null) {
                    notifyInteraction(callId, from,
                            new CallInteraction.PeerMuteRequest(from.toString(), Optional.empty()));
                } else {
                    var muteState = payload.getAttributeAsString("mute-state", null);
                    notifyMute(callId, from, "1".equals(muteState));
                }
            }
            case "user_action" -> {
                // Captured live: a server-relayed group-call user action. The `action` attribute names
                // the action; a raise_hand action wraps a <raise_hand raise-hand-state="0|1"/> child.
                sendCallAck(node, payload.description());
                var action = payload.getAttributeAsString("action", null);
                if ("raise_hand".equals(action)) {
                    var raised = payload.getChild("raise_hand")
                            .map(child -> "1".equals(child.getAttributeAsString("raise-hand-state", null)))
                            .orElse(false);
                    notifyInteraction(callId, from,
                            raised ? new CallInteraction.RaiseHand() : new CallInteraction.LowerHand());
                }
            }
            case "video", "video_state" -> {
                // The live in-call video toggle is a <video state="N"> stanza (NOT <video_state state="on|off">):
                // state 0 = off, 11 = on/upgrade, 4 = active-with-resolution (screenshare/hi-res), 6 = intermediate.
                // Treat any non-zero numeric state as video-enabled; stay robust to a missing or non-numeric state.
                sendCallAck(node, payload.description());
                notifyVideoState(callId, from, isVideoEnabledState(payload));
            }
            case "peer_state" -> {
                sendCallAck(node, payload.description());
                var state = payload.getAttributeAsString("state", null);
                notifyPeerState(callId, from, state);
            }
            case "group_update" -> {
                sendCallAck(node, payload.description());
                // A relay-less group offer (native desktop caller) gets its <relay> block here, in
                // the group_update that confirms the join, rather than inline in the offer. Feed it to
                // the engine so the callee allocates the relay and starts hop-by-hop SRTP.
                payload.getChild("relay")
                        .flatMap(CallRelay::parse)
                        .ifPresent(relay -> engine.onGroupRelay(callId, relay));
                var action = payload.getAttributeAsString("action", null);
                var participants = payload.getChild("group_info")
                        .map(info -> info.children().stream()
                                .map(c -> c.getAttributeAsJid("jid", null))
                                .filter(Objects::nonNull)
                                .toList())
                        .orElse(List.of());
                if (!participants.isEmpty()) {
                    var isAdd = "add".equals(action);
                    notifyParticipantsChanged(callId, groupJid, participants, isAdd);
                    var runtime = engine.find(callId);
                    if (runtime != null && runtime.call().isGroup()) {
                        runtime.groupSession().ifPresent(session -> {
                            if (isAdd) {
                                participants.forEach(session::notePendingJoin);
                            } else {
                                participants.forEach(session::removeParticipant);
                            }
                        });
                    }
                }
            }
            case "offer_notice" -> {
                sendCallAck(node, payload.description());
                var missed = buildIncomingCall(node, callId, payload, callCreator, groupJid, offline);
                if (missed != null) {
                    notifyOfferNotice(missed);
                }
            }
            case "transport" -> {
                // A group call's <transport transport-message-type="4"><net.../></transport> carries no
                // candidates; the captured real callee ECHOES it back to <callId>@call as part of the SFU
                // media-path negotiation, so mirror that to engage the participant's transport. The 1:1
                // direct peer-to-peer leg is not implemented (the reverse-engineered path was inaccurate),
                // so a 1:1 <transport> candidate round is acknowledged but otherwise ignored.
                sendCallAck(node, payload.description());
                var transportCall = engine.find(callId);
                if (transportCall != null && transportCall.call().isGroup()) {
                    var callTarget = Jid.of(callId + "@call");
                    Thread.ofVirtual().name("transport-echo-" + callId).start(() -> {
                        try {
                            whatsapp.sendNode(new NodeBuilder()
                                    .description("call")
                                    .attribute("to", callTarget)
                                    .content(payload));
                        } catch (RuntimeException ignored) {
                            // best-effort handshake echo
                        }
                    });
                }
            }
            case "relaylatency" -> {
                sendCallAck(node, payload.description());
                handleRelayLatency(callId, payload, from);
            }
            case "relayelection", "video_state_ack", "accept_ack",
                 "accept_receipt", "offer_receipt", "offer_ack", "offer_nack", "interruption",
                 "notify", "flow_control", "web_client", "group_info" ->
                    sendCallAck(node, payload.description());
            default -> sendCallAck(node, payload.description());
        }
    }

    /**
     * Forwards the inbound {@code <enc_rekey>} stanza's Signal-encrypted payload to the engine.
     *
     * <p>Extracts the single {@code <enc>} child carrying the rekey envelope (sender LID and the
     * Signal {@code type}/ciphertext) and dispatches to
     * {@link CallService#onEncRekey(String, Jid, com.github.auties00.cobalt.message.MessageEncryptionType, byte[])}.
     * The engine owns the decryption and the {@link com.github.auties00.cobalt.model.call.datachannel.E2eRekeyPayload}
     * parse. Malformed payloads (missing {@code <enc>}, missing {@code type}, missing content) are
     * dropped without dispatching.
     *
     * @param callId  the call identifier
     * @param payload the {@code <enc_rekey>} payload node
     * @param from    the sender JID from the outer call stanza
     */
    private void handleEncRekey(String callId, Node payload, Jid from) {
        var enc = payload.getChild("enc").orElse(null);
        if (enc == null) {
            return;
        }
        var typeAttr = enc.getAttributeAsString("type", null);
        if (typeAttr == null) {
            return;
        }
        com.github.auties00.cobalt.message.MessageEncryptionType encType;
        try {
            encType = com.github.auties00.cobalt.message.MessageEncryptionType.fromProtocolValue(typeAttr);
        } catch (IllegalArgumentException _) {
            return;
        }
        var ciphertext = enc.toContentBytes().orElse(null);
        if (ciphertext == null) {
            return;
        }
        engine.onEncRekey(callId, from, encType, ciphertext);
    }

    /**
     * Records one round of relay-latency probe samples on the call's transport.
     *
     * <p>Each {@code <te>} child of {@code <relaylatency>} carries the WhatsApp Web GraphQL endpoint identifier
     * ({@code relay_name}) and the measured round-trip time ({@code latency}). The samples are
     * folded into the call's {@link com.github.auties00.cobalt.call.transport.ActiveCallTransport#recordRelayLatency(String, long, java.net.InetSocketAddress)}
     * so a future relay re-election can prefer the lower-RTT candidate.
     *
     * @param callId  the call identifier
     * @param payload the {@code <relaylatency>} payload node
     * @param from    the peer device JID the probe arrived from, used to address the reciprocal probe
     */
    private void handleRelayLatency(String callId, Node payload, Jid from) {
        var call = engine.find(callId);
        if (call == null) {
            return;
        }
        var transport = call.transport();
        for (var te : payload.streamChildren("te").toList()) {
            var relayName = te.getAttributeAsString("relay_name", null);
            var latency = te.getAttributeAsLong("latency", (Long) null);
            if (relayName != null && latency != null) {
                transport.recordRelayLatency(relayName, latency, decodeTeAddress(te));
            }
        }
        if (call.call().isGroup()) {
            // Group: echo the probe to <callId>@call, the SFU routing object a participant joins
            // through, so the server finalises the participant's relay path.
            var callTarget = Jid.of(callId + "@call");
            Thread.ofVirtual().name("relaylatency-echo-" + callId).start(() -> {
                try {
                    whatsapp.sendNode(new NodeBuilder()
                            .description("call")
                            .attribute("to", callTarget)
                            .content(payload));
                } catch (RuntimeException ignored) {
                    // best-effort; a closed socket or unmatched ack must not break latency recording
                }
            });
            return;
        }
        // One-to-one: reciprocate the peer's relay-latency probe so the peer's relay election learns our
        // relay candidates. Without it the peer reports "no tx relays available" and never receives our
        // media, ending the call as ACCEPTED_BUT_NOT_CONNECTED at its ~20 s media-setup timeout. A live
        // WA Web caller capture shows each relay advertised to the bare peer LID with a <destination>
        // enumerating every peer device; this echoes the probe's <te> entries with that fanout.
        if (from == null) {
            return;
        }
        var teEchoes = payload.streamChildren("te").toList();
        if (teEchoes.isEmpty()) {
            return;
        }
        var peerBare = from.toUserJid();
        // The call-creator must be our own LID device JID (e.g. 39110693621863:46@lid), exactly as the
        // server addresses it on the inbound probe; sending the phone-number JID instead has the server
        // reject the reciprocal relay-latency with error 439. The inbound payload already carries that
        // LID value, so echo it rather than the runtime's phone-number creator.
        var creator = payload.getAttributeAsJid("call-creator").orElseGet(() -> call.call().creator());
        var peerDevices = whatsapp.store().contactStore().findDeviceList(peerBare)
                .map(list -> list.devices().stream()
                        .map(device -> list.userJid().withDevice(device.id()))
                        .toList())
                .filter(devices -> !devices.isEmpty())
                .orElseGet(() -> List.of(peerBare.withDevice(0)));
        var toNodes = new ArrayList<Node>(peerDevices.size());
        for (var device : peerDevices) {
            toNodes.add(new NodeBuilder()
                    .description("to")
                    .attribute("jid", device)
                    .build());
        }
        var relayLatencyContent = new ArrayList<Node>(teEchoes);
        relayLatencyContent.add(new NodeBuilder()
                .description("destination")
                .content(toNodes)
                .build());
        var relayLatency = new NodeBuilder()
                .description("relaylatency")
                .attribute("call-id", callId)
                .attribute("call-creator", creator)
                .content(relayLatencyContent)
                .build();
        Thread.ofVirtual().name("relaylatency-reciprocate-" + callId).start(() -> {
            try {
                whatsapp.sendNode(new NodeBuilder()
                        .description("call")
                        .attribute("to", peerBare)
                        .content(relayLatency));
            } catch (RuntimeException ignored) {
                // best-effort; a closed socket or unmatched ack must not break latency recording
            }
        });
    }

    /**
     * Decodes the regular-relay transport address from a {@code <relaylatency><te>} node's content.
     *
     * <p>The content is the relay address followed by a big-endian 2-byte port: a 6-byte payload is
     * {@code 4 bytes IPv4 + 2 bytes port}; an 18-byte payload is {@code 16 bytes IPv6 + 2 bytes port}.
     * This is the raw-UDP relay address the media plane allocates against, distinct from the offer-ACK
     * {@code <te2>} edgeray address.
     *
     * @param te the {@code <te>} node
     * @return the decoded relay address, or {@code null} when the content is missing or malformed
     */
    private static java.net.InetSocketAddress decodeTeAddress(Node te) {
        var content = te.streamContentBytes().findFirst().orElse(null);
        if (content == null || (content.length != 6 && content.length != 18)) {
            return null;
        }
        var addressLen = content.length == 6 ? 4 : 16;
        var addressBytes = new byte[addressLen];
        System.arraycopy(content, 0, addressBytes, 0, addressLen);
        var port = ((content[addressLen] & 0xFF) << 8) | (content[addressLen + 1] & 0xFF);
        try {
            return new java.net.InetSocketAddress(java.net.InetAddress.getByAddress(addressBytes), port);
        } catch (java.net.UnknownHostException e) {
            return null;
        }
    }

    /**
     * Handles an inbound call offer payload.
     *
     * <p>Sends a receipt back to the server, then, only for genuinely inbound offers where the local
     * user is not the call creator, builds the {@link IncomingCall} handle, registers it in the store,
     * sends a ringing stanza, and notifies listeners. Offers whose creator is the local user, and
     * offers whose metadata is insufficient to build a call, are not surfaced to listeners.
     *
     * @param node        the call stanza node
     * @param payload     the {@code offer} child node
     * @param from        the {@link Jid} of the call sender
     * @param callId      the unique call identifier
     * @param callCreator the {@link Jid} of the call initiator
     * @param groupJid    the group {@link Jid} for group calls, or {@code null} for one-to-one calls
     * @param offline     whether the stanza was received while offline
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleVoipCall", exports = "handleCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleOffer(Node node, Node payload, Jid from, String callId, Jid callCreator, Jid groupJid, boolean offline) {
        sendCallReceipt(node, from, callId, callCreator, "offer");

        var self = whatsapp.store().accountStore().jid().orElse(null);
        var outgoing = self != null && callCreator.toUserJid().equals(self.toUserJid());
        if (outgoing) {
            return;
        }

        var call = buildIncomingCall(node, callId, payload, callCreator, groupJid, offline);
        if (call == null) {
            return;
        }

        // Parse the relay block and decrypt the caller's 32-byte call key before fanning the offer out
        // to listeners, then stash both with the engine so a later accept() brings up the media plane.
        // The slim IncomingCall model cannot carry the lib relay type, and the call key arrives
        // Signal-encrypted in the offer's per-device <enc> fanout. A failed call-key decrypt leaves the
        // call key null (the bring-up then keys SRTCP from the hop-by-hop key only); it never throws.
        var relay = CallRelay.parseOffer(payload).orElse(null);
        var callKey = decryptOfferCallKey(payload, from);
        engine.noteOfferCredentials(callId, relay, callKey);

        whatsapp.store().chatStore().addCall(call);
        whatsapp.sendNodeWithNoResponse(CallStanza.ringing(callCreator, callId).build());
        notifyCall(call);
    }

    /**
     * Decrypts the caller's 32-byte per-call shared key from an inbound offer's per-device {@code <enc>}
     * fanout.
     *
     * <p>The offer carries the call key Signal-encrypted to each recipient device inside
     * {@snippet lang="xml" :
     * <offer ...>
     *   <destination>
     *     <to jid="<self-device>"><enc v="2" type="msg|pkmsg">[ciphertext]</enc></to>
     *     ...
     *   </destination>
     * </offer>
     * }
     * The plaintext of the self-addressed {@code <enc>} is a
     * {@link com.github.auties00.cobalt.model.message.MessageContainer} whose
     * {@link com.github.auties00.cobalt.model.message.MessageContainer#content() content} is a
     * {@link CallOfferMessage} carrying the key, mirroring the inverse build in
     * {@code LiveMessageService.sendCall}. The Signal decryption runs through
     * {@link MessageService#processCall(Jid, MessageEncryptionType, byte[])}, the same path the
     * {@code <enc_rekey>} envelope uses.
     *
     * <p>The {@code <enc>} addressed to this device is selected by matching the {@code <to jid>} against
     * the local device JID, falling back to the first {@code type="msg"} (or any) {@code <enc>} when no
     * exact device match is found. Returns {@code null} without throwing when the {@code <destination>}
     * block, a usable {@code <enc>}, its {@code type}, or its ciphertext is missing, or when decryption
     * or decoding fails; the caller treats a {@code null} key as "no Family-B key yet".
     *
     * @param payload the {@code <offer>} payload node
     * @param from    the offer sender JID from the outer call stanza, used as the decryption sender when
     *                the selected {@code <enc>} carries no own sender address
     * @return the decrypted 32-byte call key, or {@code null} when it could not be recovered
     */
    private byte[] decryptOfferCallKey(Node payload, Jid from) {
        // The caller's OUTGOING offer wraps each recipient device's call-key <enc> in
        // <destination><to jid="..."><enc>; the server delivers the fanned-out copy to a CALLEE device
        // with that device's <enc> DIRECTLY under <offer> (no <destination>). Handle both shapes.
        var destination = payload.getChild("destination").orElse(null);
        Node enc;
        if (destination == null) {
            enc = payload.getChild("enc").orElse(null);
        } else {
            // Match the self device's <to> in whichever addressing mode (PN or LID) the caller used,
            // falling back to the first type="msg" <enc> (or any <enc>) for a single-device callee.
            var self = whatsapp.store().accountStore().jid().orElse(null);
            var selfLid = self != null && self.hasDevice()
                    ? whatsapp.store().accountStore().lid().map(lid -> lid.toUserJid().withDevice(self.device())).orElse(null)
                    : whatsapp.store().accountStore().lid().orElse(null);
            Node selfEnc = null;
            Node fallbackEnc = null;
            for (var to : destination.streamChildren("to").toList()) {
                var e = to.getChild("enc").orElse(null);
                if (e == null) {
                    continue;
                }
                if (fallbackEnc == null || "msg".equals(e.getAttributeAsString("type", null))) {
                    fallbackEnc = e;
                }
                var toJid = to.getAttributeAsJid("jid", null);
                if (toJid != null && (toJid.equals(self) || toJid.equals(selfLid))) {
                    selfEnc = e;
                    break;
                }
            }
            enc = selfEnc != null ? selfEnc : fallbackEnc;
        }
        if (enc == null) {
            return null;
        }
        var typeAttr = enc.getAttributeAsString("type", null);
        if (typeAttr == null) {
            return null;
        }
        MessageEncryptionType encType;
        try {
            encType = MessageEncryptionType.fromProtocolValue(typeAttr);
        } catch (IllegalArgumentException _) {
            return null;
        }
        var ciphertext = enc.toContentBytes().orElse(null);
        if (ciphertext == null) {
            return null;
        }
        try {
            var plaintext = messageService.processCall(from, encType, ciphertext);
            var container = MessageContainerSpec.decode(plaintext);
            var key = container.content() instanceof CallOfferMessage offer
                    ? offer.callKey().orElse(null)
                    : null;
            return key;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Constructs an {@link IncomingCall} from a parsed call stanza.
     *
     * <p>Resolves the chat JID from {@code groupJid} for group calls or from the canonical user JID of
     * the creator otherwise, returning {@code null} when no chat JID can be resolved. The result is a
     * pure-data offer carrying only the protocol metadata; the relay block and call key the callee
     * needs to bring up its media plane on accept are parsed and stashed separately in
     * {@link #handleOffer(Node, Node, Jid, String, Jid, Jid, boolean)} through
     * {@link CallService#noteOfferCredentials(String, CallRelay, byte[])}.
     *
     * @param node        the call stanza node, used for timestamp resolution
     * @param callId      the unique call identifier
     * @param payload     the payload child node
     * @param callCreator the {@link Jid} of the call initiator
     * @param groupJid    the group JID for group calls, or {@code null}
     * @param offline     whether the stanza was received while offline
     * @return the {@link IncomingCall}, or {@code null} when the chat JID cannot be resolved
     */
    private IncomingCall buildIncomingCall(Node node, String callId, Node payload,
                                           Jid callCreator, Jid groupJid, boolean offline) {
        var chatJid = groupJid != null ? groupJid : canonicalUserJid(callCreator);
        if (chatJid == null) {
            return null;
        }
        return new IncomingCall(
                callId,
                callCreator,
                chatJid,
                resolveTimestamp(node),
                payload.hasChild("video"),
                groupJid != null,
                groupJid,
                offline
        );
    }

    /**
     * Resolves the call timestamp from the stanza's {@code t} attribute.
     *
     * <p>Returns {@link Instant#EPOCH} when the attribute is absent, matching the WhatsApp Web parser,
     * which defaults a missing {@code t} to the Unix epoch.
     *
     * @param node the call stanza node
     * @return the resolved timestamp, never {@code null}
     */
    private Instant resolveTimestamp(Node node) {
        var rawTimestamp = node.getAttributeAsLong("t", (Long) null);
        return rawTimestamp != null ? Instant.ofEpochSecond(rawTimestamp) : Instant.EPOCH;
    }

    /**
     * Resolves whether an inbound {@code <video>} payload announces video as enabled.
     *
     * <p>The live in-call video toggle carries a numeric {@code state}: {@code 0} is off, and every other value
     * ({@code 11} on/upgrade, {@code 4} active-with-resolution, {@code 6} intermediate) is a video-active state. This
     * returns {@code true} for any non-zero numeric {@code state}. A {@code <video>} payload whose {@code state} is
     * absent or non-numeric is treated as enabled, because a bare {@code <video>} child marks a video stream.
     *
     * @param payload the {@code <video>} payload node
     * @return {@code true} when video is enabled, {@code false} only for an explicit {@code state="0"}
     */
    private static boolean isVideoEnabledState(Node payload) {
        OptionalInt state = payload.getAttributeAsInt("state");
        return state.isEmpty() || state.getAsInt() != 0;
    }

    /**
     * Persists the call payload's caller attributes and any LID-to-phone mappings the stanza carries.
     *
     * <p>Forwards the caller attributes ({@code call-creator}, {@code caller_pn}, and the push name
     * from {@code notify}) to
     * {@link #attemptPersistLidMappingAndUserAttributes(Jid, Jid, String)}, then iterates every
     * participant in the optional {@code group_info} child, forwarding each participant's {@code jid}
     * and {@code user_pn} the same way. Participants carry no push name, so {@code null} is forwarded
     * for theirs.
     *
     * @param callCreator    the caller {@link Jid} from {@code call-creator}
     * @param callerPn       the caller phone-number {@link Jid} from {@code caller_pn}, or {@code null}
     * @param callerPushName the caller push name from {@code notify}, or {@code null}
     * @param payload        the call payload node, which may contain a {@code group_info} child
     * @param offline        whether the call was received while offline
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipLidUtils", exports = "persistAttributesAndLidMappingsForCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void persistAttributesAndLidMappingsForCall(Jid callCreator, Jid callerPn, String callerPushName, Node payload, boolean offline) {
        attemptPersistLidMappingAndUserAttributes(callCreator, callerPn, callerPushName);

        var groupInfo = payload.getChild("group_info").orElse(null);
        if (groupInfo == null) {
            return;
        }
        for (var participant : groupInfo.children()) {
            var participantJid = participant.getAttributeAsJid("jid", null);
            if (participantJid == null) {
                continue;
            }
            var participantPn = participant.getAttributeAsJid("user_pn", null);
            attemptPersistLidMappingAndUserAttributes(participantJid, participantPn, null);
        }
    }

    /**
     * Persists a single LID-to-phone-number mapping and updates the associated contact's push name.
     *
     * <p>When {@code pushName} is non-{@code null} and non-blank, the contact's chosen name is updated
     * through {@link #updatePushname(Jid, String)}. When {@code jid} resolves to a LID user and
     * {@code phoneNumber} is non-{@code null}, the LID-to-phone-number mapping is registered in the
     * store via {@link com.github.auties00.cobalt.store.ContactStore#registerLidMapping(Jid, Jid)}. A
     * {@code null} {@code jid} is a no-op.
     *
     * @implNote This implementation omits the WhatsApp Web username and country-code flow and the
     * privacy-enforcement fast path that can skip writing the mapping, because Cobalt implements no
     * username runtime and no calling surface reads those fields; the mapping is always written when
     * the JID is a LID and a phone number is present.
     *
     * @param jid         the candidate JID, which may be a LID; a mapping is registered only when this
     *                    resolves to a LID user
     * @param phoneNumber the phone-number {@link Jid}, or {@code null}
     * @param pushName    the push name from the stanza's {@code notify} attribute, or {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipLidUtils", exports = "attemptPersistLidMappingAndUserAttributes",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void attemptPersistLidMappingAndUserAttributes(Jid jid, Jid phoneNumber, String pushName) {
        if (jid == null) {
            return;
        }

        var userJid = jid.toUserJid();

        if (pushName != null && !pushName.isBlank()) {
            updatePushname(userJid, pushName);
        }

        if (!userJid.hasLidServer() || phoneNumber == null) {
            return;
        }
        whatsapp.store().contactStore().registerLidMapping(phoneNumber.toUserJid(), userJid);
    }

    /**
     * Updates the push (chosen) name of a contact in the local store.
     *
     * <p>Creates a new contact record when none exists for {@code contactJid}, then assigns the push
     * name. The caller is expected to have already validated that {@code pushName} is non-blank.
     *
     * @param contactJid the non-{@code null} JID of the contact to update
     * @param pushName   the push name to store, already validated as non-blank by the caller
     */
    @WhatsAppWebExport(moduleName = "WAWebHandlePushnameUpdate", exports = "updatePushname",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void updatePushname(Jid contactJid, String pushName) {
        var contact = whatsapp.store().contactStore().findContactByJid(contactJid)
                .orElseGet(() -> whatsapp.store().contactStore().addNewContact(contactJid.toUserJid()));
        contact.setChosenName(pushName);
        whatsapp.store().contactStore().addContact(contact);
    }

    /**
     * Sends a receipt stanza acknowledging a call signaling message.
     *
     * <p>The receipt wraps a child whose tag matches the signaling type ({@code offer}, {@code accept},
     * {@code reject}, or {@code enc_rekey}) and carries the {@code call-id} and {@code call-creator}
     * attributes. Its {@code from} attribute is resolved by {@link #resolveReceiptFrom(Jid)} to the
     * local LID user or the local phone-number user depending on the peer address. The receipt is not
     * sent when the local {@code from} JID or the inbound stanza identifier cannot be resolved.
     *
     * @param node        the original call stanza node
     * @param to          the {@link Jid} to send the receipt to
     * @param callId      the unique call identifier
     * @param callCreator the {@link Jid} of the call initiator
     * @param childTag    the receipt child tag, for example {@code "offer"}
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleVoipCall", exports = "handleCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void sendCallReceipt(Node node, Jid to, String callId, Jid callCreator, String childTag) {
        var from = resolveReceiptFrom(to);
        var stanzaId = node.getAttributeAsString("id", null);
        if (from == null || stanzaId == null) {
            return;
        }

        var child = new NodeBuilder()
                .description(childTag)
                .attribute("call-id", callId)
                .attribute("call-creator", callCreator)
                .build();
        var receipt = new NodeBuilder()
                .description("receipt")
                .attribute("to", to)
                .attribute("from", from)
                .attribute("id", stanzaId)
                .content(child)
                .build();
        whatsapp.sendNodeWithNoResponse(receipt);
    }

    /**
     * Sends an acknowledgement stanza for a call signaling message.
     *
     * <p>Used for payload tags that do not warrant a full receipt, such as {@code terminate} and
     * unrecognized tags. The acknowledgement carries {@code class="call"} and a {@code type} attribute
     * matching the payload tag.
     *
     * @param node the original call stanza node
     * @param type the payload tag to echo as the acknowledgement type
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleVoipCall", exports = "handleCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void sendCallAck(Node node, String type) {
        ackSender.ack(AckClass.CALL, node).type(type).send();
    }

    /**
     * Resolves the {@code from} {@link Jid} to use on outgoing receipt stanzas.
     *
     * <p>Returns the local LID user JID when the remote peer uses the LID server, and the local
     * phone-number user JID otherwise. Returns {@code null} when the local JID is not available.
     *
     * @implNote This implementation returns the stored LID with its device suffix preserved for LID
     * peers, falling back to the phone-number user JID only when no LID has ever been stored.
     *
     * @param remote the remote peer {@link Jid}
     * @return the local {@link Jid} to use as the {@code from} attribute, or {@code null} when the
     *         local JID is unavailable
     */
    private Jid resolveReceiptFrom(Jid remote) {
        var self = whatsapp.store().accountStore().jid().orElse(null);
        if (self == null) {
            return null;
        }

        if (remote.hasLidServer()) {
            return whatsapp.store().accountStore().lid().orElse(self.toUserJid());
        }

        return self.toUserJid();
    }

    /**
     * Resolves the canonical user JID for a given JID.
     *
     * <p>When the JID uses the LID server, the corresponding phone-number JID is looked up through the
     * store's LID-to-phone mapping, falling back to the LID user JID when no mapping exists. Otherwise
     * the device-stripped user JID is returned directly.
     *
     * @param jid the {@link Jid} to resolve
     * @return the canonical user JID, or {@code null} when {@code jid} is {@code null}
     */
    private Jid canonicalUserJid(Jid jid) {
        if (jid == null) {
            return null;
        }

        var userJid = jid.toUserJid();
        if (!userJid.hasLidServer()) {
            return userJid;
        }

        return whatsapp.store().contactStore().findPhoneByLid(userJid).orElse(userJid);
    }

    /**
     * Notifies every registered listener of an inbound call.
     *
     * <p>Each listener is invoked on its own virtual thread so listener execution does not block the
     * socket stream handler thread.
     *
     * @param call the {@link IncomingCall} to notify listeners about
     */
    private void notifyCall(IncomingCall call) {
        for (var listener : whatsapp.store().listeners()) {
            if (listener instanceof LinkedCallListener typed) {
                Thread.startVirtualThread(() -> typed.onCall(whatsapp, call));
            }
        }
    }

    /**
     * Notifies every registered listener that the call has terminated.
     *
     * <p>The wire reason literal is parsed into a typed {@link CallEndReason} via
     * {@link CallEndReason#fromWireValue(String)}; unrecognized literals surface as
     * {@link CallEndReason#UNKNOWN}. Each listener is invoked on its own virtual thread.
     *
     * @param callId     the identifier of the call that ended
     * @param fromJid    the JID of the party that ended the call
     * @param wireReason the wire-level reason literal, or {@code null}
     */
    private void notifyEnded(String callId, Jid fromJid, String wireReason) {
        var parsed = CallEndReason.fromWireValue(wireReason);
        for (var listener : whatsapp.store().listeners()) {
            if (listener instanceof LinkedCallEndedListener typed) {
                Thread.startVirtualThread(() -> typed.onCallEnded(whatsapp, callId, fromJid, parsed));
            }
        }
    }

    /**
     * Notifies every registered listener of a pre-accept signal.
     *
     * <p>Each listener is invoked on its own virtual thread.
     *
     * @param callId  the call identifier
     * @param fromJid the peer that sent the pre-accept
     */
    private void notifyPreaccept(String callId, Jid fromJid) {
        for (var listener : whatsapp.store().listeners()) {
            if (listener instanceof LinkedCallPreacceptListener typed) {
                Thread.startVirtualThread(() -> typed.onCallPreaccept(whatsapp, callId, fromJid));
            }
        }
    }

    /**
     * Notifies every registered listener of a microphone-mute change.
     *
     * <p>Each listener is invoked on its own virtual thread.
     *
     * @param callId  the call identifier
     * @param fromJid the participant that toggled mute
     * @param muted   the new mute state
     */
    private void notifyMute(String callId, Jid fromJid, boolean muted) {
        for (var listener : whatsapp.store().listeners()) {
            if (listener instanceof LinkedCallMuteChangedListener typed) {
                Thread.startVirtualThread(() -> typed.onCallMuteChanged(whatsapp, callId, fromJid, muted));
            }
        }
    }

    /**
     * Notifies every registered listener of an inbound in-call interaction.
     *
     * <p>Surfaces a raise-hand, lower-hand, or peer-mute request another participant sent, parsed from
     * its server-relayed signaling stanza. Each listener is invoked on its own virtual thread.
     *
     * @param callId      the call identifier
     * @param fromJid     the participant that performed the interaction
     * @param interaction the parsed interaction
     */
    private void notifyInteraction(String callId, Jid fromJid, CallInteraction interaction) {
        for (var listener : whatsapp.store().listeners()) {
            if (listener instanceof LinkedCallInteractionListener typed) {
                Thread.startVirtualThread(() -> typed.onCallInteraction(whatsapp, callId, fromJid, interaction));
            }
        }
    }

    /**
     * Notifies every registered listener of a video on/off change.
     *
     * <p>Each listener is invoked on its own virtual thread.
     *
     * @param callId  the call identifier
     * @param fromJid the participant that toggled video
     * @param enabled the new video state
     */
    private void notifyVideoState(String callId, Jid fromJid, boolean enabled) {
        for (var listener : whatsapp.store().listeners()) {
            if (listener instanceof LinkedCallVideoStateChangedListener typed) {
                Thread.startVirtualThread(() -> typed.onCallVideoStateChanged(whatsapp, callId, fromJid, enabled));
            }
        }
    }

    /**
     * Notifies every registered listener of a peer-state update.
     *
     * <p>The wire state literal is parsed into a typed {@link CallPeerState} via
     * {@link CallPeerState#fromWireValue(String)}; unrecognized literals surface as
     * {@link CallPeerState#UNKNOWN}. Each listener is invoked on its own virtual thread.
     *
     * @param callId    the call identifier
     * @param fromJid   the peer whose state changed
     * @param wireState the wire-level state literal
     */
    private void notifyPeerState(String callId, Jid fromJid, String wireState) {
        var parsed = CallPeerState.fromWireValue(wireState);
        for (var listener : whatsapp.store().listeners()) {
            if (listener instanceof LinkedCallPeerStateChangedListener typed) {
                Thread.startVirtualThread(() -> typed.onCallPeerStateChanged(whatsapp, callId, fromJid, parsed));
            }
        }
    }

    /**
     * Notifies every registered listener that group-call participants were added or removed.
     *
     * <p>Each listener is invoked on its own virtual thread.
     *
     * @param callId       the call identifier
     * @param groupJid     the group JID that owns the call, or {@code null} when the stanza carried
     *                     none
     * @param participants the JIDs that were added or removed
     * @param added        {@code true} for an addition, {@code false} for a removal
     */
    private void notifyParticipantsChanged(String callId, Jid groupJid, List<Jid> participants, boolean added) {
        for (var listener : whatsapp.store().listeners()) {
            if (listener instanceof LinkedCallParticipantsChangedListener typed) {
                Thread.startVirtualThread(() -> typed.onCallParticipantsChanged(whatsapp, callId, groupJid, participants, added));
            }
        }
    }

    /**
     * Notifies every registered listener of an offer notice for a call missed while offline.
     *
     * <p>Each listener is invoked on its own virtual thread.
     *
     * @param call the call descriptor parsed from the notice
     */
    private void notifyOfferNotice(IncomingCall call) {
        for (var listener : whatsapp.store().listeners()) {
            if (listener instanceof LinkedCallOfferNoticeListener typed) {
                Thread.startVirtualThread(() -> typed.onCallOfferNotice(whatsapp, call));
            }
        }
    }
}
