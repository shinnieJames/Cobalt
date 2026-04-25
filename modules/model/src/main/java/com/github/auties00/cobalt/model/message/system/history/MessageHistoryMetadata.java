package com.github.auties00.cobalt.model.message.system.history;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.message.Message;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Describes the contents of a shared message history bundle in terms of
 * recipients, time range and size.
 *
 * <p>When a user shares a chat's history with another participant (for example
 * when adding someone to an existing group or forwarding a conversation), the
 * client packages the matching messages into a {@link MessageHistoryBundle}
 * and attaches this metadata so that both sides can display a preview such as
 * "N messages since T have been shared with X, Y, Z" before the recipient
 * opens the bundle.
 *
 * @implNote Mirrors the {@code Message$MessageHistoryMetadata} protobuf spec
 * declared in {@code WAWebProtobufsE2E.pb} and populated by
 * {@code WAWebGenerateMessageHistoryNoticeProto} from the
 * {@code groupHistoryBundleMetadata} of the source message.
 */
@ProtobufMessage(name = "Message.MessageHistoryMetadata")
@WhatsAppWebModule(moduleName = "WAWebProtobufsE2E.pb")
@WhatsAppWebModule(moduleName = "WAWebGenerateMessageHistoryNoticeProto")
public final class MessageHistoryMetadata implements Message {
    /**
     * The display strings of the participants who are receiving the shared
     * history, used to render the preview banner.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    @WhatsAppWebExport(moduleName = "WAWebProtobufsE2E.pb", exports = "Message$MessageHistoryMetadataSpec.historyReceivers", adaptation = WhatsAppAdaptation.DIRECT)
    List<String> historyReceivers;

    /**
     * The timestamp of the earliest message contained within the preview
     * window shown to the recipient (i.e. the oldest message that will be
     * visible as part of the history banner).
     */
    @ProtobufProperty(index = 2, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    @WhatsAppWebExport(moduleName = "WAWebProtobufsE2E.pb", exports = "Message$MessageHistoryMetadataSpec.oldestMessageTimestampInWindow", adaptation = WhatsAppAdaptation.DIRECT)
    Instant oldestMessageTimestampInWindow;

    /**
     * The total number of messages contained in the shared history bundle.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.INT64)
    @WhatsAppWebExport(moduleName = "WAWebProtobufsE2E.pb", exports = "Message$MessageHistoryMetadataSpec.messageCount", adaptation = WhatsAppAdaptation.DIRECT)
    Long messageCount;

    /**
     * The display strings of participants who are not receiving the shared
     * history but are listed for context (for example participants already in
     * the chat at the time of sharing).
     */
    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    @WhatsAppWebExport(moduleName = "WAWebProtobufsE2E.pb", exports = "Message$MessageHistoryMetadataSpec.nonHistoryReceivers", adaptation = WhatsAppAdaptation.DIRECT)
    List<String> nonHistoryReceivers;

    /**
     * The timestamp of the earliest message actually packaged into the
     * accompanying history bundle (which may extend further back in time than
     * the preview window reported by {@link #oldestMessageTimestampInWindow}).
     */
    @ProtobufProperty(index = 5, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    @WhatsAppWebExport(moduleName = "WAWebProtobufsE2E.pb", exports = "Message$MessageHistoryMetadataSpec.oldestMessageTimestampInBundle", adaptation = WhatsAppAdaptation.DIRECT)
    Instant oldestMessageTimestampInBundle;


    /**
     * Constructs a new history metadata payload.
     *
     * @param historyReceivers               the list of history receivers
     * @param oldestMessageTimestampInWindow the timestamp of the earliest
     *                                       message in the preview window
     * @param messageCount                   the total number of shared
     *                                       messages
     * @param nonHistoryReceivers            the list of non-history receivers
     * @param oldestMessageTimestampInBundle the timestamp of the earliest
     *                                       message in the packaged bundle
     */
    MessageHistoryMetadata(List<String> historyReceivers,
                           Instant oldestMessageTimestampInWindow,
                           Long messageCount,
                           List<String> nonHistoryReceivers,
                           Instant oldestMessageTimestampInBundle) {
        this.historyReceivers = historyReceivers;
        this.oldestMessageTimestampInWindow = oldestMessageTimestampInWindow;
        this.messageCount = messageCount;
        this.nonHistoryReceivers = nonHistoryReceivers;
        this.oldestMessageTimestampInBundle = oldestMessageTimestampInBundle;
    }

    /**
     * Returns an unmodifiable view of the display strings that identify the
     * recipients of the shared history.
     *
     * @return the list of recipients, or an empty list when none were
     *         provided
     */
    public List<String> historyReceivers() {
        return historyReceivers == null ? List.of() : Collections.unmodifiableList(historyReceivers);
    }

    /**
     * Returns the timestamp of the earliest message included in the preview
     * window shown to the recipient.
     *
     * @return an {@link Optional} containing the oldest-message timestamp, or
     *         {@link Optional#empty()} when it was not provided
     */
    public Optional<Instant> oldestMessageTimestampInWindow() {
        return Optional.ofNullable(oldestMessageTimestampInWindow);
    }

    /**
     * Returns the total number of messages contained in the shared bundle.
     *
     * @return an {@link OptionalLong} containing the message count, or
     *         {@link OptionalLong#empty()} when it was not provided
     */
    public OptionalLong messageCount() {
        return messageCount == null ? OptionalLong.empty() : OptionalLong.of(messageCount);
    }

    /**
     * Returns an unmodifiable view of the display strings that identify
     * participants who are not receiving the shared history but are listed
     * for context.
     *
     * @return the list of non-history recipients, or an empty list when none
     *         were provided
     */
    public List<String> nonHistoryReceivers() {
        return nonHistoryReceivers == null ? List.of() : Collections.unmodifiableList(nonHistoryReceivers);
    }

    /**
     * Returns the timestamp of the earliest message actually packaged into
     * the accompanying history bundle.
     *
     * @return an {@link Optional} containing the oldest-in-bundle timestamp,
     *         or {@link Optional#empty()} when it was not provided
     */
    public Optional<Instant> oldestMessageTimestampInBundle() {
        return Optional.ofNullable(oldestMessageTimestampInBundle);
    }

    /**
     * Sets the display strings that identify the recipients of the shared
     * history.
     *
     * @param historyReceivers the new list of recipients, may be {@code null}
     */
    public void setHistoryReceivers(List<String> historyReceivers) {
        this.historyReceivers = historyReceivers;
    }

    /**
     * Sets the timestamp of the earliest message included in the preview
     * window shown to the recipient.
     *
     * @param oldestMessageTimestampInWindow the new timestamp, may be
     *                                       {@code null}
     */
    public void setOldestMessageTimestampInWindow(Instant oldestMessageTimestampInWindow) {
        this.oldestMessageTimestampInWindow = oldestMessageTimestampInWindow;
    }

    /**
     * Sets the total number of messages contained in the shared bundle.
     *
     * @param messageCount the new message count, may be {@code null}
     */
    public void setMessageCount(Long messageCount) {
        this.messageCount = messageCount;
    }

    /**
     * Sets the display strings that identify participants who are not
     * receiving the shared history.
     *
     * @param nonHistoryReceivers the new list of non-history recipients, may
     *                            be {@code null}
     */
    public void setNonHistoryReceivers(List<String> nonHistoryReceivers) {
        this.nonHistoryReceivers = nonHistoryReceivers;
    }

    /**
     * Sets the timestamp of the earliest message actually packaged into the
     * accompanying history bundle.
     *
     * @param oldestMessageTimestampInBundle the new timestamp, may be
     *                                       {@code null}
     */
    public void setOldestMessageTimestampInBundle(Instant oldestMessageTimestampInBundle) {
        this.oldestMessageTimestampInBundle = oldestMessageTimestampInBundle;
    }
}
