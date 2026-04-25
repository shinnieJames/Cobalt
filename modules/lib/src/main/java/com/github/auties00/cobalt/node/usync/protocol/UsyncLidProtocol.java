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
 * USync {@code lid} protocol.
 *
 * @implNote WAWebUsyncLid.USyncLidProtocol.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncLid")
public final class UsyncLidProtocol implements UsyncProtocol {
    /** Wire literal for the protocol tag name. */
    public static final String NAME = "lid";

    /**
     * Constructs a default LID-protocol descriptor.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncLid",
            exports = "USyncLidProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncLidProtocol() {
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncLid",
            exports = "USyncLidProtocol.getName", adaptation = WhatsAppAdaptation.DIRECT)
    public String name() {
        return NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncLid",
            exports = "USyncLidProtocol.getQueryElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Node buildQueryElement() {
        return new NodeBuilder().description(NAME).build();
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncLid",
            exports = "USyncLidProtocol.getUserElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<Node> buildUserElement(UsyncUser user) {
        return user.lid().map(lid -> new NodeBuilder()
                .description(NAME)
                .attribute("jid", lid.toString())
                .build());
    }

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
