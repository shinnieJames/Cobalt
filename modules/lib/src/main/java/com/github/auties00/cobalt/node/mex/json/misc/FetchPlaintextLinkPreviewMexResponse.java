package com.github.auties00.cobalt.node.mex.json.misc;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.mex.MexOperation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * Inbound parsed response of the {@link FetchPlaintextLinkPreviewMexRequest}
 * query, exposing the server-unfurled link metadata and encrypted
 * thumbnail handle carried by the {@code xwa2_newsletter_link_preview}
 * envelope.
 *
 * @apiNote Drives the newsletter link-preview surface in WA Web; the
 * matching call-site feeds {@link #directPath()} and {@link #hash()} into
 * {@code WAWebLinkPreviewUtils.getThumbnailDetails} so the thumbnail can
 * be downloaded through the standard MMS media pipeline as a
 * {@code NEWSLETTER_THUMBNAIL_LINK}, then composes the preview through
 * {@code WAWebLinkPreviewUtils.genLinkPreview}. Cobalt callers may apply
 * the same pipeline or treat the projected fields as opaque metadata.
 *
 * @implNote This implementation surfaces every scalar as an
 * {@link Optional}; WA Web's call-site coerces missing {@code height} and
 * {@code width} to {@code 0} when building the {@code hqThumbnail}
 * record, and skips the high-quality thumbnail entirely when either
 * {@code direct_path} or {@code hash} is {@code null}. Cobalt leaves both
 * coercions and the absence policy to the caller.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchPlaintextLinkPreviewJob")
public final class FetchPlaintextLinkPreviewMexResponse implements MexOperation.Response.Json {
    /**
     * The {@code description} scalar projected from
     * {@code xwa2_newsletter_link_preview.description}.
     */
    private final String description;

    /**
     * The {@code direct_path} scalar carrying the encrypted thumbnail
     * media handle.
     */
    private final String directPath;

    /**
     * The {@code hash} scalar paired with {@link #directPath} when the
     * high-quality thumbnail is available.
     */
    private final String hash;

    /**
     * The {@code preview_type} scalar projected from
     * {@code xwa2_newsletter_link_preview.preview_type}.
     */
    private final String previewType;

    /**
     * The {@code thumb_data} scalar carrying the inline (low-resolution)
     * thumbnail bytes.
     */
    private final String thumbData;

    /**
     * The {@code title} scalar projected from
     * {@code xwa2_newsletter_link_preview.title}.
     */
    private final String title;

    /**
     * The {@code height} scalar of the preview thumbnail.
     */
    private final String height;

    /**
     * The {@code width} scalar of the preview thumbnail.
     */
    private final String width;

    /**
     * Constructs a new response wrapping the parsed scalar fields of the
     * {@code xwa2_newsletter_link_preview} envelope.
     *
     * @apiNote Private; instances are produced by the {@link #of(Node)}
     * parser.
     *
     * @param description  the {@code description} scalar, may be {@code null}
     * @param directPath   the {@code direct_path} scalar, may be {@code null}
     * @param hash         the {@code hash} scalar, may be {@code null}
     * @param previewType  the {@code preview_type} scalar, may be {@code null}
     * @param thumbData    the {@code thumb_data} scalar, may be {@code null}
     * @param title        the {@code title} scalar, may be {@code null}
     * @param height       the {@code height} scalar, may be {@code null}
     * @param width        the {@code width} scalar, may be {@code null}
     */
    private FetchPlaintextLinkPreviewMexResponse(String description, String directPath, String hash, String previewType, String thumbData, String title, String height, String width) {
        this.description = description;
        this.directPath = directPath;
        this.hash = hash;
        this.previewType = previewType;
        this.thumbData = thumbData;
        this.title = title;
        this.height = height;
        this.width = width;
    }

    /**
     * Parses the MEX response carried by an inbound IQ stanza.
     *
     * @apiNote Reads the {@code <result>} child's byte content and routes
     * it through the private byte-level parser. Returns
     * {@link Optional#empty()} when the stanza carries no result or when
     * the {@code data.xwa2_newsletter_link_preview} envelope is absent;
     * WA Web's call-site folds the same absence into a minimal local
     * preview through
     * {@code WAWebGenMinimalLinkPreviewChatAction.genMinimalLinkPreview}.
     *
     * @param node the inbound IQ stanza carrying the {@code <result>} child
     * @return an {@link Optional} wrapping the parsed response, or
     *         {@link Optional#empty()} if the expected JSON shape is absent
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchPlaintextLinkPreviewJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<FetchPlaintextLinkPreviewMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(FetchPlaintextLinkPreviewMexResponse::of);
    }

    /**
     * Returns the {@code description} scalar of the unfurled link preview.
     *
     * @return an {@link Optional} containing the description, or
     *         {@link Optional#empty()} if the relay omitted the scalar
     */
    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    /**
     * Returns the {@code direct_path} scalar carrying the encrypted
     * thumbnail media handle.
     *
     * @apiNote Paired with {@link #hash()} when feeding the WA Web
     * thumbnail download pipeline; both fields must be present for the
     * high-quality thumbnail to be fetchable.
     *
     * @return an {@link Optional} containing the direct path, or
     *         {@link Optional#empty()} if the relay omitted the scalar
     */
    public Optional<String> directPath() {
        return Optional.ofNullable(directPath);
    }

    /**
     * Returns the {@code hash} scalar paired with {@link #directPath()}
     * when the high-quality thumbnail is available.
     *
     * @return an {@link Optional} containing the thumbnail hash, or
     *         {@link Optional#empty()} if the relay omitted the scalar
     */
    public Optional<String> hash() {
        return Optional.ofNullable(hash);
    }

    /**
     * Returns the {@code preview_type} scalar classifying the unfurled
     * link.
     *
     * @return an {@link Optional} containing the preview type, or
     *         {@link Optional#empty()} if the relay omitted the scalar
     */
    public Optional<String> previewType() {
        return Optional.ofNullable(previewType);
    }

    /**
     * Returns the {@code thumb_data} scalar carrying the inline
     * (low-resolution) thumbnail bytes.
     *
     * @return an {@link Optional} containing the inline thumbnail data,
     *         or {@link Optional#empty()} if the relay omitted the scalar
     */
    public Optional<String> thumbData() {
        return Optional.ofNullable(thumbData);
    }

    /**
     * Returns the {@code title} scalar of the unfurled link preview.
     *
     * @return an {@link Optional} containing the title, or
     *         {@link Optional#empty()} if the relay omitted the scalar
     */
    public Optional<String> title() {
        return Optional.ofNullable(title);
    }

    /**
     * Returns the {@code height} scalar of the preview thumbnail.
     *
     * @return an {@link Optional} containing the height, or
     *         {@link Optional#empty()} if the relay omitted the scalar
     */
    public Optional<String> height() {
        return Optional.ofNullable(height);
    }

    /**
     * Returns the {@code width} scalar of the preview thumbnail.
     *
     * @return an {@link Optional} containing the width, or
     *         {@link Optional#empty()} if the relay omitted the scalar
     */
    public Optional<String> width() {
        return Optional.ofNullable(width);
    }

    /**
     * Parses the JSON payload carried by the {@code <result>} child into a
     * {@link FetchPlaintextLinkPreviewMexResponse}.
     *
     * @apiNote Private; routed through {@link #of(Node)} after the byte
     * content of the {@code <result>} child is extracted. Returns
     * {@link Optional#empty()} when the envelope, the {@code data} branch,
     * or the {@code xwa2_newsletter_link_preview} child is absent.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return an {@link Optional} wrapping the parsed response, or
     *         {@link Optional#empty()} if the
     *         {@code data.xwa2_newsletter_link_preview} envelope is absent
     */
    private static Optional<FetchPlaintextLinkPreviewMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_newsletter_link_preview");
        if (root == null) {
            return Optional.empty();
        }

        var description = root.getString("description");
        var directPath = root.getString("direct_path");
        var hash = root.getString("hash");
        var previewType = root.getString("preview_type");
        var thumbData = root.getString("thumb_data");
        var title = root.getString("title");
        var height = root.getString("height");
        var width = root.getString("width");

        return Optional.of(new FetchPlaintextLinkPreviewMexResponse(description, directPath, hash, previewType, thumbData, title, height, width));
    }
}
