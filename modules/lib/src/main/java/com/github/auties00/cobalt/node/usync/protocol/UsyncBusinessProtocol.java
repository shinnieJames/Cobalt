package com.github.auties00.cobalt.node.usync.protocol;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.usync.UsyncProtocol;
import com.github.auties00.cobalt.node.usync.UsyncProtocolResult;
import com.github.auties00.cobalt.node.usync.UsyncUser;
import com.github.auties00.cobalt.node.usync.result.BusinessResult;

import java.util.List;
import java.util.Optional;

/**
 * USync {@code business} protocol descriptor.
 *
 * @apiNote
 * Asks the relay for each peer's signed verified-name certificate; used by
 * verified-business fetches such as
 * {@code WAWebGetOrQueryUsyncInfoContactAction.queryUsyncBusiness} and by
 * the contact-verifier flow that batches business and picture lookups
 * together (see {@code WAWebContactImportContactVerifier}).
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncBusiness")
public final class UsyncBusinessProtocol implements UsyncProtocol {
    /**
     * Wire literal for the protocol tag name.
     */
    public static final String NAME = "business";

    /**
     * Builds a default business-protocol descriptor.
     *
     * @apiNote
     * The descriptor is stateless; pair it with the right
     * {@link UsyncUser} addressing to get back the peer's verified-name
     * certificate.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncBusiness",
            exports = "USyncBusinessProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncBusinessProtocol() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncBusiness",
            exports = "USyncBusinessProtocol.getName", adaptation = WhatsAppAdaptation.DIRECT)
    public String name() {
        return NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation wraps a single empty {@code <verified_name/>}
     * child inside the {@code <business>} element, matching the JS
     * {@code wap("business", null, wap("verified_name", null))} shape.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncBusiness",
            exports = "USyncBusinessProtocol.getQueryElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Node buildQueryElement() {
        return new NodeBuilder()
                .description(NAME)
                .content(List.of(new NodeBuilder().description("verified_name").build()))
                .build();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always returns {@link Optional#empty()} because
     * the business protocol has no per-user payload on the request side,
     * matching the JS {@code null} return in
     * {@code USyncBusinessProtocol.getUserElement}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncBusiness",
            exports = "USyncBusinessProtocol.getUserElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<Node> buildUserElement(UsyncUser user) {
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation returns the raw {@code <verified_name>} node
     * inside {@link BusinessResult}; the actual verified-name parsing
     * (signature check, certificate decode) is deferred to the caller.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncBusiness",
            exports = "businessParser", adaptation = WhatsAppAdaptation.ADAPTED)
    public UsyncProtocolResult parseUserResult(Node child) {
        if (!child.hasDescription(NAME)) {
            throw new IllegalStateException("expected <" + NAME + ">, got <" + child.description() + ">");
        }
        var error = UsyncContactProtocol.parseError(child);
        if (error.isPresent()) {
            return error.get();
        }
        return new BusinessResult(child.getChild("verified_name").orElse(null));
    }
}
