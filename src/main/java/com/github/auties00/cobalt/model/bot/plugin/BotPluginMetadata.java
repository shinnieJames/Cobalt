package com.github.auties00.cobalt.model.bot.plugin;

import com.github.auties00.cobalt.model.message.MessageKey;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.net.URI;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Metadata describing a bot plugin that contributed to a bot response, such as
 * a web search or Instagram Reels integration.
 *
 * <p>This message is attached to {@code BotMetadata} (field 3) and carries
 * information about which search provider was used, the query that was
 * executed, and any media thumbnails or reference links returned by the
 * plugin.
 *
 * <p>A plugin response may have a parent plugin (identified by
 * {@link #parentPluginMessageKey()}) when the response is a follow-up or
 * drill-down from a previous plugin result.
 */
@ProtobufMessage(name = "BotPluginMetadata")
public final class BotPluginMetadata {
    /**
     * The search provider that fulfilled this plugin request.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    SearchProvider provider;

    /**
     * The type of plugin that generated this response.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    PluginType pluginType;

    /**
     * The CDN URL of the thumbnail image for a Reels plugin result, for
     * example {@code "https://mmg.whatsapp.net/v/t62.1234/image.jpg"}.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    URI thumbnailCdnUrl;

    /**
     * The CDN URL of the search provider's profile photo, for example
     * {@code "https://mmg.whatsapp.net/v/t62.1234/profile.jpg"}.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    URI profilePhotoCdnUrl;

    /**
     * The URL of the search provider's result page, for example
     * {@code "https://www.bing.com/search?q=weather+today"}.
     */
    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    URI searchProviderUrl;

    /**
     * The zero-based index of this plugin result within the list of results
     * returned by the search provider.
     */
    @ProtobufProperty(index = 6, type = ProtobufType.UINT32)
    Integer referenceIndex;

    /**
     * The expected number of citation links that this plugin result contains.
     */
    @ProtobufProperty(index = 7, type = ProtobufType.UINT32)
    Integer expectedLinksCount;

    /**
     * The search query string that was sent to the search provider, for
     * example {@code "weather in New York today"}.
     */
    @ProtobufProperty(index = 9, type = ProtobufType.STRING)
    String searchQuery;

    /**
     * The message key of the parent plugin message that this result is a
     * follow-up to, if any.
     */
    @ProtobufProperty(index = 10, type = ProtobufType.MESSAGE)
    MessageKey parentPluginMessageKey;

    /**
     * A deprecated field that previously held the plugin type. Use
     * {@link #pluginType()} instead.
     *
     * @deprecated superseded by {@link #pluginType()}
     */
    @ProtobufProperty(index = 11, type = ProtobufType.ENUM)
    PluginType deprecatedField;

    /**
     * The plugin type of the parent message, when this result is a follow-up
     * to a previous plugin response.
     */
    @ProtobufProperty(index = 12, type = ProtobufType.ENUM)
    PluginType parentPluginType;

    /**
     * The CDN URL of the search provider's favicon, for example
     * {@code "https://mmg.whatsapp.net/v/t62.1234/favicon.ico"}.
     */
    @ProtobufProperty(index = 13, type = ProtobufType.STRING)
    URI faviconCdnUrl;

    /**
     * Constructs a new {@code BotPluginMetadata} with the specified values.
     *
     * @param provider               the search provider, or {@code null}
     * @param pluginType             the plugin type, or {@code null}
     * @param thumbnailCdnUrl        the thumbnail CDN URL, or {@code null}
     * @param profilePhotoCdnUrl     the profile photo CDN URL, or {@code null}
     * @param searchProviderUrl      the search provider URL, or {@code null}
     * @param referenceIndex         the result reference index, or {@code null}
     * @param expectedLinksCount     the expected links count, or {@code null}
     * @param searchQuery            the search query, or {@code null}
     * @param parentPluginMessageKey the parent plugin message key, or {@code null}
     * @param deprecatedField        the deprecated plugin type, or {@code null}
     * @param parentPluginType       the parent plugin type, or {@code null}
     * @param faviconCdnUrl          the favicon CDN URL, or {@code null}
     */
    BotPluginMetadata(SearchProvider provider, PluginType pluginType, URI thumbnailCdnUrl, URI profilePhotoCdnUrl, URI searchProviderUrl, Integer referenceIndex, Integer expectedLinksCount, String searchQuery, MessageKey parentPluginMessageKey, PluginType deprecatedField, PluginType parentPluginType, URI faviconCdnUrl) {
        this.provider = provider;
        this.pluginType = pluginType;
        this.thumbnailCdnUrl = thumbnailCdnUrl;
        this.profilePhotoCdnUrl = profilePhotoCdnUrl;
        this.searchProviderUrl = searchProviderUrl;
        this.referenceIndex = referenceIndex;
        this.expectedLinksCount = expectedLinksCount;
        this.searchQuery = searchQuery;
        this.parentPluginMessageKey = parentPluginMessageKey;
        this.deprecatedField = deprecatedField;
        this.parentPluginType = parentPluginType;
        this.faviconCdnUrl = faviconCdnUrl;
    }

    /**
     * Returns the search provider that fulfilled this plugin request.
     *
     * @return an {@code Optional} describing the search provider, or an empty
     *         {@code Optional} if not set
     */
    public Optional<SearchProvider> provider() {
        return Optional.ofNullable(provider);
    }

    /**
     * Returns the type of plugin that generated this response.
     *
     * @return an {@code Optional} describing the plugin type, or an empty
     *         {@code Optional} if not set
     */
    public Optional<PluginType> pluginType() {
        return Optional.ofNullable(pluginType);
    }

    /**
     * Returns the CDN URL of the thumbnail image for a Reels plugin result.
     *
     * @return an {@code Optional} describing the thumbnail URL, or an empty
     *         {@code Optional} if not set
     */
    public Optional<URI> thumbnailCdnUrl() {
        return Optional.ofNullable(thumbnailCdnUrl);
    }

    /**
     * Returns the CDN URL of the search provider's profile photo.
     *
     * @return an {@code Optional} describing the profile photo URL, or an
     *         empty {@code Optional} if not set
     */
    public Optional<URI> profilePhotoCdnUrl() {
        return Optional.ofNullable(profilePhotoCdnUrl);
    }

    /**
     * Returns the URL of the search provider's result page.
     *
     * @return an {@code Optional} describing the search provider URL, or an
     *         empty {@code Optional} if not set
     */
    public Optional<URI> searchProviderUrl() {
        return Optional.ofNullable(searchProviderUrl);
    }

    /**
     * Returns the zero-based index of this plugin result within the list of
     * results returned by the search provider.
     *
     * @return an {@code OptionalInt} describing the reference index, or an
     *         empty {@code OptionalInt} if not set
     */
    public OptionalInt referenceIndex() {
        return referenceIndex == null ? OptionalInt.empty() : OptionalInt.of(referenceIndex);
    }

    /**
     * Returns the expected number of citation links in this plugin result.
     *
     * @return an {@code OptionalInt} describing the links count, or an empty
     *         {@code OptionalInt} if not set
     */
    public OptionalInt expectedLinksCount() {
        return expectedLinksCount == null ? OptionalInt.empty() : OptionalInt.of(expectedLinksCount);
    }

    /**
     * Returns the search query string that was sent to the search provider.
     *
     * @return an {@code Optional} describing the search query, or an empty
     *         {@code Optional} if not set
     */
    public Optional<String> searchQuery() {
        return Optional.ofNullable(searchQuery);
    }

    /**
     * Returns the message key of the parent plugin message.
     *
     * @return an {@code Optional} describing the parent message key, or an
     *         empty {@code Optional} if not set
     */
    public Optional<MessageKey> parentPluginMessageKey() {
        return Optional.ofNullable(parentPluginMessageKey);
    }

    /**
     * Returns the deprecated plugin type field.
     *
     * @return an {@code Optional} describing the deprecated plugin type, or an
     *         empty {@code Optional} if not set
     * @deprecated superseded by {@link #pluginType()}
     */
    public Optional<PluginType> deprecatedField() {
        return Optional.ofNullable(deprecatedField);
    }

    /**
     * Returns the plugin type of the parent message.
     *
     * @return an {@code Optional} describing the parent plugin type, or an
     *         empty {@code Optional} if not set
     */
    public Optional<PluginType> parentPluginType() {
        return Optional.ofNullable(parentPluginType);
    }

    /**
     * Returns the CDN URL of the search provider's favicon.
     *
     * @return an {@code Optional} describing the favicon URL, or an empty
     *         {@code Optional} if not set
     */
    public Optional<URI> faviconCdnUrl() {
        return Optional.ofNullable(faviconCdnUrl);
    }

    /**
     * Sets the search provider that fulfilled this plugin request.
     *
     * @param provider the new search provider, or {@code null}
     */
    public void setProvider(SearchProvider provider) {
        this.provider = provider;
    }

    /**
     * Sets the type of plugin that generated this response.
     *
     * @param pluginType the new plugin type, or {@code null}
     */
    public void setPluginType(PluginType pluginType) {
        this.pluginType = pluginType;
    }

    /**
     * Sets the CDN URL of the thumbnail image.
     *
     * @param thumbnailCdnUrl the new thumbnail URL, or {@code null}
     */
    public void setThumbnailCdnUrl(URI thumbnailCdnUrl) {
        this.thumbnailCdnUrl = thumbnailCdnUrl;
    }

    /**
     * Sets the CDN URL of the search provider's profile photo.
     *
     * @param profilePhotoCdnUrl the new profile photo URL, or {@code null}
     */
    public void setProfilePhotoCdnUrl(URI profilePhotoCdnUrl) {
        this.profilePhotoCdnUrl = profilePhotoCdnUrl;
    }

    /**
     * Sets the URL of the search provider's result page.
     *
     * @param searchProviderUrl the new search provider URL, or {@code null}
     */
    public void setSearchProviderUrl(URI searchProviderUrl) {
        this.searchProviderUrl = searchProviderUrl;
    }

    /**
     * Sets the zero-based index of this plugin result.
     *
     * @param referenceIndex the new reference index, or {@code null}
     */
    public void setReferenceIndex(Integer referenceIndex) {
        this.referenceIndex = referenceIndex;
    }

    /**
     * Sets the expected number of citation links.
     *
     * @param expectedLinksCount the new links count, or {@code null}
     */
    public void setExpectedLinksCount(Integer expectedLinksCount) {
        this.expectedLinksCount = expectedLinksCount;
    }

    /**
     * Sets the search query string.
     *
     * @param searchQuery the new search query, or {@code null}
     */
    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    /**
     * Sets the message key of the parent plugin message.
     *
     * @param parentPluginMessageKey the new parent message key, or {@code null}
     */
    public void setParentPluginMessageKey(MessageKey parentPluginMessageKey) {
        this.parentPluginMessageKey = parentPluginMessageKey;
    }

    /**
     * Sets the deprecated plugin type field.
     *
     * @param deprecatedField the new deprecated plugin type, or {@code null}
     * @deprecated superseded by {@link #setPluginType(PluginType)}
     */
    public void setDeprecatedField(PluginType deprecatedField) {
        this.deprecatedField = deprecatedField;
    }

    /**
     * Sets the plugin type of the parent message.
     *
     * @param parentPluginType the new parent plugin type, or {@code null}
     */
    public void setParentPluginType(PluginType parentPluginType) {
        this.parentPluginType = parentPluginType;
    }

    /**
     * Sets the CDN URL of the search provider's favicon.
     *
     * @param faviconCdnUrl the new favicon URL, or {@code null}
     */
    public void setFaviconCdnUrl(URI faviconCdnUrl) {
        this.faviconCdnUrl = faviconCdnUrl;
    }

    /**
     * The type of bot plugin that generated a response.
     */
    @ProtobufEnum(name = "BotPluginMetadata.PluginType")
    public static enum PluginType {
        /**
         * An unknown or unrecognized plugin type.
         */
        UNKNOWN_PLUGIN(0),

        /**
         * An Instagram Reels integration plugin that surfaces short-form video
         * content in bot responses.
         */
        REELS(1),

        /**
         * A web search plugin that queries an external search provider (e.g.
         * Bing, Google) and returns results inline.
         */
        SEARCH(2);

        /**
         * Constructs a new plugin type constant with the specified protobuf
         * index.
         *
         * @param index the protobuf enum index
         */
        PluginType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        /**
         * Returns the protobuf enum index of this plugin type.
         *
         * @return the protobuf index
         */
        public int index() {
            return this.index;
        }
    }

    /**
     * The external search provider used by a bot plugin to fulfill a query.
     */
    @ProtobufEnum(name = "BotPluginMetadata.SearchProvider")
    public static enum SearchProvider {
        /**
         * An unknown or unrecognized search provider.
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
        SUPPORT(3);

        /**
         * Constructs a new search provider constant with the specified
         * protobuf index.
         *
         * @param index the protobuf enum index
         */
        SearchProvider(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        /**
         * Returns the protobuf enum index of this search provider.
         *
         * @return the protobuf index
         */
        public int index() {
            return this.index;
        }
    }
}
