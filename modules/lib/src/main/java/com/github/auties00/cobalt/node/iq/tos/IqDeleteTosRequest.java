package com.github.auties00.cobalt.node.iq.tos;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;
import java.util.Objects;

/**
 * Outbound {@code <iq xmlns="tos" type="set">} stanza that clears the server-side accepted
 * state for a single notice id.
 *
 * @apiNote
 * Use this to back WA Web's {@code WAWebTos.TosManager.resetState} /
 * {@code resetAllState} paths: the notice id is one of the well-known TOS / disclosure
 * identifiers (e.g. the 3P-disclosure id {@code "20210210"}, the bot agent / invoke /
 * shortcut TOS ids, the newsletter producer / consumer / admin-invite TOS ids, the
 * MM signal-sharing disclosure id, etc.). Resetting causes WA Web to re-prompt on the
 * next surface. The reply is parsed by {@link IqDeleteTosResponse}.
 *
 * @implNote
 * This implementation mirrors WA Web's {@code WAWebTosJob.deleteTosState} verbatim,
 * including the gating: WA Web gates the dispatch on the {@code gkx 26258}
 * server-killswitch (no-op when the killswitch is active). Cobalt does not consult
 * the gkx and always dispatches.
 */
@WhatsAppWebModule(moduleName = "WAWebTosJob")
public final class IqDeleteTosRequest implements IqOperation.Request {
    /**
     * Holds the notice id to clear, routed verbatim into the {@code <delete>}
     * child's {@code id} attribute.
     */
    private final String noticeId;

    /**
     * Constructs a new delete-tos request bound to the given notice id.
     *
     * @apiNote
     * Pass the literal notice-id string (e.g. {@code "20210210"}); WA Web treats
     * unknown ids as {@code UnknownUserNoticeIdError} at the TosManager layer
     * before this IQ is ever dispatched.
     *
     * @param noticeId the notice id to clear; never {@code null}
     * @throws NullPointerException if {@code noticeId} is {@code null}
     */
    public IqDeleteTosRequest(String noticeId) {
        this.noticeId = Objects.requireNonNull(noticeId, "noticeId cannot be null");
    }

    /**
     * Returns the bound notice id.
     *
     * @return the notice id; never {@code null}
     */
    public String noticeId() {
        return noticeId;
    }

    /**
     * Builds the outbound {@code <iq>} stanza wrapping the
     * {@code <delete id="..."/>} payload.
     *
     * @apiNote
     * The resulting {@link NodeBuilder} is wire-ready except for the IQ {@code id}
     * attribute, which the dispatch layer assigns.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and the
     *         {@code <delete>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebTosJob",
            exports = "deleteTosState", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var deleteNode = new NodeBuilder()
                .description("delete")
                .attribute("id", noticeId)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "tos")
                .attribute("to", Jid.userServer())
                .attribute("type", "set")
                .content(deleteNode);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqDeleteTosRequest) obj;
        return Objects.equals(this.noticeId, that.noticeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(noticeId);
    }

    @Override
    public String toString() {
        return "IqDeleteTosRequest[noticeId=" + noticeId + ']';
    }
}
