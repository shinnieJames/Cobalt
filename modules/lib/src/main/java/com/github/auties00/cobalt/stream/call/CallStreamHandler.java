package com.github.auties00.cobalt.stream.call;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.call.CallOffer;
import com.github.auties00.cobalt.model.call.CallOfferBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;

import java.time.Instant;

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
 *
 * @implNote WA Web implements call parsing in {@code WAWebHandleVoipCall}
 *           via {@code WADeprecatedWapParser("callParser", ...)} and LID
 *           persistence in {@code WAWebVoipLidUtils}. Cobalt collapses
 *           these into a single handler that directly reads attributes
 *           from {@link Node} and uses the flattened store abstraction to
 *           register LID mappings and push names.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleVoipCall")
@WhatsAppWebModule(moduleName = "WAWebVoipLidUtils")
public final class CallStreamHandler implements SocketStream.Handler {
    /**
     * The logger for this handler.
     *
     * @implNote WAWebHandleVoipCall references {@code WALogger} for parse
     *           errors and signaling traces.
     */
    private static final System.Logger LOGGER = System.getLogger(CallStreamHandler.class.getName());

    /**
     * The WhatsApp client instance used for sending stanzas and accessing the
     * store.
     *
     * @implNote WAWebHandleVoipCall resolves its dependencies through module
     *           imports; Cobalt injects them via the constructor.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a new {@code CallStreamHandler} with the specified client.
     *
     * @param whatsapp the WhatsApp client used for sending stanzas and store
     *                 access
     * @implNote WA Web does not have an explicit constructor for this module;
     *           Cobalt replaces the module-level dependencies with constructor
     *           injection.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleVoipCall", exports = "handleCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public CallStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    /**
     * Handles a raw call stanza received from the socket stream.
     *
     * <p>Delegates to {@link #handleCall(Node)} for processing.
     *
     * @param node the incoming call stanza node
     * @implNote Cobalt dispatches stanzas to handlers through
     *           {@link SocketStream.Handler#handle(Node)}; WA Web registers
     *           handler objects keyed by stanza tag instead.
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
     *   <li>{@code offer}: build the call, send a receipt, notify incoming
     *       call listeners.</li>
     *   <li>{@code accept}: update the call to {@link CallOffer.Status#ACCEPTED},
     *       send a receipt.</li>
     *   <li>{@code reject}: update the call to {@link CallOffer.Status#REJECTED},
     *       send a receipt.</li>
     *   <li>{@code enc_rekey}: send a receipt (group call key re-exchange).</li>
     *   <li>{@code terminate}: update the call to
     *       {@link CallOffer.Status#TIMED_OUT} or
     *       {@link CallOffer.Status#CANCELLED}, send an ack.</li>
     *   <li>default: send an ack.</li>
     * </ul>
     *
     * @param node the incoming call stanza node
     * @implNote Implements {@code WAWebHandleVoipCall.handleCall} (function
     *           {@code b}/{@code v}) combined with its parser ({@code g}) and
     *           dispatch switch (function {@code C}).
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleVoipCall", exports = "handleCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleCall(Node node) {
        // WAWebHandleVoipCall parser g: e.assertTag("call") is implicit — the
        // dispatcher only routes <call> stanzas here.
        var from = node.getAttributeAsJid("from", null); // WAWebHandleVoipCall.g: c = jidWithTypeToWid(e.attrJidWithType("from"))
        var payload = node.getChild().orElse(null); // WAWebHandleVoipCall.g: m = e.mapFirstChild(e => e)
        if (from == null || payload == null) {
            // WAWebHandleVoipCall.g: throw err("Unrecognized call stanza") → WAWebHandleVoipCall.h returns null → handleCall resolves "NO_ACK"
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring malformed call stanza: {0}", node);
            return;
        }

        var senderLid = node.getAttributeAsJid("sender_lid", null); // WAWebHandleVoipCall.g: d = hasAttr("sender_lid") ? jidWithTypeToWid(attrJidWithType("sender_lid")) : null

        var callId = payload.getAttributeAsString("call-id", null); // WAWebHandleVoipCall.g: p = m.attrString("call-id")
        var callCreator = payload.getAttributeAsJid("call-creator", null); // WAWebHandleVoipCall.g: _ = jidWithTypeToWid(m.attrJidWithType("call-creator"))
        if (callId == null || callCreator == null) {
            // WAWebHandleVoipCall.g: attrString / attrJidWithType throw on missing attributes; the outer h() catches and returns null → handleCall resolves "NO_ACK".
            // No ack is sent in this case.
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring call stanza with missing call-id or call-creator: {0}", node);
            return;
        }

        var offline = node.hasAttribute("offline"); // WAWebHandleVoipCall.g: is_offline = e.hasAttr("offline")
        var callerPn = payload.getAttributeAsJid("caller_pn", null); // WAWebHandleVoipCall.g: h = hasAttr("caller_pn") ? jidWithTypeToWid(attrJidWithType("caller_pn")) : null
        var groupJid = payload.getAttributeAsJid("group-jid", null); // WAWebHandleVoipCall.g: g = hasAttr("group-jid") ? jidWithTypeToWid(attrJidWithType("group-jid")) : null
        var callerPushName = payload.getAttributeAsString("notify", null); // WAWebHandleVoipCall.g: b = m.maybeAttrString("notify") → message.caller_push_name

        // WAWebHandleVoipCall.handleCall (v): senderLid != null && (yield WAWebVoipLidUtils.attemptPersistLidMappingAndUserAttributes({jid: senderLid, phoneNumber: from, flushImmediately: true}))
        if (senderLid != null) {
            attemptPersistLidMappingAndUserAttributes(senderLid, from, null);
        }

        // WAWebHandleVoipCall.handleCall (v): yield WAWebVoipLidUtils.persistAttributesAndLidMappingsForCall(a)
        persistAttributesAndLidMappingsForCall(callCreator, callerPn, callerPushName, payload, offline);

        switch (payload.description()) {
            case "offer" -> handleOffer(node, payload, from, callId, callCreator, groupJid, offline); // WAWebHandleVoipCall.C: case TYPE.OFFER
            case "accept" -> { // WAWebHandleVoipCall.C: case TYPE.ACCEPT
                sendCallReceipt(node, from, callId, callCreator, "accept"); // WAWebHandleVoipCall.C: S(t,a,h,g,TYPE.ACCEPT)
                updateCall(node, callId, payload, from, callCreator, groupJid, CallOffer.Status.ACCEPTED, offline);
            }
            case "reject" -> { // WAWebHandleVoipCall.C: case TYPE.REJECT
                sendCallReceipt(node, from, callId, callCreator, "reject"); // WAWebHandleVoipCall.C: S(t,a,h,g,TYPE.REJECT)
                updateCall(node, callId, payload, from, callCreator, groupJid, CallOffer.Status.REJECTED, offline);
            }
            case "enc_rekey" -> { // WAWebHandleVoipCall.C: case TYPE.ENC_REKEY
                // WAWebHandleVoipCall.C: retry-receipt logic (shouldRetry) omitted — Cobalt has no VoIP media runtime that can request a rekey retry, so we always send the normal S(...) receipt branch.
                sendCallReceipt(node, from, callId, callCreator, "enc_rekey"); // WAWebHandleVoipCall.C: S(t,a,h,g,TYPE.ENC_REKEY)
            }
            case "terminate" -> {
                // ADAPTED: WAWebHandleVoipCall.C default case handles TERMINATE by calling handleVoipIncomingSignalingMessage and returning R(t,a,l) (ack).
                // Cobalt explicitly tracks the call lifecycle status for terminate stanzas before ack'ing.
                var reason = payload.getAttributeAsString("reason", null);
                var status = "timeout".equals(reason)
                        ? CallOffer.Status.TIMED_OUT
                        : CallOffer.Status.CANCELLED;
                updateCall(node, callId, payload, from, callCreator, groupJid, status, offline);
                sendCallAck(node, payload.description()); // WAWebHandleVoipCall.C default: R(t,a,l)
            }
            default -> sendCallAck(node, payload.description()); // WAWebHandleVoipCall.C default: R(t,a,l)
        }
    }

    /**
     * Handles an incoming call offer stanza.
     *
     * <p>Sends a receipt back to the server, builds or updates the call model
     * in the store, and notifies listeners if the call is incoming (not
     * outgoing relative to the local user).
     *
     * @param node        the call stanza node
     * @param payload     the {@code offer} child node
     * @param from        the {@link Jid} of the call sender
     * @param callId      the unique call identifier
     * @param callCreator the {@link Jid} of the call initiator
     * @param groupJid    the group {@link Jid} for group calls, or
     *                    {@code null} for one-to-one calls
     * @param offline     whether the stanza was received while offline
     * @implNote WAWebHandleVoipCall function C: case TYPE.OFFER invokes
     *           {@code S(t,a,h,g,TYPE.OFFER)} first, then
     *           {@code handleVoipCallOffer} (which maintains the VoIP
     *           media session). Cobalt replaces the media session with
     *           listener notification.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleVoipCall", exports = "handleCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleOffer(Node node, Node payload, Jid from, String callId, Jid callCreator, Jid groupJid, boolean offline) {
        sendCallReceipt(node, from, callId, callCreator, "offer"); // WAWebHandleVoipCall.C: S(t,a,h,g,TYPE.OFFER)

        var call = buildOrUpdateCall(node, callId, payload, from, callCreator, groupJid, CallOffer.Status.RINGING, offline); // ADAPTED: WA Web hands the message off to WAWebHandleVoipCallOffer which maintains VoIP state; Cobalt persists the call model and notifies listeners.
        if (call == null) {
            return;
        }

        if (!call.outgoing()) {
            notifyCall(call); // ADAPTED: Cobalt listener notification pattern
        }
    }

    /**
     * Builds a new {@link CallOffer} or updates an existing one in the store.
     *
     * <p>If a call with the same {@code callId} already exists, its fields
     * are updated in place. Otherwise a new call is created and stored. The
     * chat JID is resolved from the group JID (for group calls) or the
     * canonical user JID of the sender (for one-to-one calls).
     *
     * @param node        the call stanza node (used for timestamp resolution)
     * @param callId      the unique call identifier
     * @param payload     the payload child node
     * @param from        the {@link Jid} of the call sender
     * @param callCreator the {@link Jid} of the call initiator
     * @param groupJid    the group JID for group calls, or {@code null}
     * @param status      the lifecycle status to assign
     * @param offline     whether the stanza was received while offline
     * @return the created or updated call, or {@code null} if the chat JID
     *         could not be resolved
     * @implNote ADAPTED: WA Web's parser ({@code WAWebHandleVoipCall.g})
     *           builds an in-memory signaling message object without writing
     *           it to a store; the VoIP module
     *           ({@code WAWebHandleVoipCallOffer}) maintains call state
     *           separately. Cobalt flattens the call into a single
     *           {@link CallOffer} entry in
     *           {@link com.github.auties00.cobalt.store.WhatsAppStore#addCall(CallOffer)}.
     */
    private CallOffer buildOrUpdateCall(
            Node node,
            String callId,
            Node payload,
            Jid from,
            Jid callCreator,
            Jid groupJid,
            CallOffer.Status status,
            boolean offline
    ) {
        var chatJid = groupJid != null ? groupJid : canonicalUserJid(from); // WAWebHandleVoipCall.g: chat context is peer_jid for 1:1 or group_jid for group calls
        if (chatJid == null) {
            return null;
        }

        var self = whatsapp.store().jid().orElse(null);
        var outgoing = self != null && callCreator.toUserJid().equals(self.toUserJid());

        var existing = whatsapp.store().findCallById(callId).orElse(null);
        if (existing != null) {
            existing.setChatJid(chatJid);
            existing.setCallerJid(callCreator);
            existing.setVideo(payload.hasChild("video")); // WAWebHandleVoipCall.g: isVideoCall = m.hasChild("video")
            existing.setOfflineOffer(offline);
            existing.setGroup(groupJid != null);
            existing.setGroupJid(groupJid);
            existing.setOutgoing(outgoing);
            existing.setStatus(status);
            whatsapp.store().addCall(existing);
            return existing;
        }

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
     * Updates an existing call in the store with the given status and
     * attributes, or creates a new call entry if none exists.
     *
     * @param node        the call stanza node
     * @param callId      the unique call identifier
     * @param payload     the payload child node
     * @param from        the {@link Jid} of the call sender
     * @param callCreator the {@link Jid} of the call initiator
     * @param groupJid    the group JID for group calls, or {@code null}
     * @param status      the lifecycle status to assign
     * @param offline     whether the stanza was received while offline
     * @implNote ADAPTED: WA Web function C (accept/reject/terminate cases)
     *           hands the parsed message off to
     *           {@code handleVoipIncomingSignalingMessage}. Cobalt updates
     *           the {@link CallOffer} record in the flattened store.
     */
    private void updateCall(
            Node node,
            String callId,
            Node payload,
            Jid from,
            Jid callCreator,
            Jid groupJid,
            CallOffer.Status status,
            boolean offline
    ) {
        buildOrUpdateCall(node, callId, payload, from, callCreator, groupJid, status, offline);
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
     * @implNote WAWebHandleVoipCall.g:
     *           {@code t: (s=e.maybeAttrTime("t"))!=null?s:WATimeUtils.castToUnixTime(0)}
     *           — the default is epoch 0, not {@code now}.
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
     * @implNote WAWebVoipLidUtils.persistAttributesAndLidMappingsForCall
     *           (function {@code s}/{@code u}).
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipLidUtils", exports = "persistAttributesAndLidMappingsForCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void persistAttributesAndLidMappingsForCall(Jid callCreator, Jid callerPn, String callerPushName, Node payload, boolean offline) {
        // WAWebVoipLidUtils.s: var t = !e.is_offline (flushImmediately flag — ignored by Cobalt as mappings are written synchronously)
        // WAWebVoipLidUtils.s: yield c({jid: e.call_creator, phoneNumber: e.caller_pn, username: e.caller_username, countryCode: e.caller_country_code, pushName: e.caller_push_name, flushImmediately: t})
        attemptPersistLidMappingAndUserAttributes(callCreator, callerPn, callerPushName);

        // WAWebVoipLidUtils.s: var r = e.group_info_updates; r != null && yield WAPromiseEach.promiseEach(r, ...)
        var groupInfo = payload.getChild("group_info").orElse(null);
        if (groupInfo == null) {
            return;
        }
        // WAWebHandleVoipCall.g: group_info_updates = t.mapChildren(e => ({jid, user_pn, username, guest_name}))
        for (var participant : groupInfo.children()) {
            var participantJid = participant.getAttributeAsJid("jid", null);
            if (participantJid == null) {
                continue;
            }
            var participantPn = participant.getAttributeAsJid("user_pn", null);
            // WAWebVoipLidUtils.s: yield c({jid: e.jid, phoneNumber: e.user_pn, username: e.username, flushImmediately: t && o})
            // Note: no pushName for participants — WA Web does not forward it from group_info.
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
     * @implNote WAWebVoipLidUtils.attemptPersistLidMappingAndUserAttributes
     *           (function {@code c}/{@code d}).
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipLidUtils", exports = "attemptPersistLidMappingAndUserAttributes",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void attemptPersistLidMappingAndUserAttributes(Jid jid, Jid phoneNumber, String pushName) {
        if (jid == null) {
            return;
        }

        // WAWebVoipLidUtils.d: var c = WAWebWidFactory.asUserWidOrThrow(i) — strips the device suffix.
        var userJid = jid.toUserJid();

        // WAWebVoipLidUtils.d: isStringNullOrEmpty(s) || WAWebHandlePushnameUpdate.updatePushname(i, s, false)
        if (pushName != null && !pushName.isBlank()) {
            updatePushname(userJid, pushName);
        }

        // WAWebVoipLidUtils.d: if (!!c.isLid()) { ... WAWebDBCreateLidPnMappings.createLidPnMappings({mappings: [{lid: c, pn: asUserWidOrThrow(l)}], flushImmediately: a, learningSource: "other"}) ... }
        if (!userJid.hasLidServer() || phoneNumber == null) {
            return;
        }
        // WAWebVoipLidUtils.d: username + usernameCallingPhoneNumberPrivacyEnabled short-circuit is omitted; Cobalt has no username runtime and always writes the mapping.
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
     * @implNote WAWebHandlePushnameUpdate.updatePushname: persists the
     *           push name to the contact table via
     *           {@code WAWebDBBulkPersistContact.persistContactUpdateBatched}.
     *           Cobalt writes directly to the flattened contact store.
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
     * @implNote WAWebHandleVoipCall function S.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleVoipCall", exports = "handleCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void sendCallReceipt(Node node, Jid to, String callId, Jid callCreator, String childTag) {
        var from = resolveReceiptFrom(to); // WAWebHandleVoipCall.S: from: e.isLid() ? getMeDeviceLidOrThrow() : getMePnUserOrThrow_DO_NOT_USE()
        var stanzaId = node.getAttributeAsString("id", null); // WAWebHandleVoipCall.g: stanzaId = e.attrString("id")
        if (from == null || stanzaId == null) {
            return;
        }

        var child = new NodeBuilder() // WAWebHandleVoipCall.S: i = WAWap.wap(childTag, {"call-id": p, "call-creator": JID(r)})
                .description(childTag)
                .attribute("call-id", callId)
                .attribute("call-creator", callCreator)
                .build();
        var receipt = new NodeBuilder() // WAWebHandleVoipCall.S: WAWap.wap("receipt", {to: JID(e), id: t, from: ...}, i)
                .description("receipt")
                .attribute("to", to)
                .attribute("from", from)
                .attribute("id", stanzaId)
                .content(child)
                .build();
        whatsapp.sendNodeWithNoResponse(receipt); // WAWebHandleVoipCall.S: WADeprecatedSendIq.deprecatedCastStanza(...)
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
     * @implNote WAWebHandleVoipCall function R:
     *           {@code WAWap.wap("ack", {to: JID(e), id: CUSTOM_STRING(t), class:"call", type: MAYBE_CUSTOM_STRING(n)})}.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleVoipCall", exports = "handleCall",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void sendCallAck(Node node, String type) {
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
     * @implNote ADAPTED: Cobalt does not track the local device LID
     *           separately, so for the LID branch it falls back to the
     *           user-level LID from
     *           {@link com.github.auties00.cobalt.store.WhatsAppStore#lid()}.
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
     * <p>If the JID uses the LID server, the corresponding phone-number JID
     * is looked up via the store's LID-to-phone mapping; when no mapping is
     * available, the LID user JID is returned unchanged. Otherwise the user
     * JID is returned directly (with the device suffix stripped).
     *
     * @param jid the {@link Jid} to resolve
     * @return the canonical user JID, or {@code null} if {@code jid} is
     *         {@code null}
     * @implNote ADAPTED: Cobalt stores chat keys as phone-number user JIDs
     *           when the mapping is known, so the LID chatJid resolution
     *           performs a store lookup that WA Web does inline through its
     *           reactive {@code Collections}.
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
     * @param call the {@link CallOffer} to notify listeners about
     * @implNote ADAPTED: WAWebHandleVoipCall hands incoming offers to the
     *           VoIP runtime via {@code WAWebHandleVoipCallOffer}. Cobalt
     *           replaces that handoff with a virtual-thread fan-out to
     *           registered
     *           {@link com.github.auties00.cobalt.client.WhatsAppClientListener}
     *           instances.
     */
    private void notifyCall(CallOffer call) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onCall(whatsapp, call));
        }
    }
}
