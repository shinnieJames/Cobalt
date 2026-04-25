package com.github.auties00.cobalt.node.usync.protocol;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.usync.UsyncProtocol;
import com.github.auties00.cobalt.node.usync.UsyncProtocolResult;
import com.github.auties00.cobalt.node.usync.UsyncUser;
import com.github.auties00.cobalt.node.usync.result.PictureResult;

import java.util.Optional;

/**
 * USync {@code picture} protocol.
 *
 * @implNote WAWebUsyncPicture.USyncPictureProtocol.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncPicture")
public final class UsyncPictureProtocol implements UsyncProtocol {
    /** Wire literal for the protocol tag name. */
    public static final String NAME = "picture";

    /**
     * Constructs a default picture-protocol descriptor.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncPicture",
            exports = "USyncPictureProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncPictureProtocol() {
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncPicture",
            exports = "USyncPictureProtocol.getName", adaptation = WhatsAppAdaptation.DIRECT)
    public String name() {
        return NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncPicture",
            exports = "USyncPictureProtocol.getQueryElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Node buildQueryElement() {
        return new NodeBuilder().description(NAME).build();
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncPicture",
            exports = "USyncPictureProtocol.getUserElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<Node> buildUserElement(UsyncUser user) {
        return Optional.empty();
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncPicture",
            exports = "pictureParser", adaptation = WhatsAppAdaptation.ADAPTED)
    public UsyncProtocolResult parseUserResult(Node child) {
        if (!child.hasDescription(NAME)) {
            throw new IllegalStateException("expected <" + NAME + ">, got <" + child.description() + ">");
        }
        var error = UsyncContactProtocol.parseError(child);
        if (error.isPresent()) {
            return error.get();
        }
        return new PictureResult(child.getRequiredAttributeAsInt("id"));
    }
}
