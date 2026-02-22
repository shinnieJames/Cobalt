package com.github.auties00.cobalt.sync.exchange;

import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.data.SyncdPatch;
import com.github.auties00.cobalt.model.sync.data.SyncdSnapshot;

import java.util.*;

public final class MutationSyncResponse {
    private final SyncPatchType collectionName;
    private final long version;
    private final boolean hasMore;
    private final SequencedCollection<SyncdPatch> patches;
    private final SyncdSnapshot snapshot;

    public MutationSyncResponse(
            SyncPatchType collectionName,
            long version,
            boolean hasMore,
            SequencedCollection<SyncdPatch> patches,
            SyncdSnapshot snapshot
    ) {
        this.collectionName = Objects.requireNonNull(collectionName);
        this.version = version;
        this.hasMore = hasMore;
        this.patches = patches;
        this.snapshot = snapshot;
    }

    public boolean isSnapshot() {
        return snapshot != null;
    }

    public SyncPatchType collectionName() {
        return collectionName;
    }

    public long version() {
        return version;
    }

    public boolean hasMore() {
        return hasMore;
    }

    public SequencedCollection<SyncdPatch> patches() {
        return patches == null ? List.of() : Collections.unmodifiableSequencedCollection(patches);
    }

    public Optional<SyncdSnapshot> snapshot() {
        return Optional.ofNullable(snapshot);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MutationSyncResponse that
               && version == that.version
               && hasMore == that.hasMore
               && collectionName == that.collectionName
               && Objects.equals(patches, that.patches)
               && Objects.equals(snapshot, that.snapshot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(collectionName, version, hasMore, patches, snapshot);
    }

    @Override
    public String toString() {
        return "MutationSyncResponse[" +
               "collectionName=" + collectionName + ", " +
               "version=" + version + ", " +
               "hasMore=" + hasMore + ", " +
               "patches=" + patches + ", " +
               "snapshot=" + snapshot + ']';
    }
}
