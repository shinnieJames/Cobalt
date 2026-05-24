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
 * Handles incoming VoIP call stanzas received from the WhatsApp server.
 *
 * <p>This handler processes call signaling stanzas (tag {@code "call"}) that
 * arrive on the socket stream. It parses the stanza into its constituent
 * fields (peer {@link Jid}, call creator, call identifier, timestamp, etc.),
 * persists any LID-to-phone-number mappings and caller push names carried
 * by the stanza, updates the local call model in the
 * {@link com.github.auties00.cobalt.store.WhatsAppStore}, and sends the
 * appropriate receipt or acknowledgement stanza back to the server.
 *
 * <p>The handler supports the following payload tags: {@code offer},
 * {@code accept}, {@code reject}, {@code enc_rekey}, {@code terminate},
 * and a generic default path for any other payload tag (including
 * {@code offer_notice}, {@code transport}, and other signaling messages).
 * Each payload type triggers the receipt or acknowledgement behavior
 * matching the WhatsApp Web protocol flow.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleVoipCall")
@WhatsAppWebModule(moduleName = "WAWebVoipLidUtils")
public final class CallReceiver implements SocketStream.Handler {
    /**
     * Logger for parse errors and signaling traces.
     */
    private static final System.Logger LOGGER = System.getLogger(CallReceiver.class.getName());

    /**
     * The WhatsApp client used for sending stanzas and accessing the store.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The call engine — used to dispatch peer-side accept/reject/
     * terminate transitions to the right {@link ActiveCall}.
     */
    private final CallService engine;

    /**
     * The {@link AckSender} used to ship the
     * {@code <ack class="call" type="...">} stanza that echoes the
     * parsed VoIP payload tag back to the server.
     */
    private final AckSender ackSender;

    /**
     * Constructs a new {@code CallReceiver}.
     *
     * @param whatsapp  the WhatsApp client used for sending stanzas and store access
     * @param engine    the call engine the receiver routes peer-side
     *                  events to
     * @param ackSender the {@link AckSender}
     *                  used to emit the outbound
     *                  {@code <ack class="call">} stanza
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleVoipCall", exports = "handleCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public CallReceiver(WhatsAppClient whatsapp, CallService engine, AckSender ackSender) {
        this.whatsapp = whatsapp;
        this.engine = engine;
        this.ackSender = ackSender;
    }

    /**
     * Handles a raw call stanza received from the socket stream by delegating to {@link #handleCall(Node)}.
     *
     * @param node the incoming call stanza node
     */
    @Override
    public void handle(Node node) {
        handleCall(node);
    }

    /**
     * Parses the incoming call stanza and dispatches it to the appropriate
     * receipt, ack, or no-op path based on the payload tag.
     *
     * <p>The call stanza has the structure:
     * <pre>{@code
     * <call from="..." id="..." [offline] [t="..."] [sender_lid="..."]>
     *   <offer|accept|reject|enc_rekey|terminate|... call-id="..." call-creator="..."
     *          [group-jid="..."] [caller_pn="..."] [notify="..."]>
     *     [<video/>]
     *     [<group_info>
     *       <participant jid="..." [user_pn="..."] [username="..."]/> ...
     *     </group_info>]
     *   </...>
     * </call>
     * }</pre>
     *
     * <p>If the stanza carries a {@code sender_lid} attribute, the mapping
     * between the sender's LID and the {@code from} phone number is persisted
     * via
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#registerLidMapping(Jid, Jid)}.
     *
     * <p>The payload attributes and optional {@code group_info} participants
     * are persisted through {@link #persistAttributesAndLidMappingsForCall}:
     * the caller's push name is written to the contact, the caller's
     * LID-to-phone mapping is registered when applicable, and the same
     * treatment is applied to each participant in {@code group_info}.
     *
     * <p>Dispatch by payload tag:
     * <ul>
     *   <li>{@code offer}: build the {@link IncomingCall}, send a receipt,
     *       register the call, notify listeners.</li>
     *   <li>{@code accept}: send a receipt — peer accepted; engine takes
     *       over media negotiation when wired.</li>
     *   <li>{@code reject}: send a receipt, drop the call from the store,
     *       fire {@code onCallEnded}.</li>
     *   <li>{@code enc_rekey}: send a receipt (group call key re-exchange).</li>
     *   <li>{@code terminate}: drop the call from the store, send an ack,
     *       fire {@code onCallEnded}.</li>
     *   <li>default: send an ack.</li>
     * </ul>
     *
     * @param node the incoming call stanza node
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
                // WA Web's retry-receipt branch is omitted because Cobalt has no VoIP media runtime that can request a rekey retry.
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
                // Wire shape verified against captured fixtures: the attribute
                // is `mute-state` with values "1" (muted) and "0" (unmuted).
                // The legacy "mute" tag with state="muted"|"unmuted" is no
                // longer emitted by current WA Web snapshots.
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
            // Pure media-plane signals: ack and drop. Cobalt does not implement WebRTC,
            // so transport / relay election / video-state ack / flow-control are observable
            // only at the parse layer.
            case "transport", "relaylatency", "relayelection", "video_state_ack", "accept_ack",
                 "accept_receipt", "offer_receipt", "offer_ack", "offer_nack", "interruption",
                 "notify", "flow_control", "web_client", "group_info" ->
                    sendCallAck(node, payload.description());
            default -> sendCallAck(node, payload.description());
        }
    }

    /**
     * Handles an incoming call offer stanza.
     *
     * <p>Sends a receipt back to the server, builds the {@link IncomingCall}
     * handle, registers it in the store, and notifies listeners — but only
     * for inbound offers (the local user is not the call creator).
     *
     * @param node        the call stanza node
     * @param payload     the {@code offer} child node
     * @param from        the {@link Jid} of the call sender
     * @param callId      the unique call identifier
     * @param callCreator the {@link Jid} of the call initiator
     * @param groupJid    the group {@link Jid} for group calls, or
     *                    {@code null} for one-to-one calls
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
     * Constructs an {@link IncomingCall} from a parsed call stanza, or
     * returns {@code null} when the chat JID cannot be resolved. The
     * handler dispatch goes through the owning client's
     * {@link CallService}.
     *
     * @param node        the call stanza node (used for timestamp resolution)
     * @param callId      the unique call identifier
     * @param payload     the payload child node
     * @param callCreator the {@link Jid} of the call initiator
     * @param groupJid    the group JID for group calls, or {@code null}
     * @param offline     whether the stanza was received while offline
     * @return the {@link IncomingCall}, or {@code null} if metadata is
     *         insufficient
     */
    private IncomingCall buildIncomingCall(Node node, String callId, Node payload,
                                           Jid callCreator, Jid groupJid, boolean offline) {
        var chatJid = groupJid != null ? groupJid : canonicalUserJid(callCreator);
        if (chatJid == null) {
            return null;
        }
        // Parse <relay> / <rte> from the offer payload so the
        // ActiveCallTransport that takes over on accept gets the relay
        // tokens it needs to drive the handshake (Phase 3).
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
     * Resolves the timestamp from the call stanza's {@code t} attribute.
     *
     * <p>Falls back to {@link Instant#EPOCH} if the attribute is not present,
     * matching the WhatsApp Web parser which defaults missing {@code t} to
     * {@code castToUnixTime(0)}.
     *
     * @param node the call stanza node
     * @return the resolved timestamp, never {@code null}
     */
    private Instant resolveTimestamp(Node node) {
        var rawTimestamp = node.getAttributeAsLong("t", (Long) null);
        return rawTimestamp != null ? Instant.ofEpochSecond(rawTimestamp) : Instant.EPOCH;
    }

    /**
     * Persists the call payload's caller attributes and any LID-to-phone
     * mappings carried by the stanza.
     *
     * <p>This implements {@code WAWebVoipLidUtils.persistAttributesAndLidMappingsForCall}:
     * it forwards the caller attributes ({@code call_creator},
     * {@code caller_pn}, {@code caller_push_name}) to
     * {@link #attemptPersistLidMappingAndUserAttributes}, and then iterates
     * every participant in the optional {@code group_info} child (each
     * carries its own {@code jid} / {@code user_pn} / {@code username})
     * and forwards them as well.
     *
     * @param callCreator    the caller's {@link Jid} from {@code call-creator}
     * @param callerPn       the caller's phone number {@link Jid} from
     *                       {@code caller_pn}, or {@code null}
     * @param callerPushName the caller push name from {@code notify}, or
     *                       {@code null}
     * @param payload        the call payload node which may contain a
     *                       {@code group_info} child
     * @param offline        whether the call was received while offline
     *                       (WA Web uses this to decide the
     *                       {@code flushImmediately} flag as {@code !offline})
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
            // WA Web does not forward a pushName for participants from group_info.
            attemptPersistLidMappingAndUserAttributes(participantJid, participantPn, null);
        }
    }

    /**
     * Persists a single LID-to-phone-number mapping and updates the
     * associated contact's push name.
     *
     * <p>This implements {@code WAWebVoipLidUtils.attemptPersistLidMappingAndUserAttributes}:
     * <ul>
     *   <li>If {@code pushName} is non-{@code null} and non-blank, the
     *       contact's chosen name is updated via
     *       {@code WAWebHandlePushnameUpdate.updatePushname}. In Cobalt this
     *       maps to updating
     *       {@link com.github.auties00.cobalt.model.contact.Contact#setChosenName(String)}.</li>
     *   <li>If {@code jid} is a LID and {@code phoneNumber} is non-{@code null},
     *       the LID-to-phone-number mapping is registered in the store via
     *       {@link com.github.auties00.cobalt.store.WhatsAppStore#registerLidMapping(Jid, Jid)}
     *       (which is WA Web's
     *       {@code WAWebDBCreateLidPnMappings.createLidPnMappings}).</li>
     * </ul>
     *
     * <p>The username/country-code flow and the
     * {@code usernameCallingPhoneNumberPrivacyEnabled} skip path from WA Web
     * are intentionally omitted: Cobalt does not implement the username
     * system nor the privacy-enforcement fast path, and no calling surface
     * reads those fields.
     *
     * @param jid         the candidate JID (LID or otherwise); mappings are
     *                    only registered when this is a LID
     * @param phoneNumber the phone number {@link Jid}, or {@code null}
     * @param pushName    the push name from the stanza's {@code notify}
     *                    attribute, or {@code null}
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
        // The WA Web username + usernameCallingPhoneNumberPrivacyEnabled short-circuit is omitted because Cobalt has no username runtime and always writes the mapping.
        whatsapp.store().registerLidMapping(phoneNumber.toUserJid(), userJid);
    }

    /**
     * Updates the push (chosen) name of a contact in the local store.
     *
     * <p>If the contact does not exist, a new contact record is created and
     * then assigned the push name. Empty or blank push names are ignored.
     *
     * @param contactJid the non-{@code null} JID of the contact to update
     * @param pushName   the push name to store; must already be validated as
     *                   non-blank by the caller
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
     * Sends a receipt stanza for a call signaling message.
     *
     * <p>The receipt wraps a child element whose tag matches the signaling
     * type (e.g. {@code offer}, {@code accept}, {@code reject},
     * {@code enc_rekey}) and carries the {@code call-id} and
     * {@code call-creator} attributes. The {@code from} attribute of the
     * receipt is set to either the local LID user or the local phone-number
     * user, depending on whether the remote peer uses a LID-based address.
     *
     * @param node        the original call stanza node
     * @param to          the {@link Jid} to send the receipt to
     * @param callId      the unique call identifier
     * @param callCreator the {@link Jid} of the call initiator
     * @param childTag    the receipt child tag (e.g. {@code "offer"})
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
     * <p>Used for payload tags that do not require a full receipt (e.g.
     * {@code terminate} and unrecognised tags). The ack carries
     * {@code class="call"} and a {@code type} attribute matching the payload
     * tag.
     *
     * @param node the original call stanza node
     * @param type the payload tag to echo as the ack type
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleVoipCall", exports = "handleCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void sendCallAck(Node node, String type) {
        ackSender.ack(AckClass.CALL, node).type(type).send();
    }

    /**
     * Resolves the {@code from} {@link Jid} to use on outgoing receipt
     * stanzas.
     *
     * <p>If the remote peer's JID uses the LID server, the local LID user
     * JID is returned; otherwise, the local phone-number user JID is used.
     * This matches the WhatsApp Web rule where the receipt's {@code from}
     * alternates between {@code getMeDeviceLidOrThrow()} (for LID peers) and
     * {@code getMePnUserOrThrow_DO_NOT_USE()} (for phone peers).
     *
     * @param remote the remote peer {@link Jid}
     * @return the local {@link Jid} to use as the {@code from} attribute, or
     *         {@code null} if the local JID is not available
     */
    private Jid resolveReceiptFrom(Jid remote) {
        var self = whatsapp.store().jid().orElse(null);
        if (self == null) {
            return null;
        }

        if (remote.hasLidServer()) {
            // store.lid() preserves the device suffix on outgoing LID receipts; the PN fallback only kicks in when setLid was never called.
            return whatsapp.store().lid().orElse(self.toUserJid());
        }

        return self.toUserJid();
    }

    /**
     * Resolves the canonical user JID for a given JID.
     *
     * <p>If the JID uses the LID server, the corresponding phone-number JID
     * is looked up via the store's LID-to-phone mapping; when no mapping is
     * available, the LID user JID is returned unchanged. Otherwise the user
     * JID is returned directly (with the device suffix stripped).
     *
     * @param jid the {@link Jid} to resolve
     * @return the canonical user JID, or {@code null} if {@code jid} is
     *         {@code null}
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
     * Notifies all registered listeners of an incoming call.
     *
     * <p>Each listener's {@code onCall} method is invoked on a new virtual
     * thread so that listener execution does not block the socket stream
     * handler thread.
     *
     * @param call the {@link IncomingCall} to notify listeners about
     */
    private void notifyCall(IncomingCall call) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onCall(whatsapp, call));
        }
    }

    /**
     * Notifies all registered listeners that the call has terminated.
     * The wire {@code reason} attribute is parsed into a typed
     * {@link CallEndReason}; unknown literals surface as
     * {@link CallEndReason#UNKNOWN}.
     *
     * @param callId  the identifier of the call that ended
     * @param fromJid the JID of the party that ended the call
     * @param wireReason the wire-level reason literal, or {@code null}
     */
    private void notifyEnded(String callId, Jid fromJid, String wireReason) {
        var parsed = CallEndReason.fromWireValue(wireReason);
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onCallEnded(whatsapp, callId, fromJid, parsed));
        }
    }

    /**
     * Notifies all registered listeners of a preaccept signal.
     *
     * @param callId  the call identifier
     * @param fromJid the peer that sent the preaccept
     */
    private void notifyPreaccept(String callId, Jid fromJid) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onCallPreaccept(whatsapp, callId, fromJid));
        }
    }

    /**
     * Notifies all registered listeners of a microphone-mute change.
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
     * Notifies all registered listeners of a video on/off change.
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
     * Notifies all registered listeners of a peer-state update. The
     * wire {@code state} attribute is parsed into a typed
     * {@link CallPeerState}; unknown literals surface as
     * {@link CallPeerState#UNKNOWN}.
     *
     * @param callId  the call identifier
     * @param fromJid the peer whose state changed
     * @param wireState the wire-level state literal
     */
    private void notifyPeerState(String callId, Jid fromJid, String wireState) {
        var parsed = CallPeerState.fromWireValue(wireState);
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onCallPeerStateChanged(whatsapp, callId, fromJid, parsed));
        }
    }

    /**
     * Notifies all registered listeners that group-call participants were
     * added or removed.
     *
     * @param callId       the call identifier
     * @param groupJid     the group JID that owns the call (may be
     *                     {@code null} when the stanza did not carry one)
     * @param participants the JIDs that were added or removed
     * @param added        {@code true} for add, {@code false} for remove
     */
    private void notifyParticipantsChanged(String callId, Jid groupJid, List<Jid> participants, boolean added) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onCallParticipantsChanged(whatsapp, callId, groupJid, participants, added));
        }
    }

    /**
     * Notifies all registered listeners of an offer-notice (call missed
     * while offline).
     *
     * @param call the call descriptor parsed from the notice
     */
    private void notifyOfferNotice(IncomingCall call) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onCallOfferNotice(whatsapp, call));
        }
    }
}
