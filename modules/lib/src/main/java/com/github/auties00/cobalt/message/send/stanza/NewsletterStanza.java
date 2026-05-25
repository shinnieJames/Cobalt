package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;

/**
 * Builds the SMAX-flavoured child nodes shared across newsletter publish variants.
 * <p>
 * Newsletter sends use a distinct wire format from E2E-encrypted chat or group sends: the payload travels in clear
 * inside a {@code <plaintext>} child under {@code <message to="...@newsletter">}, with no Signal envelope and no
 * {@code <participants>} list. This class exposes only the {@code <plaintext>} wrapper; the {@code media_id} attribute
 * for media newsletters is set directly on the outer {@code <message>}, not as a child.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutMessagePublishPayloadMixin")
public final class NewsletterStanza {
    /**
     * Prevents instantiation; this is a static composer.
     */
    private NewsletterStanza() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Builds a {@code <plaintext>} node wrapping a serialised payload.
     * <p>
     * The resulting node carries the payload as its byte content with no attributes.
     *
     * @param payload the serialised protobuf bytes
     * @return the {@code <plaintext>} {@link Node}
     */
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishPayloadMixin", exports = "applyMixin",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static Node buildPlaintext(byte[] payload) {
        return new NodeBuilder()
                .description("plaintext")
                .content(payload)
                .build();
    }

    /**
     * Builds a {@code <plaintext>} node carrying a {@code mediatype} attribute alongside the serialised payload.
     * <p>
     * Used by newsletter media sends; the SMAX media subtype string is one of {@code "image"}, {@code "video"},
     * {@code "url"}, {@code "audio"}, {@code "document"}, {@code "sticker"}, and similar values.
     *
     * @param payload   the serialised protobuf bytes
     * @param mediaType the SMAX media subtype string; must not be {@code null}
     * @return the {@code <plaintext>} {@link Node}
     */
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishNewsletterMediaMixin", exports = "applyMixin",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static Node buildPlaintext(byte[] payload, String mediaType) {
        return new NodeBuilder()
                .description("plaintext")
                .attribute("mediatype", mediaType)
                .content(payload)
                .build();
    }
}
