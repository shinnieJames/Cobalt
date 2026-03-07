package com.github.auties00.cobalt.model.sync;

import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;

/**
 * A persisted orphan mutation entry representing a mutation that referenced an
 * entity not yet available locally at the time of processing.
 *
 * <p>Orphan mutations are stored so they can be retried when the referenced
 * entity arrives (e.g. via history sync or a new message). Each entry records
 * the plaintext index, the decoded action value, the mutation operation type,
 * the timestamp, and the action version.
 */
@ProtobufMessage
public final class OrphanMutationEntry {
    /**
     * The plaintext index string of the mutation.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String index;

    /**
     * The decoded action value of the mutation.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    SyncActionValue value;

    /**
     * The mutation operation type (SET or REMOVE).
     */
    @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
    SyncdOperation operation;

    /**
     * The timestamp of the mutation in epoch milliseconds.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    Instant timestamp;

    /**
     * The action version number.
     */
    @ProtobufProperty(index = 5, type = ProtobufType.INT32)
    int actionVersion;

    /**
     * Constructs a new {@code OrphanMutationEntry} with the specified fields.
     *
     * @param index         the plaintext index string
     * @param value         the decoded action value
     * @param operation     the mutation operation type
     * @param timestamp     the mutation timestamp
     * @param actionVersion the action version number
     */
    OrphanMutationEntry(String index, SyncActionValue value, SyncdOperation operation, Instant timestamp, int actionVersion) {
        this.index = index;
        this.value = value;
        this.operation = operation;
        this.timestamp = timestamp;
        this.actionVersion = actionVersion;
    }

    /**
     * Returns the plaintext index string of the mutation.
     *
     * @return the index string
     */
    public String index() {
        return index;
    }

    /**
     * Returns the decoded action value of the mutation.
     *
     * @return the action value
     */
    public SyncActionValue value() {
        return value;
    }

    /**
     * Returns the mutation operation type.
     *
     * @return the operation type
     */
    public SyncdOperation operation() {
        return operation;
    }

    /**
     * Returns the timestamp of the mutation.
     *
     * @return the timestamp
     */
    public Instant timestamp() {
        return timestamp;
    }

    /**
     * Returns the action version number.
     *
     * @return the action version
     */
    public int actionVersion() {
        return actionVersion;
    }
}
