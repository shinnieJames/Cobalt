package com.github.auties00.cobalt.message.receive.stanza;

import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Parses incoming {@code <message>} stanzas into
 * {@link MessageReceiveStanza} instances.
 *
 * <p>This utility class extracts all structural metadata from the raw
 * XML node — addressing information (including LID/PN migration fields),
 * encryption payloads, bot and business metadata, reporting tokens,
 * broadcast participant lists, and all {@code <meta>} attributes —
 * without performing any decryption.  The resulting
 * {@link MessageReceiveStanza} is then passed to the receiving service
 * for decryption and processing.
 *
 * <p>The parsing logic mirrors WA Web's
 * {@code WAWebHandleMsgParser.incomingMsgParser}, extracting:
 * <ul>
 *   <li>Message addressing (from, participant, sender/recipient PN/LID)</li>
 *   <li>All {@code <enc>} child nodes with type, mediatype, ciphertext,
 *       count, and decrypt-fail attributes</li>
 *   <li>The {@code <device-identity>} payload for ADV validation</li>
 *   <li>Metadata from the {@code <meta>} child (polltype, event_type,
 *       origin, target_id, target_sender_jid, target_chat_jid, etc.)</li>
 *   <li>Bot info from the {@code <bot>} child</li>
 *   <li>Business info from the {@code <biz>} child and stanza attributes</li>
 *   <li>Reporting tokens from the {@code <reporting>} child</li>
 *   <li>Broadcast participant list from the {@code <participants>} child</li>
 *   <li>Stanza-level attributes (type, edit, notify, category, offline,
 *       addressing_mode)</li>
 * </ul>
 *
 * @apiNote WAWebHandleMsgParser.incomingMsgParser: the main parser
 * for incoming message stanzas in WA Web.
 */
public final class MessageReceiveStanzaParser {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private MessageReceiveStanzaParser() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Parses a raw {@code <message>} node into a
     * {@link MessageReceiveStanza}.
     *
     * <p>The {@code selfJid} parameter is used for the
     * {@code isMeAccount} checks required to distinguish peer-broadcast
     * and peer-status messages from other-broadcast and other-status.
     * When {@code null}, the parser falls back to conservative defaults.
     *
     * @param node    the incoming {@code <message>} node
     * @param selfJid the current user's JID (nullable), used for
     *                message type classification
     * @return the parsed stanza with all extracted metadata
     * @throws NullPointerException     if {@code node} is null
     * @throws IllegalArgumentException if required attributes are missing
     * @implNote WAWebHandleMsgParser.incomingMsgParser: extracts msgInfo,
     * msgMeta, encs, deviceIdentity, bizInfo, hsmInfo, paymentInfo,
     * rcat, msgBotInfo, and reportingTokenInfo.
     */
    public static MessageReceiveStanza parse(Node node, Jid selfJid) {
        Objects.requireNonNull(node, "node cannot be null");

        // Core attributes
        var id = node.getRequiredAttributeAsString("id");
        var timestampSeconds = node.getRequiredAttributeAsLong("t");
        var timestamp = Instant.ofEpochSecond(timestampSeconds);
        var fromJid = node.getRequiredAttributeAsJid("from");

        var stanzaType = node.getRequiredAttributeAsString("type");
        var editAttribute = node.getAttributeAsInt("edit", 0);
        var pushName = node.getAttributeAsString("notify", null);
        var category = node.getAttributeAsString("category", null);
        var offline = node.getAttributeAsString("offline", null);
        var addressingMode = node.getAttributeAsString("addressing_mode", null);

        // Participant
        var participant = node.getAttributeAsJid("participant", null);

        // LID/PN attributes
        var senderPn = node.getAttributeAsJid("sender_pn", null);
        var senderLid = node.getAttributeAsJid("sender_lid", null);
        var recipientPn = node.getAttributeAsJid("recipient_pn", null);
        var recipientLid = node.getAttributeAsJid("recipient_lid", null);
        var peerRecipientPn = node.getAttributeAsJid("peer_recipient_pn", null);
        var peerRecipientLid = node.getAttributeAsJid("peer_recipient_lid", null);
        var peerRecipientUsername = node.getAttributeAsString("peer_recipient_username", null);
        var recipientLatestLid = node.getAttributeAsJid("recipient_latest_lid", null);
        var recipientUsername = node.getAttributeAsString("recipient_username", null);
        var participantPn = node.getAttributeAsJid("participant_pn", null);
        var participantLid = node.getAttributeAsJid("participant_lid", null);
        var participantUsername = node.getAttributeAsString("participant_username", null);
        var username = node.getAttributeAsString("username", null);
        var displayName = node.getAttributeAsString("display_name", null);

        // Count
        var count = node.getAttributeAsInt("count", null);

        // isHsm
        var isHsm = node.getChild("hsm").isPresent();

        // Sender resolution
        var senderJid = resolveSender(fromJid, participant);

        // Encrypted payloads
        var encs = parseEncryptedPayloads(node);

        // Message type classification
        var messageType = resolveMessageType(fromJid, participant, selfJid, encs, category);

        // Device identity
        var deviceIdentity = node.getChild("device-identity")
                .flatMap(Node::toContentBytes)
                .orElse(null);

        // Unavailable
        var unavailableNode = node.getChild("unavailable", null);
        var unavailable = unavailableNode != null;
        var hostedUnavailable = unavailable
                && "true".equals(unavailableNode.getAttributeAsString("hosted").orElse(null));
        var viewOnceUnavailable = unavailable
                && "view_once".equals(unavailableNode.getAttributeAsString("type").orElse(null));

        // Meta node
        var metaNode = node.getChild("meta", null);
        String pollType = null;
        String eventType = null;
        String origin = null;
        var statusMentioned = false;
        String appdata = null;
        String bizSource = null;
        String threadMsgId = null;
        Jid threadMsgSenderJid = null;
        String targetId = null;
        Jid targetSenderJid = null;
        Jid targetChatJid = null;
        Jid targetChatJidLid = null;
        var capi = false;
        String contextSource = null;
        String senderCountryCode = null;
        if (metaNode != null) {
            // WAWebHandleMsgParser.C: pollType only populated when stanza type is "poll"
            if ("poll".equals(stanzaType)) {
                pollType = metaNode.getAttributeAsString("polltype", null);
            }
            // WAWebHandleMsgParser.C: eventType only populated when stanza type is "event"
            if ("event".equals(stanzaType)) {
                eventType = metaNode.getAttributeAsString("event_type", null);
            }
            origin = metaNode.getAttributeAsString("origin", null);
            statusMentioned = "true".equals(
                    metaNode.getAttributeAsString("status_mentioned").orElse(null));
            appdata = metaNode.getAttributeAsString("appdata", null);
            bizSource = metaNode.getAttributeAsString("biz_source", null);
            threadMsgId = metaNode.getAttributeAsString("thread_msg_id", null);
            threadMsgSenderJid = metaNode.getAttributeAsJid("thread_msg_sender_jid", null);
            targetId = metaNode.getAttributeAsString("target_id", null);
            targetSenderJid = metaNode.getAttributeAsJid("target_sender_jid", null);
            targetChatJid = metaNode.getAttributeAsJid("target_chat_jid", null);
            targetChatJidLid = metaNode.getAttributeAsJid("target_chat_jid_lid", null);
            capi = "true".equals(
                    metaNode.getAttributeAsString("capi").orElse(null));
            contextSource = metaNode.getAttributeAsString("context_source", null);
            senderCountryCode = metaNode.getAttributeAsString("sender_country_code", null);
        }

        // url_number / url_text
        var urlNumber = node.getChild("url_number").isPresent();
        var urlText = node.getChild("url_text").isPresent();

        // Bot info
        var botInfo = parseBotInfo(node);

        // Biz info
        var bizInfo = parseBizInfo(node);

        // Reporting info
        var reportingInfo = parseReportingInfo(node);

        // Broadcast participants
        var bclParticipants = parseBroadcastParticipants(node);

        // Payment info
        var paymentInfo = parsePaymentInfo(node);

        // Eph setting (stanza-level, for OTHER_BROADCAST)
        var ephSetting = node.getAttributeAsString("eph_setting", null);

        // rcat
        var rcat = node.getChild("rcat")
                .flatMap(Node::toContentBytes)
                .orElse(null);

        // HSM tag/category
        var hsmNode = node.getChild("hsm", null);
        String hsmTag = null;
        String hsmCategory = null;
        if (hsmNode != null) {
            hsmTag = hsmNode.getAttributeAsString("tag", null);
            hsmCategory = hsmNode.getAttributeAsString("category", null);
        }

        return new MessageReceiveStanza(
                id,
                timestamp,
                fromJid,
                senderJid,
                participant,
                messageType,
                editAttribute,
                pushName,
                category,
                offline,
                addressingMode,
                isHsm,
                count,
                senderPn,
                senderLid,
                recipientPn,
                recipientLid,
                peerRecipientPn,
                peerRecipientLid,
                peerRecipientUsername,
                recipientLatestLid,
                recipientUsername,
                participantPn,
                participantLid,
                participantUsername,
                username,
                displayName,
                stanzaType,
                unavailable,
                hostedUnavailable,
                viewOnceUnavailable,
                pollType,
                eventType,
                origin,
                urlNumber,
                urlText,
                statusMentioned,
                appdata,
                bizSource,
                threadMsgId,
                threadMsgSenderJid,
                targetId,
                targetSenderJid,
                targetChatJid,
                targetChatJidLid,
                capi,
                contextSource,
                senderCountryCode,
                encs,
                deviceIdentity,
                botInfo,
                bizInfo,
                reportingInfo,
                bclParticipants,
                paymentInfo,
                ephSetting,
                rcat,
                hsmTag,
                hsmCategory
        );
    }

    /**
     * Resolves the actual sender JID from the stanza addressing.
     *
     * <p>For group, broadcast, and status messages the sender is the
     * {@code participant} attribute.  For 1:1 chat messages the sender
     * is the {@code from} attribute.
     *
     * @param fromJid     the {@code from} attribute JID
     * @param participant the {@code participant} attribute JID (nullable)
     * @return the resolved sender JID
     * @throws IllegalArgumentException if a group/broadcast message is
     *                                  missing its participant attribute
     * @implNote WAWebHandleMsgParser function y(): sender =
     * (from.isGroup() || from.isBroadcast()) ? participant : from
     */
    private static Jid resolveSender(Jid fromJid, Jid participant) {
        if (fromJid.hasGroupOrCommunityServer() || fromJid.hasBroadcastServer()) {
            if (participant == null) {
                throw new IllegalArgumentException(
                        "Group/broadcast/status message from " + fromJid
                                + " missing participant attribute");
            }
            return participant;
        }
        return fromJid;
    }

    /**
     * Classifies the message type based on the {@code from} JID, the
     * {@code participant} attribute, and the current user's JID.
     *
     * <p>The classification mirrors WA Web's
     * {@code WAWebHandleMsgParser function C()}, which determines the
     * message type for the downstream processing pipeline.
     *
     * @param fromJid     the {@code from} attribute JID
     * @param participant the {@code participant} attribute JID (nullable)
     * @param selfJid     the current user's JID (nullable)
     * @param encs        the parsed encrypted payloads, used for the
     *                    isDirect check on status messages
     * @param category    the category of the message
     * @return the classified message type
     * @implNote WAWebHandleMsgParser function y(): determines CHAT, GROUP,
     * PEER_BROADCAST, OTHER_BROADCAST, DIRECT_PEER_STATUS, or OTHER_STATUS
     * based on the from JID type and participant presence.
     */
    private static MessageType resolveMessageType(
            Jid fromJid,
            Jid participant,
            Jid selfJid,
            List<MessageReceiveEncryptedPayload> encs,
            String category) {
        if (fromJid.hasUserServer() || fromJid.hasLidServer() || fromJid.hasBotServer()) {
            if("peer".equals(category)) {
                return MessageType.PEER_CHAT;
            } else {
                return MessageType.CHAT;
            }
        }

        if (fromJid.hasGroupOrCommunityServer()) {
            return MessageType.GROUP;
        }

        if (fromJid.hasBroadcastServer()) {
            var isStatus = fromJid.isStatusBroadcastAccount();
            var isSelf = isMeAccount(participant, selfJid);
            if (!isStatus) {
                return isSelf ? MessageType.PEER_BROADCAST : MessageType.OTHER_BROADCAST;
            }

            var isDirect = encs.stream().noneMatch(enc ->
                    enc.e2eType().isSenderKeyMessage());
            if (isSelf && isDirect) {
                return MessageType.DIRECT_PEER_STATUS;
            }
            return MessageType.OTHER_STATUS;
        }

        return MessageType.CHAT;
    }

    /**
     * Checks whether the given participant JID represents the current
     * user's account.
     *
     * <p>Comparison is performed on user JIDs (stripping device and
     * agent) to handle companion device addressing correctly.
     *
     * @param participant the participant JID to check (nullable)
     * @param selfJid     the current user's JID (nullable)
     * @return {@code true} if participant is the same account as selfJid
     * @implNote WAWebHandleMsgParser function y(): uses
     * isMeAccount(participant) which compares the participant's user JID
     * against the logged-in user.
     */
    private static boolean isMeAccount(Jid participant, Jid selfJid) {
        return selfJid != null
                && participant != null
                && participant.toUserJid().equals(selfJid.toUserJid());
    }

    /**
     * Parses all {@code <enc>} child nodes into encrypted payload objects.
     *
     * <p>Each {@code <enc>} node carries a Signal encryption type, an
     * optional media type, the raw ciphertext bytes, a retry count, and
     * an optional {@code decrypt-fail="hide"} attribute.
     *
     * @param node the parent {@code <message>} node
     * @return the list of parsed encrypted payloads
     * @implNote WAWebHandleMsgParser.incomingMsgParser: maps each enc
     * child extracting type, mediatype, ciphertext bytes, count, and
     * decrypt-fail.
     */
    private static List<MessageReceiveEncryptedPayload> parseEncryptedPayloads(Node node) {
        var encNodes = node.getChildren("enc");
        var payloads = new ArrayList<MessageReceiveEncryptedPayload>(encNodes.size());
        for (var encNode : encNodes) {
            var typeStr = encNode.getRequiredAttributeAsString("type");
            var e2eType = MessageEncryptionType.fromProtocolValue(typeStr);
            var encMediaType = encNode.getAttributeAsString("mediatype", null);
            var ciphertext = encNode.toContentBytes().orElse(null);
            if (ciphertext == null || ciphertext.length == 0) {
                continue;
            }
            var retryCount = encNode.getAttributeAsInt("count", 0);
            var hideFail = "hide".equals(
                    encNode.getAttributeAsString("decrypt-fail").orElse(null));
            payloads.add(new MessageReceiveEncryptedPayload(
                    e2eType, encMediaType, ciphertext, retryCount, hideFail));
        }
        return payloads;
    }

    /**
     * Parses the {@code <bot>} child node into a
     * {@link MessageReceiveBotInfo}, if present.
     *
     * <p>Extracts the bot sender timestamp, edit target ID, edit type,
     * body type, and business bot classification from the bot node's
     * attributes.
     *
     * @param node the parent {@code <message>} node
     * @return the parsed bot info, or {@code null} if no bot child exists
     * @implNote WAWebHandleMsgParser function b(): parses the bot node
     * to extract botSenderTimestampMs, botEditTargetId, botEditType,
     * botMsgBodyType, and bizBotType.
     */
    private static MessageReceiveBotInfo parseBotInfo(Node node) {
        var botNode = node.getChild("bot", null);
        if (botNode == null) {
            return null;
        }

        var senderTimestampMs = botNode.getAttributeAsString("sender_timestamp_ms", null);
        var editTargetId = botNode.getAttributeAsString("edit_target_id", null); // WAWebHandleMsgParser.b
        var editType = botNode.getAttributeAsString("edit", null); // WAWebHandleMsgParser.b
        var bodyType = botNode.getAttributeAsString("type", null); // WAWebHandleMsgParser.b
        var bizBotType = botNode.getAttributeAsString("biz_bot", null);
        return new MessageReceiveBotInfo(
                senderTimestampMs,
                editTargetId,
                editType,
                bodyType,
                bizBotType
        );
    }

    /**
     * Parses business information from the stanza and its {@code <biz>}
     * child node into a {@link MessageReceiveBizInfo}, if present.
     *
     * <p>Combines stanza-level attributes ({@code verified_name} int,
     * {@code verified_level} string, and the {@code <verified_name>}
     * child's bytes) with the {@code <biz>} node's attributes
     * ({@code actual_actors}, {@code host_storage},
     * {@code privacy_mode_ts}, {@code native_flow_name},
     * {@code campaign_id}) and child element presence checks for
     * buttons, list, and hsm envelopes.
     *
     * @param node the parent {@code <message>} node
     * @return the parsed biz info, or {@code null} if neither a
     *         {@code verified_name} attribute nor a {@code <biz>} child
     *         is present
     * @implNote WAWebHandleMsgParser function v(): parses verified_name,
     * verified_level, biz node (actual_actors, host_storage,
     * privacy_mode_ts, native_flow_name, campaign_id, button/list/hsm
     * envelope flags).
     */
    private static MessageReceiveBizInfo parseBizInfo(Node node) {
        var verifiedNameSerial = node.getAttributeAsInt("verified_name", null);
        var verifiedLevel = node.getAttributeAsString("verified_level", null);
        var verifiedNameCert = node.getChild("verified_name")
                .flatMap(Node::toContentBytes)
                .orElse(null);

        var bizNode = node.getChild("biz", null);
        if (verifiedNameSerial == null && verifiedLevel == null
                && verifiedNameCert == null && bizNode == null) {
            return null;
        }

        Integer actualActors = null;
        Integer hostStorage = null;
        Integer privacyModeTs = null;
        String nativeFlowName = null;
        String campaignId = null;
        var verifiedButtonsEnvelope = false;
        var verifiedListEnvelope = false;
        var verifiedHsmEnvelope = false;

        if (bizNode != null) {
            actualActors = bizNode.getAttributeAsInt("actual_actors", null);
            hostStorage = bizNode.getAttributeAsInt("host_storage", null);
            privacyModeTs = bizNode.getAttributeAsInt("privacy_mode_ts", null);
            campaignId = bizNode.getAttributeAsString("campaign_id", null);
            nativeFlowName = resolveNativeFlowName(bizNode);
            verifiedButtonsEnvelope = bizNode.getChild("buttons").isPresent();
            verifiedListEnvelope = bizNode.getChild("list").isPresent();
            verifiedHsmEnvelope = node.getChild("hsm").isPresent(); // WAWebHandleMsgParser.v: e.hasChild("hsm") checks message node
        }

        return new MessageReceiveBizInfo(
                verifiedNameCert,
                verifiedNameSerial != null ? verifiedNameSerial : -1, // WAWebHandleMsgParser.v: default -1
                verifiedLevel,
                nativeFlowName,
                campaignId,
                actualActors,
                hostStorage,
                privacyModeTs,
                verifiedButtonsEnvelope,
                verifiedListEnvelope,
                verifiedHsmEnvelope
        );
    }

    /**
     * Resolves the native flow name from a {@code <biz>} node.
     *
     * <p>First checks for a nested
     * {@code <interactive><native_flow name="...">} structure, and
     * falls back to a direct {@code native_flow_name} attribute on
     * the biz node.
     *
     * @param bizNode the {@code <biz>} child node
     * @return the native flow name, or {@code null} if not present
     * @implNote WAWebHandleMsgParser function v(): resolves native_flow_name
     * from either the nested interactive/native_flow structure or the
     * direct attribute.
     */
    private static String resolveNativeFlowName(Node bizNode) {
        var interactiveNode = bizNode.getChild("interactive", null);
        if (interactiveNode != null) {
            var nativeFlowNode = interactiveNode.getChild("native_flow", null);
            if (nativeFlowNode != null) {
                var name = nativeFlowNode.getAttributeAsString("name", null);
                if (name != null) {
                    return name;
                }
            }
        }
        return bizNode.getAttributeAsString("native_flow_name", null);
    }

    /**
     * Parses the {@code <reporting>} child node into a
     * {@link MessageReceiveReportingInfo}, if present.
     *
     * <p>Extracts the reporting token bytes and version from the
     * {@code <reporting_token>} child, and the reporting tag bytes
     * from the {@code <reporting_tag>} child.
     *
     * @param node the parent {@code <message>} node
     * @return the parsed reporting info, or {@code null} if no
     *         reporting child exists
     * @implNote WAWebHandleMsgParser function k(): parses the reporting
     * node to extract reporting_token (bytes + version),
     * reporting_tag (bytes), and stanzaTs.
     */
    private static MessageReceiveReportingInfo parseReportingInfo(Node node) {
        var reportingNode = node.getChild("reporting", null);
        if (reportingNode == null) {
            return null;
        }

        // WAWebHandleMsgParser function k(): stanzaTs = e.attrTime("t")
        var stanzaTs = Instant.ofEpochSecond(node.getRequiredAttributeAsLong("t"));

        var tokenNode = reportingNode.getChild("reporting_token", null);
        byte[] reportingToken = null;
        var version = 0;
        if (tokenNode != null) {
            reportingToken = tokenNode.toContentBytes().orElse(null);
            version = tokenNode.getAttributeAsInt("v", 0);
        }

        var reportingTag = reportingNode.getChild("reporting_tag")
                .flatMap(Node::toContentBytes)
                .orElse(null);

        return new MessageReceiveReportingInfo(
                stanzaTs,
                reportingToken,
                version,
                reportingTag
        );
    }

    /**
     * Parses payment information from the {@code <pay>} and/or
     * {@code <transaction>} children of the message stanza.
     *
     * <p>Both {@code <pay>} and {@code <transaction>} are direct children
     * of the message node (siblings, not nested).  The function returns
     * {@code null} when neither is present.
     *
     * @param node the parent {@code <message>} node
     * @return the parsed payment info, or {@code null} if neither pay nor
     *         transaction child exists
     * @implNote WAWebHandleMsgParser function R(): parses pay node type,
     * receiver JID string, transaction currency/amount/status/timestamp,
     * and novi/futureproof detection.
     */
    private static MessageReceivePaymentInfo parsePaymentInfo(Node node) {
        // WAWebHandleMsgParser.R: pay and transaction are siblings on the message node
        var payNode = node.getChild("pay", null);
        var transactionNode = node.getChild("transaction", null);

        if (payNode == null && transactionNode == null) {
            return null;
        }

        // WAWebHandleMsgParser.R: transaction node takes precedence over pay node
        if (transactionNode != null) {
            var currency = transactionNode.getAttributeAsString("currency", null);
            var amount1000 = transactionNode.getAttributeAsLong("amount_1000", null);
            var status = transactionNode.getAttributeAsString("status", null);
            var ts = transactionNode.getAttributeAsLong("t", null);
            var receiver = transactionNode.getAttributeAsString("receiver", null);
            return new MessageReceivePaymentInfo(
                    false,
                    receiver,
                    currency,
                    amount1000,
                    status,
                    ts
            );
        }

        // WAWebHandleMsgParser.R: pay node processing by type
        var payType = payNode.getAttributeAsString("type", null);
        if ("send".equals(payType)) {
            var currency = payNode.getAttributeAsString("currency", null);
            var amount1000 = payNode.getAttributeAsLong("amount_1000", null);
            var receiver = payNode.getAttributeAsString("receiver", null);
            if (receiver == null) {
                receiver = node.getAttributeAsString("recipient", null);
            }
            var ts = node.getAttributeAsLong("t", null);
            return new MessageReceivePaymentInfo(
                    false,
                    receiver,
                    currency,
                    amount1000,
                    null,
                    ts
            );
        }

        // WAWebHandleMsgParser.R: request and invite types have no payment data
        return null;
    }

    /**
     * Parses the {@code <participants>} child node into a list of
     * {@link MessageReceiveBroadcastParticipant} instances.
     *
     * <p>Each {@code <to>} child within the participants node
     * represents one recipient in the broadcast contact list.
     *
     * @param node the parent {@code <message>} node
     * @return the list of broadcast participants (empty if no
     *         participants child exists)
     * @implNote WAWebHandleMsgParser function y(): maps each {@code <to>}
     * child within {@code <participants>} to extract jid, eph_setting,
     * peer_recipient_lid, peer_recipient_pn, peer_recipient_username,
     * and recipient_latest_lid.
     */
    private static List<MessageReceiveBroadcastParticipant> parseBroadcastParticipants(Node node) {
        var participantsNode = node.getChild("participants", null);
        if (participantsNode == null) {
            return List.of();
        }

        var toNodes = participantsNode.getChildren("to");
        var participants = new ArrayList<MessageReceiveBroadcastParticipant>(toNodes.size());
        for (var toNode : toNodes) {
            var jid = toNode.getRequiredAttributeAsJid("jid");
            var ephSetting = toNode.getAttributeAsString("eph_setting", null);
            var peerRecipientLid = toNode.getAttributeAsJid("peer_recipient_lid", null);
            var peerRecipientPn = toNode.getAttributeAsJid("peer_recipient_pn", null);
            var peerRecipientUsername = toNode.getAttributeAsString("peer_recipient_username", null);
            var recipientLatestLid = toNode.getAttributeAsJid("recipient_latest_lid", null);
            participants.add(new MessageReceiveBroadcastParticipant(
                    jid,
                    ephSetting,
                    peerRecipientLid,
                    peerRecipientPn,
                    peerRecipientUsername,
                    recipientLatestLid
            ));
        }
        return participants;
    }
}
