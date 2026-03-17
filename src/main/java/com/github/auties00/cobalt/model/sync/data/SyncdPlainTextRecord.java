package com.github.auties00.cobalt.model.sync.data;

import com.github.auties00.cobalt.model.sync.SyncActionData;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;

/**
 * A plaintext record used in snapshot recovery responses from the primary device.
 *
 * <p>Unlike {@link SyncdRecord}, which contains encrypted value blobs, this record
 * contains already-decrypted {@link SyncActionData} along with the original key ID
 * and value MAC. The companion device must re-derive the index MAC from the
 * plaintext index using the referenced sync key.
 *
 * <p>Per WhatsApp Web {@code SyncdPlainTextRecord}: this protobuf is part of the
 * {@link SyncdSnapshotRecovery} response sent by the primary device when a
 * companion requests snapshot recovery after a MAC validation failure.
 */
@ProtobufMessage(name = "SyncdPlainTextRecord")
public final class SyncdPlainTextRecord {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    SyncActionData value;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] keyId;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] mac;

    SyncdPlainTextRecord(SyncActionData value, byte[] keyId, byte[] mac) {
        this.value = value;
        this.keyId = keyId;
        this.mac = mac;
    }

    /**
     * Returns the already-decrypted action data containing the plaintext index,
     * action value, and action version.
     *
     * @return the plaintext action data, or empty if not present
     */
    public Optional<SyncActionData> value() {
        return Optional.ofNullable(value);
    }

    /**
     * Returns the key ID used to encrypt the original record on the primary device.
     *
     * <p>The companion uses this key ID to look up the sync key and re-derive
     * the index MAC.
     *
     * @return the key ID bytes, or empty if not present
     */
    public Optional<byte[]> keyId() {
        return Optional.ofNullable(keyId);
    }

    /**
     * Returns the value MAC from the primary device.
     *
     * <p>This MAC is used to populate the sync action entry store so that
     * future incremental LT-Hash computations remain correct.
     *
     * @return the value MAC bytes, or empty if not present
     */
    public Optional<byte[]> mac() {
        return Optional.ofNullable(mac);
    }

    /**
     * Sets the plaintext action data.
     *
     * @param value the action data to set
     * @return this record for chaining
     */
    public void setValue(SyncActionData value) {
        this.value = value;
    }

    /**
     * Sets the key ID.
     *
     * @param keyId the key ID bytes to set
     * @return this record for chaining
     */
    public void setKeyId(byte[] keyId) {
        this.keyId = keyId;
    }

    /**
     * Sets the value MAC.
     *
     * @param mac the value MAC bytes to set
     * @return this record for chaining
     */
    public void setMac(byte[] mac) {
        this.mac = mac;
    }
}
