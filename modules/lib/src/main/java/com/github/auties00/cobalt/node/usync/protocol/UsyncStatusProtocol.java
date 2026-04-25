package com.github.auties00.cobalt.node.usync.protocol;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.usync.UsyncProtocol;
import com.github.auties00.cobalt.node.usync.UsyncProtocolResult;
import com.github.auties00.cobalt.node.usync.UsyncUser;
import com.github.auties00.cobalt.node.usync.result.StatusResult;

import java.util.Optional;

/**
 * USync {@code status} protocol.
 *
 * @implNote WAWebUsyncStatus.USyncStatusProtocol.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncStatus")
public final class UsyncStatusProtocol implements UsyncProtocol {
    /** Wire literal for the protocol tag name. */
    public static final String NAME = "status";

    /**
     * Constructs a default status-protocol descriptor.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncStatus",
            exports = "USyncStatusProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncStatusProtocol() {
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncStatus",
            exports = "USyncStatusProtocol.getName", adaptation = WhatsAppAdaptation.DIRECT)
    public String name() {
        return NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncStatus",
            exports = "USyncStatusProtocol.getQueryElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Node buildQueryElement() {
        return new NodeBuilder().description(NAME).build();
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncStatus",
            exports = "USyncStatusProtocol.getUserElement", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<Node> buildUserElement(UsyncUser user) {
        return user.trustedContactToken().map(token -> new NodeBuilder()
                .description("tctoken")
                .content(token)
                .build());
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncStatus",
            exports = "statusParser", adaptation = WhatsAppAdaptation.ADAPTED)
    public UsyncProtocolResult parseUserResult(Node child) {
        if (!child.hasDescription(NAME)) {
            throw new IllegalStateException("expected <" + NAME + ">, got <" + child.description() + ">");
        }
        var error = UsyncContactProtocol.parseError(child);
        if (error.isPresent()) {
            return error.get();
        }
        if (child.hasContent()) {
            var text = child.toContentString().orElse("");
            return new StatusResult(text.isEmpty() ? null : text);
        }
        if (child.getAttributeAsInt("code", -1) == 401) {
            return new StatusResult("");
        }
        return new StatusResult(null);
    }
}
