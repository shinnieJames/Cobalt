package com.github.auties00.cobalt.model.bot.plugin;

import com.github.auties00.cobalt.model.bot.rendering.BotMediaMetadata;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Collections;
import java.util.List;

/**
 * Metadata for the "Me, Myself, and AI" (MeMu) personalized image-generation
 * feature on WhatsApp.
 *
 * <p>This message is attached to {@code BotMetadata} (field 7) and carries
 * the user's face images that the AI uses as reference when generating
 * personalized images. Each face image is represented as a
 * {@link BotMediaMetadata} containing the encrypted media reference.
 */
@ProtobufMessage(name = "BotMemuMetadata")
public final class BotMemuMetadata {
    /**
     * The user's face images used as reference for personalized image
     * generation.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    List<BotMediaMetadata> faceImages;

    /**
     * Constructs a new {@code BotMemuMetadata} with the specified face images.
     *
     * @param faceImages the face images, or {@code null}
     */
    BotMemuMetadata(List<BotMediaMetadata> faceImages) {
        this.faceImages = faceImages;
    }

    /**
     * Returns the user's face images used as reference for personalized image
     * generation.
     *
     * @return an unmodifiable list of face image media metadata, possibly empty
     */
    public List<BotMediaMetadata> faceImages() {
        return faceImages == null ? List.of() : Collections.unmodifiableList(faceImages);
    }

    /**
     * Sets the user's face images used as reference for personalized image
     * generation.
     *
     * @param faceImages the new list of face images, or {@code null}
     */
    public void setFaceImages(List<BotMediaMetadata> faceImages) {
        this.faceImages = faceImages;
    }
}
