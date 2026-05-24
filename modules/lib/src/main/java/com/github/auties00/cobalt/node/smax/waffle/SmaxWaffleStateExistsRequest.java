package com.github.auties00.cobalt.node.smax.waffle;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <iq xmlns="waffle" smax_id="142" type="get"/>}
 * Waffle state-existence probe.
 *
 * @apiNote
 * Powers the boot-time check in
 * {@code WAWebAccountLinkingAPI.stateExists}, which asks the relay
 * whether this device is currently linked to a Facebook account and
 * caches the answer as one of {@code UNLINKED}, {@code ACTIVE}, or
 * {@code PAUSED} (the {@code AccountLinkingStateExists} enum in
 * {@code WAWebAccountLinkingConstants}). The reply is paired with
 * {@link SmaxWaffleStateExistsResponse}. The request body carries only
 * a {@code <timestamp/>} child; the relay identifies the caller from
 * the authenticated session.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutWaffleStateExistsRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutWaffleBaseIQGetRequestMixin")
public final class SmaxWaffleStateExistsRequest implements SmaxOperation.Request {
    /**
     * The client wall-clock at request time, in seconds since the Unix
     * epoch.
     */
    private final long timestamp;

    /**
     * Constructs a state-existence probe stamped at the given timestamp.
     *
     * @apiNote
     * WA Web stamps the request with {@code Date.now()} (milliseconds
     * since the Unix epoch) for this RPC; the value is opaque to the
     * relay and is echoed back inside the reply for clock-skew
     * diagnostics.
     *
     * @param timestamp the request timestamp
     */
    public SmaxWaffleStateExistsRequest(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Returns the request timestamp.
     *
     * @return the timestamp as supplied at construction time
     */
    public long timestamp() {
        return timestamp;
    }

    /**
     * Builds the outbound IQ stanza ready for dispatch.
     *
     * @apiNote
     * Produces
     * {@code <iq xmlns="waffle" smax_id="142" type="get" to="s.whatsapp.net">
     * <timestamp>...</timestamp></iq>}; the dispatch path stamps a fresh
     * {@code id} attribute on every outbound stanza so the reply parser
     * can match it back to this request.
     *
     * @return a {@link NodeBuilder} carrying the {@code <iq>} envelope
     *         and the {@code <timestamp/>} payload; never {@code null}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutWaffleStateExistsRequest",
            exports = "makeStateExistsRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var timestampNode = new NodeBuilder()
                .description("timestamp")
                .content(timestamp)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "waffle")
                .attribute("smax_id", 142)
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(timestampNode);
    }

    /**
     * Returns whether the given object is a
     * {@link SmaxWaffleStateExistsRequest} with an equal timestamp.
     *
     * @param obj the candidate; may be {@code null}
     * @return {@code true} when both timestamps match
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxWaffleStateExistsRequest) obj;
        return this.timestamp == that.timestamp;
    }

    /**
     * Returns a hash code derived from the timestamp.
     *
     * @return the {@link Long#hashCode(long)} of the timestamp
     */
    @Override
    public int hashCode() {
        return Long.hashCode(timestamp);
    }

    /**
     * Returns a debug rendering of this request.
     *
     * @return a human-readable summary; never {@code null}
     */
    @Override
    public String toString() {
        return "SmaxWaffleStateExistsRequest[timestamp=" + timestamp + ']';
    }
}
