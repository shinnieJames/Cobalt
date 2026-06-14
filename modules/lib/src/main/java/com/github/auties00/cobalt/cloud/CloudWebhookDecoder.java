package com.github.auties00.cobalt.cloud;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.cloud.CloudAccountUpdate;
import com.github.auties00.cobalt.model.cloud.CloudBusinessCapabilityUpdate;
import com.github.auties00.cobalt.model.cloud.CloudFlowStatusUpdate;
import com.github.auties00.cobalt.model.cloud.CloudHistorySync;
import com.github.auties00.cobalt.model.cloud.CloudPhoneNumberUpdate;
import com.github.auties00.cobalt.model.cloud.CloudTemplateCategoryUpdate;
import com.github.auties00.cobalt.model.cloud.CloudTemplateQualityUpdate;
import com.github.auties00.cobalt.model.cloud.CloudTemplateStatusUpdate;
import com.github.auties00.cobalt.model.cloud.CloudUserPreferenceUpdate;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.model.message.MessageStatus;
import com.github.auties00.cobalt.model.message.commerce.ButtonsResponseMessage;
import com.github.auties00.cobalt.model.message.commerce.ButtonsResponseMessageBuilder;
import com.github.auties00.cobalt.model.message.list.ListResponseMessage;
import com.github.auties00.cobalt.model.message.list.ListResponseMessageBuilder;
import com.github.auties00.cobalt.model.message.list.ListResponseMessageSingleSelectReplyBuilder;
import com.github.auties00.cobalt.model.message.location.LocationMessageBuilder;
import com.github.auties00.cobalt.model.message.media.AudioMessageBuilder;
import com.github.auties00.cobalt.model.message.media.DocumentMessageBuilder;
import com.github.auties00.cobalt.model.message.media.ImageMessageBuilder;
import com.github.auties00.cobalt.model.message.media.StickerMessageBuilder;
import com.github.auties00.cobalt.model.message.media.VideoMessageBuilder;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessageBuilder;
import com.github.auties00.cobalt.model.message.text.ReactionMessageBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates inbound WhatsApp Cloud API webhook change values into Cobalt's universal message model.
 *
 * <p>The decoder is the inverse of {@link CloudMessageEncoder}: it reads the {@code messages[]},
 * {@code statuses[]}, and {@code contacts[]} arrays of a webhook change {@code value} and produces
 * {@link ChatMessageInfo} instances for inbound messages and {@link StatusUpdate} records for outbound
 * delivery receipts, mapping the Cloud status strings onto the shared {@link MessageStatus} model.
 * Inbound media is referenced by its Cloud media id, stored in the media message's
 * url field so a later download resolves it through the media edge.
 */
public final class CloudWebhookDecoder {
    /**
     * Private constructor; the decoder exposes only static behaviour.
     */
    private CloudWebhookDecoder() {

    }

    /**
     * A single outbound message status transition decoded from a {@code statuses[]} entry.
     *
     * @param info    the message descriptor carrying the key and the mapped {@link MessageStatus}
     * @param deleted whether the entry reported a {@code deleted} status rather than a delivery
     *                transition
     */
    public record StatusUpdate(ChatMessageInfo info, boolean deleted) {
    }

    /**
     * Decodes the inbound messages of a webhook change value.
     *
     * @param value the webhook change {@code value}
     * @return the decoded inbound messages, empty when the change carried none
     */
    public static List<ChatMessageInfo> decodeMessages(JSONObject value) {
        var messages = value.getJSONArray("messages");
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        var pushNames = pushNamesByWaId(value);
        var result = new ArrayList<ChatMessageInfo>();
        for (var index = 0; index < messages.size(); index++) {
            var message = messages.getJSONObject(index);
            result.add(decodeMessage(message, pushNames));
        }
        return result;
    }

    /**
     * Decodes the outbound status transitions of a webhook change value.
     *
     * @param value the webhook change {@code value}
     * @return the decoded status updates, empty when the change carried none
     */
    public static List<StatusUpdate> decodeStatuses(JSONObject value) {
        var statuses = value.getJSONArray("statuses");
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<StatusUpdate>();
        for (var index = 0; index < statuses.size(); index++) {
            var status = statuses.getJSONObject(index);
            var key = new MessageKeyBuilder()
                    .id(status.getString("id"))
                    .parentJid(userJid(status.getString("recipient_id")))
                    .fromMe(true)
                    .build();
            var statusValue = status.getString("status");
            var deleted = "deleted".equalsIgnoreCase(statusValue);
            var builder = new ChatMessageInfoBuilder()
                    .key(key)
                    .message(MessageContainer.empty());
            if (!deleted) {
                builder.status(toMessageStatus(statusValue));
            }
            var timestamp = status.getLong("timestamp");
            if (timestamp != null) {
                builder.timestamp(Instant.ofEpochSecond(timestamp));
            }
            result.add(new StatusUpdate(builder.build(), deleted));
        }
        return result;
    }

    /**
     * Maps a Cloud API {@code status} string onto the shared {@link MessageStatus} model.
     *
     * @param value the raw status string, for example {@code "delivered"}
     * @return the matching status, defaulting to {@link MessageStatus#SERVER_ACK} when the string is
     *         unrecognised
     */
    private static MessageStatus toMessageStatus(String value) {
        if (value == null) {
            return MessageStatus.SERVER_ACK;
        }
        return switch (value.toLowerCase()) {
            case "delivered" -> MessageStatus.DELIVERED;
            case "read" -> MessageStatus.READ;
            case "failed" -> MessageStatus.ERROR;
            default -> MessageStatus.SERVER_ACK;
        };
    }


    /**
     * Decodes a {@code message_template_status_update} change value.
     *
     * @param value the webhook change {@code value}
     * @return the decoded status transition
     */
    public static CloudTemplateStatusUpdate decodeTemplateStatus(JSONObject value) {
        Instant disableDate = null;
        var disableInfo = value.getJSONObject("disable_info");
        if (disableInfo != null) {
            disableDate = epochInstant(disableInfo.getLong("disable_date"));
        }
        return new CloudTemplateStatusUpdate(
                stringOrUnknown(value, "event"),
                idString(value, "message_template_id"),
                value.getString("message_template_name"),
                value.getString("message_template_language"),
                value.getString("reason"),
                disableDate);
    }

    /**
     * Decodes a {@code template_category_update} change value.
     *
     * @param value the webhook change {@code value}
     * @return the decoded category transition
     */
    public static CloudTemplateCategoryUpdate decodeTemplateCategory(JSONObject value) {
        return new CloudTemplateCategoryUpdate(
                idString(value, "message_template_id"),
                value.getString("message_template_name"),
                value.getString("message_template_language"),
                value.getString("previous_category"),
                stringOrUnknown(value, "new_category"),
                value.getString("correct_category"));
    }

    /**
     * Decodes a {@code message_template_quality_update} change value.
     *
     * @param value the webhook change {@code value}
     * @return the decoded quality transition
     */
    public static CloudTemplateQualityUpdate decodeTemplateQuality(JSONObject value) {
        return new CloudTemplateQualityUpdate(
                idString(value, "message_template_id"),
                value.getString("message_template_name"),
                value.getString("message_template_language"),
                value.getString("previous_quality_score"),
                stringOrUnknown(value, "new_quality_score"));
    }

    /**
     * Decodes a {@code phone_number_name_update} change value.
     *
     * @param value the webhook change {@code value}
     * @return the decoded display-name review outcome
     */
    public static CloudPhoneNumberUpdate decodePhoneNumberName(JSONObject value) {
        return new CloudPhoneNumberUpdate.Name(
                stringOrUnknown(value, "display_phone_number"),
                stringOrUnknown(value, "decision"),
                value.getString("requested_verified_name"),
                value.getString("rejection_reason"));
    }

    /**
     * Decodes a {@code phone_number_quality_update} change value.
     *
     * @param value the webhook change {@code value}
     * @return the decoded quality transition
     */
    public static CloudPhoneNumberUpdate decodePhoneNumberQuality(JSONObject value) {
        return new CloudPhoneNumberUpdate.Quality(
                stringOrUnknown(value, "display_phone_number"),
                stringOrUnknown(value, "event"),
                value.getString("current_limit"));
    }

    /**
     * Decodes an {@code account_update}, {@code account_alerts}, or {@code account_review_update}
     * change value.
     *
     * @param value the webhook change {@code value}
     * @return the decoded account update
     */
    public static CloudAccountUpdate decodeAccountUpdate(JSONObject value) {
        String banState = null;
        Instant banDate = null;
        var banInfo = value.getJSONObject("ban_info");
        if (banInfo != null) {
            banState = banInfo.getString("waba_ban_state");
            banDate = epochInstant(banInfo.getLong("waba_ban_date"));
        }
        var restrictions = new ArrayList<CloudAccountUpdate.Restriction>();
        var restrictionInfo = value.getJSONArray("restriction_info");
        if (restrictionInfo != null) {
            for (var index = 0; index < restrictionInfo.size(); index++) {
                var restriction = restrictionInfo.getJSONObject(index);
                var type = restriction.getString("restriction_type");
                if (type != null) {
                    restrictions.add(new CloudAccountUpdate.Restriction(type, epochInstant(restriction.getLong("expiration"))));
                }
            }
        }
        String violationType = null;
        var violationInfo = value.getJSONObject("violation_info");
        if (violationInfo != null) {
            violationType = violationInfo.getString("violation_type");
        }
        return new CloudAccountUpdate(
                value.getString("event"),
                value.getString("phone_number"),
                value.getString("decision"),
                banState,
                banDate,
                restrictions,
                violationType);
    }

    /**
     * Decodes a {@code business_capability_update} change value.
     *
     * @param value the webhook change {@code value}
     * @return the decoded capability update
     */
    public static CloudBusinessCapabilityUpdate decodeBusinessCapability(JSONObject value) {
        var maxDaily = value.getInteger("max_daily_conversation_per_phone");
        var maxNumbers = value.getInteger("max_phone_numbers_per_business");
        return new CloudBusinessCapabilityUpdate(
                maxDaily == null ? -1 : maxDaily,
                maxNumbers == null ? -1 : maxNumbers);
    }

    /**
     * Decodes the entries of a {@code user_preferences} change value.
     *
     * @param value the webhook change {@code value}
     * @return the decoded preference changes, empty when the change carried none
     */
    public static List<CloudUserPreferenceUpdate> decodeUserPreferences(JSONObject value) {
        var entries = value.getJSONArray("user_preferences");
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<CloudUserPreferenceUpdate>();
        for (var index = 0; index < entries.size(); index++) {
            var entry = entries.getJSONObject(index);
            var waId = entry.getString("wa_id");
            if (waId == null) {
                continue;
            }
            result.add(new CloudUserPreferenceUpdate(
                    waId,
                    entry.getString("detail"),
                    stringOrUnknown(entry, "category"),
                    stringOrUnknown(entry, "value"),
                    epochInstant(entry.getLong("timestamp"))));
        }
        return result;
    }

    /**
     * Decodes a {@code flows} change value.
     *
     * @param value the webhook change {@code value}
     * @return the decoded flow event
     */
    public static CloudFlowStatusUpdate decodeFlowStatus(JSONObject value) {
        return new CloudFlowStatusUpdate(
                stringOrUnknown(value, "event"),
                idString(value, "flow_id"),
                value.getString("message"),
                value.getString("old_status"),
                value.getString("new_status"));
    }

    /**
     * Decodes the chunks of a {@code history} change value.
     *
     * @param value the webhook change {@code value}
     * @return the decoded history chunks, empty when the change carried none
     */
    public static List<CloudHistorySync> decodeHistory(JSONObject value) {
        var chunks = value.getJSONArray("history");
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<CloudHistorySync>();
        for (var index = 0; index < chunks.size(); index++) {
            var chunk = chunks.getJSONObject(index);
            var phase = -1;
            var chunkOrder = -1;
            var progress = -1;
            var metadata = chunk.getJSONObject("metadata");
            if (metadata != null) {
                var phaseValue = metadata.getInteger("phase");
                var chunkOrderValue = metadata.getInteger("chunk_order");
                var progressValue = metadata.getInteger("progress");
                phase = phaseValue == null ? -1 : phaseValue;
                chunkOrder = chunkOrderValue == null ? -1 : chunkOrderValue;
                progress = progressValue == null ? -1 : progressValue;
            }
            var messages = new ArrayList<ChatMessageInfo>();
            var threads = chunk.getJSONArray("threads");
            if (threads != null) {
                for (var threadIndex = 0; threadIndex < threads.size(); threadIndex++) {
                    var threadMessages = threads.getJSONObject(threadIndex).getJSONArray("messages");
                    if (threadMessages == null) {
                        continue;
                    }
                    for (var messageIndex = 0; messageIndex < threadMessages.size(); messageIndex++) {
                        messages.add(decodeMessage(threadMessages.getJSONObject(messageIndex), Map.of()));
                    }
                }
            }
            result.add(new CloudHistorySync(phase, chunkOrder, progress, messages));
        }
        return result;
    }

    /**
     * Reads a string member, substituting {@code UNKNOWN} when absent.
     *
     * @param value the change value
     * @param key   the member key
     * @return the member value, or {@code UNKNOWN} when absent
     */
    private static String stringOrUnknown(JSONObject value, String key) {
        var member = value.getString(key);
        return member == null ? "UNKNOWN" : member;
    }

    /**
     * Reads an id member that the wire may carry as a number or as a string.
     *
     * @param value the change value
     * @param key   the member key
     * @return the id as a string, or {@code null} when absent
     */
    private static String idString(JSONObject value, String key) {
        var member = value.get(key);
        return member == null ? null : String.valueOf(member);
    }

    /**
     * Converts an epoch-seconds value into an {@link Instant}.
     *
     * @param epochSeconds the epoch seconds, or {@code null}
     * @return the instant, or {@code null} when the input is {@code null}
     */
    private static Instant epochInstant(Long epochSeconds) {
        return epochSeconds == null ? null : Instant.ofEpochSecond(epochSeconds);
    }

    /**
     * Decodes a single inbound message into a {@link ChatMessageInfo}.
     *
     * @param message   the inbound message object
     * @param pushNames the profile names indexed by sender wa_id
     * @return the decoded message
     */
    private static ChatMessageInfo decodeMessage(JSONObject message, Map<String, String> pushNames) {
        var from = message.getString("from");
        var senderJid = userJid(from);
        var key = new MessageKeyBuilder()
                .id(message.getString("id"))
                .parentJid(senderJid)
                .fromMe(false)
                .build();
        var container = decodeContent(message);
        var builder = new ChatMessageInfoBuilder()
                .key(key)
                .message(container)
                .senderJid(senderJid)
                .status(MessageStatus.DELIVERED);
        var timestamp = message.getLong("timestamp");
        if (timestamp != null) {
            builder.timestamp(Instant.ofEpochSecond(timestamp));
        }
        var pushName = pushNames.get(from);
        if (pushName != null) {
            builder.pushName(pushName);
        }
        return builder.build();
    }

    /**
     * Decodes the content of an inbound message into a {@link MessageContainer}.
     *
     * @param message the inbound message object
     * @return the decoded container
     */
    private static MessageContainer decodeContent(JSONObject message) {
        var type = message.getString("type");
        if (type == null) {
            return MessageContainer.empty();
        }
        return switch (type) {
            case "text" -> MessageContainer.of(new ExtendedTextMessageBuilder()
                    .text(textBody(message))
                    .build());
            case "image" -> MessageContainer.of(new ImageMessageBuilder()
                    .mediaUrl(mediaId(message, "image"))
                    .mimetype(mediaMime(message, "image"))
                    .caption(mediaCaption(message, "image"))
                    .build());
            case "video" -> MessageContainer.of(new VideoMessageBuilder()
                    .mediaUrl(mediaId(message, "video"))
                    .mimetype(mediaMime(message, "video"))
                    .caption(mediaCaption(message, "video"))
                    .build());
            case "audio" -> MessageContainer.of(new AudioMessageBuilder()
                    .mediaUrl(mediaId(message, "audio"))
                    .mimetype(mediaMime(message, "audio"))
                    .build());
            case "document" -> MessageContainer.of(new DocumentMessageBuilder()
                    .mediaUrl(mediaId(message, "document"))
                    .mimetype(mediaMime(message, "document"))
                    .caption(mediaCaption(message, "document"))
                    .build());
            case "sticker" -> MessageContainer.of(new StickerMessageBuilder()
                    .mediaUrl(mediaId(message, "sticker"))
                    .mimetype(mediaMime(message, "sticker"))
                    .build());
            case "location" -> decodeLocation(message);
            case "reaction" -> decodeReaction(message);
            case "interactive" -> decodeInteractiveReply(message);
            case "button" -> decodeButtonReply(message);
            default -> MessageContainer.empty();
        };
    }

    /**
     * Decodes an inbound location message.
     *
     * @param message the inbound message object
     * @return the decoded container
     */
    private static MessageContainer decodeLocation(JSONObject message) {
        var location = message.getJSONObject("location");
        var builder = new LocationMessageBuilder();
        if (location != null) {
            var latitude = location.getDouble("latitude");
            var longitude = location.getDouble("longitude");
            if (latitude != null) {
                builder.degreesLatitude(latitude);
            }
            if (longitude != null) {
                builder.degreesLongitude(longitude);
            }
            builder.name(location.getString("name"));
            builder.address(location.getString("address"));
        }
        return MessageContainer.of(builder.build());
    }

    /**
     * Decodes an inbound reaction message.
     *
     * @param message the inbound message object
     * @return the decoded container
     */
    private static MessageContainer decodeReaction(JSONObject message) {
        var reaction = message.getJSONObject("reaction");
        var builder = new ReactionMessageBuilder();
        if (reaction != null) {
            builder.text(reaction.getString("emoji"));
            var targetId = reaction.getString("message_id");
            if (targetId != null) {
                builder.key(new MessageKeyBuilder().id(targetId).fromMe(true).build());
            }
        }
        return MessageContainer.of(builder.build());
    }

    /**
     * Extracts the body of an inbound text message.
     *
     * @param message the inbound message object
     * @return the message body, or the empty string when absent
     */
    private static String textBody(JSONObject message) {
        var text = message.getJSONObject("text");
        return text == null ? "" : text.getString("body");
    }

    /**
     * Decodes an inbound interactive reply (a reply button or a list selection) into a typed response
     * message.
     *
     * @param message the inbound message object
     * @return the decoded container
     */
    private static MessageContainer decodeInteractiveReply(JSONObject message) {
        var interactive = message.getJSONObject("interactive");
        if (interactive != null) {
            var buttonReply = interactive.getJSONObject("button_reply");
            if (buttonReply != null) {
                return MessageContainer.of(new ButtonsResponseMessageBuilder()
                        .selectedButtonId(buttonReply.getString("id"))
                        .selectedDisplayText(buttonReply.getString("title"))
                        .type(ButtonsResponseMessage.Type.DISPLAY_TEXT)
                        .build());
            }
            var listReply = interactive.getJSONObject("list_reply");
            if (listReply != null) {
                var reply = new ListResponseMessageSingleSelectReplyBuilder()
                        .selectedRowId(listReply.getString("id"))
                        .build();
                return MessageContainer.of(new ListResponseMessageBuilder()
                        .title(listReply.getString("title"))
                        .description(listReply.getString("description"))
                        .singleSelectReply(reply)
                        .listType(ListResponseMessage.ListType.SINGLE_SELECT)
                        .build());
            }
        }
        return MessageContainer.empty();
    }

    /**
     * Decodes an inbound legacy quick-reply button tap into a typed response message.
     *
     * @param message the inbound message object
     * @return the decoded container
     */
    private static MessageContainer decodeButtonReply(JSONObject message) {
        var builder = new ButtonsResponseMessageBuilder()
                .type(ButtonsResponseMessage.Type.DISPLAY_TEXT);
        var button = message.getJSONObject("button");
        if (button != null) {
            builder.selectedButtonId(button.getString("payload"))
                    .selectedDisplayText(button.getString("text"));
        }
        return MessageContainer.of(builder.build());
    }

    /**
     * Extracts the media id of an inbound media message.
     *
     * @param message the inbound message object
     * @param type    the media type key, for example {@code "image"}
     * @return the media id, or {@code null} when absent
     */
    private static String mediaId(JSONObject message, String type) {
        var media = message.getJSONObject(type);
        return media == null ? null : media.getString("id");
    }

    /**
     * Extracts the MIME type of an inbound media message.
     *
     * @param message the inbound message object
     * @param type    the media type key
     * @return the MIME type, or {@code null} when absent
     */
    private static String mediaMime(JSONObject message, String type) {
        var media = message.getJSONObject(type);
        return media == null ? null : media.getString("mime_type");
    }

    /**
     * Extracts the caption of an inbound media message.
     *
     * @param message the inbound message object
     * @param type    the media type key
     * @return the caption, or {@code null} when absent
     */
    private static String mediaCaption(JSONObject message, String type) {
        var media = message.getJSONObject(type);
        return media == null ? null : media.getString("caption");
    }

    /**
     * Indexes the inbound profile names by sender wa_id.
     *
     * @param value the webhook change {@code value}
     * @return a map of wa_id to profile name
     */
    private static Map<String, String> pushNamesByWaId(JSONObject value) {
        var result = new HashMap<String, String>();
        var contacts = value.getJSONArray("contacts");
        if (contacts == null) {
            return result;
        }
        for (var index = 0; index < contacts.size(); index++) {
            var contact = contacts.getJSONObject(index);
            var waId = contact.getString("wa_id");
            var profile = contact.getJSONObject("profile");
            if (waId != null && profile != null) {
                result.put(waId, profile.getString("name"));
            }
        }
        return result;
    }

    /**
     * Builds a user JID from an E.164 phone number.
     *
     * @param phoneNumber the phone number in E.164 form, without a leading plus
     * @return the user JID
     */
    private static Jid userJid(String phoneNumber) {
        return Jid.of(phoneNumber, JidServer.user());
    }
}
