package com.github.auties00.cobalt.model.bot.plugin;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Metadata containing the source attributions for a bot's rich response
 * content.
 *
 * <p>This message is attached to {@code BotMetadata} (field 19) and carries
 * a list of {@link BotSourceItem} entries, each representing a web source
 * that the bot cited when generating its response.
 */
@ProtobufMessage(name = "BotSourcesMetadata")
public final class BotSourcesMetadata {
    /**
     * The list of source items cited in the bot response.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    List<BotSourceItem> sources;

    /**
     * Constructs a new {@code BotSourcesMetadata} with the specified sources.
     *
     * @param sources the source items, or {@code null}
     */
    BotSourcesMetadata(List<BotSourceItem> sources) {
        this.sources = sources;
    }

    /**
     * Returns the list of source items cited in the bot response.
     *
     * @return an unmodifiable list of source items, possibly empty
     */
    public List<BotSourceItem> sources() {
        return sources == null ? List.of() : Collections.unmodifiableList(sources);
    }

    /**
     * Sets the list of source items cited in the bot response.
     *
     * @param sources the new list of source items, or {@code null}
     */
    public void setSources(List<BotSourceItem> sources) {
        this.sources = sources;
    }

    /**
     * A single source citation within a bot response, identifying the web
     * page or resource that the bot referenced when generating its answer.
     */
    @ProtobufMessage(name = "BotSourcesMetadata.BotSourceItem")
    public static final class BotSourceItem {
        /**
         * The search provider that returned this source.
         */
        @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
        BotSourceItem.SourceProvider provider;

        /**
         * The CDN URL of a thumbnail image for this source, for example
         * {@code "https://mmg.whatsapp.net/v/t62.1234/thumb.jpg"}.
         */
        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        URI thumbnailCdnUrl;

        /**
         * The URL of the source page on the search provider's site, for
         * example {@code "https://www.example.com/article"}.
         */
        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        URI sourceProviderUrl;

        /**
         * The search query that produced this source result, for example
         * {@code "best restaurants in Tokyo"}.
         */
        @ProtobufProperty(index = 4, type = ProtobufType.STRING)
        String sourceQuery;

        /**
         * The CDN URL of the source site's favicon, for example
         * {@code "https://mmg.whatsapp.net/v/t62.1234/favicon.ico"}.
         */
        @ProtobufProperty(index = 5, type = ProtobufType.STRING)
        URI faviconCdnUrl;

        /**
         * The citation number displayed in the bot response (e.g. [1], [2]),
         * used for inline footnote-style references.
         */
        @ProtobufProperty(index = 6, type = ProtobufType.UINT32)
        Integer citationNumber;

        /**
         * The title of the source page, for example
         * {@code "10 Best Restaurants in Tokyo - Travel Guide"}.
         */
        @ProtobufProperty(index = 7, type = ProtobufType.STRING)
        String sourceTitle;

        /**
         * Constructs a new {@code BotSourceItem} with the specified values.
         *
         * @param provider          the source provider, or {@code null}
         * @param thumbnailCdnUrl   the thumbnail URL, or {@code null}
         * @param sourceProviderUrl the source page URL, or {@code null}
         * @param sourceQuery       the search query, or {@code null}
         * @param faviconCdnUrl     the favicon URL, or {@code null}
         * @param citationNumber    the citation number, or {@code null}
         * @param sourceTitle       the source page title, or {@code null}
         */
        BotSourceItem(SourceProvider provider, URI thumbnailCdnUrl, URI sourceProviderUrl, String sourceQuery, URI faviconCdnUrl, Integer citationNumber, String sourceTitle) {
            this.provider = provider;
            this.thumbnailCdnUrl = thumbnailCdnUrl;
            this.sourceProviderUrl = sourceProviderUrl;
            this.sourceQuery = sourceQuery;
            this.faviconCdnUrl = faviconCdnUrl;
            this.citationNumber = citationNumber;
            this.sourceTitle = sourceTitle;
        }

        /**
         * Returns the search provider that returned this source.
         *
         * @return an {@code Optional} describing the provider, or an empty
         *         {@code Optional} if not set
         */
        public Optional<SourceProvider> provider() {
            return Optional.ofNullable(provider);
        }

        /**
         * Returns the CDN URL of a thumbnail image for this source.
         *
         * @return an {@code Optional} describing the thumbnail URL, or an
         *         empty {@code Optional} if not set
         */
        public Optional<URI> thumbnailCdnUrl() {
            return Optional.ofNullable(thumbnailCdnUrl);
        }

        /**
         * Returns the URL of the source page on the search provider's site.
         *
         * @return an {@code Optional} describing the source URL, or an empty
         *         {@code Optional} if not set
         */
        public Optional<URI> sourceProviderUrl() {
            return Optional.ofNullable(sourceProviderUrl);
        }

        /**
         * Returns the search query that produced this source result.
         *
         * @return an {@code Optional} describing the query, or an empty
         *         {@code Optional} if not set
         */
        public Optional<String> sourceQuery() {
            return Optional.ofNullable(sourceQuery);
        }

        /**
         * Returns the CDN URL of the source site's favicon.
         *
         * @return an {@code Optional} describing the favicon URL, or an empty
         *         {@code Optional} if not set
         */
        public Optional<URI> faviconCdnUrl() {
            return Optional.ofNullable(faviconCdnUrl);
        }

        /**
         * Returns the citation number displayed in the bot response.
         *
         * @return an {@code OptionalInt} describing the citation number, or an
         *         empty {@code OptionalInt} if not set
         */
        public OptionalInt citationNumber() {
            return citationNumber == null ? OptionalInt.empty() : OptionalInt.of(citationNumber);
        }

        /**
         * Returns the title of the source page.
         *
         * @return an {@code Optional} describing the title, or an empty
         *         {@code Optional} if not set
         */
        public Optional<String> sourceTitle() {
            return Optional.ofNullable(sourceTitle);
        }

        /**
         * Sets the search provider that returned this source.
         *
         * @param provider the new provider, or {@code null}
         */
        public void setProvider(SourceProvider provider) {
            this.provider = provider;
    }

        /**
         * Sets the CDN URL of a thumbnail image for this source.
         *
         * @param thumbnailCdnUrl the new thumbnail URL, or {@code null}
         */
        public void setThumbnailCdnUrl(URI thumbnailCdnUrl) {
            this.thumbnailCdnUrl = thumbnailCdnUrl;
    }

        /**
         * Sets the URL of the source page.
         *
         * @param sourceProviderUrl the new source URL, or {@code null}
         */
        public void setSourceProviderUrl(URI sourceProviderUrl) {
            this.sourceProviderUrl = sourceProviderUrl;
    }

        /**
         * Sets the search query that produced this source result.
         *
         * @param sourceQuery the new query, or {@code null}
         */
        public void setSourceQuery(String sourceQuery) {
            this.sourceQuery = sourceQuery;
    }

        /**
         * Sets the CDN URL of the source site's favicon.
         *
         * @param faviconCdnUrl the new favicon URL, or {@code null}
         */
        public void setFaviconCdnUrl(URI faviconCdnUrl) {
            this.faviconCdnUrl = faviconCdnUrl;
    }

        /**
         * Sets the citation number displayed in the bot response.
         *
         * @param citationNumber the new citation number, or {@code null}
         */
        public void setCitationNumber(Integer citationNumber) {
            this.citationNumber = citationNumber;
    }

        /**
         * Sets the title of the source page.
         *
         * @param sourceTitle the new title, or {@code null}
         */
        public void setSourceTitle(String sourceTitle) {
            this.sourceTitle = sourceTitle;
    }

        /**
         * The search provider that returned a source citation in a bot
         * response.
         */
        @ProtobufEnum(name = "BotSourcesMetadata.BotSourceItem.SourceProvider")
        public static enum SourceProvider {
            /**
             * An unknown or unrecognized source provider.
             */
            UNKNOWN(0),

            /**
             * Microsoft Bing search.
             */
            BING(1),

            /**
             * Google search.
             */
            GOOGLE(2),

            /**
             * A support/help-center search provider.
             */
            SUPPORT(3),

            /**
             * A source provider not covered by the other constants.
             */
            OTHER(4);

            /**
             * Constructs a new source provider constant with the specified
             * protobuf index.
             *
             * @param index the protobuf enum index
             */
            SourceProvider(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            /**
             * Returns the protobuf enum index of this source provider.
             *
             * @return the protobuf index
             */
            public int index() {
                return this.index;
            }
        }
    }
}
