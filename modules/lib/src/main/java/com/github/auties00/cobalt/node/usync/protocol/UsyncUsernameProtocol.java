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
 * USync {@code username} protocol.
 *
 * @implNote WAWebUsyncUsername.USyncUsernameProtocol.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncUsername")
public final class UsyncUsernameProtocol implements UsyncProtocol {
    /** Wire literal for the protocol tag name. */
    public static final String NAME = "username";

    /**
     * Constructs a default username-protocol descriptor.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUsername",
            exports = "USyncUsernameProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncUsernameProtocol() {
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncUsername",
            exports = "USyncUsernameProtocol.getName", adaptation = WhatsAppAdaptation.DIRECT)
    public String name() {
        return NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncUsername",
            exports = "USyncUsernameProtocol.getQueryElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Node buildQueryElement() {
        return new NodeBuilder().description(NAME).build();
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncUsername",
            exports = "USyncUsernameProtocol.getUserElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<Node> buildUserElement(UsyncUser user) {
        return Optional.empty();
    }

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
