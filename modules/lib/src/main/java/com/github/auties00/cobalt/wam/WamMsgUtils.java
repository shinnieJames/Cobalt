package com.github.auties00.cobalt.wam;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.Message;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.event.EncEventResponseMessage;
import com.github.auties00.cobalt.model.message.event.EventResponseMessage;
import com.github.auties00.cobalt.model.message.media.AudioMessage;
import com.github.auties00.cobalt.model.message.media.DocumentMessage;
import com.github.auties00.cobalt.model.message.media.ImageMessage;
import com.github.auties00.cobalt.model.message.media.StickerMessage;
import com.github.auties00.cobalt.model.message.media.StickerPackMessage;
import com.github.auties00.cobalt.model.message.media.VideoMessage;
import com.github.auties00.cobalt.model.message.interactive.InteractiveMessage;
import com.github.auties00.cobalt.model.message.interactive.InteractiveMessageContent;
import com.github.auties00.cobalt.wam.event.SendDocumentEventBuilder;
import com.github.auties00.cobalt.wam.type.AgentEngagementEnumType;
import com.github.auties00.cobalt.wam.type.BotType;
import com.github.auties00.cobalt.wam.type.DocumentType;
import com.github.auties00.cobalt.wam.type.E2eDeviceType;
import com.github.auties00.cobalt.wam.type.InvisibleMessageCategoryType;
import com.github.auties00.cobalt.wam.type.MediaType;
import com.github.auties00.cobalt.wam.type.MessageType;

import java.util.Map;

/**
 * Static helpers for mapping WhatsApp message payloads to their WAM
 * {@link MediaType} classification.
 *
 * <p>WhatsApp Web exposes an equivalent helper in
 * {@code WAWebWamMsgUtils} that every WAM logger calls before emitting a
 * {@code messageMediaType} property. Since several WAM events in Cobalt
 * surface the same classification, the mapping is centralised here so
 * every emission site produces identical enum values without duplicating
 * the long {@code switch} cascade.
 *
 * <p>Instances of this class are not meant to be created: all members are
 * {@code static}. The hidden private constructor prevents accidental
 * instantiation.
 *
 * @implNote Adapts {@code WAWebWamMsgUtils.getWamMediaType} which
 * switches on the WhatsApp Web {@code Msg.type} field. Cobalt uses the
 * sealed {@link Message} hierarchy instead, so every WA Web branch is
 * recovered via {@code instanceof} pattern matching over the resolved
 * {@link MessageContainer#content()}.
 */
@WhatsAppWebModule(moduleName = "WAWebWamMsgUtils")
public final class WamMsgUtils {
    /**
     * Prevents instantiation of this utility class.
     */
    private WamMsgUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns the WAM {@link MediaType} classification for the payload
     * carried by the given {@link ChatMessageInfo}.
     *
     * <p>The method delegates to {@link #getWamMediaType(MessageContainer)}
     * after resolving the wrapped message container. {@link MediaType#NONE}
     * is returned for unrecognised or unclassified message types.
     *
     * @param info the chat message info whose payload is being
     *             classified; must not be {@code null}
     * @return the WAM media-type classification for the resolved
     * payload, or {@link MediaType#NONE} if no matching classification
     * exists
     *
     * @implNote WAWebWamMsgUtils.getWamMediaType accepts a {@code Msg}
     * model; Cobalt's closest equivalent is {@link ChatMessageInfo},
     * so the helper unwraps {@link ChatMessageInfo#message()} before
     * forwarding to the container-level overload.
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "getWamMediaType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static MediaType getWamMediaType(ChatMessageInfo info) {
        return info == null ? MediaType.NONE : getWamMediaType(info.message());
    }

    /**
     * Returns the WAM {@link MediaType} classification for the resolved
     * content of the given {@link MessageContainer}.
     *
     * <p>The method mirrors WA Web's {@code getWamMediaType} switch table:
     * every branch of the WA Web function corresponds to an
     * {@code instanceof} check here. The mapping covers the common media
     * payloads used in PSA-style flows (image, video, GIF, document,
     * audio, PTT, sticker) plus the placeholder entries for the fallback
     * categories. Unrecognised types fall back to {@link MediaType#NONE}.
     *
     * @param container the container whose resolved content should be
     *                  classified; {@code null} yields
     *                  {@link MediaType#NONE}
     * @return the WAM media-type classification for the resolved
     * payload
     *
     * @implNote Adapts the {@code switch (e.type)} cascade in
     * {@code WAWebWamMsgUtils.getWamMediaType}. Only the branches that
     * Cobalt's message model can currently distinguish are implemented;
     * payloads that have no dedicated Cobalt counterpart fall through to
     * {@link MediaType#NONE}.
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "getWamMediaType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static MediaType getWamMediaType(MessageContainer container) {
        if (container == null) {
            return MediaType.NONE;
        }
        var content = container.content();
        return switch (content) {
            case ImageMessage ignored -> MediaType.PHOTO; // WAWebWamMsgUtils: case "image" -> MEDIA_TYPE.PHOTO
            case VideoMessage video -> video.gifPlayback() // WAWebWamMsgUtils: case "video" -> e.isGif ? GIF : VIDEO
                    ? MediaType.GIF
                    : MediaType.VIDEO;
            case AudioMessage audio -> audio.ptt() // WAWebWamMsgUtils: case "audio" -> AUDIO; case "ptt" -> PTT
                    ? MediaType.PTT
                    : MediaType.AUDIO;
            case DocumentMessage ignored -> MediaType.DOCUMENT; // WAWebWamMsgUtils: case "document" -> DOCUMENT
            case StickerMessage ignored -> MediaType.STICKER; // WAWebWamMsgUtils: case "sticker" -> STICKER
            case StickerPackMessage ignored -> MediaType.STICKER_PACK; // WAWebWamMsgUtils: case "sticker-pack" -> STICKER_PACK
            case com.github.auties00.cobalt.model.message.text.ReactionMessage ignored -> MediaType.REACTION; // WAWebWamMsgUtils: case "reaction"/"reaction_enc" -> REACTION
            case com.github.auties00.cobalt.model.message.security.EncReactionMessage ignored -> MediaType.REACTION; // WAWebWamMsgUtils: case "reaction_enc" -> REACTION
            case com.github.auties00.cobalt.model.message.poll.PollCreationMessage ignored -> MediaType.POLL_CREATE; // WAWebWamMsgUtils: case "poll_creation" -> POLL_CREATE
            case com.github.auties00.cobalt.model.message.poll.PollUpdateMessage ignored -> MediaType.POLL_VOTE; // WAWebWamMsgUtils: case "poll_update"/"poll_vote" -> POLL_VOTE
            case com.github.auties00.cobalt.model.message.contact.ContactMessage ignored -> MediaType.CONTACT; // WAWebWamMsgUtils: case "vcard" -> CONTACT
            case com.github.auties00.cobalt.model.message.contact.ContactsArrayMessage ignored -> MediaType.CONTACT_ARRAY; // WAWebWamMsgUtils: case "multi_vcard" -> CONTACT_ARRAY
            case com.github.auties00.cobalt.model.message.location.LocationMessage ignored ->
                    MediaType.LOCATION; // WAWebWamMsgUtils: case "location" (non-live) -> LOCATION
            case com.github.auties00.cobalt.model.message.location.LiveLocationMessage ignored -> MediaType.LIVE_LOCATION; // WAWebWamMsgUtils: case "location" when isLive -> LIVE_LOCATION
            case com.github.auties00.cobalt.model.message.commerce.ProductMessage ignored -> MediaType.PRODUCT_IMAGE; // WAWebWamMsgUtils: case "product" -> PRODUCT_IMAGE
            case com.github.auties00.cobalt.model.message.list.ListMessage ignored -> MediaType.LIST; // WAWebWamMsgUtils: case "list" (SINGLE_SELECT) -> LIST
            case com.github.auties00.cobalt.model.message.list.ListResponseMessage ignored -> MediaType.LIST_REPLY; // WAWebWamMsgUtils: case "list_response" -> LIST_REPLY
            case com.github.auties00.cobalt.model.message.commerce.OrderMessage ignored -> MediaType.ORDER; // WAWebWamMsgUtils: case "order" -> ORDER
            case EventResponseMessage ignored -> MediaType.EVENT_RESPOND; // WAWebWamMsgUtils: case "event_response" -> EVENT_RESPOND
            case EncEventResponseMessage ignored -> MediaType.EVENT_RESPOND; // WAWebWamMsgUtils: case "event_response" -> EVENT_RESPOND
            case com.github.auties00.cobalt.model.message.event.EventMessage ignored -> MediaType.EVENT_CREATE; // WAWebWamMsgUtils: case "event_creation" -> EVENT_CREATE
            case com.github.auties00.cobalt.model.message.media.AlbumMessage ignored -> MediaType.MEDIA_ALBUM; // WAWebWamMsgUtils: case "album" -> MEDIA_ALBUM
            case com.github.auties00.cobalt.model.message.system.PinInChatMessage ignored -> MediaType.PIN_IN_CHAT; // WAWebWamMsgUtils: case "pin_message" -> PIN_IN_CHAT
            case com.github.auties00.cobalt.model.message.text.ExtendedTextMessage ignored -> MediaType.TEXT; // WAWebWamMsgUtils: case "chat" -> default TEXT branch in m(e.matchedText)
            case null, default -> MediaType.NONE; // WAWebWamMsgUtils: default -> MEDIA_TYPE.NONE
        };
    }

    /**
     * Returns the WAM {@link MessageType} classification derived from the
     * chat JID carried by the given {@link ChatMessageInfo}.
     *
     * <p>The method mirrors WA Web's {@code WAWebWamMsgUtils.getWamMessageType}
     * switch on {@code e.isStatus() / e.isGroupMsg() / isBroadcast(e.id.remote)
     * / isNewsletter(e.id.remote)} fallback to
     * {@link MessageType#INDIVIDUAL}. The {@code STATUS} branch is
     * disambiguated before {@code BROADCAST} because status messages live on
     * the broadcast server but must not be reported as generic broadcasts.
     *
     * @param info the chat message info whose destination is being
     *             classified; {@code null} yields {@link MessageType#INDIVIDUAL}
     * @return the WAM message-type classification, defaulting to
     * {@link MessageType#INDIVIDUAL} when the destination does not match any
     * recognised server category
     *
     * @implNote Adapts the flat {@code if} cascade in
     * {@code WAWebWamMsgUtils.getWamMessageType}. Cobalt's message model does
     * not expose {@code getBroadcastId()} / non-status broadcasts distinctly,
     * so the helper treats any non-status broadcast JID as
     * {@link MessageType#BROADCAST}.
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "getWamMessageType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static MessageType getWamMessageType(ChatMessageInfo info) {
        if (info == null) {
            return MessageType.INDIVIDUAL;
        }
        var parent = info.key().parentJid().orElse(null);
        if (parent == null) {
            return MessageType.INDIVIDUAL;
        }
        return getWamMessageType(parent);
    }

    /**
     * Returns the WAM {@link MessageType} classification derived from the
     * given chat JID.
     *
     * <p>The method is the JID-level fan-out of
     * {@link #getWamMessageType(ChatMessageInfo)}: it classifies purely based
     * on the server component of the supplied JID.
     *
     * @param chatJid the chat JID whose server is being classified;
     *                {@code null} yields {@link MessageType#INDIVIDUAL}
     * @return the WAM message-type classification, defaulting to
     * {@link MessageType#INDIVIDUAL} when the server is a user / LID / bot
     * domain or unrecognised
     *
     * @implNote Adapts the {@code WAWebWamMsgUtils.getWamMessageType} fallback
     * table; the {@code STATUS} check precedes the {@code BROADCAST} check to
     * match WA Web's disambiguation order.
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "getWamMessageType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static MessageType getWamMessageType(Jid chatJid) {
        if (chatJid == null) {
            return MessageType.INDIVIDUAL;
        }
        if (chatJid.isStatusBroadcastAccount()) { // WAWebMsgGetters.getIsStatus -> MESSAGE_TYPE.STATUS
            return MessageType.STATUS;
        }
        if (chatJid.hasGroupOrCommunityServer()) { // WAWebMsgGetters.getIsGroupMsg -> MESSAGE_TYPE.GROUP
            return MessageType.GROUP;
        }
        if (chatJid.hasBroadcastServer()) { // WAWebWid.isBroadcast -> MESSAGE_TYPE.BROADCAST
            return MessageType.BROADCAST;
        }
        if (chatJid.hasNewsletterServer()) { // WAWebWid.isNewsletter -> MESSAGE_TYPE.CHANNEL
            return MessageType.CHANNEL;
        }
        return MessageType.INDIVIDUAL; // WAWebWamMsgUtils: default -> MESSAGE_TYPE.INDIVIDUAL
    }

    /**
     * Returns the WAM {@link MessageType} classification derived from the
     * stanza-level {@link com.github.auties00.cobalt.message.receive.stanza.MessageType}
     * enum produced during parsing.
     *
     * <p>WA Web feeds {@code msgInfo.type} (a string of {@code chat /
     * group / peer_broadcast / other_broadcast / direct_peer_status /
     * other_status}) into {@code getMessageTypeFromMsgInfoType}, which
     * normalises broadcasts into {@link MessageType#BROADCAST} and status
     * flavours into {@link MessageType#STATUS}. Cobalt classifies the
     * stanza once during parsing into
     * {@link com.github.auties00.cobalt.message.receive.stanza.MessageType},
     * so this helper performs the equivalent normalisation over that enum
     * directly.
     *
     * @param stanzaType the parser-level message type; {@code null}
     *                   yields {@link MessageType#INDIVIDUAL}
     * @return the WAM message-type classification, defaulting to
     * {@link MessageType#INDIVIDUAL} when the parser-level type maps to
     * {@code CHAT} or {@code PEER_CHAT} or is {@code null}
     *
     * @implNote Adapts {@code WAWebWamMsgUtils.getMessageTypeFromMsgInfoType}.
     * The WA Web function throws on unmatched values; Cobalt falls back to
     * {@link MessageType#INDIVIDUAL} so callers in the receive pipeline
     * cannot crash on unexpected enum values introduced upstream.
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "getMessageTypeFromMsgInfoType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static MessageType getWamMessageTypeFromStanzaType(
            com.github.auties00.cobalt.message.receive.stanza.MessageType stanzaType
    ) {
        if (stanzaType == null) {
            return MessageType.INDIVIDUAL;
        }
        return switch (stanzaType) {
            // WAWebWamMsgUtils.getMessageTypeFromMsgInfoType: "chat" -> INDIVIDUAL
            case CHAT, PEER_CHAT -> MessageType.INDIVIDUAL;
            // WAWebWamMsgUtils.getMessageTypeFromMsgInfoType: "group" -> GROUP
            case GROUP -> MessageType.GROUP;
            // WAWebWamMsgUtils.getMessageTypeFromMsgInfoType: "peer_broadcast"/"other_broadcast" -> BROADCAST
            case PEER_BROADCAST, OTHER_BROADCAST -> MessageType.BROADCAST;
            // WAWebWamMsgUtils.getMessageTypeFromMsgInfoType: "direct_peer_status"/"other_status" -> STATUS
            case DIRECT_PEER_STATUS, OTHER_STATUS -> MessageType.STATUS;
        };
    }

    /**
     * Returns the WAM {@link E2eDeviceType} classification of the sender
     * JID relative to the current account.
     *
     * <p>The classification tree mirrors WA Web's {@code getWamE2eSenderType}:
     * the sender is first bucketed as {@code MY} (current account) or
     * {@code OTHER} based on whether its user JID matches the stored
     * self-JID; within each bucket the sender is further classified as
     * {@code PRIMARY}, {@code COMPANION}, or {@code HOSTED_COMPANION}
     * based on the device id and server domain.
     *
     * @param senderJid the sender's full device JID; {@code null} yields
     *                  {@code null}
     * @param selfJid   the logged-in account's primary JID; may be
     *                  {@code null} when the account is not yet bound
     * @return the WAM classification, or {@code null} when the sender is
     * not a user/LID JID, matching WA Web's {@code instanceof Wid} guard
     *
     * @implNote Adapts {@code WAWebWamMsgUtils.getWamE2eSenderType}.
     * WA Web's {@code e instanceof Wid} gate is approximated here by
     * accepting any non-null JID: the receive pipeline only ever calls
     * the helper with a resolved sender JID parsed from a stanza, so the
     * check is redundant. {@code isMeAccount} is realised through the
     * {@code selfJid.toUserJid().equals(senderJid.toUserJid())} check
     * that the rest of the receive pipeline uses.
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "getWamE2eSenderType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static E2eDeviceType getWamE2eSenderType(Jid senderJid, Jid selfJid) {
        if (senderJid == null) {
            return null;
        }
        // WAWebWamMsgUtils.getWamE2eSenderType: e instanceof Wid filter; Cobalt only accepts user/LID JIDs here
        if (!senderJid.hasUserServer() && !senderJid.hasLidServer()
                && !senderJid.hasHostedServer() && !senderJid.hasHostedLidServer()) {
            return null;
        }
        var isMe = selfJid != null
                && selfJid.toUserJid().equals(senderJid.toUserJid());
        var isCompanion = senderJid.hasDevice(); // WAWebWid.isCompanion: device != 0
        var isHosted = senderJid.hasHostedServer() || senderJid.hasHostedLidServer();
        if (isMe) {
            if (isCompanion) {
                // WAWebWamMsgUtils.getWamE2eSenderType: MY + companion + hosted -> MY_HOSTED_COMPANION
                return isHosted ? E2eDeviceType.MY_HOSTED_COMPANION : E2eDeviceType.MY_COMPANION;
            }
            // WAWebWamMsgUtils.getWamE2eSenderType: MY + primary -> MY_PRIMARY
            return E2eDeviceType.MY_PRIMARY;
        }
        if (isCompanion) {
            // WAWebWamMsgUtils.getWamE2eSenderType: OTHER + companion + hosted -> OTHER_HOSTED_COMPANION
            return isHosted ? E2eDeviceType.OTHER_HOSTED_COMPANION : E2eDeviceType.OTHER_COMPANION;
        }
        // WAWebWamMsgUtils.getWamE2eSenderType: OTHER + primary -> OTHER_PRIMARY
        return E2eDeviceType.OTHER_PRIMARY;
    }

    /**
     * Returns the WAM {@link MediaType} classification for an interactive
     * message based on its body variant.
     *
     * <p>The classification mirrors WA Web's {@code getInteractiveWamType}:
     * shop storefront variants map to {@link MediaType#SHOP_STOREFRONT},
     * carousels map to {@link MediaType#INTERACTIVE_CAROUSEL}, and native
     * flow variants delegate to {@link #getInteractiveNativeFlowWamType}
     * to disambiguate {@code CTA_FLOW} ({@link MediaType#NONE}) from any
     * other native flow ({@link MediaType#INTERACTIVE_NFM}).
     *
     * @param interactive the interactive message whose body variant is
     *                    being classified; {@code null} yields
     *                    {@link MediaType#NONE}
     * @return the WAM media-type classification for the interactive
     * variant, or {@link MediaType#NONE} when no variant is set
     *
     * @implNote Adapts {@code WAWebWamMsgUtils.getInteractiveWamType}.
     * WA Web reads {@code e.interactiveType} (a string from
     * {@code WAWebInteractiveMessageType}); Cobalt switches on the
     * {@link InteractiveMessageContent} sealed hierarchy returned by
     * {@link InteractiveMessage#content()}, which carries the same
     * variant information.
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "getInteractiveWamType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static MediaType getInteractiveWamType(InteractiveMessage interactive) {
        if (interactive == null) {
            return MediaType.NONE;
        }
        InteractiveMessageContent content = interactive.content().orElse(null);
        if (content == null) {
            // WAWebWamMsgUtils.getInteractiveWamType: t == null -> MEDIA_TYPE.NONE
            return MediaType.NONE;
        }
        return switch (content) {
            // WAWebWamMsgUtils.getInteractiveWamType: case SHOPS_STOREFRONT -> SHOP_STOREFRONT
            case InteractiveMessage.ShopMessage ignored -> MediaType.SHOP_STOREFRONT;
            // WAWebWamMsgUtils.getInteractiveWamType: case CAROUSEL -> INTERACTIVE_CAROUSEL
            case InteractiveMessage.CarouselMessage ignored -> MediaType.INTERACTIVE_CAROUSEL;
            // WAWebWamMsgUtils.getInteractiveWamType: case NATIVE_FLOW -> d(e)
            case InteractiveMessage.NativeFlowMessage native_ -> getInteractiveNativeFlowWamType(native_);
            // Cobalt-only branch: collection messages have no WA Web counterpart in this mapper
            case InteractiveMessage.CollectionMessage ignored -> MediaType.NONE;
        };
    }

    /**
     * Disambiguates the WAM {@link MediaType} for an interactive native
     * flow message based on the resolved native flow name.
     *
     * <p>WA Web treats the {@code CTA_FLOW} (galaxy) variant as
     * {@link MediaType#NONE} since it is filtered from interactive WAM
     * reporting; any other native flow name falls through to
     * {@link MediaType#INTERACTIVE_NFM}.
     *
     * @param nativeFlow the native flow message whose name drives the
     *                   classification; must not be {@code null}
     * @return {@link MediaType#NONE} when the native flow is the
     * {@code CTA_FLOW} (galaxy) variant, otherwise
     * {@link MediaType#INTERACTIVE_NFM}
     *
     * @implNote Adapts {@code WAWebWamMsgUtils.d}, the inner helper that
     * post-processes a native flow interactive payload.
     * {@code WAWebInteractiveMessagesNativeFlowName.CTA_FLOW} resolves to
     * the literal {@code "galaxy_message"}, which Cobalt matches via the
     * first button name on the native flow message.
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "getInteractiveWamType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static MediaType getInteractiveNativeFlowWamType(InteractiveMessage.NativeFlowMessage nativeFlow) {
        for (var button : nativeFlow.buttons()) {
            var name = button.name().orElse(null);
            // WAWebInteractiveMessagesNativeFlowName.CTA_FLOW -> "galaxy_message"
            if ("galaxy_message".equals(name)) {
                return MediaType.NONE;
            }
        }
        return MediaType.INTERACTIVE_NFM;
    }

    /**
     * Returns the WAM {@link AgentEngagementEnumType} classification for a
     * message exchanged with a bot.
     *
     * <p>The classification mirrors WA Web's {@code getWamAgentEngagementType}:
     * messages whose chat JID is itself a bot map to
     * {@link AgentEngagementEnumType#DIRECT_CHAT}; messages whose payload is
     * recognised as a bot query or a Meta-bot response map to
     * {@link AgentEngagementEnumType#INVOKED}; otherwise the helper returns
     * {@code null} so callers can omit the property from the WAM event.
     *
     * @param chatJid       the chat JID that hosts the message; {@code null}
     *                      yields {@code null}
     * @param isBotInvoked  {@code true} when the message originates from a
     *                      bot query or Meta-bot response; this corresponds
     *                      to the disjunction of WA Web's
     *                      {@code getIsBotQuery} and {@code getIsMetaBotResponse}
     * @return the WAM agent-engagement classification, or {@code null}
     * when the message is unrelated to a bot conversation
     *
     * @implNote Adapts {@code WAWebWamMsgUtils.getWamAgentEngagementType}.
     * Cobalt does not track {@code isBotQuery} / {@code isMetaBotResponse}
     * on its message model, so the disjunction is exposed as an explicit
     * caller-supplied flag rather than recomputed here. The
     * {@code remote.isBot()} guard is realised through {@link Jid#isBot()}.
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "getWamAgentEngagementType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static AgentEngagementEnumType getWamAgentEngagementType(Jid chatJid, boolean isBotInvoked) {
        if (chatJid == null) {
            return null;
        }
        if (chatJid.isBot()) {
            // WAWebWamMsgUtils.getWamAgentEngagementType: e.id.remote.isBot() -> DIRECT_CHAT
            return AgentEngagementEnumType.DIRECT_CHAT;
        }
        if (isBotInvoked) {
            // WAWebWamMsgUtils.getWamAgentEngagementType: getIsBotQuery || getIsMetaBotResponse -> INVOKED
            return AgentEngagementEnumType.INVOKED;
        }
        return null;
    }

    /**
     * Returns the WAM {@link BotType} classification of a bot interaction.
     *
     * <p>The classification mirrors WA Web's {@code getWamBotType}: a
     * Meta-bot JID maps to {@link BotType#METABOT}; a 1P business bot
     * (either via the {@code BizBotType.BIZ_1P} flag or the
     * {@code BizBotAutomatedType.PARTIAL_1P} automated flag) maps to
     * {@link BotType#BOT_1P_BIZ}; a 3P business bot (via
     * {@code BizBotType.BIZ_3P} or {@code BizBotAutomatedType.FULL_3P})
     * maps to {@link BotType#BOT_3P_BIZ}; everything else falls through to
     * {@link BotType#UNKNOWN}.
     *
     * @param botJid        the JID involved in the bot interaction;
     *                      may be {@code null}
     * @param is1pBizBot    {@code true} when WA Web would classify the
     *                      interaction as {@code BizBotType.BIZ_1P} or
     *                      {@code BizBotAutomatedType.PARTIAL_1P}
     * @param is3pBizBot    {@code true} when WA Web would classify the
     *                      interaction as {@code BizBotType.BIZ_3P} or
     *                      {@code BizBotAutomatedType.FULL_3P}
     * @return the matching WAM bot type, defaulting to
     * {@link BotType#UNKNOWN}
     *
     * @implNote Adapts {@code WAWebWamMsgUtils.getWamBotType}. Cobalt does
     * not currently model {@code BizBotType} or {@code BizBotAutomatedType}
     * as Java enums, so the two boolean flags are exposed in their stead;
     * any future {@code BizBotType} enum can be added here without
     * disturbing callers.
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "getWamBotType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static BotType getWamBotType(Jid botJid, boolean is1pBizBot, boolean is3pBizBot) {
        if (botJid != null && botJid.isBot()) {
            // WAWebWamMsgUtils.getWamBotType: e?.isBot() -> METABOT
            return BotType.METABOT;
        }
        if (is1pBizBot) {
            // WAWebWamMsgUtils.getWamBotType: BizBotType.BIZ_1P / BizBotAutomatedType.PARTIAL_1P -> BOT_1P_BIZ
            return BotType.BOT_1P_BIZ;
        }
        if (is3pBizBot) {
            // WAWebWamMsgUtils.getWamBotType: BizBotType.BIZ_3P / BizBotAutomatedType.FULL_3P -> BOT_3P_BIZ
            return BotType.BOT_3P_BIZ;
        }
        // WAWebWamMsgUtils.getWamBotType: default -> UNKNOWN
        return BotType.UNKNOWN;
    }

    /**
     * Returns the WAM {@link InvisibleMessageCategoryType} classification
     * for the supplied stanza-level message category attribute.
     *
     * <p>WA Web defines a single recognised category, {@code MSG_CATEGORY.peer},
     * which maps to {@link InvisibleMessageCategoryType#PEER}; any other
     * value (including {@code null} and the empty string) yields
     * {@code null} so callers omit the property from the WAM event.
     *
     * @param category the {@code category} attribute carried on the
     *                 incoming stanza; may be {@code null}
     * @return {@link InvisibleMessageCategoryType#PEER} for {@code "peer"},
     * otherwise {@code null}
     *
     * @implNote Adapts {@code WAWebWamMsgUtils.getWamInvisibleMessageCatgoryType}.
     * The helper preserves WA Web's misspelling of {@code Catgory} in the
     * export name while exposing a corrected Java method name. The category
     * lookup uses the {@code WAWebHandleMsgCommon.MSG_CATEGORY} constants;
     * Cobalt mirrors that table inline because the only recognised value
     * is the literal {@code "peer"}.
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "getWamInvisibleMessageCatgoryType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static InvisibleMessageCategoryType getWamInvisibleMessageCategoryType(String category) {
        if (category == null || category.isEmpty()) {
            return null;
        }
        // WAWebHandleMsgCommon.MSG_CATEGORY.peer -> INVISIBLE_MESSAGE_CATEGORY_TYPE.PEER
        if ("peer".equals(category)) {
            return InvisibleMessageCategoryType.PEER;
        }
        return null;
    }

    /**
     * Returns whether any of the JIDs that participated in the message
     * exchange are LID-addressed.
     *
     * <p>WA Web's {@code msgIsLid} returns:
     * <ul>
     *   <li>for groups: the supplied {@code participantIsLid} flag
     *       (truthy-coerced).</li>
     *   <li>for status updates: whether the
     *       {@code key.id.participant} is a LID JID; missing values
     *       evaluate to {@code false}.</li>
     *   <li>otherwise: whether either the {@code from} or the {@code to}
     *       JID is LID-addressed.</li>
     * </ul>
     *
     * @param fromJid             the sender JID; may be {@code null}
     * @param toJid               the recipient JID; may be {@code null}
     * @param keyParticipantJid   the {@code key.participant} JID, used for
     *                            status updates; may be {@code null}
     * @param chatType            the WAM message-type classification of
     *                            the chat (used to disambiguate group /
     *                            status / other); must not be {@code null}
     * @param participantIsLid    {@code true} when the {@code participant}
     *                            attribute on a group stanza is LID-addressed
     * @return {@code true} when the relevant JID for the chat type is
     * LID-addressed, otherwise {@code false}
     *
     * @implNote Adapts {@code WAWebWamMsgUtils.msgIsLid}. The
     * {@code chatType.isGroup() / chatType.isStatus()} branch checks are
     * recovered here through the WAM {@link MessageType} classification
     * already produced by {@link #getWamMessageType(Jid)}, so callers do
     * not need a {@code Wid}-style helper. The fall-through case checks
     * both endpoints, matching WA Web's {@code e.from.isLid() || e.to.isLid()}.
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "msgIsLid",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static boolean msgIsLid(
            Jid fromJid,
            Jid toJid,
            Jid keyParticipantJid,
            MessageType chatType,
            boolean participantIsLid
    ) {
        if (chatType == MessageType.GROUP) {
            // WAWebWamMsgUtils.msgIsLid: t.isGroup() -> !!n
            return participantIsLid;
        }
        if (chatType == MessageType.STATUS) {
            // WAWebWamMsgUtils.msgIsLid: t.isStatus() -> e.id.participant?.isLid() ?? false
            return keyParticipantJid != null && keyParticipantJid.hasLidServer();
        }
        // WAWebWamMsgUtils.msgIsLid: default -> e.from.isLid() || e.to.isLid()
        var fromIsLid = fromJid != null && fromJid.hasLidServer();
        var toIsLid = toJid != null && toJid.hasLidServer();
        return fromIsLid || toIsLid;
    }

    /**
     * Fixed mapping from lower-case file extensions (without the leading
     * dot) to the corresponding WAM {@link DocumentType} bucket.
     *
     * <p>Populated verbatim from WA Web's
     * {@code WAWebProcessRawMediaLogging} extension table so that
     * {@link #logSendDocumentEvent(WamService, String, long)} produces
     * identical {@code documentType} / {@code documentExt} values for
     * every known extension. Unknown extensions fall back to
     * {@link DocumentType#OTHER} with an empty {@code documentExt}, again
     * mirroring WA Web.
     *
     * @implNote WAWebProcessRawMediaLogging: module-level {@code Map}
     * literal that seeds the classification.
     */
    @WhatsAppWebExport(moduleName = "WAWebProcessRawMediaLogging", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final Map<String, DocumentType> DOCUMENT_EXT_TO_TYPE = Map.ofEntries(
            Map.entry("ai", DocumentType.IMAGE),
            Map.entry("ico", DocumentType.IMAGE),
            Map.entry("jpeg", DocumentType.IMAGE),
            Map.entry("jpg", DocumentType.IMAGE),
            Map.entry("png", DocumentType.IMAGE),
            Map.entry("ps", DocumentType.IMAGE),
            Map.entry("psd", DocumentType.IMAGE),
            Map.entry("svg", DocumentType.IMAGE),
            Map.entry("tif", DocumentType.IMAGE),
            Map.entry("tiff", DocumentType.IMAGE),
            Map.entry("3g2", DocumentType.VIDEO),
            Map.entry("3gp", DocumentType.VIDEO),
            Map.entry("avi", DocumentType.VIDEO),
            Map.entry("flv", DocumentType.VIDEO),
            Map.entry("h264", DocumentType.VIDEO),
            Map.entry("m4v", DocumentType.VIDEO),
            Map.entry("mkv", DocumentType.VIDEO),
            Map.entry("mov", DocumentType.VIDEO),
            Map.entry("mp4", DocumentType.VIDEO),
            Map.entry("mpg", DocumentType.VIDEO),
            Map.entry("mpeg", DocumentType.VIDEO),
            Map.entry("rm", DocumentType.VIDEO),
            Map.entry("vob", DocumentType.VIDEO),
            // WAWebProcessRawMediaLogging: ["wmv", AUDIO] is exactly what the WA Web table has;
            // the classification is kept even though wmv is conventionally a video container.
            Map.entry("wmv", DocumentType.AUDIO),
            Map.entry("aif", DocumentType.AUDIO),
            Map.entry("cda", DocumentType.AUDIO),
            Map.entry("mpa", DocumentType.AUDIO),
            Map.entry("opus", DocumentType.AUDIO),
            Map.entry("ogg", DocumentType.AUDIO),
            Map.entry("wlp", DocumentType.AUDIO),
            Map.entry("amr", DocumentType.AUDIO),
            Map.entry("mp3", DocumentType.AUDIO),
            Map.entry("m4a", DocumentType.AUDIO),
            Map.entry("aac", DocumentType.AUDIO),
            Map.entry("wav", DocumentType.AUDIO),
            Map.entry("wma", DocumentType.AUDIO),
            Map.entry("pdf", DocumentType.DOCUMENT),
            Map.entry("doc", DocumentType.DOCUMENT),
            Map.entry("docx", DocumentType.DOCUMENT),
            Map.entry("ppt", DocumentType.DOCUMENT),
            Map.entry("pptx", DocumentType.DOCUMENT),
            Map.entry("xls", DocumentType.DOCUMENT),
            Map.entry("xlsx", DocumentType.DOCUMENT),
            Map.entry("txt", DocumentType.DOCUMENT),
            Map.entry("rtf", DocumentType.DOCUMENT),
            Map.entry("tex", DocumentType.DOCUMENT),
            Map.entry("csv", DocumentType.DOCUMENT),
            Map.entry("wpd", DocumentType.DOCUMENT),
            Map.entry("7z", DocumentType.COMPRESSED_FILE),
            Map.entry("arj", DocumentType.COMPRESSED_FILE),
            Map.entry("deb", DocumentType.COMPRESSED_FILE),
            Map.entry("pkg", DocumentType.COMPRESSED_FILE),
            Map.entry("rar", DocumentType.COMPRESSED_FILE),
            Map.entry("rpm", DocumentType.COMPRESSED_FILE),
            Map.entry("gz", DocumentType.COMPRESSED_FILE),
            Map.entry("z", DocumentType.COMPRESSED_FILE),
            Map.entry("zip", DocumentType.COMPRESSED_FILE),
            Map.entry("apk", DocumentType.EXECUTABLE),
            Map.entry("bat", DocumentType.EXECUTABLE),
            Map.entry("bin", DocumentType.EXECUTABLE),
            Map.entry("cgi", DocumentType.EXECUTABLE),
            Map.entry("pl", DocumentType.EXECUTABLE),
            Map.entry("com", DocumentType.EXECUTABLE),
            Map.entry("exe", DocumentType.EXECUTABLE),
            Map.entry("gadget", DocumentType.EXECUTABLE),
            Map.entry("jar", DocumentType.EXECUTABLE),
            Map.entry("msi", DocumentType.EXECUTABLE),
            Map.entry("py", DocumentType.EXECUTABLE),
            Map.entry("wsf", DocumentType.EXECUTABLE)
    );

    /**
     * Commits the {@code SendDocumentEvent} (id 2172) for an outgoing
     * document send.
     *
     * <p>Mirrors WA Web's {@code WAWebProcessRawMediaLogging.logSendDocumentEvent}:
     * splits the filename on {@code .} and takes the last segment as the
     * extension, looks it up in {@link #DOCUMENT_EXT_TO_TYPE} to resolve
     * the {@link DocumentType}, and populates {@code documentSize} with
     * the raw file size in bytes. When the filename has no extension or
     * the extension is not a known key, the {@code documentExt} property
     * is emitted as the empty string and the type falls back to
     * {@link DocumentType#OTHER}, matching WA Web.
     *
     * <p>The {@code documentPageSize} WAM property is declared in the
     * event spec but never populated by WA Web's emission site, so it is
     * intentionally left unset here too.
     *
     * @param wamService the {@link WamService} used to commit the event;
     *                   when {@code null} the emission is skipped so the
     *                   helper can safely be invoked before the WAM
     *                   pipeline is initialised
     * @param filename   the user-visible document filename; when
     *                   {@code null} the extension is resolved as the
     *                   empty string, matching WA Web's
     *                   {@code e?.split(".").pop() ?? ""} fallback
     * @param size       the raw decrypted document size in bytes
     *
     * @implNote WAWebProcessRawMediaLogging.logSendDocumentEvent: the
     * single call site that constructs and commits the event, invoked by
     * {@code WAWebProcessRawMedia.processRawMedia} when the selected
     * media is classified as {@code "document"}.
     */
    @WhatsAppWebExport(moduleName = "WAWebProcessRawMediaLogging", exports = "logSendDocumentEvent",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static void logSendDocumentEvent(WamService wamService, String filename, long size) {
        if (wamService == null) {
            return;
        }
        // WAWebProcessRawMediaLogging.logSendDocumentEvent:
        //   a = e == null ? undefined : e.split(".").pop() ?? ""
        //   i = s.has(a) ? a : ""
        //   l = s.get(i) ?? DOCUMENT_TYPE.OTHER
        String extension;
        if (filename == null || filename.isEmpty()) {
            extension = "";
        } else {
            var dotIndex = filename.lastIndexOf('.');
            var tail = dotIndex < 0 ? filename : filename.substring(dotIndex + 1);
            extension = tail.toLowerCase(java.util.Locale.ROOT);
        }
        var normalizedExt = DOCUMENT_EXT_TO_TYPE.containsKey(extension) ? extension : "";
        var documentType = DOCUMENT_EXT_TO_TYPE.getOrDefault(normalizedExt, DocumentType.OTHER);
        wamService.commit(new SendDocumentEventBuilder()
                // WAWebProcessRawMediaLogging.logSendDocumentEvent: documentSize = t (raw byte count)
                .documentSize((double) size)
                // WAWebProcessRawMediaLogging.logSendDocumentEvent: documentType = l
                .documentType(documentType)
                // WAWebProcessRawMediaLogging.logSendDocumentEvent: documentExt = i
                .documentExt(normalizedExt)
                .build());
    }
}
