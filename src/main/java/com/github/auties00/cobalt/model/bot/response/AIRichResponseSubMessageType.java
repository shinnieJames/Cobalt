package com.github.auties00.cobalt.model.bot.response;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

/**
 * A content type that identifies which metadata payload is carried
 * by an {@link AIRichResponseSubMessage} fragment.
 *
 * <p>Each constant maps to exactly one of the optional metadata
 * fields on {@code AIRichResponseSubMessage}; the client uses this
 * discriminator to decide which field to read and how to render the
 * fragment.
 */
@ProtobufEnum(name = "AIRichResponseSubMessageType")
public enum AIRichResponseSubMessageType {
    /**
     * An unrecognised or unsupported fragment type.
     *
     * <p>Clients should skip or display a fallback placeholder for
     * fragments of this type.
     */
    UNKNOWN(0),

    /**
     * A grid of images rendered as a collage.
     *
     * <p>The corresponding metadata is
     * {@link AIRichResponseSubMessage#gridImageMetadata()}.
     */
    GRID_IMAGE(1),

    /**
     * A plain-text or markdown-formatted text fragment.
     *
     * <p>The corresponding payload is
     * {@link AIRichResponseSubMessage#messageText()}.
     */
    TEXT(2),

    /**
     * A single inline image with optional alignment and tap link.
     *
     * <p>The corresponding metadata is
     * {@link AIRichResponseSubMessage#imageMetadata()}.
     */
    INLINE_IMAGE(3),

    /**
     * A tabular data fragment rendered as rows and columns.
     *
     * <p>The corresponding metadata is
     * {@link AIRichResponseSubMessage#tableMetadata()}.
     */
    TABLE(4),

    /**
     * A syntax-highlighted code block.
     *
     * <p>The corresponding metadata is
     * {@link AIRichResponseSubMessage#codeMetadata()}.
     */
    CODE(5),

    /**
     * A dynamic media element such as an animated GIF or a
     * static image delivered via a URL.
     *
     * <p>The corresponding metadata is
     * {@link AIRichResponseSubMessage#dynamicMetadata()}.
     */
    DYNAMIC(6),

    /**
     * An interactive map view with pin annotations.
     *
     * <p>The corresponding metadata is
     * {@link AIRichResponseSubMessage#mapMetadata()}.
     */
    MAP(7),

    /**
     * A LaTeX mathematical expression rendered as an image.
     *
     * <p>The corresponding metadata is
     * {@link AIRichResponseSubMessage#latexMetadata()}.
     */
    LATEX(8),

    /**
     * A collection of content items such as reels displayed
     * as a carousel.
     *
     * <p>The corresponding metadata is
     * {@link AIRichResponseSubMessage#contentItemsMetadata()}.
     */
    CONTENT_ITEMS(9);

    AIRichResponseSubMessageType(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    final int index;

    /**
     * Returns the protobuf index associated with this sub-message type.
     *
     * @return the protobuf index
     */
    public int index() {
        return this.index;
    }
}
