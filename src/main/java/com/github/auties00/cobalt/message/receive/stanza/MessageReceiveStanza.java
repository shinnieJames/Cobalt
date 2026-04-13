package com.github.auties00.cobalt.message.receive.stanza;

import com.github.auties00.cobalt.model.jid.Jid;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Fully-parsed representation of an incoming {@code <message>} stanza.
 *
 * <p>Captures all structural metadata extracted from the raw XML node:
 * addressing information (including LID/PN migration fields), encryption
 * payloads, bot and business metadata, payment info, reporting tokens,
 * broadcast participant lists, and all {@code <meta>} attributes.
 *
 * <p>The parsing mirrors WA Web's {@code WAWebHandleMsgParser.incomingMsgParser},
 * which produces separate {@code msgInfo}, {@code msgMeta}, {@code encs},
 * {@code deviceIdentity}, {@code bizInfo}, {@code hsmInfo},
 * {@code paymentInfo}, {@code rcat}, {@code msgBotInfo}, and
 * {@code reportingTokenInfo} objects.  This class combines them into a
 * single cohesive container.
 *
 * @apiNote WAWebHandleMsgParser.incomingMsgParser: the main parser for
 * incoming message stanzas in WA Web.
 */
public final class MessageReceiveStanza {
    /**
     * Edit attribute indicating no edit.
     */
    public static final int EDIT_NONE = 0;

    /**
     * Edit attribute for message edits.
     */
    public static final int EDIT_MESSAGE = 1;

    /**
     * Edit attribute for pin-in-chat.
     */
    public static final int EDIT_PIN = 2;

    /**
     * Edit attribute for sender revoke.
     */
    public static final int EDIT_SENDER_REVOKE = 7;

    /**
     * Edit attribute for admin revoke.
     */
    public static final int EDIT_ADMIN_REVOKE = 8;

    /**
     * The constant context source value for channel invitations.
     *
     * @implNote WAWebHandleMsgCommon.CONTEXT_SOURCE
     */
    public static final String CONTEXT_SOURCE_CHANNELS_INVITATION = "channels_invitation";

    /**
     * The stanza {@code id} attribute.
     *
     * @apiNote WAWebHandleMsgParser: {@code e.attrString("id")}
     */
    private final String id;

    /**
     * The message timestamp parsed from the {@code t} attribute.
     *
     * @apiNote WAWebHandleMsgParser: {@code e.attrTime("t")}
     */
    private final Instant timestamp;

    /**
     * The chat JID derived from the {@code from} attribute.
     * For 1:1 messages this is the sender's user JID.
     * For groups, broadcasts, and status this is the group/broadcast JID.
     *
     * @apiNote WAWebHandleMsgParser: {@code jidWithTypeToWid(e.attrJidWithType("from"))}
     */
    private final Jid chatJid;

    /**
     * The actual sender's device JID.
     * For 1:1 messages this equals {@link #chatJid}.
     * For groups/broadcasts this is the {@code participant} attribute.
     *
     * @apiNote WAWebHandleMsgParser: {@code from.isGroup()||from.isBroadcast() ? participant : from}
     */
    private final Jid senderJid;

    /**
     * The {@code participant} attribute, present for group, broadcast, and
     * status messages.  Identifies the sender's device within the group.
     *
     * @apiNote WAWebHandleMsgParser: {@code e.attrDeviceJid("participant")}
     */
    private final Jid participant;

    /**
     * The classified message type derived from the stanza addressing.
     *
     * @apiNote WAWebHandleMsgParser function C(): determines CHAT, GROUP,
     * PEER_BROADCAST, OTHER_BROADCAST, DIRECT_PEER_STATUS, or OTHER_STATUS
     * based on the {@code from} JID type and participant presence.
     */
    private final MessageType messageType;

    /**
     * The {@code edit} attribute, defaulting to {@link #EDIT_NONE}.
     *
     * @apiNote WAWebHandleMsgParser: {@code e.maybeAttrInt("edit") ?? EDIT_ATTR.NONE}
     */
    private final int editAttribute;

    /**
     * The sender's push name from the {@code notify} attribute.
     *
     * @apiNote WAWebHandleMsgParser: {@code e.maybeAttrString("notify")}
     */
    private final String pushName;

    /**
     * The message category.  The only defined value is {@code "peer"}
     * for peer protocol messages.
     *
     * @implNote WAWebHandleMsgCommon.MSG_CATEGORY
     */
    private final String category;

    /**
     * The offline attribute value, present when the message was received
     * while the client was offline.
     *
     * @apiNote WAWebHandleMsgParser: {@code e.maybeAttrString("offline")}
     */
    private final String offline;

    /**
     * The addressing mode for group messages.  Valid values are
     * {@code "pn"} (phone number) and {@code "lid"} (LID).
     *
     * @implNote WAWebHandleMsgCommon.STANZA_MSG_ADDRESSING_MODE
     */
    private final String addressingMode;

    /**
     * Whether the stanza has an {@code <hsm>} child indicating a highly
     * structured message (business template).
     *
     * @apiNote WAWebHandleMsgParser: {@code e.hasChild("hsm")}
     */
    private final boolean isHsm;

    /**
     * The optional {@code count} attribute on the stanza.
     *
     * @apiNote WAWebHandleMsgParser: {@code e.maybeAttrInt("count")}
     */
    private final Integer count;

    /**
     * The sender's phone number JID from {@code sender_pn} attribute.
     * Used for LID-addressed groups to provide the PN mapping.
     *
     * @apiNote WAWebHandleMsgParser: {@code e.attrUserJid("sender_pn")}
     */
    private final Jid senderPn;

    /**
     * The sender's LID JID from {@code sender_lid} attribute.
     *
     * @apiNote WAWebHandleMsgParser: {@code e.attrUserJid("sender_lid")}
     */
    private final Jid senderLid;

    /**
     * The recipient's phone number JID from {@code recipient_pn} attribute.
     * Present on peer messages to identify the actual chat destination.
     *
     * @apiNote WAWebHandleMsgParser: {@code e.attrUserJid("recipient_pn")}
     */
    private final Jid recipientPn;

    /**
     * The recipient's LID from {@code recipient_lid} attribute.
     *
     * @apiNote WAWebHandleMsgParser: {@code e.attrLidUserJid("recipient_lid")}
     */
    private final Jid recipientLid;

    /**
     * The peer recipient's phone number from {@code peer_recipient_pn}.
     * Used on peer broadcast messages to identify the destination.
     *
     * @apiNote WAWebHandleMsgParser: {@code e.attrUserJid("peer_recipient_pn")}
     */
    private final Jid peerRecipientPn;

    /**
     * The peer recipient's LID from {@code peer_recipient_lid}.
     *
     * @apiNote WAWebHandleMsgParser: {@code e.attrLidUserJid("peer_recipient_lid")}
     */
    private final Jid peerRecipientLid;

    /**
     * The peer recipient's username from {@code peer_recipient_username}.
     *
     * @apiNote WAWebHandleMsgParser: {@code e.maybeAttrString("peer_recipient_username")}
     */
    private final String peerRecipientUsername;

    /**
     * The latest LID for the recipient from {@code recipient_latest_lid}.
     *
     * @apiNote WAWebHandleMsgParser: {@code e.attrLidUserJid("recipient_latest_lid")}
     */
    private final Jid recipientLatestLid;

    /**
     * The recipient's username from {@code recipient_username}.
     *
     * @apiNote WAWebHandleMsgParser: {@code e.maybeAttrString("recipient_username")}
     */
    private final String recipientUsername;

    /**
     * The group participant's phone number from {@code participant_pn}.
     *
     * @apiNote WAWebHandleMsgParser: {@code e.attrUserJid("participant_pn")}
     */
    private final Jid participantPn;

    /**
     * The group participant's LID from {@code participant_lid}.
     *
     * @apiNote WAWebHandleMsgParser: {@code e.attrLidUserJid("participant_lid")}
     */
    private final Jid participantLid;

    /**
     * The group participant's username from {@code participant_username}.
     *
     * @apiNote WAWebHandleMsgParser: {@code e.maybeAttrString("participant_username")}
     */
    private final String participantUsername;

    /**
     * The sender's username from the {@code username} attribute.
     *
     * @apiNote WAWebHandleMsgParser: {@code e.maybeAttrString("username")}
     */
    private final String username;

    /**
     * The sender's display name from the {@code display_name} attribute.
     *
     * @apiNote WAWebHandleMsgParser: {@code e.maybeAttrString("display_name")}
     */
    private final String displayName;

    /**
     * The stanza {@code type} attribute.  Valid values are
     * {@code "text"}, {@code "media"}, {@code "medianotify"},
     * {@code "pay"}, {@code "poll"}, {@code "reaction"}, and
     * {@code "event"}.
     *
     * @implNote WAWebHandleMsgCommon.STANZA_MSG_TYPES
     */
    private final String stanzaType;

    /**
     * Whether the message has an {@code <unavailable>} child, indicating
     * the actual payload is not present (fanout placeholder).
     *
     * @apiNote WAWebHandleMsgParser function b(): {@code e.hasChild("unavailable")}
     */
    private final boolean unavailable;

    /**
     * Whether the {@code <unavailable>} child has {@code hosted="true"},
     * indicating a hosted-device fanout placeholder.
     *
     * @apiNote WAWebHandleMsgParser: {@code unavailable.maybeAttrString("hosted") === "true"}
     */
    private final boolean hostedUnavailable;

    /**
     * Whether the {@code <unavailable>} child has {@code type="view_once"},
     * indicating a view-once fanout placeholder.
     *
     * @apiNote WAWebHandleMsgParser: {@code unavailable.maybeAttrString("type") === "view_once"}
     */
    private final boolean viewOnceUnavailable;

    /**
     * The {@code polltype} from the {@code <meta>} node.  Valid values are
     * {@code "creation"}, {@code "quiz_creation"}, {@code "vote"},
     * {@code "result_snapshot"}, and {@code "edit"}.
     *
     * @implNote WAWebHandleMsgCommon.POLL_TYPES
     */
    private final String pollType;

    /**
     * The {@code event_type} from the {@code <meta>} node.  Valid values are
     * {@code "creation"}, {@code "response"}, and {@code "edit"}.
     * Only populated when the stanza type is {@code "event"}.
     *
     * @implNote WAWebHandleMsgCommon.EVENT_TYPES
     */
    private final String eventType;

    /**
     * The {@code origin} from the {@code <meta>} node.  The only defined
     * value is {@code "ctwa"} (click-to-WhatsApp ads).
     *
     * @implNote WAWebHandleMsgCommon.STANZA_MSG_ORIGIN
     */
    private final String origin;

    /**
     * Whether the stanza has a {@code <url_number>} child.
     *
     * @apiNote WAWebHandleMsgParser: {@code e.hasChild("url_number")}
     */
    private final boolean urlNumber;

    /**
     * Whether the stanza has a {@code <url_text>} child.
     *
     * @apiNote WAWebHandleMsgParser: {@code e.hasChild("url_text")}
     */
    private final boolean urlText;

    /**
     * Whether the {@code <meta>} node has {@code status_mentioned="true"}.
     *
     * @apiNote WAWebHandleMsgParser: {@code meta.maybeAttrString("status_mentioned") === "true"}
     */
    private final boolean statusMentioned;

    /**
     * The {@code appdata} attribute from the {@code <meta>} node.
     * Valid values are {@code "default"}, {@code "member_tag"}, and
     * {@code "group_history"}.
     *
     * @implNote WAWebHandleMsgCommon.APPDATA
     */
    private final String appdata;

    /**
     * The {@code biz_source} attribute from the {@code <meta>} node.
     * The attribute name is defined as the constant {@code "biz_source"}.
     *
     * @implNote WAWebHandleMsgCommon.BIZ_SOURCE_ATTR
     */
    private final String bizSource;

    /**
     * The {@code thread_msg_id} from the {@code <meta>} node, identifying
     * the parent message of a comment thread.
     *
     * @apiNote WAWebHandleMsgParser: {@code meta.attrString("thread_msg_id")}
     */
    private final String threadMsgId;

    /**
     * The {@code thread_msg_sender_jid} from the {@code <meta>} node.
     *
     * @apiNote WAWebHandleMsgParser: {@code meta.attrJidWithType("thread_msg_sender_jid")}
     */
    private final Jid threadMsgSenderJid;

    /**
     * The {@code target_id} from the {@code <meta>} node, referencing
     * the parent message for addon messages (reactions, poll votes, etc.).
     *
     * @apiNote WAWebHandleMsgParser: {@code meta.attrString("target_id")}
     */
    private final String targetId;

    /**
     * The {@code target_sender_jid} from the {@code <meta>} node.
     *
     * @apiNote WAWebHandleMsgParser: {@code meta.attrJidWithType("target_sender_jid")}
     */
    private final Jid targetSenderJid;

    /**
     * The {@code target_chat_jid} from the {@code <meta>} node.
     *
     * @apiNote WAWebHandleMsgParser: {@code meta.attrJidWithType("target_chat_jid")}
     */
    private final Jid targetChatJid;

    /**
     * The {@code target_chat_jid_lid} from the {@code <meta>} node.
     *
     * @apiNote WAWebHandleMsgParser: {@code meta.attrJidWithType("target_chat_jid_lid")}
     */
    private final Jid targetChatJidLid;

    /**
     * Whether the {@code <meta>} node has {@code capi="true"}.
     *
     * @apiNote WAWebHandleMsgParser: {@code meta.attrString("capi") === "true"}
     */
    private final boolean capi;

    /**
     * The {@code context_source} from the {@code <meta>} node.
     * The known constant value is {@code "channels_invitation"},
     * defined by {@code WAWebHandleMsgCommon.CONTEXT_SOURCE}.
     *
     * @implNote WAWebHandleMsgCommon.CONTEXT_SOURCE
     */
    private final String contextSource;

    /**
     * The sender's country code parsed from the {@code <meta>} node's
     * {@code sender_country_code} attribute.
     *
     * @apiNote WAWebHandleMsgParser function T(): parses and validates
     * the ISO country code.
     */
    private final String senderCountryCode;

    /**
     * The list of encrypted payloads ({@code <enc>} child nodes).
     *
     * @apiNote WAWebHandleMsgParser: maps each {@code <enc>} child to an
     * object with e2eType, encMediaType, ciphertext, retryCount, hideFail.
     */
    private final List<MessageReceiveEncryptedPayload> encs;

    /**
     * The device identity bytes from the {@code <device-identity>} child
     * node, used for ADV (Account Device Verification) validation of
     * companion devices.
     *
     * @apiNote WAWebHandleMsgParser: {@code deviceIdentityNode.contentBytes()}
     */
    private final byte[] deviceIdentity;

    /**
     * Parsed data from the {@code <bot>} child node.
     *
     * @apiNote WAWebHandleMsgParser function v(): parses the bot node.
     */
    private final MessageReceiveBotInfo botInfo;

    /**
     * Parsed business information from the stanza and {@code <biz>} child.
     *
     * @apiNote WAWebHandleMsgParser function S(): parses verified_name,
     * verified_level, biz node attributes.
     */
    private final MessageReceiveBizInfo bizInfo;

    /**
     * Parsed reporting token information from the {@code <reporting>} child.
     *
     * @apiNote WAWebHandleMsgParser function I(): parses reporting_token
     * and reporting_tag.
     */
    private final MessageReceiveReportingInfo reportingInfo;

    /**
     * The broadcast contact list participants from the
     * {@code <participants>} child node.  Present for PEER_BROADCAST
     * and DIRECT_PEER_STATUS messages.
     *
     * @apiNote WAWebHandleMsgParser: maps each {@code <to>} child within
     * {@code <participants>} to extract jid, eph_setting, peer_recipient
     * mappings.
     */
    private final List<MessageReceiveBroadcastParticipant> bclParticipants;

    /**
     * Parsed payment information from the {@code <pay>} and
     * {@code <transaction>} children of the message stanza.
     *
     * @apiNote WAWebHandleMsgParser function L(): parses pay node type,
     * receiver JID, and transaction child.
     */
    private final MessageReceivePaymentInfo paymentInfo;

    /**
     * The content bytes of the {@code <rcat>} child node, used for
     * content binding verification.
     *
     * @apiNote WAWebHandleMsgParser: {@code rcat.contentBytes()}
     */
    private final byte[] rcat;

    /**
     * The {@code eph_setting} attribute from the stanza, present on
     * OTHER_BROADCAST messages for ephemeral message settings.
     *
     * @apiNote WAWebHandleMsgParser: {@code e.maybeAttrString("eph_setting")}
     */
    private final String ephSetting;

    /**
     * The {@code tag} attribute from the {@code <hsm>} child node.
     *
     * @apiNote WAWebHandleMsgParser function R(): {@code hsm.maybeAttrString("tag")}
     */
    private final String hsmTag;

    /**
     * The {@code category} attribute from the {@code <hsm>} child node.
     *
     * @apiNote WAWebHandleMsgParser function R(): {@code hsm.maybeAttrString("category")}
     */
    private final String hsmCategory;

    public MessageReceiveStanza(
            String id,
            Instant timestamp,
            Jid chatJid,
            Jid senderJid,
            Jid participant,
            MessageType messageType,
            int editAttribute,
            String pushName,
            String category,
            String offline,
            String addressingMode,
            boolean isHsm,
            Integer count,
            Jid senderPn,
            Jid senderLid,
            Jid recipientPn,
            Jid recipientLid,
            Jid peerRecipientPn,
            Jid peerRecipientLid,
            String peerRecipientUsername,
            Jid recipientLatestLid,
            String recipientUsername,
            Jid participantPn,
            Jid participantLid,
            String participantUsername,
            String username,
            String displayName,
            String stanzaType,
            boolean unavailable,
            boolean hostedUnavailable,
            boolean viewOnceUnavailable,
            String pollType,
            String eventType,
            String origin,
            boolean urlNumber,
            boolean urlText,
            boolean statusMentioned,
            String appdata,
            String bizSource,
            String threadMsgId,
            Jid threadMsgSenderJid,
            String targetId,
            Jid targetSenderJid,
            Jid targetChatJid,
            Jid targetChatJidLid,
            boolean capi,
            String contextSource,
            String senderCountryCode,
            List<MessageReceiveEncryptedPayload> encs,
            byte[] deviceIdentity,
            MessageReceiveBotInfo botInfo,
            MessageReceiveBizInfo bizInfo,
            MessageReceiveReportingInfo reportingInfo,
            List<MessageReceiveBroadcastParticipant> bclParticipants,
            MessageReceivePaymentInfo paymentInfo,
            String ephSetting,
            byte[] rcat,
            String hsmTag,
            String hsmCategory
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.chatJid = Objects.requireNonNull(chatJid, "chatJid");
        this.senderJid = Objects.requireNonNull(senderJid, "senderJid");
        this.participant = participant;
        this.messageType = Objects.requireNonNull(messageType, "messageType");
        this.editAttribute = editAttribute;
        this.pushName = pushName;
        this.category = category;
        this.offline = offline;
        this.addressingMode = addressingMode;
        this.isHsm = isHsm;
        this.count = count;
        this.senderPn = senderPn;
        this.senderLid = senderLid;
        this.recipientPn = recipientPn;
        this.recipientLid = recipientLid;
        this.peerRecipientPn = peerRecipientPn;
        this.peerRecipientLid = peerRecipientLid;
        this.peerRecipientUsername = peerRecipientUsername;
        this.recipientLatestLid = recipientLatestLid;
        this.recipientUsername = recipientUsername;
        this.participantPn = participantPn;
        this.participantLid = participantLid;
        this.participantUsername = participantUsername;
        this.username = username;
        this.displayName = displayName;
        this.stanzaType = Objects.requireNonNull(stanzaType, "stanzaType");
        this.unavailable = unavailable;
        this.hostedUnavailable = hostedUnavailable;
        this.viewOnceUnavailable = viewOnceUnavailable;
        this.pollType = pollType;
        this.eventType = eventType;
        this.origin = origin;
        this.urlNumber = urlNumber;
        this.urlText = urlText;
        this.statusMentioned = statusMentioned;
        this.appdata = appdata;
        this.bizSource = bizSource;
        this.threadMsgId = threadMsgId;
        this.threadMsgSenderJid = threadMsgSenderJid;
        this.targetId = targetId;
        this.targetSenderJid = targetSenderJid;
        this.targetChatJid = targetChatJid;
        this.targetChatJidLid = targetChatJidLid;
        this.capi = capi;
        this.contextSource = contextSource;
        this.senderCountryCode = senderCountryCode;
        this.encs = List.copyOf(Objects.requireNonNull(encs, "encs"));
        this.deviceIdentity = deviceIdentity;
        this.botInfo = botInfo;
        this.bizInfo = bizInfo;
        this.reportingInfo = reportingInfo;
        this.bclParticipants = bclParticipants != null ? List.copyOf(bclParticipants) : List.of();
        this.paymentInfo = paymentInfo;
        this.ephSetting = ephSetting;
        this.rcat = rcat;
        this.hsmTag = hsmTag;
        this.hsmCategory = hsmCategory;
    }

    /**
     * Returns the stanza {@code id} attribute.
     */
    public String id() { return id; }

    /**
     * Returns the message timestamp parsed from the {@code t} attribute.
     */
    public Instant timestamp() { return timestamp; }

    /**
     * Returns the chat JID derived from the {@code from} attribute.
     */
    public Jid chatJid() { return chatJid; }

    /**
     * Returns the actual sender's device JID.
     */
    public Jid senderJid() { return senderJid; }

    /**
     * Returns the optional {@code participant} attribute.
     */
    public Optional<Jid> participant() { return Optional.ofNullable(participant); }

    /**
     * Returns the classified message type.
     */
    public MessageType messageType() { return messageType; }

    /**
     * Returns the {@code edit} attribute value.
     */
    public int editAttribute() { return editAttribute; }

    /**
     * Returns the optional push name ({@code notify} attribute).
     */
    public Optional<String> pushName() { return Optional.ofNullable(pushName); }

    /**
     * Returns the optional message category.
     */
    public Optional<String> category() { return Optional.ofNullable(category); }

    /**
     * Returns the optional offline attribute value.
     */
    public Optional<String> offline() { return Optional.ofNullable(offline); }

    /**
     * Returns the optional addressing mode ("pn" or "lid").
     */
    public Optional<String> addressingMode() { return Optional.ofNullable(addressingMode); }

    /**
     * Returns whether the stanza has an {@code <hsm>} child.
     */
    public boolean isHsm() { return isHsm; }

    /**
     * Returns the optional {@code count} attribute.
     */
    public Optional<Integer> count() { return Optional.ofNullable(count); }

    /**
     * Returns the optional sender phone number JID.
     */
    public Optional<Jid> senderPn() { return Optional.ofNullable(senderPn); }

    /**
     * Returns the optional sender LID JID.
     */
    public Optional<Jid> senderLid() { return Optional.ofNullable(senderLid); }

    /**
     * Returns the optional recipient phone number JID.
     */
    public Optional<Jid> recipientPn() { return Optional.ofNullable(recipientPn); }

    /**
     * Returns the optional recipient LID.
     */
    public Optional<Jid> recipientLid() { return Optional.ofNullable(recipientLid); }

    /**
     * Returns the optional peer recipient phone number.
     */
    public Optional<Jid> peerRecipientPn() { return Optional.ofNullable(peerRecipientPn); }

    /**
     * Returns the optional peer recipient LID.
     */
    public Optional<Jid> peerRecipientLid() { return Optional.ofNullable(peerRecipientLid); }

    /**
     * Returns the optional peer recipient username.
     */
    public Optional<String> peerRecipientUsername() { return Optional.ofNullable(peerRecipientUsername); }

    /**
     * Returns the optional latest LID for the recipient.
     */
    public Optional<Jid> recipientLatestLid() { return Optional.ofNullable(recipientLatestLid); }

    /**
     * Returns the optional recipient username.
     */
    public Optional<String> recipientUsername() { return Optional.ofNullable(recipientUsername); }

    /**
     * Returns the optional participant phone number.
     */
    public Optional<Jid> participantPn() { return Optional.ofNullable(participantPn); }

    /**
     * Returns the optional participant LID.
     */
    public Optional<Jid> participantLid() { return Optional.ofNullable(participantLid); }

    /**
     * Returns the optional participant username.
     */
    public Optional<String> participantUsername() { return Optional.ofNullable(participantUsername); }

    /**
     * Returns the optional sender username.
     */
    public Optional<String> username() { return Optional.ofNullable(username); }

    /**
     * Returns the optional sender display name.
     */
    public Optional<String> displayName() { return Optional.ofNullable(displayName); }

    /**
     * Returns the stanza {@code type} attribute.
     */
    public String stanzaType() { return stanzaType; }

    /**
     * Returns whether the message is an unavailable fanout placeholder.
     */
    public boolean isUnavailable() { return unavailable; }

    /**
     * Returns whether the unavailable placeholder is for a hosted device.
     */
    public boolean isHostedUnavailable() { return hostedUnavailable; }

    /**
     * Returns whether the unavailable placeholder is for a view-once message.
     */
    public boolean isViewOnceUnavailable() { return viewOnceUnavailable; }

    /**
     * Returns the optional {@code polltype} from the {@code <meta>} node.
     */
    public Optional<String> pollType() { return Optional.ofNullable(pollType); }

    /**
     * Returns the optional {@code event_type} from the {@code <meta>} node.
     */
    public Optional<String> eventType() { return Optional.ofNullable(eventType); }

    /**
     * Returns the optional {@code origin} from the {@code <meta>} node.
     */
    public Optional<String> origin() { return Optional.ofNullable(origin); }

    /**
     * Returns whether the stanza has a {@code <url_number>} child.
     */
    public boolean urlNumber() { return urlNumber; }

    /**
     * Returns whether the stanza has a {@code <url_text>} child.
     */
    public boolean urlText() { return urlText; }

    /**
     * Returns whether the status message mentions the current user.
     */
    public boolean statusMentioned() { return statusMentioned; }

    /**
     * Returns the optional {@code appdata} from the {@code <meta>} node.
     */
    public Optional<String> appdata() { return Optional.ofNullable(appdata); }

    /**
     * Returns the optional {@code biz_source} from the {@code <meta>} node.
     */
    public Optional<String> bizSource() { return Optional.ofNullable(bizSource); }

    /**
     * Returns the optional {@code thread_msg_id} from the {@code <meta>} node.
     */
    public Optional<String> threadMsgId() { return Optional.ofNullable(threadMsgId); }

    /**
     * Returns the optional {@code thread_msg_sender_jid} from the {@code <meta>} node.
     */
    public Optional<Jid> threadMsgSenderJid() { return Optional.ofNullable(threadMsgSenderJid); }

    /**
     * Returns the optional {@code target_id} from the {@code <meta>} node.
     */
    public Optional<String> targetId() { return Optional.ofNullable(targetId); }

    /**
     * Returns the optional {@code target_sender_jid} from the {@code <meta>} node.
     */
    public Optional<Jid> targetSenderJid() { return Optional.ofNullable(targetSenderJid); }

    /**
     * Returns the optional {@code target_chat_jid} from the {@code <meta>} node.
     */
    public Optional<Jid> targetChatJid() { return Optional.ofNullable(targetChatJid); }

    /**
     * Returns the optional {@code target_chat_jid_lid} from the {@code <meta>} node.
     */
    public Optional<Jid> targetChatJidLid() { return Optional.ofNullable(targetChatJidLid); }

    /**
     * Returns whether the {@code <meta>} node has {@code capi="true"}.
     */
    public boolean isCapi() { return capi; }

    /**
     * Returns the optional {@code context_source} from the {@code <meta>} node.
     */
    public Optional<String> contextSource() { return Optional.ofNullable(contextSource); }

    /**
     * Returns the optional sender country code from the {@code <meta>} node.
     */
    public Optional<String> senderCountryCode() { return Optional.ofNullable(senderCountryCode); }

    /**
     * Returns the list of encrypted payloads ({@code <enc>} child nodes).
     */
    public List<MessageReceiveEncryptedPayload> encs() { return encs; }

    /**
     * Returns the optional device identity bytes for ADV validation.
     */
    public Optional<byte[]> deviceIdentity() { return Optional.ofNullable(deviceIdentity); }

    /**
     * Returns the optional parsed bot info from the {@code <bot>} child.
     */
    public Optional<MessageReceiveBotInfo> botInfo() { return Optional.ofNullable(botInfo); }

    /**
     * Returns the optional parsed business info.
     */
    public Optional<MessageReceiveBizInfo> bizInfo() { return Optional.ofNullable(bizInfo); }

    /**
     * Returns the optional parsed reporting token info.
     */
    public Optional<MessageReceiveReportingInfo> reportingInfo() { return Optional.ofNullable(reportingInfo); }

    /**
     * Returns the broadcast contact list participants.
     */
    public List<MessageReceiveBroadcastParticipant> bclParticipants() { return bclParticipants; }

    /**
     * Returns the optional parsed payment info.
     */
    public Optional<MessageReceivePaymentInfo> paymentInfo() { return Optional.ofNullable(paymentInfo); }

    /**
     * Returns the optional stanza-level {@code eph_setting} attribute.
     */
    public Optional<String> ephSetting() { return Optional.ofNullable(ephSetting); }

    /**
     * Returns the optional {@code <rcat>} content bytes.
     */
    public Optional<byte[]> rcat() { return Optional.ofNullable(rcat); }

    /**
     * Returns the optional HSM tag from the {@code <hsm>} child.
     */
    public Optional<String> hsmTag() { return Optional.ofNullable(hsmTag); }

    /**
     * Returns the optional HSM category from the {@code <hsm>} child.
     */
    public Optional<String> hsmCategory() { return Optional.ofNullable(hsmCategory); }

    /**
     * Returns the retry count from the first encrypted payload.
     *
     * @apiNote WAWebHandleMsg: uses {@code encs[0].retryCount} to
     * determine retry receipt count.
     */
    public OptionalInt retryCount() {
        if (encs.isEmpty()) {
            return OptionalInt.empty();
        }
        var count = encs.getFirst().retryCount();
        return count > 0 ? OptionalInt.of(count) : OptionalInt.empty();
    }

    /**
     * Returns whether any encrypted payload has {@code decrypt-fail="hide"}.
     *
     * @apiNote WAWebHandleMsg function v(): checks
     * {@code encs.some(e => e.hideFail)} to determine dedup eligibility.
     */
    public boolean hasHideFailPayload() {
        return encs.stream().anyMatch(MessageReceiveEncryptedPayload::hideFail);
    }

    /**
     * Returns whether the message was received while offline.
     *
     * @apiNote WAWebHandleMsg: {@code msgInfo.offline != null}
     */
    public boolean isOffline() {
        return offline != null;
    }

    /**
     * Returns whether this is a peer message ({@code category="peer"}).
     *
     * @return {@code true} if the category is {@code "peer"}
     * @implNote WAWebHandleMsgCommon.MSG_CATEGORY.peer
     */
    public boolean isPeer() {
        return "peer".equals(category);
    }

    /**
     * Returns whether all encrypted payloads use direct (non-skmsg)
     * encryption.
     *
     * @apiNote WAWebHandleMsgParser: {@code isDirect = encs.every(
     * e => e.e2eType !== Skmsg)}
     */
    public boolean isDirect() {
        return encs.stream().noneMatch(enc ->
                enc.e2eType().isSenderKeyMessage());
    }

    /**
     * Returns whether the sender is from a companion device (device &ne; 0).
     *
     * @apiNote WAWebMsgProcessingDecryptApi: validates ADV only when
     * {@code author.device != null && author.device !== 0}.
     */
    public boolean isCompanionDevice() {
        return senderJid.device() != 0;
    }

    /**
     * Returns whether there is any retry in the encrypted payloads.
     *
     * @apiNote WAWebHandleMsgParser: {@code encs.some(e => e.retryCount > 0)}
     */
    public boolean isRetry() {
        return encs.stream().anyMatch(enc -> enc.retryCount() > 0);
    }
}
