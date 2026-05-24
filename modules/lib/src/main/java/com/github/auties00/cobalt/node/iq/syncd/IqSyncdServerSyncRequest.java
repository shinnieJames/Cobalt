package com.github.auties00.cobalt.node.iq.syncd;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SequencedCollection;

/**
 * Outbound {@code <iq xmlns="w:sync:app:state" type="set">} stanza that drives the syncd
 * app-state two-way sync loop for a typed list of collection entries.
 *
 * @apiNote
 * Use this to back WA Web's {@code WAWebSyncdServerSync.serverSync} loop: each
 * iteration uploads any pending local mutations as encrypted patches and asks the
 * relay for any patches / snapshot it has buffered for the bound collections. The
 * caller drives a fresh request per iteration; the relay's per-collection state
 * (see {@link IqSyncdServerSyncCollectionState}) determines whether to issue a
 * follow-up. The reply is parsed by {@link IqSyncdServerSyncResponse}.
 *
 * @implNote
 * This implementation mirrors WA Web's {@code WAWebSyncdRequestBuilderBuild.buildSyncIqNode}
 * verbatim: the outbound payload is a single {@code <sync>} child carrying one
 * {@code <collection name return_snapshot version>} per entry, with an optional
 * {@code <patch>} grandchild when the entry ships encrypted local mutations. WA
 * Web's {@code return_snapshot} attribute is set to {@code "true"} when the
 * caller has no local version (initial bootstrap) and {@code "false"} otherwise.
 */
@WhatsAppWebModule(moduleName = "WAWebSyncdServerSync")
@WhatsAppWebModule(moduleName = "WAWebSyncdRequestBuilderBuild")
public final class IqSyncdServerSyncRequest implements IqOperation.Request {
    /**
     * The default collection version emitted in the {@code version} attribute when
     * the caller has never synced a collection.
     *
     * @apiNote
     * Matches WA Web's {@code WASyncdConst.DEFAULT_COLLECTION_VERSION = 0}; the
     * relay interprets a zero version paired with {@code return_snapshot="true"}
     * as an initial-bootstrap request.
     */
    @WhatsAppWebExport(moduleName = "WASyncdConst",
            exports = "DEFAULT_COLLECTION_VERSION", adaptation = WhatsAppAdaptation.DIRECT)
    public static final long DEFAULT_COLLECTION_VERSION = 0L;

    /**
     * Holds the {@link IqSyncdServerSyncRequestCollection} entries to sync in this
     * iteration.
     */
    private final List<IqSyncdServerSyncRequestCollection> collections;

    /**
     * Constructs a new server-sync request bound to the given collection entries.
     *
     * @apiNote
     * An empty list produces a degenerate {@code <sync/>} child with no collection
     * grandchildren; WA Web treats this as a no-op iteration and returns an empty
     * reply. Callers normally pass at least one entry.
     *
     * @param collections the collection entries; never {@code null}, may be empty
     * @throws NullPointerException if {@code collections} is {@code null}
     */
    public IqSyncdServerSyncRequest(List<IqSyncdServerSyncRequestCollection> collections) {
        Objects.requireNonNull(collections, "collections cannot be null");
        this.collections = collections;
    }

    /**
     * Returns the bound list of collection entries.
     *
     * @return an unmodifiable view of the entries; never {@code null}, possibly
     *         empty
     */
    public SequencedCollection<IqSyncdServerSyncRequestCollection> collections() {
        return Collections.unmodifiableSequencedCollection(collections);
    }

    /**
     * Builds the outbound {@code <iq>} stanza from the typed collection list.
     *
     * @apiNote
     * The resulting {@link NodeBuilder} is wire-ready except for the IQ {@code id}
     * attribute, which the dispatch layer assigns. The encrypted patch protobuf
     * bytes carried in {@link IqSyncdServerSyncRequestCollection#patch()} are
     * routed verbatim into the optional {@code <patch>} grandchild.
     *
     * @implNote
     * This implementation sets {@code return_snapshot="true"} when the local
     * version is empty and {@code "false"} otherwise; the relay uses this to
     * decide whether to ship a snapshot blob or only patches.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and the typed
     *         {@code <sync>}/{@code <collection>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSyncdRequestBuilderBuild",
            exports = "buildSyncIqNode", adaptation = WhatsAppAdaptation.ADAPTED)
    public NodeBuilder toNode() {
        var collectionNodes = new ArrayList<Node>(collections.size());
        for (var collection : collections) {
            var collectionBuilder = new NodeBuilder()
                    .description("collection")
                    .attribute("name", collection.name())
                    .attribute("return_snapshot", collection.version().isEmpty() ? "true" : "false")
                    .attribute("version", collection.version().orElse(DEFAULT_COLLECTION_VERSION));
            collection.patch().ifPresent(patch -> {
                var patchNode = new NodeBuilder()
                        .description("patch")
                        .content(patch)
                        .build();
                collectionBuilder.content(patchNode);
            });
            collectionNodes.add(collectionBuilder.build());
        }
        var syncNode = new NodeBuilder()
                .description("sync")
                .content(collectionNodes)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:sync:app:state")
                .attribute("to", JidServer.user())
                .attribute("type", "set")
                .content(syncNode);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqSyncdServerSyncRequest) obj;
        return Objects.equals(this.collections, that.collections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(collections);
    }

    @Override
    public String toString() {
        return "IqSyncdServerSyncRequest[collections=" + collections + ']';
    }
}
