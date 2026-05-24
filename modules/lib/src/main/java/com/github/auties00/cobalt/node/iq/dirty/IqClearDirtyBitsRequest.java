package com.github.auties00.cobalt.node.iq.dirty;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Outbound {@code <iq xmlns="urn:xmpp:whatsapp:dirty" type="set">} stanza acknowledging that
 * a batch of dirty-bit resources has been resynchronised on the client.
 *
 * @apiNote
 * Used by the dirty-bit pipeline: when the relay raises an {@code <ib>} broadcast that one
 * of the supported resource types ({@code account_sync}, {@code groups}, {@code blocklist},
 * {@code syncd_app_state}, ...) needs a refresh, the client performs the corresponding sync
 * and then sends this request to clear the relay-side dirty marker so the broadcast is not
 * repeated. WA Web invokes it from {@code WAWebHandleDirtyBits.handleDirtyBits} at the end
 * of every dirty-bit processing pass.
 */
@WhatsAppWebModule(moduleName = "WAWebClearDirtyBitsJob")
public final class IqClearDirtyBitsRequest implements IqOperation.Request {
    /**
     * Dirty-bit entries to clear.
     *
     * @apiNote
     * Must be non-empty; WA Web's {@code clearDirtyBits(t)} early-returns on
     * {@code t.length === 0} without dispatching anything, and Cobalt rejects the same shape
     * at construction time so the empty-batch invariant fails fast on the caller side.
     */
    private final List<DirtyEntry> entries;

    /**
     * Constructs a new clear-dirty-bits request.
     *
     * @apiNote
     * Defensively copies {@code entries} so subsequent mutation by the caller does not
     * affect the dispatched stanza.
     *
     * @param entries the dirty entries to clear
     * @throws NullPointerException     if {@code entries} is {@code null}
     * @throws IllegalArgumentException if {@code entries} is empty
     */
    public IqClearDirtyBitsRequest(List<DirtyEntry> entries) {
        Objects.requireNonNull(entries, "entries cannot be null");
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("entries cannot be empty");
        }
        this.entries = List.copyOf(entries);
    }

    /**
     * Returns the unmodifiable list of dirty entries being cleared.
     *
     * @return the entries, never {@code null} or empty
     */
    public List<DirtyEntry> entries() {
        return entries;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Produces a {@code <iq xmlns="urn:xmpp:whatsapp:dirty" type="set">} envelope addressed
     * to {@link Jid#userServer()} and wrapping one
     * {@code <clean type="..." timestamp="..."/>} child per entry, mirroring WA Web's
     * {@code t.map(e => wap("clean", {type, timestamp}))} fan-out.
     *
     * @return a {@link NodeBuilder} carrying the {@code <iq>} envelope and the
     *         {@code <clean/>} payload children
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebClearDirtyBitsJob",
            exports = "clearDirtyBits", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var cleanNodes = new ArrayList<Node>(entries.size());
        for (var entry : entries) {
            var cleanNode = new NodeBuilder()
                    .description("clean")
                    .attribute("type", entry.type())
                    .attribute("timestamp", entry.timestamp())
                    .build();
            cleanNodes.add(cleanNode);
        }
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "urn:xmpp:whatsapp:dirty")
                .attribute("to", Jid.userServer())
                .attribute("type", "set")
                .content(cleanNodes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqClearDirtyBitsRequest) obj;
        return Objects.equals(this.entries, that.entries);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(entries);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "IqClearDirtyBitsRequest[entries=" + entries + ']';
    }

    /**
     * Per-resource dirty-bit entry, materialised as a single {@code <clean/>} child of the
     * outbound request.
     *
     * @apiNote
     * Carries the {@code (type, timestamp)} pair that the relay needs to identify the dirty
     * marker; {@code timestamp} is the high-water-mark the client reached during the
     * corresponding sync, and the relay only clears markers whose own timestamp is at most
     * this value.
     */
    @WhatsAppWebModule(moduleName = "WAWebClearDirtyBitsJob")
    public static final class DirtyEntry {
        /**
         * Resource type for the dirty marker.
         *
         * @apiNote
         * One of the documented resource keys ({@code account_sync}, {@code groups},
         * {@code blocklist}, {@code syncd_app_state}, ...); routed verbatim into the
         * {@code type} attribute.
         */
        private final String type;

        /**
         * High-water-mark timestamp the relay should treat as "clean as of".
         *
         * @apiNote
         * Routed verbatim into the {@code timestamp} attribute via WA Web's
         * {@code WAWap.INT} wrapper; expressed in the relay's native dirty-bit clock units.
         */
        private final long timestamp;

        /**
         * Constructs a new dirty-bit entry.
         *
         * @param type      the resource type
         * @param timestamp the high-water-mark timestamp
         * @throws NullPointerException if {@code type} is {@code null}
         */
        public DirtyEntry(String type, long timestamp) {
            this.type = Objects.requireNonNull(type, "type cannot be null");
            this.timestamp = timestamp;
        }

        /**
         * Returns the resource type.
         *
         * @return the type, never {@code null}
         */
        public String type() {
            return type;
        }

        /**
         * Returns the high-water-mark timestamp.
         *
         * @return the timestamp
         */
        public long timestamp() {
            return timestamp;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (DirtyEntry) obj;
            return this.timestamp == that.timestamp
                    && Objects.equals(this.type, that.type);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(type, timestamp);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "Request.DirtyEntry[type=" + type
                    + ", timestamp=" + timestamp + ']';
        }
    }
}
