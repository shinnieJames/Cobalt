package com.github.auties00.cobalt.node.smax.receipt;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Models the outbound {@code <receipt type="view">} stanza that publishes a
 * batch of newsletter or status view-receipts to the relay.
 *
 * <p>This request backs the newsletter and status view-counters. A single
 * stanza batches up to {@code 255} message server-ids so the relay can
 * increment the per-message view counter server-side. Setting
 * {@link #hasStatusClass()} selects between a regular newsletter view receipt
 * and a status-broadcast view receipt, the latter being marked with
 * {@code class="status"}. The reply is parsed by
 * {@link SmaxReceiptPublishViewResponse}.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutReceiptPublishViewRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutReceiptSenderAggregatedViewPublishMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutReceiptViewTypeMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutReceiptNewsletterMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutReceiptStatusClassMixin")
public final class SmaxReceiptPublishViewRequest implements SmaxOperation.Request {
    /**
     * Holds the opaque stanza id emitted as the receipt's {@code id} attribute.
     *
     * @implNote
     * This implementation accepts a caller-supplied {@link String} rather than
     * deriving the id from {@code WAWap.generateId}; the stanza id is stamped at
     * the job-call site so the typed request can stay caller-supplied.
     */
    private final String receiptId;

    /**
     * Holds the recipient JID of the acknowledgement, typically a newsletter or
     * status JID.
     */
    private final Jid receiptTo;

    /**
     * Holds whether the receipt carries {@code class="status"}, marking the ack
     * as a status-broadcast view receipt rather than a regular newsletter view
     * receipt.
     */
    private final boolean hasStatusClass;

    /**
     * Holds the {@code <item server_id=INT/>} entries, between {@code 0} and
     * {@code 255} of them.
     */
    private final List<Integer> itemServerIds;

    /**
     * Constructs a new view-receipt request.
     *
     * <p>Pass {@code hasStatusClass} as {@code true} when sending a
     * status-broadcast view receipt and {@code false} for a regular newsletter
     * view receipt. The list is defensively copied, so later mutation of the
     * supplied collection does not affect this request.
     *
     * @implNote
     * This implementation rejects batches larger than {@code 255} entries; the
     * ceiling matches the limit the relay enforces server-side.
     *
     * @param receiptId the stanza id; never {@code null}
     * @param receiptTo the recipient JID; never {@code null}
     * @param hasStatusClass whether to emit {@code class="status"}
     * @param itemServerIds the list of server ids; never {@code null}; at most
     *                      {@code 255} entries
     * @throws NullPointerException if any required argument is {@code null}
     * @throws IllegalArgumentException if {@code itemServerIds} carries more
     *                                  than {@code 255} entries
     */
    public SmaxReceiptPublishViewRequest(String receiptId, Jid receiptTo, boolean hasStatusClass,
                   List<Integer> itemServerIds) {
        this.receiptId = Objects.requireNonNull(receiptId, "receiptId cannot be null");
        this.receiptTo = Objects.requireNonNull(receiptTo, "receiptTo cannot be null");
        Objects.requireNonNull(itemServerIds, "itemServerIds cannot be null");
        if (itemServerIds.size() > 255) {
            throw new IllegalArgumentException(
                    "itemServerIds must carry at most 255 entries");
        }
        this.hasStatusClass = hasStatusClass;
        this.itemServerIds = List.copyOf(itemServerIds);
    }

    /**
     * Returns the stanza id emitted as the receipt's {@code id} attribute.
     *
     * @return the id; never {@code null}
     */
    public String receiptId() {
        return receiptId;
    }

    /**
     * Returns the recipient JID of the acknowledgement.
     *
     * @return the JID; never {@code null}
     */
    public Jid receiptTo() {
        return receiptTo;
    }

    /**
     * Returns whether the receipt carries {@code class="status"}.
     *
     * @return {@code true} when the {@code class="status"} attribute is set
     */
    public boolean hasStatusClass() {
        return hasStatusClass;
    }

    /**
     * Returns the {@code <item server_id/>} ids carried by this request.
     *
     * @return an unmodifiable list; never {@code null}
     */
    public List<Integer> itemServerIds() {
        return itemServerIds;
    }

    /**
     * Builds the outbound {@code <receipt>} stanza ready for dispatch.
     *
     * <p>The produced envelope has the shape
     * {@snippet lang="xml" :
     * <receipt id="..." to="..." type="view" class="status">
     *   <list>
     *     <item server_id="N"/>
     *     <!-- ... -->
     *   </list>
     * </receipt>
     * }
     * where the {@code class="status"} attribute is emitted only when
     * {@link #hasStatusClass()} returns {@code true}.
     *
     * @return a {@link NodeBuilder} carrying the receipt envelope and the
     *         {@code <list>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutReceiptPublishViewRequest",
            exports = "makePublishViewRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var itemNodes = new ArrayList<Node>(itemServerIds.size());
        for (var serverId : itemServerIds) {
            var itemNode = new NodeBuilder()
                    .description("item")
                    .attribute("server_id", serverId)
                    .build();
            itemNodes.add(itemNode);
        }
        var listNode = new NodeBuilder()
                .description("list")
                .content(itemNodes)
                .build();
        var receiptBuilder = new NodeBuilder()
                .description("receipt")
                .attribute("id", receiptId)
                .attribute("to", receiptTo)
                .attribute("type", "view")
                .attribute("class", "status", hasStatusClass)
                .content(listNode);
        return receiptBuilder;
    }

    /**
     * Compares this request to the given object for equality.
     *
     * <p>Two requests are equal when they are both
     * {@link SmaxReceiptPublishViewRequest} instances with equal echoed
     * attributes and item list.
     *
     * @param obj the candidate; may be {@code null}
     * @return {@code true} when every field matches
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxReceiptPublishViewRequest) obj;
        return this.hasStatusClass == that.hasStatusClass
                && Objects.equals(this.receiptId, that.receiptId)
                && Objects.equals(this.receiptTo, that.receiptTo)
                && Objects.equals(this.itemServerIds, that.itemServerIds);
    }

    /**
     * Returns a hash code derived from every field.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(receiptId, receiptTo, hasStatusClass, itemServerIds);
    }

    /**
     * Returns a debug-friendly textual representation of this request.
     *
     * @return the textual representation
     */
    @Override
    public String toString() {
        return "SmaxReceiptPublishViewRequest[receiptId=" + receiptId
                + ", receiptTo=" + receiptTo
                + ", hasStatusClass=" + hasStatusClass
                + ", itemServerIds=" + itemServerIds + ']';
    }
}
