package com.github.auties00.cobalt.model.bot.metrics;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

/**
 * The type of AI conversation thread from which a bot interaction originated.
 *
 * <p>This enum is recorded in {@link BotMetricsMetadata#threadOrigin()} to
 * indicate which kind of thread surface the user was in when they sent a
 * message to the bot. Each constant corresponds to a distinct thread
 * presentation in the WhatsApp client.
 */
@ProtobufEnum(name = "BotMetricsThreadEntryPoint")
public enum BotMetricsThreadEntryPoint {
    /**
     * A thread opened from the dedicated AI tab.
     */
    AI_TAB_THREAD(1),

    /**
     * A thread opened from the AI home screen.
     */
    AI_HOME_THREAD(2),

    /**
     * A thread opened from an immersive AI deep link.
     */
    AI_DEEPLINK_IMMERSIVE_THREAD(3),

    /**
     * A thread opened from a standard AI deep link.
     */
    AI_DEEPLINK_THREAD(4),

    /**
     * A thread opened via the "Ask Meta AI" context menu action.
     */
    ASK_META_AI_CONTEXT_MENU_THREAD(5);

    /**
     * Constructs a new thread entry point constant with the specified protobuf
     * index.
     *
     * @param index the protobuf enum index
     */
    BotMetricsThreadEntryPoint(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    final int index;

    /**
     * Returns the protobuf enum index of this thread entry point.
     *
     * @return the protobuf index
     */
    public int index() {
        return this.index;
    }
}
