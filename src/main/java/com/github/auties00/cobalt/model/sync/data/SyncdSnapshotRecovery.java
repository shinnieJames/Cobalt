package com.github.auties00.cobalt.model.sync.data;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A snapshot recovery response sent by the primary device when a companion
 * device encounters a snapshot MAC validation failure.
 *
 * <p>Per WhatsApp Web {@code SyncdSnapshotRecovery}: this protobuf contains
 * the primary device's authoritative view of a collection's state, including
 * already-decrypted mutation records, the computed LT-Hash, and the current
 * version. The companion uses this data in-place instead of the corrupted
 * server snapshot.
 *
 * <p>The recovery data is encoded inside the {@code collectionSnapshot} field
 * of a {@code SyncDSnapshotFatalRecoveryResponse}, optionally gzip-compressed.
 */
@ProtobufMessage(name = "SyncdSnapshotRecovery")
public final class SyncdSnapshotRecovery {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    SyncdVersion version;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String collectionName;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    List<SyncdPlainTextRecord> mutationRecords;

    @ProtobufProperty(index = 4, type = ProtobufType.BYTES)
    byte[] collectionLthash;

    SyncdSnapshotRecovery(SyncdVersion version, String collectionName, List<SyncdPlainTextRecord> mutationRecords, byte[] collectionLthash) {
        this.version = version;
        this.collectionName = collectionName;
        this.mutationRecords = mutationRecords;
        this.collectionLthash = collectionLthash;
    }

    /**
     * Returns the version of the recovered snapshot.
     *
     * @return the snapshot version, or empty if not present
     */
    public Optional<SyncdVersion> version() {
        return Optional.ofNullable(version);
    }

    /**
     * Returns the name of the collection this recovery applies to.
     *
     * @return the collection name, or empty if not present
     */
    public Optional<String> collectionName() {
        return Optional.ofNullable(collectionName);
    }

    /**
     * Returns the plaintext mutation records from the primary device.
     *
     * <p>All records represent {@code SET} operations, as a recovery snapshot
     * contains the complete current state of the collection.
     *
     * @return the list of plaintext records, never {@code null}
     */
    public List<SyncdPlainTextRecord> mutationRecords() {
        return mutationRecords == null ? List.of() : Collections.unmodifiableList(mutationRecords);
    }

    /**
     * Returns the LT-Hash computed by the primary device for this collection.
     *
     * <p>This hash replaces the locally computed LT-Hash, avoiding the need
     * to recompute from the recovered mutations.
     *
     * @return the LT-Hash bytes, or empty if not present
     */
    public Optional<byte[]> collectionLthash() {
        return Optional.ofNullable(collectionLthash);
    }

    /**
     * Sets the snapshot version.
     *
     * @param version the version to set
     * @return this recovery for chaining
     */
    public void setVersion(SyncdVersion version) {
        this.version = version;
    }

    /**
     * Sets the collection name.
     *
     * @param collectionName the collection name to set
     * @return this recovery for chaining
     */
    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    /**
     * Sets the plaintext mutation records.
     *
     * @param mutationRecords the records to set
     * @return this recovery for chaining
     */
    public void setMutationRecords(List<SyncdPlainTextRecord> mutationRecords) {
        this.mutationRecords = mutationRecords;
    }

    /**
     * Sets the LT-Hash.
     *
     * @param collectionLthash the LT-Hash bytes to set
     * @return this recovery for chaining
     */
    public void setCollectionLthash(byte[] collectionLthash) {
        this.collectionLthash = collectionLthash;
    }
}
