package com.github.auties00.cobalt.model.sync;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

/**
 * Stores per-mutation state in the sync action entry map, including the MAC
 * values used for LT-Hash computation, the key ID of the encrypting key, and
 * the plaintext index and action data needed for key rotation re-encryption.
 *
 * <p>The key ID is preserved so that REMOVE operations can use the original
 * SET mutation's key for encryption, matching WhatsApp Web behavior.
 *
 * <p>The plaintext index and action value are preserved so that key rotation
 * can re-encrypt entries using the latest app state sync key without requiring
 * a full re-sync from the server.
 */
@ProtobufMessage
public final class SyncActionEntry {
    /**
     * The HMAC of the mutation's plaintext index, used as a lookup key in the
     * sync action entry map.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] indexMac;

    /**
     * The HMAC of the mutation's encrypted value, used in LT-Hash computation.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] valueMac;

    /**
     * The ID of the app state sync key that was used to encrypt this mutation.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] keyId;

    /**
     * The plaintext index string of the mutation, preserved for key rotation
     * re-encryption.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    String actionIndex;

    /**
     * The decoded action value of the mutation, preserved for key rotation
     * re-encryption.
     */
    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    SyncActionValue actionValue;

    /**
     * The action version of the mutation, preserved for key rotation
     * re-encryption.
     */
    @ProtobufProperty(index = 6, type = ProtobufType.INT32)
    int actionVersion;

    /**
     * The processing state of this sync action mutation.
     *
     * <p>Per WhatsApp Web, every mutation is tracked with a state such as
     * {@code SUCCESS}, {@code ORPHAN}, or {@code UNSUPPORTED} to enable
     * proper auditing and re-processing.
     */
    @ProtobufProperty(index = 7, type = ProtobufType.INT32)
    SyncActionState actionState;

    /**
     * The identifier of the entity this action targets.
     *
     * <p>For example, a chat JID for chat actions, or a message ID for
     * message actions. Used for event-driven orphan retry.
     */
    @ProtobufProperty(index = 8, type = ProtobufType.STRING)
    String modelId;

    /**
     * The type of entity this action targets.
     *
     * <p>For example, {@code "chat"}, {@code "message"}, or {@code "contact"}.
     * Used in combination with {@code modelId} for targeted orphan retry.
     */
    @ProtobufProperty(index = 9, type = ProtobufType.STRING)
    String modelType;

    /**
     * Constructs a new {@code SyncActionEntry} with the given field values.
     *
     * @param indexMac      the HMAC of the mutation index
     * @param valueMac      the HMAC of the encrypted value
     * @param keyId         the encrypting key ID
     * @param actionIndex   the plaintext index string
     * @param actionValue   the decoded action value
     * @param actionVersion the action version number
     * @param actionState   the processing state of this mutation
     * @param modelId       the entity identifier this action targets
     * @param modelType     the entity type this action targets
     */
    SyncActionEntry(byte[] indexMac, byte[] valueMac, byte[] keyId, String actionIndex, SyncActionValue actionValue, int actionVersion, SyncActionState actionState, String modelId, String modelType) {
        this.indexMac = indexMac;
        this.valueMac = valueMac;
        this.keyId = keyId;
        this.actionIndex = actionIndex;
        this.actionValue = actionValue;
        this.actionVersion = actionVersion;
        this.actionState = actionState;
        this.modelId = modelId;
        this.modelType = modelType;
    }

    /**
     * Returns the HMAC of the mutation index.
     *
     * @return the index MAC bytes
     */
    public byte[] indexMac() {
        return indexMac;
    }

    /**
     * Returns the HMAC of the encrypted value.
     *
     * @return the value MAC bytes
     */
    public byte[] valueMac() {
        return valueMac;
    }

    /**
     * Returns the ID of the encrypting key.
     *
     * @return the key ID bytes
     */
    public byte[] keyId() {
        return keyId;
    }

    /**
     * Returns the plaintext index string.
     *
     * @return the index string, or {@code null} if not stored
     */
    public String actionIndex() {
        return actionIndex;
    }

    /**
     * Returns the decoded action value.
     *
     * @return the action value, or {@code null} if not stored
     */
    public SyncActionValue actionValue() {
        return actionValue;
    }

    /**
     * Returns the action version number.
     *
     * @return the action version
     */
    public int actionVersion() {
        return actionVersion;
    }

    /**
     * Sets the HMAC of the mutation index.
     *
     * @param indexMac the index MAC bytes
     */
    public void setIndexMac(byte[] indexMac) {
        this.indexMac = indexMac;
    }

    /**
     * Sets the HMAC of the encrypted value.
     *
     * @param valueMac the value MAC bytes
     */
    public void setValueMac(byte[] valueMac) {
        this.valueMac = valueMac;
    }

    /**
     * Sets the ID of the encrypting key.
     *
     * @param keyId the key ID bytes
     */
    public void setKeyId(byte[] keyId) {
        this.keyId = keyId;
    }

    /**
     * Sets the plaintext index string.
     *
     * @param actionIndex the index string
     */
    public void setActionIndex(String actionIndex) {
        this.actionIndex = actionIndex;
    }

    /**
     * Sets the decoded action value.
     *
     * @param actionValue the action value
     */
    public void setActionValue(SyncActionValue actionValue) {
        this.actionValue = actionValue;
    }

    /**
     * Sets the action version number.
     *
     * @param actionVersion the action version
     */
    public void setActionVersion(int actionVersion) {
        this.actionVersion = actionVersion;
    }

    /**
     * Returns the processing state of this sync action mutation.
     *
     * @return the action state, or {@code null} if not set
     */
    public SyncActionState actionState() {
        return actionState;
    }

    /**
     * Sets the processing state of this sync action mutation.
     *
     * @param actionState the action state
     */
    public void setActionState(SyncActionState actionState) {
        this.actionState = actionState;
    }

    /**
     * Returns the identifier of the entity this action targets.
     *
     * @return the model ID, or {@code null} if not set
     */
    public String modelId() {
        return modelId;
    }

    /**
     * Sets the identifier of the entity this action targets.
     *
     * @param modelId the model ID
     */
    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    /**
     * Returns the type of entity this action targets.
     *
     * @return the model type, or {@code null} if not set
     */
    public String modelType() {
        return modelType;
    }

    /**
     * Sets the type of entity this action targets.
     *
     * @param modelType the model type
     */
    public void setModelType(String modelType) {
        this.modelType = modelType;
    }
}
