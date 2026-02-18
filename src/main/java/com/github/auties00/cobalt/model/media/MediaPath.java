package com.github.auties00.cobalt.model.media;

import it.auties.protobuf.annotation.ProtobufEnum;

import java.util.*;

/**
 * A media attachment type that maps to a specific CDN upload/download path
 * and encryption key derivation label.
 *
 * <p>Each constant defines a triple of: the CDN path segment used to
 * construct the upload or download URL (for example {@code "mms/image"}),
 * the HKDF info string used when deriving the encryption key for that media
 * type (for example {@code "WhatsApp Image Keys"}), and a flag indicating
 * whether the downloaded content is compressed and must be inflated before
 * use.
 *
 * <p>The {@link #NONE} constant represents the absence of a media
 * attachment. All other constants describe a known attachment type. The
 * {@link #known()} method returns the set of all defined attachment types
 * excluding {@code NONE}, and {@link #ofId(String)} provides a lookup by
 * the path suffix (the portion after the {@code '/'} separator).
 *
 * <p>Newsletter media types use a separate CDN namespace prefixed with
 * {@code "newsletter/"} and do not require a key derivation label because
 * newsletter media is not end-to-end encrypted.
 */
@ProtobufEnum
public enum MediaPath {
    /**
     * The singleton instance representing the absence of a media attachment.
     * Both the path and key name are {@code null}.
     */
    NONE(null, null, false),

    /**
     * The singleton instance for audio attachments, using the CDN path
     * {@code "mms/audio"} and the key label {@code "WhatsApp Audio Keys"}.
     */
    AUDIO("mms/audio", "WhatsApp Audio Keys", false),

    /**
     * The singleton instance for document attachments, using the CDN path
     * {@code "mms/document"} and the key label
     * {@code "WhatsApp Document Keys"}.
     */
    DOCUMENT("mms/document", "WhatsApp Document Keys", false),

    /**
     * The singleton instance for GIF attachments, using the CDN path
     * {@code "mms/gif"} and the key label {@code "WhatsApp Video Keys"}.
     * GIF media is stored as video on the server.
     */
    GIF("mms/gif", "WhatsApp Video Keys", false),

    /**
     * The singleton instance for image attachments, using the CDN path
     * {@code "mms/image"} and the key label {@code "WhatsApp Image Keys"}.
     */
    IMAGE("mms/image", "WhatsApp Image Keys", false),

    /**
     * The singleton instance for profile picture media, using the CDN path
     * {@code "pps/photo"}. Profile pictures do not use a key derivation
     * label.
     */
    PROFILE_PICTURE("pps/photo", null, false),

    /**
     * The singleton instance for product images, using the CDN path
     * {@code "mms/image"} and the key label {@code "WhatsApp Image Keys"}.
     * Product images share the same encryption scheme as regular images.
     */
    PRODUCT("mms/image", "WhatsApp Image Keys", false),

    /**
     * The singleton instance for push-to-talk voice message attachments,
     * using the CDN path {@code "mms/ptt"} and the key label
     * {@code "WhatsApp Audio Keys"}.
     */
    VOICE("mms/ptt", "WhatsApp Audio Keys", false),

    /**
     * The singleton instance for sticker attachments, using the CDN path
     * {@code "mms/sticker"} and the key label
     * {@code "WhatsApp Image Keys"}.
     */
    STICKER("mms/sticker", "WhatsApp Image Keys", false),

    /**
     * The singleton instance for document thumbnail media, using the CDN
     * path {@code "mms/thumbnail-document"} and the key label
     * {@code "WhatsApp Document Thumbnail Keys"}.
     */
    THUMBNAIL_DOCUMENT("mms/thumbnail-document", "WhatsApp Document Thumbnail Keys", false),

    /**
     * The singleton instance for link preview thumbnail media, using the
     * CDN path {@code "mms/thumbnail-link"} and the key label
     * {@code "WhatsApp Link Thumbnail Keys"}.
     */
    THUMBNAIL_LINK("mms/thumbnail-link", "WhatsApp Link Thumbnail Keys", false),

    /**
     * The singleton instance for image thumbnail media, using the CDN path
     * {@code "mms/thumbnail-image"} and the key label
     * {@code "WhatsApp Image Thumbnail Keys"}.
     */
    THUMBNAIL_IMAGE("mms/thumbnail-image", "WhatsApp Image Thumbnail Keys", false),

    /**
     * The singleton instance for video thumbnail media, using the CDN path
     * {@code "mms/thumbnail-video"} and the key label
     * {@code "WhatsApp Video Thumbnail Keys"}.
     */
    THUMBNAIL_VIDEO("mms/thumbnail-video", "WhatsApp Video Thumbnail Keys", false),

    /**
     * The singleton instance for video attachments, using the CDN path
     * {@code "mms/video"} and the key label {@code "WhatsApp Video Keys"}.
     */
    VIDEO("mms/video", "WhatsApp Video Keys", false),

    /**
     * The singleton instance for push-to-video (personal video message)
     * attachments, using the CDN path {@code "mms/ptv"} and the key label
     * {@code "WhatsApp Video Keys"}.
     */
    PTV("mms/ptv", "WhatsApp Video Keys", false),

    /**
     * The singleton instance for application state synchronization blobs,
     * using the CDN path {@code "mms/md-app-state"} and the key label
     * {@code "WhatsApp App State Keys"}. This media type is inflatable,
     * meaning the downloaded content must be decompressed.
     */
    APP_STATE("mms/md-app-state", "WhatsApp App State Keys", true),

    /**
     * The singleton instance for message history synchronization blobs,
     * using the CDN path {@code "mms/md-msg-hist"} and the key label
     * {@code "WhatsApp History Keys"}. This media type is inflatable,
     * meaning the downloaded content must be decompressed.
     */
    HISTORY_SYNC("mms/md-msg-hist", "WhatsApp History Keys", true),

    /**
     * The singleton instance for product catalog images, using the CDN
     * path {@code "product/image"}. Catalog images do not use a key
     * derivation label.
     */
    PRODUCT_CATALOG_IMAGE("product/image", null, false),

    /**
     * The singleton instance for payment background images, using the CDN
     * path {@code "mms/payment-bg-image"} and the key label
     * {@code "WhatsApp Payment Background Keys"}.
     */
    PAYMENT_BG_IMAGE("mms/payment-bg-image", "WhatsApp Payment Background Keys", false),

    /**
     * The singleton instance for business cover photos, using the CDN path
     * {@code "pps/biz-cover-photo"}. Cover photos do not use a key
     * derivation label.
     */
    BUSINESS_COVER_PHOTO("pps/biz-cover-photo", null, false),

    /**
     * The singleton instance for native advertisement images, using the
     * CDN path {@code "mms/ads-image"} and the key label
     * {@code "ads-image"}.
     */
    NATIVE_AD_IMAGE("mms/ads-image", "ads-image", false),

    /**
     * The singleton instance for native advertisement videos, using the
     * CDN path {@code "mms/ads-video"} and the key label
     * {@code "ads-video"}.
     */
    NATIVE_AD_VIDEO("mms/ads-video", "ads-video", false),

    /**
     * The singleton instance for sticker pack bundles, using the CDN path
     * {@code "mms/sticker-pack"} and the key label
     * {@code "WhatsApp Sticker Pack Keys"}.
     */
    STICKER_PACK("mms/sticker-pack", "WhatsApp Sticker Pack Keys", false),

    /**
     * The singleton instance for sticker pack thumbnail media, using the
     * CDN path {@code "mms/thumbnail-sticker-pack"} and the key label
     * {@code "WhatsApp Sticker Pack Thumbnail Keys"}.
     */
    THUMBNAIL_STICKER_PACK("mms/thumbnail-sticker-pack", "WhatsApp Sticker Pack Thumbnail Keys", false),

    /**
     * The singleton instance for music artwork images, using the CDN path
     * {@code "mms/music-artwork"} and the key label
     * {@code "WhatsApp Music Artwork Keys"}.
     */
    MUSIC_ARTWORK("mms/music-artwork", "WhatsApp Music Artwork Keys", false),

    /**
     * The singleton instance for group history synchronization blobs,
     * using the CDN path {@code "mms/group-history"} and the key label
     * {@code "Group History"}.
     */
    GROUP_HISTORY("mms/group-history", "Group History", false),

    /**
     * The singleton instance for newsletter audio media, using the CDN
     * path {@code "newsletter/newsletter-audio"}. Newsletter media is not
     * end-to-end encrypted.
     */
    NEWSLETTER_AUDIO("newsletter/newsletter-audio", null, false),

    /**
     * The singleton instance for newsletter image media, using the CDN
     * path {@code "newsletter/newsletter-image"}. Newsletter media is not
     * end-to-end encrypted.
     */
    NEWSLETTER_IMAGE("newsletter/newsletter-image", null, false),

    /**
     * The singleton instance for newsletter document media, using the CDN
     * path {@code "newsletter/newsletter-document"}. Newsletter media is
     * not end-to-end encrypted.
     */
    NEWSLETTER_DOCUMENT("newsletter/newsletter-document", null, false),

    /**
     * The singleton instance for newsletter GIF media, using the CDN path
     * {@code "newsletter/newsletter-gif"}. Newsletter media is not
     * end-to-end encrypted.
     */
    NEWSLETTER_GIF("newsletter/newsletter-gif", null, false),

    /**
     * The singleton instance for newsletter voice message media, using
     * the CDN path {@code "newsletter/newsletter-ptt"}. Newsletter media
     * is not end-to-end encrypted.
     */
    NEWSLETTER_VOICE("newsletter/newsletter-ptt", null, false),

    /**
     * The singleton instance for newsletter push-to-video media, using
     * the CDN path {@code "newsletter/newsletter-ptv"}. Newsletter media
     * is not end-to-end encrypted.
     */
    NEWSLETTER_PTV("newsletter/newsletter-ptv", null, false),

    /**
     * The singleton instance for newsletter sticker media, using the CDN
     * path {@code "newsletter/newsletter-sticker"}. Newsletter media is
     * not end-to-end encrypted.
     */
    NEWSLETTER_STICKER("newsletter/newsletter-sticker", null, false),

    /**
     * The singleton instance for newsletter sticker pack media, using the
     * CDN path {@code "newsletter/newsletter-sticker-pack"}. Newsletter
     * media is not end-to-end encrypted.
     */
    NEWSLETTER_STICKER_PACK("newsletter/newsletter-sticker-pack", null, false),

    /**
     * The singleton instance for newsletter link preview thumbnails, using
     * the CDN path {@code "newsletter/newsletter-thumbnail-link"}.
     * Newsletter media is not end-to-end encrypted.
     */
    NEWSLETTER_THUMBNAIL_LINK("newsletter/newsletter-thumbnail-link", null, false),

    /**
     * The singleton instance for newsletter video media, using the CDN
     * path {@code "newsletter/newsletter-video"}. Newsletter media is not
     * end-to-end encrypted.
     */
    NEWSLETTER_VIDEO("newsletter/newsletter-video", null, false),

    /**
     * The singleton instance for newsletter music artwork media, using
     * the CDN path {@code "mms/newsletter-music-artwork"}. Newsletter
     * media is not end-to-end encrypted.
     */
    NEWSLETTER_MUSIC_ARTWORK("mms/newsletter-music-artwork", null, false);

    /**
     * The CDN path segment used to construct media upload and download URLs.
     */
    private final String path;

    /**
     * The HKDF info string used when deriving the encryption key for this
     * media type, or {@code null} if the media is not end-to-end encrypted.
     */
    private final String keyName;

    /**
     * Whether the downloaded content is compressed and must be inflated
     * (decompressed) before processing.
     */
    private final boolean inflatable;

    /**
     * The precomputed set of all known media path constants, excluding
     * {@link #NONE}.
     */
    private static final Set<MediaPath> KNOWN;

    /**
     * A lookup map from path suffix (the segment after the {@code '/'}
     * separator) to the corresponding {@code MediaPath} constant.
     */
    private static final Map<String, MediaPath> BY_ID;

    static {
        var known = new HashSet<MediaPath>();
        for(var value : values()) {
            if(value != NONE) {
                known.add(value);
            }
        }
        KNOWN = Collections.unmodifiableSet(known);

        Map<String, MediaPath> byId = HashMap.newHashMap(known.size());
        for(var value : known) {
            var path = value.path;
            var separator = path.indexOf('/');
            var id = separator == -1 ? path : path.substring(separator + 1);
            byId.put(id, value);
        }
        BY_ID = Collections.unmodifiableMap(byId);
    }

    /**
     * Returns the set of all known media path constants, excluding
     * {@link #NONE}.
     *
     * @return an unmodifiable set of known media paths
     */
    public static Set<MediaPath> known() {
        return KNOWN;
    }

    /**
     * Returns the {@code MediaPath} constant corresponding to the given
     * path suffix identifier.
     *
     * <p>The identifier is the portion of the CDN path after the
     * {@code '/'} separator. For example, passing {@code "image"} returns
     * {@link #IMAGE}.
     *
     * @param id the path suffix to look up
     * @return an {@link Optional} containing the matching media path, or
     *         empty if no constant matches the given identifier
     */
    public static Optional<MediaPath> ofId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    /**
     * Constructs a new {@code MediaPath} with the given CDN path, key
     * derivation label, and inflation flag.
     *
     * @param path      the CDN path segment, or {@code null} for {@link #NONE}
     * @param keyName   the HKDF info string, or {@code null} if not encrypted
     * @param inflatable whether the content must be decompressed after download
     */
    MediaPath(String path, String keyName, boolean inflatable) {
        this.path = path;
        this.keyName = keyName;
        this.inflatable = inflatable;
    }

    /**
     * Returns the CDN path segment used to construct media upload and
     * download URLs.
     *
     * @return an {@link Optional} containing the path, or empty for
     *         {@link #NONE}
     */
    public Optional<String> path() {
        return Optional.ofNullable(path);
    }

    /**
     * Returns the HKDF info string used when deriving the encryption key
     * for this media type.
     *
     * @return an {@link Optional} containing the key name, or empty if the
     *         media type is not end-to-end encrypted
     */
    public Optional<String> keyName() {
        return Optional.ofNullable(keyName);
    }

    /**
     * Returns whether the downloaded content for this media type is
     * compressed and must be inflated before processing.
     *
     * @return {@code true} if the content must be decompressed,
     *         {@code false} otherwise
     */
    public boolean inflatable() {
        return this.inflatable;
    }
}
