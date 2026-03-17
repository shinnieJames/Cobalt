package com.github.auties00.cobalt.model.bot.response;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.ProtobufType;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Metadata for a collection of content items within an AI rich response.
 *
 * <p>Content items are typically rendered as a horizontally scrollable
 * carousel when the {@linkplain #contentType() content type} is
 * {@link ContentType#CAROUSEL}. Each item in the collection is wrapped
 * in an {@link AIRichResponseContentItemMetadata} that carries
 * one of the supported content variants (currently only
 * {@link AIRichResponseReelItem reels}).
 */
@ProtobufMessage(name = "AIRichResponseContentItemsMetadata")
public final class AIRichResponseContentItemsMetadata implements AIRichResponseSubMessageContent {
    /**
     * The list of content item wrappers in this collection.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    List<AIRichResponseContentItemMetadata> itemsMetadata;

    /**
     * The layout type that determines how the items are rendered.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    ContentType contentType;


    AIRichResponseContentItemsMetadata(List<AIRichResponseContentItemMetadata> itemsMetadata, ContentType contentType) {
        this.itemsMetadata = itemsMetadata;
        this.contentType = contentType;
    }

    /**
     * Returns the list of content item wrappers in this collection.
     *
     * @return an unmodifiable list of item metadata, never {@code null}
     */
    public List<AIRichResponseContentItemMetadata> itemsMetadata() {
        return itemsMetadata == null ? List.of() : Collections.unmodifiableList(itemsMetadata);
    }

    /**
     * Returns the layout type that determines how the items are
     * rendered.
     *
     * @return an {@link Optional} containing the content type, or
     *         empty if not set
     */
    public Optional<ContentType> contentType() {
        return Optional.ofNullable(contentType);
    }

    /**
     * Sets the list of content item wrappers in this collection.
     *
     * @param itemsMetadata the item metadata to set
     */
    public void setItemsMetadata(List<AIRichResponseContentItemMetadata> itemsMetadata) {
        this.itemsMetadata = itemsMetadata;
    }

    /**
     * Sets the layout type that determines how the items are rendered.
     *
     * @param contentType the content type to set
     */
    public void setContentType(ContentType contentType) {
        this.contentType = contentType;
    }

    /**
     * A sealed interface representing the concrete content item
     * variants that can appear in an
     * {@link AIRichResponseContentItemMetadata}.
     *
     * <p>Currently the only supported variant is
     * {@link AIRichResponseReelItem}.
     */
    public sealed interface AIRichResponseContentItem permits AIRichResponseReelItem {
    }

    /**
     * A layout type that determines how content items are rendered
     * within an AI rich response.
     */
    @ProtobufEnum(name = "AIRichResponseContentItemsMetadata.ContentType")
    public static enum ContentType {
        /**
         * The default layout for content items.
         */
        DEFAULT(0),

        /**
         * A horizontally scrollable carousel layout.
         */
        CAROUSEL(1);

        ContentType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        /**
         * Returns the protobuf index associated with this content type.
         *
         * @return the protobuf index
         */
        public int index() {
            return this.index;
        }
    }

    /**
     * A wrapper for a single content item in the collection.
     *
     * <p>This message acts as a protobuf {@code oneof} container;
     * exactly one of the item variants will be present. Instances
     * should be constructed via the generated
     * {@code AIRichResponseContentItemMetadataBuilder}, which accepts
     * a single {@link AIRichResponseContentItem} parameter. Use
     * {@link #content()} to retrieve the active variant
     * polymorphically.
     */
    @ProtobufMessage(name = "AIRichResponseContentItemsMetadata.AIRichResponseContentItemMetadata", generateBuilder = false)
    public static final class AIRichResponseContentItemMetadata {
        /**
         * A reel item, present when this content item represents a
         * short-form video reel.
         */
        @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
        AIRichResponseReelItem reelItem;


        AIRichResponseContentItemMetadata(AIRichResponseReelItem reelItem) {
            this.reelItem = reelItem;
        }

        /**
         * Constructs an {@code AIRichResponseContentItemMetadata}
         * from a type-safe {@link AIRichResponseContentItem} variant.
         *
         * <p>The correct protobuf field is populated automatically
         * based on the concrete type of the supplied content item.
         *
         * @param content the content item variant to wrap, or
         *        {@code null}
         * @return a new {@code AIRichResponseContentItemMetadata}
         *         wrapping the supplied content
         */
        @ProtobufBuilder(className = "AIRichResponseContentItemMetadataBuilder")
        static AIRichResponseContentItemMetadata of(AIRichResponseContentItem content) {
            return switch (content) {
                case AIRichResponseReelItem r -> new AIRichResponseContentItemMetadata(r);
                case null -> new AIRichResponseContentItemMetadata(null);
            };
        }

        /**
         * Returns the active content item variant.
         *
         * @return an {@link Optional} containing the content item,
         *         or empty if no variant is set
         */
        public Optional<AIRichResponseContentItem> content() {
            if (reelItem != null) return Optional.of(reelItem);
            return Optional.empty();
        }
    }

    /**
     * A short-form video reel item within an AI rich response
     * content collection.
     *
     * <p>Reel items are typically displayed as vertically-oriented
     * cards in a carousel, showing a thumbnail preview with the
     * creator's profile icon and title.
     */
    @ProtobufMessage(name = "AIRichResponseContentItemsMetadata.AIRichResponseReelItem")
    public static final class AIRichResponseReelItem implements AIRichResponseContentItem {
        /**
         * The title or caption of this reel.
         *
         * <p>Example: {@code "How to make pasta from scratch"}
         */
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String title;

        /**
         * The URL of the reel creator's profile icon.
         *
         * <p>Example: {@code "https://scontent.xx.fbcdn.net/v/t51/profile_abc.jpg"}
         */
        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        URI profileIconUrl;

        /**
         * The URL of the reel's thumbnail image.
         *
         * <p>Example: {@code "https://scontent.xx.fbcdn.net/v/t15/thumb_abc.jpg"}
         */
        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        URI thumbnailUrl;

        /**
         * The URL of the reel's video content.
         *
         * <p>Example: {@code "https://video.xx.fbcdn.net/v/t42/video_abc.mp4"}
         */
        @ProtobufProperty(index = 4, type = ProtobufType.STRING)
        URI videoUrl;


        AIRichResponseReelItem(String title, URI profileIconUrl, URI thumbnailUrl, URI videoUrl) {
            this.title = title;
            this.profileIconUrl = profileIconUrl;
            this.thumbnailUrl = thumbnailUrl;
            this.videoUrl = videoUrl;
        }

        /**
         * Returns the title or caption of this reel.
         *
         * @return an {@link Optional} containing the title, or empty
         *         if not set
         */
        public Optional<String> title() {
            return Optional.ofNullable(title);
        }

        /**
         * Returns the URL of the reel creator's profile icon.
         *
         * @return an {@link Optional} containing the profile icon URL,
         *         or empty if not set
         */
        public Optional<URI> profileIconUrl() {
            return Optional.ofNullable(profileIconUrl);
        }

        /**
         * Returns the URL of the reel's thumbnail image.
         *
         * @return an {@link Optional} containing the thumbnail URL,
         *         or empty if not set
         */
        public Optional<URI> thumbnailUrl() {
            return Optional.ofNullable(thumbnailUrl);
        }

        /**
         * Returns the URL of the reel's video content.
         *
         * @return an {@link Optional} containing the video URL, or
         *         empty if not set
         */
        public Optional<URI> videoUrl() {
            return Optional.ofNullable(videoUrl);
        }

        /**
         * Sets the title or caption of this reel.
         *
         * @param title the title to set
         */
        public void setTitle(String title) {
            this.title = title;
    }

        /**
         * Sets the URL of the reel creator's profile icon.
         *
         * @param profileIconUrl the profile icon URL to set
         */
        public void setProfileIconUrl(URI profileIconUrl) {
            this.profileIconUrl = profileIconUrl;
    }

        /**
         * Sets the URL of the reel's thumbnail image.
         *
         * @param thumbnailUrl the thumbnail URL to set
         */
        public void setThumbnailUrl(URI thumbnailUrl) {
            this.thumbnailUrl = thumbnailUrl;
    }

        /**
         * Sets the URL of the reel's video content.
         *
         * @param videoUrl the video URL to set
         */
        public void setVideoUrl(URI videoUrl) {
            this.videoUrl = videoUrl;
    }
    }
}
