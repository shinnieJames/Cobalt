package com.github.auties00.cobalt.model.sync.data;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

/**
 * Logical identifier of an app-state collection used in sync operations.
 *
 * <p>App-state is partitioned into named collections, each holding a specific
 * family of actions (for example chat-level actions, critical account-level
 * settings, or bulk low-priority settings). The values in this enum name the
 * partitions used by the sync protocol so that patches, snapshots and
 * mutations can be routed to the correct state store.
 *
 * <p>This enum mirrors the WhatsApp Web {@code WASyncdConst.CollectionName}
 * Mirrored enum with five partitions: {@code Regular} ({@code "regular"}),
 * {@code RegularLow} ({@code "regular_low"}), {@code RegularHigh}
 * ({@code "regular_high"}), {@code CriticalBlock} ({@code "critical_block"}),
 * and {@code CriticalUnblockLow} ({@code "critical_unblock_low"}). A leading
 * {@link #COLLECTION_NAME_UNKNOWN} sentinel at index {@code 0} is added so the
 * protobuf serializer can represent a missing or unrecognized partition.
 *
 * <p>At runtime, sync operations use the companion
 * {@link com.github.auties00.cobalt.model.sync.SyncPatchType} enum, which
 * carries both the protobuf index and the lowercase wire name; this enum is
 * retained for forward compatibility with protobuf messages that reference
 * {@code CollectionName} by name.
 *
 * @implNote WASyncdConst.CollectionName (h = e({Regular:"regular", RegularLow:"regular_low",
 *     RegularHigh:"regular_high", CriticalBlock:"critical_block",
 *     CriticalUnblockLow:"critical_unblock_low"}))
 */
@ProtobufEnum(name = "CollectionName")
public enum SyncCollectionName {
    /**
     * Sentinel value used when the collection is missing or unrecognised.
     *
     * @implNote NO_WA_BASIS: added so the protobuf serializer can express an
     *     unknown or absent {@code CollectionName} wire value
     */
    COLLECTION_NAME_UNKNOWN(0),
    /**
     * Standard-priority collection for most user actions.
     *
     * @implNote WASyncdConst.CollectionName.Regular ({@code "regular"})
     */
    REGULAR(1),
    /**
     * Low-priority, bulky collection used for large volumes of non-essential
     * actions.
     *
     * @implNote WASyncdConst.CollectionName.RegularLow ({@code "regular_low"})
     */
    REGULAR_LOW(2),
    /**
     * High-priority collection for actions that must sync quickly.
     *
     * @implNote WASyncdConst.CollectionName.RegularHigh ({@code "regular_high"})
     */
    REGULAR_HIGH(3),
    /**
     * Critical collection containing block-related state.
     *
     * @implNote WASyncdConst.CollectionName.CriticalBlock ({@code "critical_block"})
     */
    CRITICAL_BLOCK(4),
    /**
     * Critical, low-priority collection containing unblock-related state.
     *
     * @implNote WASyncdConst.CollectionName.CriticalUnblockLow ({@code "critical_unblock_low"})
     */
    CRITICAL_UNBLOCK_LOW(5);

    /**
     * Constructs a collection-name constant with the given protobuf index.
     *
     * @param index the protobuf wire index
     */
    SyncCollectionName(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    /**
     * The protobuf wire index assigned to this collection.
     */
    final int index;

    /**
     * Returns the protobuf wire index of this collection name.
     *
     * @return the protobuf enum index
     */
    public int index() {
        return this.index;
    }
}
