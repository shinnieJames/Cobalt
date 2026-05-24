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
 * USync {@code picture} protocol descriptor.
 *
 * @apiNote
 * Asks the relay for each peer's profile-picture id; the JPEG payload is
 * fetched separately through the media URL. Used by contact-import flows
 * (see {@code WAWebContactImportContactVerifier}, which combines picture
 * and business protocols in one IQ).
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncPicture")
public final class UsyncPictureProtocol implements UsyncProtocol {
    /**
     * Wire literal for the protocol tag name.
     */
    public static final String NAME = "picture";

    /**
     * Builds a default picture-protocol descriptor.
     *
     * @apiNote
     * The descriptor is stateless; pair it with any {@link UsyncUser} that
     * carries an addressing slot.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncPicture",
            exports = "USyncPictureProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncPictureProtocol() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncPicture",
            exports = "USyncPictureProtocol.getName", adaptation = WhatsAppAdaptation.DIRECT)
    public String name() {
        return NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation emits an empty {@code <picture/>} element,
     * matching the JS {@code wap("picture", null)} shape.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncPicture",
            exports = "USyncPictureProtocol.getQueryElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Node buildQueryElement() {
        return new NodeBuilder().description(NAME).build();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always returns {@link Optional#empty()} because
     * the picture protocol has no per-user payload on the request side,
     * matching the JS {@code null} return in
     * {@code USyncPictureProtocol.getUserElement}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncPicture",
            exports = "USyncPictureProtocol.getUserElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<Node> buildUserElement(UsyncUser user) {
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation reads the {@code id} attribute as a required int,
     * matching the JS {@code attrInt("id")} call.
     */
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
