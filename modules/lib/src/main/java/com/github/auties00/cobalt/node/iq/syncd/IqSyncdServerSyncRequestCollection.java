package com.github.auties00.cobalt.node.iq.syncd;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * A single {@code <collection/>} entry inside an outbound
 * {@link IqSyncdServerSyncRequest}.
 *
 * @apiNote
 * Construct one entry per collection name (e.g. {@code "regular"}, {@code "regular_low"},
 * {@code "regular_high"}, {@code "critical_block"}, {@code "critical_unblock_low"}) per
 * sync iteration. Pass {@code version = null} to request an initial snapshot; pass a
 * known {@code version} to fetch only the patches above it. Pass {@code patch} bytes
 * to upload encrypted local mutations as part of the same iteration; pass
 * {@code null} when the entry only fetches remote state.
 */
public final class IqSyncdServerSyncRequestCollection {
    /**
     * Holds the collection name (one of the values in WA Web's
     * {@code WASyncdConst.CollectionName}).
     */
    private final String name;

    /**
     * Holds the locally-known collection version, or {@code null} when the caller
     * has never synced this collection.
     */
    private final Long version;

    /**
     * Holds the encoded {@code SyncdPatch} protobuf carrying encrypted local
     * mutations to push, or {@code null} when this entry only fetches remote state.
     */
    private final byte[] patch;

    /**
     * Constructs a new outbound collection entry.
     *
     * @apiNote
     * The {@code patch} parameter is the already-encrypted-and-encoded protobuf
     * bytes produced by WA Web's
     * {@code WAWebSyncdRequestBuilderBuild._buildCollectionNodes} pipeline (LtHash
     * computation, per-mutation encryption, snapshot/patch-MAC computation, and
     * SyncdPatch encoding); pass {@code null} when the caller has no local
     * mutations to ship.
     *
     * @param name    the collection name; never {@code null}
     * @param version the locally-known version, or {@code null} when the caller
     *                has never synced this collection
     * @param patch   the encoded {@code SyncdPatch} bytes, or {@code null}
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public IqSyncdServerSyncRequestCollection(String name, Long version, byte[] patch) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.version = version;
        this.patch = patch;
    }

    /**
     * Returns the collection name.
     *
     * @return the name; never {@code null}
     */
    public String name() {
        return name;
    }

    /**
     * Returns the locally-known collection version.
     *
     * @return an {@link Optional} containing the version, or empty when the caller
     *         has never synced this collection
     */
    public Optional<Long> version() {
        return Optional.ofNullable(version);
    }

    /**
     * Returns the encoded {@code SyncdPatch} bytes carrying local mutations.
     *
     * @return an {@link Optional} containing the patch bytes, or empty when this
     *         entry only fetches remote state
     */
    public Optional<byte[]> patch() {
        return Optional.ofNullable(patch);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqSyncdServerSyncRequestCollection) obj;
        return Objects.equals(this.name, that.name)
                && Objects.equals(this.version, that.version)
                && Arrays.equals(this.patch, that.patch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, version, Arrays.hashCode(patch));
    }

    @Override
    public String toString() {
        return "IqSyncdServerSyncRequestCollection[name=" + name
                + ", version=" + version
                + ", patch=" + (patch == null ? "null" : "byte[" + patch.length + "]") + ']';
    }
}
