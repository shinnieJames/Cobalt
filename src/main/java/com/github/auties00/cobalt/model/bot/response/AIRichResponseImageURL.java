package com.github.auties00.cobalt.model.bot.response;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.net.URI;
import java.util.Optional;

/**
 * A set of URLs that reference different resolutions of a single image
 * within an AI rich response.
 *
 * <p>Clients typically display the {@linkplain #imagePreviewUrl() preview}
 * image first and lazy-load the {@linkplain #imageHighResUrl() high-resolution}
 * variant when the user expands or taps the image. The
 * {@linkplain #sourceUrl() source URL} points to the original web page
 * where the image was found.
 */
@ProtobufMessage(name = "AIRichResponseImageURL")
public final class AIRichResponseImageURL {
    /**
     * A URL pointing to a low-resolution preview of the image,
     * suitable for inline thumbnails.
     *
     * <p>Example: {@code "https://scontent.xx.fbcdn.net/v/t39.8562-6/123_n.jpg"}
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    URI imagePreviewUrl;

    /**
     * A URL pointing to the full-resolution version of the image.
     *
     * <p>Example: {@code "https://scontent.xx.fbcdn.net/v/t39.8562-6/123_o.jpg"}
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    URI imageHighResUrl;

    /**
     * A URL pointing to the original web page or resource from which
     * the image was sourced.
     *
     * <p>Example: {@code "https://www.example.com/article/image-gallery"}
     */
    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    URI sourceUrl;


    AIRichResponseImageURL(URI imagePreviewUrl, URI imageHighResUrl, URI sourceUrl) {
        this.imagePreviewUrl = imagePreviewUrl;
        this.imageHighResUrl = imageHighResUrl;
        this.sourceUrl = sourceUrl;
    }

    /**
     * Returns the low-resolution preview URL for this image.
     *
     * @return an {@link Optional} containing the preview URL, or empty
     *         if not set
     */
    public Optional<URI> imagePreviewUrl() {
        return Optional.ofNullable(imagePreviewUrl);
    }

    /**
     * Returns the full-resolution URL for this image.
     *
     * @return an {@link Optional} containing the high-resolution URL,
     *         or empty if not set
     */
    public Optional<URI> imageHighResUrl() {
        return Optional.ofNullable(imageHighResUrl);
    }

    /**
     * Returns the URL of the original web page or resource from which
     * this image was sourced.
     *
     * @return an {@link Optional} containing the source URL, or empty
     *         if not set
     */
    public Optional<URI> sourceUrl() {
        return Optional.ofNullable(sourceUrl);
    }

    /**
     * Sets the low-resolution preview URL for this image.
     *
     * @param imagePreviewUrl the preview URL to set
     */
    public void setImagePreviewUrl(URI imagePreviewUrl) {
        this.imagePreviewUrl = imagePreviewUrl;
    }

    /**
     * Sets the full-resolution URL for this image.
     *
     * @param imageHighResUrl the high-resolution URL to set
     */
    public void setImageHighResUrl(URI imageHighResUrl) {
        this.imageHighResUrl = imageHighResUrl;
    }

    /**
     * Sets the URL of the original web page or resource from which
     * this image was sourced.
     *
     * @param sourceUrl the source URL to set
     */
    public void setSourceUrl(URI sourceUrl) {
        this.sourceUrl = sourceUrl;
    }
}
