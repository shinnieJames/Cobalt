package com.github.auties00.cobalt.node.iq.tos;

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
 * Outbound {@code <iq xmlns="tos" type="get">} stanza that fetches the per-user
 * accepted-state for a batch of TOS / disclosure notice ids.
 *
 * @apiNote
 * Use this to back WA Web's {@code WAWebTos.TosManager.run} state-pull loop: the
 * caller passes the notice ids known to the local {@code TosManager} (the 3P
 * disclosure, bot agent / invoke / shortcut TOS, biz-bot TOS, MM signal-sharing
 * disclosure, newsletter producer / consumer / admin-invite TOS, biz-broadcast TOS,
 * etc.) and the relay returns the current accepted state plus a refresh interval
 * driving the loop cadence. The reply is parsed by {@link IqQueryTosResponse}.
 *
 * @implNote
 * This implementation mirrors WA Web's {@code WAWebTosJob.queryTosState} verbatim:
 * the outbound payload is a single {@code <request>} child carrying one
 * {@code <notice id="..."/>} grandchild per requested id.
 */
@WhatsAppWebModule(moduleName = "WAWebTosJob")
public final class IqQueryTosRequest implements IqOperation.Request {
    /**
     * Holds the notice ids being queried; routed verbatim into one
     * {@code <notice/>} child per entry.
     */
    private final List<String> noticeIds;

    /**
     * Constructs a new query-tos request bound to the given notice ids.
     *
     * @apiNote
     * An empty list produces a {@code <request>} child with no grandchildren; the
     * relay returns an empty notice list inside a still-valid envelope, which the
     * caller treats as a no-op.
     *
     * @param noticeIds the notice ids to query; never {@code null}, may be empty
     * @throws NullPointerException if {@code noticeIds} is {@code null}
     */
    public IqQueryTosRequest(List<String> noticeIds) {
        Objects.requireNonNull(noticeIds, "noticeIds cannot be null");
        this.noticeIds = List.copyOf(noticeIds);
    }

    /**
     * Returns the unmodifiable list of bound notice ids.
     *
     * @return the notice ids; never {@code null}
     */
    public List<String> noticeIds() {
        return noticeIds;
    }

    /**
     * Builds the outbound {@code <iq>} stanza wrapping the {@code <request>}
     * payload.
     *
     * @apiNote
     * The resulting {@link NodeBuilder} is wire-ready except for the IQ {@code id}
     * attribute, which the dispatch layer assigns.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and the
     *         {@code <request>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebTosJob",
            exports = "queryTosState", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var noticeNodes = new ArrayList<Node>(noticeIds.size());
        for (var id : noticeIds) {
            var noticeNode = new NodeBuilder()
                    .description("notice")
                    .attribute("id", id)
                    .build();
            noticeNodes.add(noticeNode);
        }
        var requestNode = new NodeBuilder()
                .description("request")
                .content(noticeNodes)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "tos")
                .attribute("to", Jid.userServer())
                .attribute("type", "get")
                .content(requestNode);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqQueryTosRequest) obj;
        return Objects.equals(this.noticeIds, that.noticeIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(noticeIds);
    }

    @Override
    public String toString() {
        return "IqQueryTosRequest[noticeIds=" + noticeIds + ']';
    }
}
