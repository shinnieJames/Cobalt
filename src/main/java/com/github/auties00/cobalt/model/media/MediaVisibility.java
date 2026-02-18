package com.github.auties00.cobalt.model.media;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

/**
 * A visibility setting that controls whether media attachments are automatically
 * downloaded and displayed within a conversation or across all conversations.
 *
 * <p>This enum is defined in {@code WAWebProtobufsHistorySync.pb} and is
 * referenced in two contexts: at field index 27 in the {@code Conversation}
 * message to control per-chat media visibility, and at field index 2 in
 * {@code GlobalSettings} to control the application-wide default. When a
 * conversation-level setting is {@link #DEFAULT}, the client falls back to the
 * value specified in {@code GlobalSettings}.
 */
@ProtobufEnum(name = "MediaVisibility")
public enum MediaVisibility {
    /**
     * The singleton instance for the default media visibility setting.
     * When used at the conversation level, this indicates that the global
     * setting should be applied instead.
     * This has the numeric value of {@code 0}.
     */
    DEFAULT(0),

    /**
     * The singleton instance for disabling media visibility.
     * Media attachments are not automatically downloaded or displayed.
     * This has the numeric value of {@code 1}.
     */
    OFF(1),

    /**
     * The singleton instance for enabling media visibility.
     * Media attachments are automatically downloaded and displayed.
     * This has the numeric value of {@code 2}.
     */
    ON(2);

    /**
     * Constructs a new {@code MediaVisibility} with the given protobuf index.
     *
     * @param index the protobuf enum index
     */
    MediaVisibility(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    /**
     * The protobuf enum index of this visibility setting.
     */
    final int index;

    /**
     * Returns the protobuf enum index of this visibility setting.
     *
     * @return the numeric index
     */
    public int index() {
        return this.index;
    }
}
