package com.github.auties00.cobalt.node.usync.result;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.Node;

import java.util.Optional;

/**
 * Success result of {@code WAWebUsyncBusiness.businessParser}.
 *
 * <p>Carries the raw {@code <verified_name>} child node so callers can
 * decode it through their own verified-name protobuf decoder. Mirroring
 * the way {@code WAWebCommonParsersVerifiedName} runs in WhatsApp Web is
 * left to the caller because Cobalt's verified-name protobuf decoder
 * lives in a separate module and is shared with notification handlers.
 *
 * @implNote WAWebUsyncBusiness.businessParser: returns
 *     {@code {verifiedName: parser(node)}} where {@code parser} is
 *     {@code WAWebCommonParsersVerifiedName}. Cobalt defers the decode to
 *     the consumer so the protocol carrier stays cheap.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncBusiness")
public final class BusinessResult implements UsyncProtocolResponse {
    /**
     * The {@code <verified_name>} child node, or {@code null} when the
     * relay did not return one for this peer.
     */
    private final Node verifiedName;

    /**
     * Creates a new business result.
     *
     * @param verifiedName the {@code <verified_name>} node, or {@code null}
     */
    public BusinessResult(Node verifiedName) {
        this.verifiedName = verifiedName;
    }

    /**
     * Returns the {@code <verified_name>} child node, when present.
     *
     * @return the node
     */
    public Optional<Node> verifiedName() {
        return Optional.ofNullable(verifiedName);
    }
}
