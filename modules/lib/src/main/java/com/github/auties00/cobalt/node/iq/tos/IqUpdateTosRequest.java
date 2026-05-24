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
 * Outbound {@code <iq xmlns="tos" type="set">} stanza that pushes a batch of
 * locally-accepted TOS / disclosure notices to the relay.
 *
 * @apiNote
 * Use this to back WA Web's {@code WAWebTos.TosManager.maybeUpdateServer} path: the
 * caller filters the locally-accepted notice ids against the set of ids the relay
 * has not yet seen and pushes them in a single batch. The relay's role is to record
 * acceptance, not to gate, so the caller can pre-mark notices as accepted before the
 * server roundtrip completes. The reply is parsed by {@link IqUpdateTosResponse}.
 *
 * @implNote
 * This implementation mirrors WA Web's {@code WAWebTosJob.updateTosState} verbatim:
 * the outbound payload is a single {@code <request type="session_update">} child
 * carrying one {@code <notice id="..."/>} grandchild per accepted id; WA Web also
 * uses {@code WAExponentialBackoff} to retry the 500 arm, which Cobalt callers
 * apply at the dispatch layer rather than here.
 */
@WhatsAppWebModule(moduleName = "WAWebTosJob")
public final class IqUpdateTosRequest implements IqOperation.Request {
    /**
     * Holds the notice ids being acknowledged; routed verbatim into one
     * {@code <notice/>} child per entry.
     */
    private final List<String> noticeIds;

    /**
     * Constructs a new update-tos request bound to the given notice ids.
     *
     * @apiNote
     * An empty list produces a {@code <request type="session_update">} child with
     * no grandchildren; WA Web's {@code TosManager.maybeUpdateServer} skips the
     * dispatch entirely in that case rather than emitting an empty batch.
     *
     * @param noticeIds the notice ids being acknowledged; never {@code null}, may
     *                  be empty
     * @throws NullPointerException if {@code noticeIds} is {@code null}
     */
    public IqUpdateTosRequest(List<String> noticeIds) {
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
     * Builds the outbound {@code <iq>} stanza wrapping the
     * {@code <request type="session_update">} payload.
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
            exports = "updateTosState", adaptation = WhatsAppAdaptation.DIRECT)
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
                .attribute("type", "session_update")
                .content(noticeNodes)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "tos")
                .attribute("to", Jid.userServer())
                .attribute("type", "set")
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
        var that = (IqUpdateTosRequest) obj;
        return Objects.equals(this.noticeIds, that.noticeIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(noticeIds);
    }

    @Override
    public String toString() {
        return "IqUpdateTosRequest[noticeIds=" + noticeIds + ']';
    }
}
