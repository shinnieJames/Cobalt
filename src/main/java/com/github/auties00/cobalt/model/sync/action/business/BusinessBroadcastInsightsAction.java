package com.github.auties00.cobalt.model.sync.action.business;

import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.OptionalInt;

/**
 * A sync action representing business broadcast insights (delivery statistics).
 *
 * <p>Per WhatsApp Web, this action tracks recipient, delivered, read, replied,
 * and quick reply counts for a business broadcast campaign. It is synced via
 * the {@code REGULAR} collection.
 */
@ProtobufMessage(name = "SyncActionValue.BusinessBroadcastInsightsAction")
public final class BusinessBroadcastInsightsAction implements SyncAction<BusinessBroadcastInsightsActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "business_broadcast_insights_sync";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 1;

    /**
     * Canonical WhatsApp Web collection name for this action type.
     */
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR;

    @ProtobufProperty(index = 1, type = ProtobufType.INT32)
    Integer recipientCount;

    @ProtobufProperty(index = 2, type = ProtobufType.INT32)
    Integer deliveredCount;

    @ProtobufProperty(index = 3, type = ProtobufType.INT32)
    Integer readCount;

    @ProtobufProperty(index = 4, type = ProtobufType.INT32)
    Integer repliedCount;

    @ProtobufProperty(index = 5, type = ProtobufType.INT32)
    Integer quickReplyCount;

    BusinessBroadcastInsightsAction(Integer recipientCount, Integer deliveredCount, Integer readCount, Integer repliedCount, Integer quickReplyCount) {
        this.recipientCount = recipientCount;
        this.deliveredCount = deliveredCount;
        this.readCount = readCount;
        this.repliedCount = repliedCount;
        this.quickReplyCount = quickReplyCount;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String actionName() {
        return ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int actionVersion() {
        return ACTION_VERSION;
    }

    /**
     * Returns the total number of recipients.
     *
     * @return the recipient count, or empty if not set
     */
    public OptionalInt recipientCount() {
        return recipientCount == null ? OptionalInt.empty() : OptionalInt.of(recipientCount);
    }

    /**
     * Returns the number of delivered messages.
     *
     * @return the delivered count, or empty if not set
     */
    public OptionalInt deliveredCount() {
        return deliveredCount == null ? OptionalInt.empty() : OptionalInt.of(deliveredCount);
    }

    /**
     * Returns the number of read messages.
     *
     * @return the read count, or empty if not set
     */
    public OptionalInt readCount() {
        return readCount == null ? OptionalInt.empty() : OptionalInt.of(readCount);
    }

    /**
     * Returns the number of replied messages.
     *
     * @return the replied count, or empty if not set
     */
    public OptionalInt repliedCount() {
        return repliedCount == null ? OptionalInt.empty() : OptionalInt.of(repliedCount);
    }

    /**
     * Returns the number of quick replies.
     *
     * @return the quick reply count, or empty if not set
     */
    public OptionalInt quickReplyCount() {
        return quickReplyCount == null ? OptionalInt.empty() : OptionalInt.of(quickReplyCount);
    }
}
