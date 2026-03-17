package com.github.auties00.cobalt.model.jid.migration;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

/**
 * A protocol message that carries the compressed payload for a LID migration
 * mapping synchronization.
 *
 * <p>During the 1:1 chat LID migration process, the primary device sends this
 * message to companion devices as field 23 of {@code ProtocolMessage}. The
 * {@linkplain #encodedMappingPayload() encoded mapping payload} contains a
 * gzip-compressed, protobuf-encoded {@link LIDMigrationMappingSyncPayload}
 * that the companion must decompress and decode to obtain the phone-number-to-LID
 * mappings required for migrating its local chat threads from phone-number-based
 * addressing to LID-based addressing.
 *
 * <p>If the companion fails to parse this message, it triggers a logout with
 * a {@code LidMigrationFailedToParseMapping} reason.
 */
@ProtobufMessage(name = "LIDMigrationMappingSyncMessage")
public final class LIDMigrationMappingSyncMessage {
    /**
     * The gzip-compressed, protobuf-encoded {@link LIDMigrationMappingSyncPayload}.
     *
     * <p>To obtain the actual mappings, this byte array must be decompressed
     * with gzip and then decoded as a {@code LIDMigrationMappingSyncPayload}
     * protobuf message.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] encodedMappingPayload;


    /**
     * Constructs a new {@code LIDMigrationMappingSyncMessage} with the specified
     * encoded mapping payload.
     *
     * @param encodedMappingPayload the gzip-compressed, protobuf-encoded mapping
     *        payload, or {@code null}
     */
    LIDMigrationMappingSyncMessage(byte[] encodedMappingPayload) {
        this.encodedMappingPayload = encodedMappingPayload;
    }

    /**
     * Returns the gzip-compressed, protobuf-encoded mapping payload.
     *
     * @return an {@code Optional} describing the encoded payload bytes, or an
     *         empty {@code Optional} if not set
     */
    public Optional<byte[]> encodedMappingPayload() {
        return Optional.ofNullable(encodedMappingPayload);
    }

    /**
     * Sets the gzip-compressed, protobuf-encoded mapping payload.
     *
     * @param encodedMappingPayload the new encoded payload bytes, or {@code null}
     */
    public void setEncodedMappingPayload(byte[] encodedMappingPayload) {
        this.encodedMappingPayload = encodedMappingPayload;
    }
}
