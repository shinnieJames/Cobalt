package com.github.auties00.cobalt.stream.call;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.call.CallOffer;
import com.github.auties00.cobalt.model.call.CallOfferBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;

import java.time.Instant;

/**
 * Handles incoming VoIP call stanzas from the WhatsApp server.
 *
 * <p>This handler processes call signaling stanzas (tag {@code "call"}) received
 * on the socket stream. It parses the stanza into its constituent fields (peer JID,
 * call creator, call ID, etc.), persists any LID-to-phone number mappings carried
 * by the stanza, maintains the local call model in the store, and sends the
 * appropriate receipt or acknowledgement back to the server.
 *
 * <p>The handler supports the following payload types: {@code offer},
 * {@code accept}, {@code reject}, {@code enc_rekey}, {@code terminate}, and a
 * generic default path for any other payload tag. Each payload type triggers
 * specific receipt or acknowledgement behavior matching the WhatsApp Web
 * protocol flow.
 *
 * @implNote WAWebHandleVoipCall
 */
public final class CallStreamHandler implements SocketStream.Handler {
    /**
     * The logger for this handler.
     *
     * @implNote WAWebHandleVoipCall (WALogger references)
     */
    private static final System.Logger LOGGER = System.getLogger(CallStreamHandler.class.getName());

    /**
     * The WhatsApp client instance used for sending stanzas and accessing the store.
     *
     * @implNote WAWebHandleVoipCall (module-level dependencies)
     */
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a new {@code CallStreamHandler} with the specified client.
     *
     * @param whatsapp the WhatsApp client used for sending stanzas and store access
     * @implNote WAWebHandleVoipCall (constructor DI replaces module imports)
     */
    public CallStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    /**
     * Handles a raw call stanza received from the socket stream.
     *
     * <p>Delegates to {@link #handleCall(Node)} for processing.
     *
     * @param node the incoming call stanza node
     * @implNote WAWebHandleVoipCall.handleCall
     */
    @Override
    public void handle(Node node) {
        handleCall(node);
    }

    /**
     * Parses the incoming call stanza and dispatches it to the appropriate handler
     * based on the payload tag.
     *
     * <p>The call stanza has the structure:
     * <pre>{@code
     * <call from="..." id="..." [offline] [t="..."]>
     *   <offer|accept|reject|enc_rekey|terminate|... call-id="..." call-creator="..." [group-jid="..."] [caller_pn="..."]>
     *     [<video/>]
     *   </...>
     * </call>
     * }</pre>
     *
     * <p>If the stanza has a {@code sender_lid} attribute, the LID-to-phone-number
     * mapping is persisted in the store. Additionally, if the payload carries a
     * {@code caller_pn} attribute with a LID call creator, the mapping between the
     * caller's LID and phone number is persisted.
     *
     * @param node the incoming call stanza node
     * @implNote WAWebHandleVoipCall.handleCall (function b/v)
     */
    private void handleCall(Node node) {
        // WAWebHandleVoipCall parser g: e.assertTag("call")
        var from = node.getAttributeAsJid("from", null);
        var payload = node.getChild().orElse(null);
        if (from == null || payload == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring malformed call stanza: {0}", node);
            return;
        }

        // WAWebHandleVoipCall parser g: sender_lid extraction
        var senderLid = node.getAttributeAsJid("sender_lid", null);

        var callId = payload.getAttributeAsString("call-id", null);
        var callCreator = payload.getAttributeAsJid("call-creator", null);
        if (callId == null || callCreator == null) {
            sendCallAck(node, payload.description());
            return;
        }

        // WAWebHandleVoipCall function v: persist sender LID mapping
        // WAWebVoipLidUtils.attemptPersistLidMappingAndUserAttributes
        if (senderLid != null) {
            persistLidMapping(senderLid, from);
        }

        // WAWebHandleVoipCall function v -> WAWebVoipLidUtils.persistAttributesAndLidMappingsForCall
        // Persists LID mapping for caller_pn if the call creator is a LID
        var callerPn = payload.getAttributeAsJid("caller_pn", null);
        if (callerPn != null && callCreator.hasLidServer()) {
            persistLidMapping(callCreator, callerPn);
        }

        switch (payload.description()) {
            case "offer" -> handleOffer(node, payload, from, callId, callCreator);
            case "accept" -> {
                // WAWebHandleVoipCall function C: TYPE.ACCEPT case
                updateCall(node, callId, payload, from, callCreator, CallOffer.Status.ACCEPTED, node.hasAttribute("offline"));
                sendCallReceipt(node, from, callId, callCreator, "accept");
            }
            case "reject" -> {
                // WAWebHandleVoipCall function C: TYPE.REJECT case
                updateCall(node, callId, payload, from, callCreator, CallOffer.Status.REJECTED, node.hasAttribute("offline"));
                sendCallReceipt(node, from, callId, callCreator, "reject");
            }
            case "enc_rekey" -> {
                // WAWebHandleVoipCall function C: TYPE.ENC_REKEY case
                sendCallReceipt(node, from, callId, callCreator, "enc_rekey");
            }
            case "terminate" -> {
                // ADAPTED: WAWebHandleVoipCall function C: default case (TERMINATE falls through)
                // Cobalt explicitly tracks call lifecycle status for terminate stanzas
                var reason = payload.getAttributeAsString("reason", null);
                var status = "timeout".equals(reason)
                        ? CallOffer.Status.TIMED_OUT
                        : CallOffer.Status.CANCELLED;
                updateCall(node, callId, payload, from, callCreator, status, node.hasAttribute("offline"));
                sendCallAck(node, payload.description());
            }
            default -> {
                // WAWebHandleVoipCall function C: default case -> R(ack)
                sendCallAck(node, payload.description());
            }
        }
    }

    /**
     * Handles an incoming call offer stanza.
     *
     * <p>Builds or updates the call model in the store, sends a receipt back to
     * the server, and notifies listeners if the call is incoming (not outgoing).
     *
     * @param node        the call stanza node
     * @param payload     the offer child node
     * @param from        the JID of the call sender
     * @param callId      the unique call identifier
     * @param callCreator the JID of the call initiator
     * @implNote WAWebHandleVoipCall function C: TYPE.OFFER case
     */
    private void handleOffer(Node node, Node payload, Jid from, String callId, Jid callCreator) {
        // WAWebHandleVoipCall function C: S(receipt) for OFFER
        var call = buildOrUpdateCall(node, callId, payload, from, callCreator, CallOffer.Status.RINGING, node.hasAttribute("offline"));
        if (call == null) {
            sendCallAck(node, payload.description());
            return;
        }

        sendCallReceipt(node, from, callId, callCreator, "offer");
        if (!call.outgoing()) {
            notifyCall(call);
        }
    }

    /**
     * Builds a new {@link CallOffer} or updates an existing one in the store.
     *
     * <p>If a call with the same {@code callId} already exists, its fields are
     * updated. Otherwise a new call is created and stored. The chat JID is
     * resolved from the group JID (for group calls) or the canonical user JID
     * of the sender (for one-to-one calls).
     *
     * @param node        the call stanza node (used for timestamp resolution)
     * @param callId      the unique call identifier
     * @param payload     the payload child node
     * @param from        the JID of the call sender
     * @param callCreator the JID of the call initiator
     * @param status      the lifecycle status to assign
     * @param offline     whether the stanza was received while offline
     * @return the created or updated call, or {@code null} if the chat JID
     *         could not be resolved
     * @implNote WAWebHandleVoipCall parser g (call model construction)
     */
    private CallOffer buildOrUpdateCall(
            Node node,
            String callId,
            Node payload,
            Jid from,
            Jid callCreator,
            CallOffer.Status status,
            boolean offline
    ) {
        // WAWebHandleVoipCall parser g: group_jid extraction
        var groupJid = payload.getAttributeAsJid("group-jid", null);
        var chatJid = groupJid != null ? groupJid : canonicalUserJid(from);
        if (chatJid == null) {
            return null;
        }

        var self = whatsapp.store().jid().orElse(null);
        var outgoing = self != null && callCreator.toUserJid().equals(self.toUserJid());

        var existing = whatsapp.store().findCallById(callId).orElse(null);
        if (existing != null) {
            existing.setChatJid(chatJid);
            existing.setCallerJid(callCreator);
            existing.setVideo(payload.hasChild("video"));
            existing.setOfflineOffer(offline);
            existing.setGroup(groupJid != null);
            existing.setGroupJid(groupJid);
            existing.setOutgoing(outgoing);
            existing.setStatus(status);
            whatsapp.store().addCall(existing);
            return existing;
        }

        // WAWebHandleVoipCall parser g: S object construction
        var call = new CallOfferBuilder()
                .chatJid(chatJid)
                .callerJid(callCreator)
                .callId(callId)
                .timestamp(resolveTimestamp(node))
                .video(payload.hasChild("video"))
                .status(status)
                .offlineOffer(offline)
                .group(groupJid != null)
                .groupJid(groupJid)
                .outgoing(outgoing)
                .build();
        whatsapp.store().addCall(call);
        return call;
    }

    /**
     * Updates an existing call in the store with the given status and attributes,
     * or creates a new call entry if none exists.
     *
     * @param node        the call stanza node
     * @param callId      the unique call identifier
     * @param payload     the payload child node
     * @param from        the JID of the call sender
     * @param callCreator the JID of the call initiator
     * @param status      the lifecycle status to assign
     * @param offline     whether the stanza was received while offline
     * @implNote WAWebHandleVoipCall function C (accept/reject/terminate cases)
     */
    private void updateCall(
            Node node,
            String callId,
            Node payload,
            Jid from,
            Jid callCreator,
            CallOffer.Status status,
            boolean offline
    ) {
        buildOrUpdateCall(node, callId, payload, from, callCreator, status, offline);
    }

    /**
     * Resolves the timestamp from the call stanza's {@code t} attribute.
     *
     * <p>Falls back to {@link Instant#now()} if the attribute is not present
     * or cannot be parsed.
     *
     * @param node the call stanza node
     * @return the resolved timestamp, never {@code null}
     * @implNote WAWebHandleVoipCall parser g: (s=e.maybeAttrTime("t"))!=null?s:castToUnixTime(0)
     */
    private Instant resolveTimestamp(Node node) {
        var rawTimestamp = node.getAttributeAsLong("t", (Long) null);
        var timestamp = rawTimestamp != null ? Instant.ofEpochSecond(rawTimestamp) : null;
        return timestamp != null ? timestamp : Instant.now();
    }

    /**
     * Persists a LID-to-phone-number mapping in the store.
     *
     * <p>This is called when the call stanza carries a {@code sender_lid} attribute
     * or when the call creator is a LID with a corresponding {@code caller_pn}.
     *
     * @param lidJid   the LID JID
     * @param phoneJid the phone number JID
     * @implNote WAWebVoipLidUtils.attemptPersistLidMappingAndUserAttributes,
     *           WAWebVoipLidUtils.persistAttributesAndLidMappingsForCall
     */
    private void persistLidMapping(Jid lidJid, Jid phoneJid) {
        if (lidJid == null || phoneJid == null) {
            return;
        }
        // WAWebVoipLidUtils -> WAWebDBCreateLidPnMappings.createLidPnMappings
        if (lidJid.hasLidServer()) {
            whatsapp.store().registerLidMapping(phoneJid.toUserJid(), lidJid.toUserJid());
        }
    }

    /**
     * Sends a receipt stanza for a call signaling message.
     *
     * <p>The receipt wraps a child element whose tag matches the signaling type
     * (e.g. {@code offer}, {@code accept}, {@code reject}, {@code enc_rekey})
     * and carries the {@code call-id} and {@code call-creator} attributes. The
     * {@code from} attribute of the receipt is set to either the local LID or
     * the local phone number JID, depending on whether the remote peer uses
     * a LID-based address.
     *
     * @param node        the original call stanza node
     * @param to          the JID to send the receipt to
     * @param callId      the unique call identifier
     * @param callCreator the JID of the call initiator
     * @param childTag    the receipt child tag (e.g. {@code "offer"})
     * @implNote WAWebHandleVoipCall function S
     */
    private void sendCallReceipt(Node node, Jid to, String callId, Jid callCreator, String childTag) {
        // WAWebHandleVoipCall function S: resolve from based on LID
        var from = resolveReceiptFrom(to);
        var stanzaId = node.getAttributeAsString("id", null);
        if (from == null || stanzaId == null) {
            return;
        }

        // WAWebHandleVoipCall function S: child = wap(childTag, {call-id, call-creator})
        var child = new NodeBuilder()
                .description(childTag)
                .attribute("call-id", callId)
                .attribute("call-creator", callCreator)
                .build();
        // WAWebHandleVoipCall function S: receipt = wap("receipt", {to, id, from}, child)
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
     * <p>Used for payload types that do not require a full receipt (e.g.
     * {@code terminate} and unrecognised tags). The ack carries
     * {@code class="call"} and {@code type} matching the payload tag.
     *
     * @param node the original call stanza node
     * @param type the payload tag to echo as the ack type
     * @implNote WAWebHandleVoipCall function R
     */
    private void sendCallAck(Node node, String type) {
        // WAWebHandleVoipCall function R: wap("ack", {to, id, class:"call", type})
        var to = node.getAttributeAsJid("from", null);
        var id = node.getAttributeAsString("id", null);
        if (to == null || id == null) {
            return;
        }

        var ack = new NodeBuilder()
                .description("ack")
                .attribute("to", to)
                .attribute("id", id)
                .attribute("class", "call")
                .attribute("type", type)
                .build();
        whatsapp.sendNodeWithNoResponse(ack);
    }

    /**
     * Resolves the {@code from} JID for outgoing receipt stanzas.
     *
     * <p>If the remote peer uses a LID-based address, the local LID user JID
     * is returned. Otherwise, the local phone number user JID is returned.
     * This matches the WhatsApp Web behavior where the {@code from} field
     * alternates between the device LID and the phone number JID.
     *
     * @param remote the remote peer JID
     * @return the local JID to use as {@code from}, or {@code null} if the
     *         local JID is not available
     * @implNote WAWebHandleVoipCall function S:
     *           e.isLid() ? getMeDeviceLidOrThrow() : getMePnUserOrThrow_DO_NOT_USE()
     */
    private Jid resolveReceiptFrom(Jid remote) {
        var self = whatsapp.store().jid().orElse(null);
        if (self == null) {
            return null;
        }

        if (remote.hasLidServer()) {
            return whatsapp.store().lid()
                    .map(Jid::toUserJid)
                    .orElse(self.toUserJid());
        }

        return self.toUserJid();
    }

    /**
     * Resolves the canonical user JID for a given JID.
     *
     * <p>If the JID has a LID server, attempts to find the corresponding
     * phone number JID via the store's LID-to-phone mapping. Otherwise,
     * returns the user JID directly.
     *
     * @param jid the JID to resolve
     * @return the canonical user JID, or {@code null} if the input is {@code null}
     * @implNote ADAPTED: WAWebHandleVoipCall parser g (chatJid resolution)
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
     * thread to avoid blocking the socket stream handler.
     *
     * @param call the call offer to notify about
     * @implNote ADAPTED: WAWebHandleVoipCall (Cobalt listener notification pattern)
     */
    private void notifyCall(CallOffer call) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onCall(whatsapp, call));
        }
    }
}
