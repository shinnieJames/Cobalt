package com.github.auties00.cobalt.model.bot.plugin;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

/**
 * Metadata describing an AI image-generation ("Imagine") response from a
 * WhatsApp bot.
 *
 * <p>This message is attached to {@code BotMetadata} (field 14) and indicates
 * the type of image generation that was performed and, optionally, a
 * shortened version of the prompt used.
 */
@ProtobufMessage(name = "BotImagineMetadata")
public final class BotImagineMetadata {
    /**
     * The type of image-generation operation that was performed.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    ImagineType imagineType;

    /**
     * A shortened version of the user's prompt used for image generation, for
     * example {@code "sunset over mountains"}.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String shortPrompt;

    /**
     * Constructs a new {@code BotImagineMetadata} with the specified values.
     *
     * @param imagineType the image-generation type, or {@code null}
     * @param shortPrompt the shortened prompt, or {@code null}
     */
    BotImagineMetadata(ImagineType imagineType, String shortPrompt) {
        this.imagineType = imagineType;
        this.shortPrompt = shortPrompt;
    }

    /**
     * Returns the type of image-generation operation that was performed.
     *
     * @return an {@code Optional} describing the imagine type, or an empty
     *         {@code Optional} if not set
     */
    public Optional<ImagineType> imagineType() {
        return Optional.ofNullable(imagineType);
    }

    /**
     * Returns the shortened version of the user's prompt.
     *
     * @return an {@code Optional} describing the short prompt, or an empty
     *         {@code Optional} if not set
     */
    public Optional<String> shortPrompt() {
        return Optional.ofNullable(shortPrompt);
    }

    /**
     * Sets the type of image-generation operation.
     *
     * @param imagineType the new imagine type, or {@code null}
     */
    public void setImagineType(ImagineType imagineType) {
        this.imagineType = imagineType;
    }

    /**
     * Sets the shortened version of the user's prompt.
     *
     * @param shortPrompt the new short prompt, or {@code null}
     */
    public void setShortPrompt(String shortPrompt) {
        this.shortPrompt = shortPrompt;
    }

    /**
     * The type of AI image-generation operation that was performed by the bot.
     */
    @ProtobufEnum(name = "BotImagineMetadata.ImagineType")
    public static enum ImagineType {
        /**
         * An unknown or unrecognized image-generation type.
         */
        UNKNOWN(0),

        /**
         * A standard text-to-image generation from a prompt.
         */
        IMAGINE(1),

        /**
         * A "Me, Myself, and AI" (MeMu) personalized image generation using
         * the user's face.
         */
        MEMU(2),

        /**
         * A quick "flash" image generation with faster, lower-quality results.
         */
        FLASH(3),

        /**
         * An image editing operation on an existing image.
         */
        EDIT(4);

        /**
         * Constructs a new imagine type constant with the specified protobuf
         * index.
         *
         * @param index the protobuf enum index
         */
        ImagineType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        /**
         * Returns the protobuf enum index of this imagine type.
         *
         * @return the protobuf index
         */
        public int index() {
            return this.index;
        }
    }
}
