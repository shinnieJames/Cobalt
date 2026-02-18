package com.github.auties00.cobalt.model.bot.response;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

/**
 * A message type that classifies the overall format of an
 * {@link AIRichResponseMessage} sent by the WhatsApp AI bot.
 *
 * <p>The type determines how the client should interpret and render
 * the list of {@link AIRichResponseSubMessage} fragments contained
 * in the response.
 */
@ProtobufEnum(name = "AIRichResponseMessageType")
public enum AIRichResponseMessageType {
    /**
     * An unrecognised or unsupported message type.
     *
     * <p>Clients should treat responses carrying this type as
     * unparseable and fall back to a plain-text representation.
     */
    UNKNOWN(0),

    /**
     * A standard rich response composed of one or more typed
     * {@link AIRichResponseSubMessage} fragments such as text,
     * code, tables, images, maps, or LaTeX expressions.
     */
    STANDARD(1);

    AIRichResponseMessageType(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    final int index;

    /**
     * Returns the protobuf index associated with this message type.
     *
     * @return the protobuf index
     */
    public int index() {
        return this.index;
    }
}
