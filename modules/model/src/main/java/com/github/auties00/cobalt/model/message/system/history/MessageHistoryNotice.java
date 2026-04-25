package com.github.auties00.cobalt.model.message.system.history;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

/**
 * Informs a chat participant that a batch of historical messages is about to
 * be shared with them.
 *
 * <p>This notice is sent right before the corresponding
 * {@link MessageHistoryBundle} so that the chat UI can render a preview
 * banner (for example "You will receive N messages since T") and prepare the
 * user for the back-fill that follows. Unlike the bundle itself, the notice
 * carries no media reference; it only carries the descriptive metadata and
 * the surrounding context info.
 *
 * @implNote Mirrors the {@code Message$MessageHistoryNotice} protobuf spec
 * declared in {@code WAWebProtobufsE2E.pb} and produced by
 * {@code WAWebGenerateMessageHistoryNoticeProto} from the
 * {@code groupHistoryBundleMetadata} of the source message.
 */
@ProtobufMessage(name = "Message.MessageHistoryNotice")
@WhatsAppWebModule(moduleName = "WAWebProtobufsE2E.pb")
@WhatsAppWebModule(moduleName = "WAWebGenerateMessageHistoryNoticeProto")
public final class MessageHistoryNotice implements ContextualMessage {
    /**
     * The quoted-message and mention context in which the notice was sent.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    @WhatsAppWebExport(moduleName = "WAWebProtobufsE2E.pb", exports = "Message$MessageHistoryNoticeSpec.contextInfo", adaptation = WhatsAppAdaptation.DIRECT)
    ContextInfo contextInfo;

    /**
     * The descriptive metadata (recipients, oldest timestamp, message count)
     * of the history bundle being announced.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    @WhatsAppWebExport(moduleName = "WAWebProtobufsE2E.pb", exports = "Message$MessageHistoryNoticeSpec.messageHistoryMetadata", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebGenerateMessageHistoryNoticeProto", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    MessageHistoryMetadata messageHistoryMetadata;


    /**
     * Constructs a new history-sharing notice.
     *
     * @param contextInfo            the surrounding context info
     * @param messageHistoryMetadata the metadata of the announced history
     *                               bundle
     */
    MessageHistoryNotice(ContextInfo contextInfo, MessageHistoryMetadata messageHistoryMetadata) {
        this.contextInfo = contextInfo;
        this.messageHistoryMetadata = messageHistoryMetadata;
    }

    /**
     * Returns the contextual information (quoted message, mentions, ephemeral
     * settings) that surrounds this notice.
     *
     * @return an {@link Optional} containing the context info, or
     *         {@link Optional#empty()} if it was not provided
     */
    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    /**
     * Returns the descriptive metadata of the history bundle being announced.
     *
     * @return an {@link Optional} containing the history metadata, or
     *         {@link Optional#empty()} if it was not provided
     */
    public Optional<MessageHistoryMetadata> messageHistoryMetadata() {
        return Optional.ofNullable(messageHistoryMetadata);
    }

    /**
     * Sets the contextual information associated with this notice.
     *
     * @param contextInfo the new context info, may be {@code null}
     */
    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    /**
     * Sets the descriptive metadata of the announced history bundle.
     *
     * @param messageHistoryMetadata the new history metadata, may be
     *                               {@code null}
     */
    public void setMessageHistoryMetadata(MessageHistoryMetadata messageHistoryMetadata) {
        this.messageHistoryMetadata = messageHistoryMetadata;
    }
}
