package com.github.auties00.cobalt.node.usync.protocol;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.usync.UsyncProtocol;
import com.github.auties00.cobalt.node.usync.UsyncProtocolResult;
import com.github.auties00.cobalt.node.usync.UsyncUser;
import com.github.auties00.cobalt.node.usync.result.UsernameResult;

import java.util.Optional;

/**
 * USync {@code username} protocol descriptor.
 *
 * @apiNote
 * Asks the relay for each peer's claimed username; added by every
 * contact-sync flow that supports username addressing (see
 * {@code WAWebContactSyncApi}, {@code WAWebContactSyncUtils}, and
 * {@code WAWebQueryExistsJob}).
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncUsername")
public final class UsyncUsernameProtocol implements UsyncProtocol {
    /**
     * Wire literal for the protocol tag name.
     */
    public static final String NAME = "username";

    /**
     * Builds a default username-protocol descriptor.
     *
     * @apiNote
     * The descriptor is stateless; pair it with any {@link UsyncUser} that
     * carries an addressing slot.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUsername",
            exports = "USyncUsernameProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncUsernameProtocol() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncUsername",
            exports = "USyncUsernameProtocol.getName", adaptation = WhatsAppAdaptation.DIRECT)
    public String name() {
        return NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation emits an empty {@code <username/>} element,
     * matching the JS {@code wap("username", null)} shape.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncUsername",
            exports = "USyncUsernameProtocol.getQueryElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Node buildQueryElement() {
        return new NodeBuilder().description(NAME).build();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always returns {@link Optional#empty()} because
     * the username protocol has no per-user payload on the request side,
     * matching the JS {@code null} return in
     * {@code USyncUsernameProtocol.getUserElement}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncUsername",
            exports = "USyncUsernameProtocol.getUserElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<Node> buildUserElement(UsyncUser user) {
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation reads the username from the node's inline text
     * content, matching the JS {@code hasContent() ? contentString() : null}
     * branch.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncUsername",
            exports = "usernameParser", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncProtocolResult parseUserResult(Node child) {
        if (!child.hasDescription(NAME)) {
            throw new IllegalStateException("expected <" + NAME + ">, got <" + child.description() + ">");
        }
        var error = UsyncContactProtocol.parseError(child);
        if (error.isPresent()) {
            return error.get();
        }
        return new UsernameResult(child.toContentString().orElse(null));
    }
}
