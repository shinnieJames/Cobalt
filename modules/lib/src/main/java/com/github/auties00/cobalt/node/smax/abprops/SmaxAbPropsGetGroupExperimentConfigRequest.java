package com.github.auties00.cobalt.node.smax.abprops;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
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
 * group-scoped AB-props bundle fetch.
 *
 * @apiNote
 * Drives WA Web's
 * {@code WASmaxAbPropsGetGroupExperimentConfigRPC.sendGetGroupExperimentConfigRPC},
 * invoked by {@code WAWebGroupAbPropsSyncJob} when a group becomes
 * active so the relay returns the per-group experiment overrides
 * layered on top of the user-scoped bundle from
 * {@link SmaxAbPropsGetExperimentConfigRequest}; Cobalt embedders
 * dispatch one of these per group they want to sync.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutAbPropsGetGroupExperimentConfigRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutAbPropsBaseIQGetRequestMixin")
public final class SmaxAbPropsGetGroupExperimentConfigRequest implements SmaxOperation.Request {
    /**
     * The group JID whose experiment configuration is requested.
     *
     * @apiNote
     * Routed verbatim into the {@code <props group/>} attribute via
     * the SMAX {@code GROUP_JID} wrapper; matches the
     * {@code propsGroup} field {@code WAWebGroupAbPropsSyncJob} keys
     * its sync state by.
     */
    private final Jid groupJid;

    /**
     * The optional content hash echoed back to the relay.
     *
     * @apiNote
     * Mirrors the per-group cached hash; the relay short-circuits the
     * reply to a delta when the supplied hash matches its current
     * bundle for that group.
     */
    private final String propsHash;

    /**
     * Constructs a conditional request for the given group.
     *
     * @apiNote
     * Use this overload when the client already has a cached bundle
     * for the group and wants the relay to short-circuit on a hash
     * match.
     *
     * @param groupJid  the target group JID; never {@code null}
     * @param propsHash the cached props hash; may be {@code null}
     * @throws NullPointerException if {@code groupJid} is {@code null}
     */
    public SmaxAbPropsGetGroupExperimentConfigRequest(Jid groupJid, String propsHash) {
        this.groupJid = Objects.requireNonNull(groupJid, "groupJid cannot be null");
        this.propsHash = propsHash;
    }

    /**
     * Constructs an unconditional request for the given group.
     *
     * @apiNote
     * Use this overload on the first fetch for a group, when no
     * cached bundle exists.
     *
     * @param groupJid the target group JID; never {@code null}
     * @throws NullPointerException if {@code groupJid} is {@code null}
     */
    public SmaxAbPropsGetGroupExperimentConfigRequest(Jid groupJid) {
        this(groupJid, null);
    }

    /**
     * Returns the target group JID.
     *
     * @return the group JID; never {@code null}
     */
    public Jid groupJid() {
        return groupJid;
    }

    /**
     * Returns the cached props hash, when set.
     *
     * @apiNote
     * Empty on the first fetch for the group.
     *
     * @return an {@link Optional} carrying the hash
     */
    public Optional<String> propsHash() {
        return Optional.ofNullable(propsHash);
    }

    /**
     * Builds the outbound IQ stanza ready for dispatch.
     *
     * @apiNote
     * Returned unbuilt so the dispatch path can stamp a fresh IQ id
     * before flushing; mirrors
     * {@code WASmaxOutAbPropsGetGroupExperimentConfigRequest.makeGetGroupExperimentConfigRequest}
     * composed with the IQ-get merge mixin.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and the
     *         {@code <props/>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutAbPropsGetGroupExperimentConfigRequest",
            exports = "makeGetGroupExperimentConfigRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var propsBuilder = new NodeBuilder()
                .description("props")
                .attribute("group", groupJid);
        if (propsHash != null) {
            propsBuilder.attribute("hash", propsHash);
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
        var that = (SmaxAbPropsGetGroupExperimentConfigRequest) obj;
        return Objects.equals(this.groupJid, that.groupJid)
                && Objects.equals(this.propsHash, that.propsHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupJid, propsHash);
    }

    @Override
    public String toString() {
        return "SmaxAbPropsGetGroupExperimentConfigRequest[groupJid=" + groupJid
                + ", propsHash=" + propsHash + ']';
    }
}
