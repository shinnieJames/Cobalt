package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import java.util.Objects;

/**
 * The {@code (id, type)} pair carried on the CTWA native-ad
 * upload-media stanzas.
 *
 * @apiNote
 * Used both for the outbound primary {@code <media/>} child and for
 * each of the 0..10 {@code <media_list/>} children built by
 * {@link SmaxUploadAdMediaRequest#toNode()}, and for the matching
 * inbound echoes parsed by
 * {@link SmaxUploadAdMediaResponse.Success#of(com.github.auties00.cobalt.node.Node, com.github.auties00.cobalt.node.Node)}.
 * The {@code id} is the relay-allocated media identifier produced by
 * a prior upload; the {@code type} classifies the asset as
 * {@link SmaxUploadAdMediaMediaType#IMAGE} or
 * {@link SmaxUploadAdMediaMediaType#VIDEO}.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutBizCtwaNativeAdUploadAdMediaRequest")
@WhatsAppWebModule(moduleName = "WASmaxInBizCtwaNativeAdUploadAdMediaResponseSuccess")
public final class SmaxUploadAdMediaMediaEntry {
    /**
     * The relay-allocated media identifier.
     */
    private final String id;

    /**
     * The asset kind classifier.
     */
    private final SmaxUploadAdMediaMediaType type;

    /**
     * Constructs a new entry from the supplied identifier and type.
     *
     * @apiNote
     * Called by both the application code building a
     * {@link SmaxUploadAdMediaRequest} and the response parser in
     * {@link SmaxUploadAdMediaResponse.Success}.
     *
     * @param id   the relay-allocated media identifier; never
     *             {@code null}
     * @param type the asset kind; never {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    public SmaxUploadAdMediaMediaEntry(String id, SmaxUploadAdMediaMediaType type) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
    }

    /**
     * Returns the relay-allocated media identifier.
     *
     * @apiNote
     * Surfaces as the {@code id} attribute on the corresponding
     * {@code <media/>} or {@code <media_list/>} child.
     *
     * @return the identifier; never {@code null}
     */
    public String id() {
        return id;
    }

    /**
     * Returns the asset kind classifier.
     *
     * @apiNote
     * Surfaces as the {@code type} attribute on the corresponding
     * {@code <media/>} or {@code <media_list/>} child via
     * {@link SmaxUploadAdMediaMediaType#wire()}.
     *
     * @return the type; never {@code null}
     */
    public SmaxUploadAdMediaMediaType type() {
        return type;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxUploadAdMediaMediaEntry) obj;
        return Objects.equals(this.id, that.id) && this.type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type);
    }

    @Override
    public String toString() {
        return "SmaxUploadAdMediaMediaEntry[id=" + id + ", type=" + type + ']';
    }
}
