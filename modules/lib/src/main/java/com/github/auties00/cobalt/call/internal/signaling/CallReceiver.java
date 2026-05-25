package com.github.auties00.cobalt.call.internal.signaling;

import com.github.auties00.cobalt.ack.AckClass;
import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.call.ActiveCall;
import com.github.auties00.cobalt.call.internal.CallService;
import com.github.auties00.cobalt.call.IncomingCall;
import com.github.auties00.cobalt.call.internal.transport.OfferTransportSpec;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import com.github.auties00.cobalt.call.CallEndReason;

/**
 * Handles inbound VoIP call stanzas received from the WhatsApp server.
 *
 * <p>This handler processes call signaling stanzas (tag {@code "call"}) arriving on the socket stream.
 * It parses each stanza into its constituent fields (peer {@link Jid}, call creator, call identifier,
 * timestamp, and similar), persists the LID-to-phone-number mappings and caller push names the stanza
 * carries, updates the local call model in the
 * {@link com.github.auties00.cobalt.store.WhatsAppStore}, and sends the receipt or acknowledgement the
 * payload type requires back to the server. The supported payload tags are {@code offer},
 * {@code accept}, {@code reject}, {@code preaccept}, {@code enc_rekey}, {@code terminate},
 * {@code mute_v2}, {@code video_state}, {@code peer_state}, {@code group_update}, {@code offer_notice},
 * and a set of media-plane tags that are acknowledged and otherwise ignored; any other tag falls
 * through to a plain acknowledgement.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleVoipCall")
@WhatsAppWebModule(moduleName = "WAWebVoipLidUtils")
public final class CallReceiver implements SocketStream.Handler {
    /**
     * Logs parse errors and signaling traces for malformed or ignored stanzas.
     */
    private static final System.Logger LOGGER = System.getLogger(CallReceiver.class.getName());

    /**
     * Holds the WhatsApp client used to send stanzas and access the store.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Holds the call engine that dispatches peer-side accept, reject, and terminate transitions to the
     * matching {@link ActiveCall}.
     */
    private final CallService engine;

    /**
     * Holds the {@link AckSender} used to emit the {@code <ack class="call" type="...">} stanza that
     * echoes the parsed VoIP payload tag back to the server.
     */
    private final AckSender ackSender;

    /**
     * Constructs a call receiver bound to its client, call engine, and acknowledgement sender.
     *
     * @param whatsapp  the WhatsApp client used to send stanzas and access the store
     * @param engine    the call engine the receiver routes peer-side events to
     * @param ackSender the {@link AckSender} used to emit the outbound {@code <ack class="call">}
     *                  stanza
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleVoipCall", exports = "handleCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public CallReceiver(WhatsAppClient whatsapp, CallService engine, AckSender ackSender) {
        this.whatsapp = whatsapp;
        this.engine = engine;
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
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#registerLidMapping(Jid, Jid)}. The payload
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
     * @implNote This implementation acknowledges and otherwise drops the pure media-plane signals
     * ({@code transport}, {@code relaylatency}, {@code relayelection}, {@code video_state_ack},
     * {@code accept_ack}, {@code accept_receipt}, {@code offer_receipt}, {@code offer_ack},
     * {@code offer_nack}, {@code interruption}, {@code notify}, {@code flow_control},
     * {@code web_client}, {@code group_info}) because Cobalt implements no WebRTC media plane and can
     * observe them only at the parse layer. The {@code enc_rekey} retry-receipt branch is likewise
     * omitted because there is no media runtime to request a rekey retry.
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
                engine.onPeerAccept(callId);
            }
            case "reject" -> {
                sendCallReceipt(node, from, callId, callCreator, "reject");
                engine.onPeerReject(callId, "reject");
                whatsapp.store().removeCall(callId);
                notifyEnded(callId, from, "reject");
            }
            case "preaccept" -> {
                sendCallAck(node, payload.description());
                notifyPreaccept(callId, from);
            }
            case "enc_rekey" -> {
                sendCallReceipt(node, from, callId, callCreator, "enc_rekey");
            }
            case "terminate" -> {
                var reason = payload.getAttributeAsString("reason", null);
                engine.onPeerTerminate(callId, reason);
                whatsapp.store().removeCall(callId);
                sendCallAck(node, payload.description());
                notifyEnded(callId, from, reason);
            }
            case "mute_v2" -> {
                // Wire shape verified against captured fixtures: the attribute is `mute-state` with
                // values "1" (muted) and "0" (unmuted); the legacy `mute` tag with
                // state="muted"|"unmuted" is no longer emitted by current WA Web snapshots.
                sendCallAck(node, payload.description());
                var muteState = payload.getAttributeAsString("mute-state", null);
                notifyMute(callId, from, "1".equals(muteState));
            }
            case "video_state" -> {
                sendCallAck(node, payload.description());
                var state = payload.getAttributeAsString("state", null);
                notifyVideoState(callId, from, "on".equals(state));
            }
            case "peer_state" -> {
                sendCallAck(node, payload.description());
                var state = payload.getAttributeAsString("state", null);
                notifyPeerState(callId, from, state);
            }
            case "group_update" -> {
                sendCallAck(node, payload.description());
                var action = payload.getAttributeAsString("action", null);
                var participants = payload.getChild("group_info")
                        .map(info -> info.children().stream()
                                .map(c -> c.getAttributeAsJid("jid", null))
                                .filter(Objects::nonNull)
                                .toList())
                        .orElse(List.of());
                if (!participants.isEmpty()) {
                    notifyParticipantsChanged(callId, groupJid, participants, "add".equals(action));
                }
            }
            case "offer_notice" -> {
                sendCallAck(node, payload.description());
                var missed = buildIncomingCall(node, callId, payload, callCreator, groupJid, offline);
                if (missed != null) {
                    notifyOfferNotice(missed);
                }
            }
            case "transport", "relaylatency", "relayelection", "video_state_ack", "accept_ack",
                 "accept_receipt", "offer_receipt", "offer_ack", "offer_nack", "interruption",
                 "notify", "flow_control", "web_client", "group_info" ->
                    sendCallAck(node, payload.description());
            default -> sendCallAck(node, payload.description());
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

        var self = whatsapp.store().jid().orElse(null);
        var outgoing = self != null && callCreator.toUserJid().equals(self.toUserJid());
        if (outgoing) {
            return;
        }

        var call = buildIncomingCall(node, callId, payload, callCreator, groupJid, offline);
        if (call == null) {
            return;
        }
        whatsapp.store().addCall(call);
        whatsapp.sendNodeWithNoResponse(CallStanza.ringing(callCreator, callId));
        notifyCall(call);
    }

    /**
     * Constructs an {@link IncomingCall} from a parsed call stanza.
     *
     * <p>Resolves the chat JID from {@code groupJid} for group calls or from the canonical user JID of
     * the creator otherwise, returning {@code null} when no chat JID can be resolved. The offer
     * payload's relay transport metadata is parsed through
     * {@link OfferTransportSpec#parse(Node)} so the transport that takes over on accept receives the
     * relay tokens it needs; a missing or unparseable transport spec yields a call with no transport
     * spec rather than a failure.
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
        var transportSpec = OfferTransportSpec
                .parse(payload)
                .orElse(null);
        return new IncomingCall(
                callId,
                callCreator,
                chatJid,
                resolveTimestamp(node),
                payload.hasChild("video"),
                groupJid != null,
                groupJid,
                offline,
                transportSpec
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
     * store via {@link com.github.auties00.cobalt.store.WhatsAppStore#registerLidMapping(Jid, Jid)}. A
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
        whatsapp.store().registerLidMapping(phoneNumber.toUserJid(), userJid);
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
        var contact = whatsapp.store().findContactByJid(contactJid)
                .orElseGet(() -> whatsapp.store().addNewContact(contactJid.toUserJid()));
        contact.setChosenName(pushName);
        whatsapp.store().addContact(contact);
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
        var self = whatsapp.store().jid().orElse(null);
        if (self == null) {
            return null;
        }

        if (remote.hasLidServer()) {
            return whatsapp.store().lid().orElse(self.toUserJid());
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

        return whatsapp.store().findPhoneByLid(userJid).orElse(userJid);
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
            Thread.startVirtualThread(() -> listener.onCall(whatsapp, call));
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
            Thread.startVirtualThread(() -> listener.onCallEnded(whatsapp, callId, fromJid, parsed));
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
            Thread.startVirtualThread(() -> listener.onCallPreaccept(whatsapp, callId, fromJid));
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
            Thread.startVirtualThread(() -> listener.onCallMuteChanged(whatsapp, callId, fromJid, muted));
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
            Thread.startVirtualThread(() -> listener.onCallVideoStateChanged(whatsapp, callId, fromJid, enabled));
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
            Thread.startVirtualThread(() -> listener.onCallPeerStateChanged(whatsapp, callId, fromJid, parsed));
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
            Thread.startVirtualThread(() -> listener.onCallParticipantsChanged(whatsapp, callId, groupJid, participants, added));
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
            Thread.startVirtualThread(() -> listener.onCallOfferNotice(whatsapp, call));
        }
    }
}
