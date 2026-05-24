package com.github.auties00.cobalt.node.smax.bugreporting;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * A single attachment carried by a {@link SmaxBugReportingReportBugRequest}.
 *
 * @apiNote
 * Models one entry of the {@code mediaArgs} array that
 * {@code WASmaxOutBugReportingReportBugRequest.makeReportBugRequest}
 * folds into the outbound report stanza as a repeated
 * {@code <media iv cipherKey type? fileName?>{bytes}</media>} child;
 * Cobalt embedders attach screenshots, logcat dumps, and other binary
 * artefacts to the same {@code submitBugReport} flow WA Web exposes via
 * the Help and Feedback surface.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutBugReportingReportBugRequest")
public final class SmaxBugReportingReportBugMediaUpload {
    /**
     * The IV used to encrypt the upload.
     *
     * @apiNote
     * Routed verbatim into the {@code iv} attribute by
     * {@link #toNode()} via the SMAX {@code CUSTOM_STRING} wrapper; the
     * relay stores the bytes opaquely so any binary-safe encoding the
     * caller picks (base64 / hex / raw) is preserved end to end.
     */
    private final String mediaIv;

    /**
     * The cipher key paired with {@link #mediaIv}.
     *
     * @apiNote
     * Routed verbatim into the {@code cipherKey} attribute by
     * {@link #toNode()} via the SMAX {@code CUSTOM_STRING} wrapper.
     */
    private final String mediaCipherKey;

    /**
     * The optional MIME type of the upload.
     *
     * @apiNote
     * Stamped on the {@code type} attribute only when non-null; mirrors
     * the {@code OPTIONAL(CUSTOM_STRING, mediaType)} attribute slot in
     * {@code makeReportBugRequestMedia}.
     */
    private final String mediaType;

    /**
     * The optional original file name of the upload.
     *
     * @apiNote
     * Stamped on the {@code fileName} attribute only when non-null;
     * mirrors the {@code OPTIONAL(CUSTOM_STRING, mediaFileName)}
     * attribute slot in {@code makeReportBugRequestMedia}.
     */
    private final String mediaFileName;

    /**
     * The encrypted blob carried as the {@code <media/>} content bytes.
     *
     * @apiNote
     * Stored as a raw byte array because the wire layer transmits it
     * verbatim; consumers are expected to have already encrypted the
     * payload under {@link #mediaCipherKey} and {@link #mediaIv} before
     * constructing this projection.
     */
    private final byte[] mediaElementValue;

    /**
     * Constructs a new media-upload projection.
     *
     * @apiNote
     * Called by embedders building the {@code mediaUploads} list passed
     * to {@link SmaxBugReportingReportBugRequest}; the request enforces
     * the {@code REPEATED_CHILD(media, 0, 10)} ceiling so callers do
     * not need to validate cardinality here.
     *
     * @param mediaIv             the encryption IV; never {@code null}
     * @param mediaCipherKey      the cipher key; never {@code null}
     * @param mediaType           the optional MIME type; may be
     *                            {@code null}
     * @param mediaFileName       the optional file name; may be
     *                            {@code null}
     * @param mediaElementValue   the encrypted blob; never {@code null}
     * @throws NullPointerException if any required argument is
     *                              {@code null}
     */
    public SmaxBugReportingReportBugMediaUpload(String mediaIv, String mediaCipherKey, String mediaType,
                       String mediaFileName, byte[] mediaElementValue) {
        this.mediaIv = Objects.requireNonNull(mediaIv, "mediaIv cannot be null");
        this.mediaCipherKey = Objects.requireNonNull(mediaCipherKey, "mediaCipherKey cannot be null");
        this.mediaType = mediaType;
        this.mediaFileName = mediaFileName;
        this.mediaElementValue = Objects.requireNonNull(mediaElementValue, "mediaElementValue cannot be null");
    }

    /**
     * Returns the encryption IV.
     *
     * @apiNote
     * Consumed by {@link #toNode()} to populate the {@code iv}
     * attribute; embedders that round-trip a parsed request also use
     * this value to decrypt {@link #mediaElementValue}.
     *
     * @return the IV; never {@code null}
     */
    public String mediaIv() {
        return mediaIv;
    }

    /**
     * Returns the cipher key.
     *
     * @apiNote
     * Consumed by {@link #toNode()} to populate the {@code cipherKey}
     * attribute; the relay treats the value as an opaque string so any
     * binary-safe encoding agreed with the decoder is acceptable.
     *
     * @return the cipher key; never {@code null}
     */
    public String mediaCipherKey() {
        return mediaCipherKey;
    }

    /**
     * Returns the optional MIME type.
     *
     * @apiNote
     * Empty when the embedder omitted it; the relay leaves the
     * {@code type} attribute off the rendered child accordingly.
     *
     * @return an {@link Optional} carrying the MIME type
     */
    public Optional<String> mediaType() {
        return Optional.ofNullable(mediaType);
    }

    /**
     * Returns the optional file name.
     *
     * @apiNote
     * Empty when the embedder omitted it; the relay leaves the
     * {@code fileName} attribute off the rendered child accordingly.
     *
     * @return an {@link Optional} carrying the file name
     */
    public Optional<String> mediaFileName() {
        return Optional.ofNullable(mediaFileName);
    }

    /**
     * Returns the encrypted blob.
     *
     * @apiNote
     * Returned as the underlying mutable array for zero-copy serialisation
     * by {@link #toNode()}; callers must not mutate the buffer.
     *
     * @return the bytes; never {@code null}
     */
    public byte[] mediaElementValue() {
        return mediaElementValue;
    }

    /**
     * Builds the {@code <media iv cipherKey type? fileName?>{bytes}</media>}
     * child node.
     *
     * @apiNote
     * Folded into the parent {@code <iq/>} stanza by
     * {@link SmaxBugReportingReportBugRequest#toNode()} as one entry of
     * the {@code REPEATED_CHILD(media, 0, 10)} slot.
     *
     * @return the rendered {@link Node}
     */
    @WhatsAppWebExport(moduleName = "WASmaxOutBugReportingReportBugRequest",
            exports = "makeReportBugRequestMedia",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Node toNode() {
        var builder = new NodeBuilder()
                .description("media")
                .attribute("iv", mediaIv)
                .attribute("cipherKey", mediaCipherKey)
                .content(mediaElementValue);
        if (mediaType != null) {
            builder.attribute("type", mediaType);
        }
        if (mediaFileName != null) {
            builder.attribute("fileName", mediaFileName);
        }
        return builder.build();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxBugReportingReportBugMediaUpload) obj;
        return Objects.equals(this.mediaIv, that.mediaIv)
                && Objects.equals(this.mediaCipherKey, that.mediaCipherKey)
                && Objects.equals(this.mediaType, that.mediaType)
                && Objects.equals(this.mediaFileName, that.mediaFileName)
                && Arrays.equals(this.mediaElementValue, that.mediaElementValue);
    }

    @Override
    public int hashCode() {
        var result = Objects.hash(mediaIv, mediaCipherKey, mediaType, mediaFileName);
        result = 31 * result + Arrays.hashCode(mediaElementValue);
        return result;
    }

    @Override
    public String toString() {
        return "SmaxBugReportingReportBugMediaUpload[mediaIv=" + mediaIv
                + ", mediaCipherKey=" + mediaCipherKey
                + ", mediaType=" + mediaType
                + ", mediaFileName=" + mediaFileName
                + ", mediaElementValue=" + Arrays.toString(mediaElementValue) + ']';
    }
}
