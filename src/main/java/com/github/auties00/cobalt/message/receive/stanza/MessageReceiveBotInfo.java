package com.github.auties00.cobalt.message.receive.stanza;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Parsed data from the {@code <bot>} child node of an incoming message
 * stanza.
 *
 * <p>Bot info is present when the message involves a WhatsApp bot
 * (either a Meta AI bot or a business bot).  It carries timing
 * information, edit metadata, and the bot's business classification.
 *
 * @implNote WAWebHandleMsgParser function b(): parses the {@code <bot>}
 * node to extract botSenderTimestampMs, botEditTargetId, botEditType,
 * botMsgBodyType, and bizBotType.
 */
public final class MessageReceiveBotInfo {
    private final String senderTimestampMs;
    private final String editTargetId;
    private final String editType;
    private final String bodyType;
    private final String bizBotType;

    public MessageReceiveBotInfo(
            String senderTimestampMs,
            String editTargetId,
            String editType,
            String bodyType,
            String bizBotType
    ) {
        this.senderTimestampMs = senderTimestampMs;
        this.editTargetId = editTargetId;
        this.editType = editType;
        this.bodyType = bodyType;
        this.bizBotType = bizBotType;
    }

    /**
     * Returns the bot's sender timestamp in milliseconds.
     */
    public Optional<String> senderTimestampMs() {
        return Optional.ofNullable(senderTimestampMs);
    }

    /**
     * Returns the target message ID for bot edits.
     */
    public Optional<String> editTargetId() {
        return Optional.ofNullable(editTargetId);
    }

    /**
     * Returns the bot edit type (e.g. "inner", "last").
     *
     * @apiNote WAWebBotTypes.BotMsgEditType
     */
    public Optional<String> editType() {
        return Optional.ofNullable(editType);
    }

    /**
     * Returns the bot message body type.
     *
     * @apiNote WAWebBotTypes.BotMsgBodyType
     */
    public Optional<String> bodyType() {
        return Optional.ofNullable(bodyType);
    }

    /**
     * Returns the business bot type ("1" for BIZ_1P, "3" for BIZ_3P).
     *
     * @apiNote WAWebBotTypes.BizBotType
     */
    public Optional<String> bizBotType() {
        return Optional.ofNullable(bizBotType);
    }
}
