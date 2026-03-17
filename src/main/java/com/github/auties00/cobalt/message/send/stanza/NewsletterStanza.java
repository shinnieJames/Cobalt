package com.github.auties00.cobalt.message.send.stanza;

import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;

/**
 * Builds common SMAX stanza child nodes used across newsletter
 * message types.
 *
 * @apiNote WASmaxOutMessagePublishPayloadMixin: wraps the serialised
 * protobuf in a {@code <plaintext>} node.
 * WASmaxOutMessagePublishNewsletterMediaPublishMixin: includes
 * {@code <media_id>handle</media_id>} when a media handle is available.
 */
public final class NewsletterStanza {
    private NewsletterStanza() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Builds the {@code <plaintext>} node wrapping a serialised payload.
     *
     * @param payload the serialised protobuf bytes
     * @return the plaintext node
     *
     * @apiNote WASmaxOutMessagePublishPayloadMixin
     */
    public static Node buildPlaintext(byte[] payload) {
        return new NodeBuilder()
                .description("plaintext")
                .content(payload)
                .build();
    }

    /**
     * Builds the {@code <plaintext>} node with a mediatype attribute.
     *
     * @param payload   the serialised protobuf bytes
     * @param mediaType the SMAX media type, or {@code null} for text
     * @return the plaintext node
     *
     * @apiNote WASmaxOutMessagePublishNewsletterMediaPublishMixin:
     * includes {@code mediatype} attribute on the plaintext node.
     */
    public static Node buildPlaintext(byte[] payload, String mediaType) {
        return new NodeBuilder()
                .description("plaintext")
                .attribute("mediatype", mediaType)
                .content(payload)
                .build();
    }

    /**
     * Builds the {@code <media_id>} node from the newsletter message
     * info's media handle, or returns {@code null} if absent.
     *
     * @param info the newsletter message info
     * @return the media_id node, or {@code null}
     *
     * @apiNote WASmaxOutMessagePublishNewsletterMediaPublishMixin
     */
    public static Node buildMediaId(NewsletterMessageInfo info) {
        return info.mediaHandle()
                .map(NewsletterStanza::buildMediaId)
                .orElse(null);
    }

    private static Node buildMediaId(String handle) {
        return new NodeBuilder()
                .description("media_id")
                .content(handle)
                .build();
    }
}
