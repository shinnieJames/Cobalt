package com.github.auties00.cobalt.model.message;

import com.github.auties00.cobalt.model.message.system.DeviceSentMessage;
import com.github.auties00.cobalt.model.message.system.FutureProofMessage;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

/**
 * Identifies which {@link FutureProofMessage} or {@link DeviceSentMessage}
 * wrapper field is populated in a {@link MessageContainer}.
 *
 * <p>{@link #NONE} is returned when the container holds a direct message
 * field (no wrapper).
 */
@ProtobufEnum(name = "FutureProofMessageType")
public enum FutureProofMessageType {
    /**
     * No wrapper is present; the container holds a direct message field.
     */
    NONE(0),

    /**
     * A view-once media message (any version: V1, V2, or V2 extension).
     */
    VIEW_ONCE(1),

    /**
     * A disappearing (ephemeral) message that auto-deletes after the
     * chat's ephemeral timer expires.
     */
    EPHEMERAL(2),

    /**
     * A document message with a separate caption.
     */
    DOCUMENT_WITH_CAPTION(3),

    /**
     * An edit to a previously sent message.
     */
    EDITED(4),

    /**
     * A group-mention message. Has the highest unwrapping priority in
     * WhatsApp Web.
     */
    GROUP_MENTIONED(5),

    /**
     * A bot invocation message.
     */
    BOT_INVOKE(6),

    /**
     * An animated Lottie sticker message.
     */
    LOTTIE_STICKER(7),

    /**
     * An event cover image.
     */
    EVENT_COVER_IMAGE(8),

    /**
     * A status mention message.
     */
    STATUS_MENTION(9),

    /**
     * A poll creation option image.
     */
    POLL_CREATION_OPTION_IMAGE(10),

    /**
     * An associated child message.
     */
    ASSOCIATED_CHILD(11),

    /**
     * A group status mention message.
     */
    GROUP_STATUS_MENTION(12),

    /**
     * A poll creation message (V4), wrapped for forward compatibility.
     */
    POLL_CREATION(13),

    /**
     * A status "Add Yours" sticker or prompt.
     */
    STATUS_ADD_YOURS(14),

    /**
     * A group status message (any version: V1 or V2).
     */
    GROUP_STATUS(15),

    /**
     * A limit-sharing message.
     */
    LIMIT_SHARING(16),

    /**
     * A bot task message.
     */
    BOT_TASK(17),

    /**
     * A question message (AI/bot context).
     */
    QUESTION(18),

    /**
     * A bot-forwarded message.
     */
    BOT_FORWARDED(19),

    /**
     * A question reply message (AI/bot context).
     */
    QUESTION_REPLY(20),

    /**
     * A newsletter admin profile message (any version: V1 or V2).
     */
    NEWSLETTER_ADMIN_PROFILE(21),

    /**
     * A message sent from a linked device, distributed to the user's
     * other devices via a {@link DeviceSentMessage} wrapper.
     */
    DEVICE_SENT(22);

    FutureProofMessageType(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    final int index;

    public int index() {
        return this.index;
    }
}
