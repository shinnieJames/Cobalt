package com.github.auties00.cobalt.model.bot.plugin;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

/**
 * Metadata describing how a document attached to a bot message is processed.
 *
 * <p>This message is attached to {@code BotMetadata} (field 34) and indicates
 * which document-processing plugin was used to extract content from the
 * attached file.
 */
@ProtobufMessage(name = "BotDocumentMessageMetadata")
public final class BotDocumentMessageMetadata {
    /**
     * The document-processing plugin type used to extract content from the
     * attachment.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    DocumentPluginType pluginType;

    /**
     * Constructs a new {@code BotDocumentMessageMetadata} with the specified
     * plugin type.
     *
     * @param pluginType the document plugin type, or {@code null}
     */
    BotDocumentMessageMetadata(DocumentPluginType pluginType) {
        this.pluginType = pluginType;
    }

    /**
     * Returns the document-processing plugin type used to extract content.
     *
     * @return an {@code Optional} describing the plugin type, or an empty
     *         {@code Optional} if not set
     */
    public Optional<DocumentPluginType> pluginType() {
        return Optional.ofNullable(pluginType);
    }

    /**
     * Sets the document-processing plugin type.
     *
     * @param pluginType the new plugin type, or {@code null}
     */
    public void setPluginType(DocumentPluginType pluginType) {
        this.pluginType = pluginType;
    }

    /**
     * The method used to extract content from a document attached to a bot
     * message.
     */
    @ProtobufEnum(name = "BotDocumentMessageMetadata.DocumentPluginType")
    public static enum DocumentPluginType {
        /**
         * Plain text extraction from the document (e.g. reading a PDF or
         * text file as raw text).
         */
        TEXT_EXTRACTION(0),

        /**
         * Optical character recognition (OCR) combined with image extraction,
         * used for scanned documents or image-heavy files.
         */
        OCR_AND_IMAGES(1);

        /**
         * Constructs a new document plugin type constant with the specified
         * protobuf index.
         *
         * @param index the protobuf enum index
         */
        DocumentPluginType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        /**
         * Returns the protobuf enum index of this document plugin type.
         *
         * @return the protobuf index
         */
        public int index() {
            return this.index;
        }
    }
}
