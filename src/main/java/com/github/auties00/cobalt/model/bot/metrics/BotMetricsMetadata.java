package com.github.auties00.cobalt.model.bot.metrics;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

/**
 * Telemetry metadata that records how and where a user navigated to a bot
 * conversation on WhatsApp.
 *
 * <p>This message is attached to {@code BotMetadata} (field 17) and captures
 * the destination bot, the UI entry point used to reach it, and the type of
 * thread in which the interaction takes place. It is used for analytics and
 * engagement tracking.
 */
@ProtobufMessage(name = "BotMetricsMetadata")
public final class BotMetricsMetadata {
    /**
     * The JID of the destination bot that the user navigated to, for example
     * {@code "13135550002@s.whatsapp.net"}.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String destinationId;

    /**
     * The UI entry point from which the user initiated the bot interaction.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    BotMetricsEntryPoint destinationEntryPoint;

    /**
     * The type of AI conversation thread from which the interaction originated.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
    BotMetricsThreadEntryPoint threadOrigin;

    /**
     * Constructs a new {@code BotMetricsMetadata} with the specified values.
     *
     * @param destinationId        the destination bot JID, or {@code null}
     * @param destinationEntryPoint the UI entry point, or {@code null}
     * @param threadOrigin         the thread origin type, or {@code null}
     */
    BotMetricsMetadata(String destinationId, BotMetricsEntryPoint destinationEntryPoint, BotMetricsThreadEntryPoint threadOrigin) {
        this.destinationId = destinationId;
        this.destinationEntryPoint = destinationEntryPoint;
        this.threadOrigin = threadOrigin;
    }

    /**
     * Returns the JID of the destination bot.
     *
     * @return an {@code Optional} describing the destination bot JID, or an
     *         empty {@code Optional} if not set
     */
    public Optional<String> destinationId() {
        return Optional.ofNullable(destinationId);
    }

    /**
     * Returns the UI entry point from which the user initiated the bot
     * interaction.
     *
     * @return an {@code Optional} describing the entry point, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotMetricsEntryPoint> destinationEntryPoint() {
        return Optional.ofNullable(destinationEntryPoint);
    }

    /**
     * Returns the type of AI conversation thread from which the interaction
     * originated.
     *
     * @return an {@code Optional} describing the thread origin, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotMetricsThreadEntryPoint> threadOrigin() {
        return Optional.ofNullable(threadOrigin);
    }

    /**
     * Sets the JID of the destination bot.
     *
     * @param destinationId the new destination bot JID, or {@code null}
     */
    public void setDestinationId(String destinationId) {
        this.destinationId = destinationId;
    }

    /**
     * Sets the UI entry point from which the user initiated the bot
     * interaction.
     *
     * @param destinationEntryPoint the new entry point, or {@code null}
     */
    public void setDestinationEntryPoint(BotMetricsEntryPoint destinationEntryPoint) {
        this.destinationEntryPoint = destinationEntryPoint;
    }

    /**
     * Sets the type of AI conversation thread from which the interaction
     * originated.
     *
     * @param threadOrigin the new thread origin, or {@code null}
     */
    public void setThreadOrigin(BotMetricsThreadEntryPoint threadOrigin) {
        this.threadOrigin = threadOrigin;
    }
}
