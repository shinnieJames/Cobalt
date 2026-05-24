package com.github.auties00.cobalt.node.smax.offlinebatch;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;

/**
 * The outbound {@code <ib><offline_batch count/></ib>} stanza.
 *
 * @apiNote
 * Drives the offline-stanza pump: WA Web's {@code WAWebOfflineHandler}
 * sends this request through {@code WASmaxOfflineBatchRPC.sendBatchRPC}
 * after every backlog-processing tick, telling the relay how many
 * additional offline stanzas the client is ready to absorb in the next
 * batch. The cast-shape RPC has no reply variant; the relay simply
 * resumes flushing offline messages until the announced budget is
 * consumed.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutOfflineBatchRequest")
public final class SmaxOfflineBatchRequest implements SmaxOperation.Request {
    /**
     * The number of offline stanzas the client is ready to absorb in
     * the upcoming batch.
     *
     * @implNote
     * This implementation stores the raw {@code int} value; WA Web
     * wraps it through {@code WAWap.INT} at serialisation time and the
     * relay rejects negative counts, but Cobalt does not pre-validate
     * the range here because the caller in
     * {@code LinkedWhatsAppClient.acknowledgeOfflineBatch} sources the
     * value from its own offline-pump counter.
     */
    private final int offlineBatchCount;

    /**
     * Constructs a request announcing the given offline-batch budget.
     *
     * @apiNote
     * Used by
     * {@link com.github.auties00.cobalt.client.LinkedWhatsAppClient#acknowledgeOfflineBatch(int)}
     * to keep the offline-stanza pump flowing across the connection's
     * post-login backlog drain.
     *
     * @param offlineBatchCount the expected offline-batch size
     */
    public SmaxOfflineBatchRequest(int offlineBatchCount) {
        this.offlineBatchCount = offlineBatchCount;
    }

    /**
     * Returns the announced offline-batch budget.
     *
     * @return the count
     */
    public int offlineBatchCount() {
        return offlineBatchCount;
    }

    /**
     * Builds the outbound stanza ready for dispatch.
     *
     * @apiNote
     * Produces {@code <ib><offline_batch count="N"/></ib>}; the
     * envelope carries no {@code id} attribute because the cast-shape
     * RPC is fire-and-forget. WA Web's
     * {@code WASmaxOutOfflineBatchRequest.makeBatchRequest} produces
     * the same node tree.
     *
     * @return a {@link NodeBuilder} carrying the {@code <ib>} envelope
     *         and the {@code <offline_batch/>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutOfflineBatchRequest",
            exports = "makeBatchRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var offlineBatchNode = new NodeBuilder()
                .description("offline_batch")
                .attribute("count", offlineBatchCount)
                .build();
        return new NodeBuilder()
                .description("ib")
                .content(offlineBatchNode);
    }

    /**
     * Returns whether the given object is a
     * {@link SmaxOfflineBatchRequest} with an equal batch count.
     *
     * @param obj the candidate; may be {@code null}
     * @return {@code true} when both batch counts match
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxOfflineBatchRequest) obj;
        return this.offlineBatchCount == that.offlineBatchCount;
    }

    /**
     * Returns a hash code derived from the batch count.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(offlineBatchCount);
    }

    /**
     * Returns a debug-friendly textual representation of this request.
     *
     * @return the textual representation
     */
    @Override
    public String toString() {
        return "SmaxOfflineBatchRequest[offlineBatchCount=" + offlineBatchCount + ']';
    }
}
