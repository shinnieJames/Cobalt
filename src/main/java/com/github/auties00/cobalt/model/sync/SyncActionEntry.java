package com.github.auties00.cobalt.model.sync;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

/**
 * Stores the index MAC, value MAC and the key ID used to encrypt a mutation
 * in the sync action state map.
 *
 * <p>The key ID is preserved so that REMOVE operations can use the original
 * SET mutation's key for encryption, matching WhatsApp Web behavior.
 */
@ProtobufMessage
public final class SyncActionEntry {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] indexMac;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] valueMac;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] keyId;

    SyncActionEntry(byte[] indexMac, byte[] valueMac, byte[] keyId) {
        this.indexMac = indexMac;
        this.valueMac = valueMac;
        this.keyId = keyId;
    }

    public byte[] indexMac() {
        return indexMac;
    }

    public byte[] valueMac() {
        return valueMac;
    }

    public byte[] keyId() {
        return keyId;
    }

    public void setIndexMac(byte[] indexMac) {
        this.indexMac = indexMac;
    }

    public void setValueMac(byte[] valueMac) {
        this.valueMac = valueMac;
    }

    public void setKeyId(byte[] keyId) {
        this.keyId = keyId;
    }
}
