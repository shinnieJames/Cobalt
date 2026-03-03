package com.github.auties00.cobalt.sync.exchange;

import com.github.auties00.cobalt.model.media.ExternalBlobReference;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.data.SyncdPatch;

import java.util.*;

/**
 * Represents the parsed response from a sync request, containing either
 * a snapshot reference (as an {@link ExternalBlobReference} to be downloaded)
 * or a collection of patches.
 */
public final class MutationSyncResponse {
    private final SyncPatchType collectionName;
    private final long version;
    private final boolean hasMore;
    private final SequencedCollection<SyncdPatch> patches;
    private final ExternalBlobReference snapshotReference;

    /**
     * Constructs a new sync response.
     *
     * @param collectionName the sync collection type
     * @param version the collection version
     * @param hasMore whether more patches are available
     * @param patches the patches in this response
     * @param snapshotReference the external blob reference for the snapshot, or {@code null}
     */
    public MutationSyncResponse(
            SyncPatchType collectionName,
            long version,
            boolean hasMore,
            SequencedCollection<SyncdPatch> patches,
            ExternalBlobReference snapshotReference
    ) {
        this.collectionName = Objects.requireNonNull(collectionName);
        this.version = version;
        this.hasMore = hasMore;
        this.patches = patches;
        this.snapshotReference = snapshotReference;
    }

    /**
     * Returns whether this response contains a snapshot reference.
     *
     * @return {@code true} if a snapshot reference is present
     */
    public boolean isSnapshot() {
        return snapshotReference != null;
    }

    /**
     * Returns the sync collection type.
     *
     * @return the collection name
     */
    public SyncPatchType collectionName() {
        return collectionName;
    }

    /**
     * Returns the collection version.
     *
     * @return the version number
     */
    public long version() {
        return version;
    }

    /**
     * Returns whether more patches are available from the server.
     *
     * @return {@code true} if more patches are available
     */
    public boolean hasMore() {
        return hasMore;
    }

    /**
     * Returns the patches in this response.
     *
     * @return an unmodifiable collection of patches
     */
    public SequencedCollection<SyncdPatch> patches() {
        return patches == null ? List.of() : Collections.unmodifiableSequencedCollection(patches);
    }

    /**
     * Returns the external blob reference for the snapshot, if present.
     *
     * @return an optional containing the snapshot reference
     */
    public Optional<ExternalBlobReference> snapshotReference() {
        return Optional.ofNullable(snapshotReference);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MutationSyncResponse that
               && version == that.version
               && hasMore == that.hasMore
               && collectionName == that.collectionName
               && Objects.equals(patches, that.patches)
               && Objects.equals(snapshotReference, that.snapshotReference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(collectionName, version, hasMore, patches, snapshotReference);
    }

    @Override
    public String toString() {
        return "MutationSyncResponse[" +
               "collectionName=" + collectionName + ", " +
               "version=" + version + ", " +
               "hasMore=" + hasMore + ", " +
               "patches=" + patches + ", " +
               "snapshotReference=" + snapshotReference + ']';
    }
}
