package com.github.auties00.cobalt.model.bot.session;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

/**
 * Metadata identifying a single bot participant in a WhatsApp group chat.
 *
 * <p>Each entry contains the Facebook ID (FBID) of the bot, which uniquely
 * identifies the bot account on Meta's platform. This is used when sending
 * messages to a group that includes one or more bot participants, so the
 * server can route the message to the correct bot backend.
 *
 * @see BotGroupMetadata
 */
@ProtobufMessage(name = "BotGroupParticipantMetadata")
public final class BotGroupParticipantMetadata {
    /**
     * The Facebook ID (FBID) of the bot participant, for example
     * {@code "1234567890123456"}.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String botFacebookId;


    /**
     * Constructs a new {@code BotGroupParticipantMetadata} with the
     * specified bot Facebook ID.
     *
     * @param botFacebookId the bot's Facebook ID, or {@code null}
     */
    BotGroupParticipantMetadata(String botFacebookId) {
        this.botFacebookId = botFacebookId;
    }

    /**
     * Returns the Facebook ID (FBID) of the bot participant.
     *
     * @return an {@code Optional} describing the bot's Facebook ID, or an
     *         empty {@code Optional} if not set
     */
    public Optional<String> botFacebookId() {
        return Optional.ofNullable(botFacebookId);
    }

    /**
     * Sets the Facebook ID (FBID) of the bot participant.
     *
     * @param botFacebookId the new bot Facebook ID, or {@code null}
     */
    public void setBotFacebookId(String botFacebookId) {
        this.botFacebookId = botFacebookId;
    }
}
