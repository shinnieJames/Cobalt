package com.github.auties00.cobalt.node.smax.abprops;

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
 * The outbound {@code <iq xmlns="abt" type="get" to="s.whatsapp.net">}
 * AB-props bundle fetch.
 *
 * @apiNote
 * Drives WA Web's
 * {@code WASmaxAbPropsGetExperimentConfigRPC.sendGetExperimentConfigRPC},
 * invoked by {@code WAWebAbPropsSyncJob} on session bootstrap and on
 * every server-pushed {@code <notification type="abprops">} bump; the
 * reply populates the local AB-prop store consumed by
 * {@code WAWebABPropsParseConfigValue} and the runtime feature gates.
 * Cobalt embedders dispatch one of these to mirror WA Web's
 * experiment-config sync.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutAbPropsGetExperimentConfigRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutAbPropsBaseIQGetRequestMixin")
public final class SmaxAbPropsGetExperimentConfigRequest implements SmaxOperation.Request {
    /**
     * The optional content hash echoed back to the relay.
     *
     * @apiNote
     * Mirrors {@code WAWebABPropsLocalStorage.getHash()} when WA Web
     * dispatches the request; the relay short-circuits the reply to a
     * delta when the supplied hash matches its current bundle.
     */
    private final String propsHash;

    /**
     * The optional refresh id echoed back to the relay.
     *
     * @apiNote
     * Carried when the {@code 3330} gate is on and the client wants
     * the relay to correlate this fetch with a prior server-pushed
     * {@code <notification type="abprops">} bump.
     */
    private final Integer propsRefreshId;

    /**
     * Constructs a conditional request.
     *
     * @apiNote
     * Use this overload when the client already has a cached bundle
     * and wants the relay to short-circuit on a hash or refresh-id
     * match.
     *
     * @param propsHash      the cached props hash; may be
     *                       {@code null}
     * @param propsRefreshId the cached refresh id; may be
     *                       {@code null}
     */
    public SmaxAbPropsGetExperimentConfigRequest(String propsHash, Integer propsRefreshId) {
        this.propsHash = propsHash;
        this.propsRefreshId = propsRefreshId;
    }

    /**
     * Constructs an unconditional request.
     *
     * @apiNote
     * Use this overload on the first fetch of a session, when no
     * cached bundle exists and the relay should always return the
     * full props bundle.
     */
    public SmaxAbPropsGetExperimentConfigRequest() {
        this(null, null);
    }

    /**
     * Returns the cached props hash, when set.
     *
     * @apiNote
     * Empty on the first fetch of a session.
     *
     * @return an {@link Optional} carrying the hash
     */
    public Optional<String> propsHash() {
        return Optional.ofNullable(propsHash);
    }

    /**
     * Returns the cached refresh id, when set.
     *
     * @apiNote
     * Empty when the client did not receive a prior refresh-id bump.
     *
     * @return an {@link Optional} carrying the refresh id
     */
    public Optional<Integer> propsRefreshId() {
        return Optional.ofNullable(propsRefreshId);
    }

    /**
     * Builds the outbound IQ stanza ready for dispatch.
     *
     * @apiNote
     * Returned unbuilt so the dispatch path can stamp a fresh IQ id
     * before flushing; mirrors
     * {@code WASmaxOutAbPropsGetExperimentConfigRequest.makeGetExperimentConfigRequest}
     * composed with the IQ-get merge mixin.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and the
     *         {@code <props/>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutAbPropsGetExperimentConfigRequest",
            exports = "makeGetExperimentConfigRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var propsBuilder = new NodeBuilder()
                .description("props")
                .attribute("protocol", "1");
        if (propsHash != null) {
            propsBuilder.attribute("hash", propsHash);
        }
        if (propsRefreshId != null) {
            propsBuilder.attribute("refresh_id", propsRefreshId);
        }
        var propsNode = propsBuilder.build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "abt")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(propsNode);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxAbPropsGetExperimentConfigRequest) obj;
        return Objects.equals(this.propsHash, that.propsHash)
                && Objects.equals(this.propsRefreshId, that.propsRefreshId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(propsHash, propsRefreshId);
    }

    @Override
    public String toString() {
        return "SmaxAbPropsGetExperimentConfigRequest[propsHash=" + propsHash
                + ", propsRefreshId=" + propsRefreshId + ']';
    }
}
