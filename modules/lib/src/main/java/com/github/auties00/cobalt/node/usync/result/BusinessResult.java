package com.github.auties00.cobalt.node.usync.result;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.Node;

import java.util.Optional;

/**
 * Holds the success result of the business USync parser.
 *
 * Surfaced by USync queries that request the business protocol, such as the
 * contact-import verifier, the background contact sync, the per-contact
 * verified-name fetch, and the username probe. The raw {@code <verified_name>}
 * {@link Node} is exposed so callers can pipe it through their own
 * verified-name protobuf decoder rather than decoding it here.
 *
 * @implNote
 * This implementation keeps the verified-name decoding out of the result type
 * because the verified-name decoder lives in a sibling module shared with
 * notification handlers.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncBusiness")
public final class BusinessResult implements UsyncProtocolResponse {
    /**
     * Holds the raw {@code <verified_name>} child {@link Node}.
     *
     * Is {@code null} when the relay did not return one for this peer.
     */
    private final Node verifiedName;

    /**
     * Creates a new business result.
     *
     * @param verifiedName the raw {@code <verified_name>} {@link Node}, or
     *                     {@code null}
     */
    public BusinessResult(Node verifiedName) {
        this.verifiedName = verifiedName;
    }

    /**
     * Returns the raw {@code <verified_name>} {@link Node}, when present.
     *
     * Decode through the verified-name protobuf reader to extract the business
     * name, issuer, and serial. Absent when the peer is not a verified
     * business.
     *
     * @return the verified-name {@link Node}, or empty when absent
     */
    public Optional<Node> verifiedName() {
        return Optional.ofNullable(verifiedName);
    }
}
