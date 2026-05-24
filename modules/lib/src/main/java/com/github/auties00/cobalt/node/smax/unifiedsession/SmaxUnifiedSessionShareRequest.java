package com.github.auties00.cobalt.node.smax.unifiedsession;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;

/**
 * The outbound {@code <ib><unified_session id/></ib>} stanza.
 *
 * @apiNote
 * Announces this device's WAM user-journey analytics session id to the
 * relay. WA Web's {@code WAWebUnifiedSession} module derives the id from
 * a rolling 7-day clock ({@code (now + 3d) mod 7d}) and tags WAM events
 * emitted from the Channels surface, the Forward-message flow, the
 * CTWA ad-creation pipeline, and the signup funnel. Cobalt does not run
 * those user-journey loggers itself; the request is exposed through
 * {@link com.github.auties00.cobalt.client.LinkedWhatsAppClient#joinUnifiedSession(String)}
 * for embedders that mirror WA Web's telemetry surface.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutUnifiedSessionShareRequest")
public final class SmaxUnifiedSessionShareRequest implements SmaxOperation.Request {
    /**
     * The opaque user-journey session id token to announce to the relay.
     */
    private final String unifiedSessionId;

    /**
     * Constructs a request announcing the given session id.
     *
     * @apiNote
     * The cast-shape RPC has no reply; the relay accepts any non-null
     * string verbatim and the caller is responsible for re-issuing the
     * request when its rolling-clock derivation produces a new id.
     *
     * @param unifiedSessionId the session id token; never {@code null}
     * @throws NullPointerException if {@code unifiedSessionId} is
     *                              {@code null}
     */
    public SmaxUnifiedSessionShareRequest(String unifiedSessionId) {
        this.unifiedSessionId = Objects.requireNonNull(unifiedSessionId, "unifiedSessionId cannot be null");
    }

    /**
     * Returns the announced session id.
     *
     * @return the id; never {@code null}
     */
    public String unifiedSessionId() {
        return unifiedSessionId;
    }

    /**
     * Builds the outbound stanza ready for dispatch.
     *
     * @apiNote
     * Produces {@code <ib><unified_session id="..."/></ib>}; the
     * cast-shape RPC carries no {@code id} attribute on the envelope
     * because no reply is expected.
     *
     * @return a {@link NodeBuilder} carrying the {@code <ib>} envelope
     *         and the {@code <unified_session/>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutUnifiedSessionShareRequest",
            exports = "makeShareRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var unifiedSessionNode = new NodeBuilder()
                .description("unified_session")
                .attribute("id", unifiedSessionId)
                .build();
        return new NodeBuilder()
                .description("ib")
                .content(unifiedSessionNode);
    }

    /**
     * Returns whether the given object is a
     * {@link SmaxUnifiedSessionShareRequest} with an equal session id.
     *
     * @param obj the candidate; may be {@code null}
     * @return {@code true} when both ids match
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxUnifiedSessionShareRequest) obj;
        return Objects.equals(this.unifiedSessionId, that.unifiedSessionId);
    }

    /**
     * Returns a hash code derived from the session id.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(unifiedSessionId);
    }

    /**
     * Returns a debug-friendly textual representation of this request.
     *
     * @return the textual representation
     */
    @Override
    public String toString() {
        return "SmaxUnifiedSessionShareRequest[unifiedSessionId=" + unifiedSessionId + ']';
    }
}
