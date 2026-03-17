package com.github.auties00.cobalt.model.bot.metrics;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.List;
import java.util.Optional;

/**
 * Diagnostics metadata identifying the backend infrastructure that processed
 * a bot interaction on WhatsApp.
 *
 * <p>This message is attached to {@code BotMetadata} (field 37) and carries
 * information about which server-side system handled the bot request and
 * which tools were invoked during processing. It is intended for debugging
 * and infrastructure monitoring.
 */
@ProtobufMessage(name = "BotInfrastructureDiagnostics")
public final class BotInfrastructureDiagnostics {
    /**
     * The backend system that processed this bot interaction.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    BotBackend botBackend;

    /**
     * The names of the tools that the backend invoked while processing this
     * bot interaction, for example {@code "web_search"} or
     * {@code "code_interpreter"}.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    List<String> toolsUsed;

    /**
     * Constructs a new {@code BotInfrastructureDiagnostics} with the specified
     * values.
     *
     * @param botBackend the backend that handled the request, or {@code null}
     * @param toolsUsed  the tools used during processing, or {@code null}
     */
    BotInfrastructureDiagnostics(BotBackend botBackend, List<String> toolsUsed) {
        this.botBackend = botBackend;
        this.toolsUsed = toolsUsed;
    }

    /**
     * Returns the backend system that processed this bot interaction.
     *
     * @return an {@code Optional} describing the bot backend, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotBackend> botBackend() {
        return Optional.ofNullable(botBackend);
    }

    /**
     * Returns the names of the tools that the backend invoked while processing
     * this bot interaction.
     *
     * @return an unmodifiable list of tool names, possibly empty
     */
    public List<String> toolsUsed() {
        return toolsUsed != null ? toolsUsed : List.of();
    }

    /**
     * Sets the backend system that processed this bot interaction.
     *
     * @param botBackend the new bot backend, or {@code null}
     */
    public void setBotBackend(BotBackend botBackend) {
        this.botBackend = botBackend;
    }

    /**
     * Sets the names of the tools that the backend invoked while processing
     * this bot interaction.
     *
     * @param toolsUsed the new list of tool names, or {@code null}
     */
    public void setToolsUsed(List<String> toolsUsed) {
        this.toolsUsed = toolsUsed;
    }

    /**
     * The server-side backend system that handled a bot request.
     *
     * <p>These are internal Meta infrastructure codenames. No public
     * documentation exists for these values.
     */
    @ProtobufEnum(name = "BotInfrastructureDiagnostics.BotBackend")
    public static enum BotBackend {
        /**
         * The primary AI API backend (internal Meta codename).
         *
         * <p>This is the default backend (protobuf index {@code 0}) used for
         * serving Meta AI bot responses on WhatsApp.
         */
        AAPI(0),

        /**
         * An alternative bot backend (internal Meta codename).
         *
         * <p>A secondary infrastructure path for handling bot interactions,
         * distinct from the primary {@link #AAPI} backend.
         */
        CLIPPY(1);

        /**
         * Constructs a new backend constant with the specified protobuf index.
         *
         * @param index the protobuf enum index
         */
        BotBackend(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        /**
         * Returns the protobuf enum index of this backend.
         *
         * @return the protobuf index
         */
        public int index() {
            return this.index;
        }
    }
}
