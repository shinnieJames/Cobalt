package com.github.auties00.cobalt.node.usync.protocol;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.usync.UsyncProtocol;
import com.github.auties00.cobalt.node.usync.UsyncProtocolResult;
import com.github.auties00.cobalt.node.usync.UsyncUser;
import com.github.auties00.cobalt.node.usync.result.LidResult;

import java.util.Optional;

/**
 * USync {@code lid} protocol descriptor.
 *
 * @apiNote
 * Asks the relay to resolve each peer's LID identifier or to confirm a hint
 * the client already holds. Used in the LID migration pipeline (see
 * {@code WAWebContactSyncUtils.constructUsyncDeltaQuery}) and added
 * idempotently to many other USync queries via
 * {@link com.github.auties00.cobalt.node.usync.UsyncQuery#withLidProtocol()}.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncLid")
public final class UsyncLidProtocol implements UsyncProtocol {
    /**
     * Wire literal for the protocol tag name.
     */
    public static final String NAME = "lid";

    /**
     * Builds a default LID-protocol descriptor.
     *
     * @apiNote
     * The descriptor is stateless; per-user state lives on each
     * {@link UsyncUser} (specifically the optional LID hint set through
     * {@link UsyncUser#withLid(com.github.auties00.cobalt.model.jid.Jid)}).
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncLid",
            exports = "USyncLidProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncLidProtocol() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncLid",
            exports = "USyncLidProtocol.getName", adaptation = WhatsAppAdaptation.DIRECT)
    public String name() {
        return NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation emits an empty {@code <lid/>} element, matching
     * the JS {@code wap("lid", null)} shape.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncLid",
            exports = "USyncLidProtocol.getQueryElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Node buildQueryElement() {
        return new NodeBuilder().description(NAME).build();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation only emits the per-user element when the user
     * carries a LID hint, matching the JS {@code null} return when
     * {@code getLid()} is empty. The hint is shipped on the {@code jid}
     * attribute of the per-user {@code <lid>} element.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncLid",
            exports = "USyncLidProtocol.getUserElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<Node> buildUserElement(UsyncUser user) {
        return user.lid().map(lid -> new NodeBuilder()
                .description(NAME)
                .attribute("jid", lid.toString())
                .build());
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation reads the resolved LID from the {@code val}
     * attribute, matching the JS {@code maybeAttrLidUserJid("val")} call.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncLid",
            exports = "lidParser", adaptation = WhatsAppAdaptation.ADAPTED)
    public UsyncProtocolResult parseUserResult(Node child) {
        if (!child.hasDescription(NAME)) {
            throw new IllegalStateException("expected <" + NAME + ">, got <" + child.description() + ">");
        }
        var error = UsyncContactProtocol.parseError(child);
        if (error.isPresent()) {
            return error.get();
        }
        return new LidResult(child.getAttributeAsJid("val").orElse(null));
    }
}
