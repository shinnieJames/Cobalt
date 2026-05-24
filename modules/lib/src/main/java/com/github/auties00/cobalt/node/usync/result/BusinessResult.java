package com.github.auties00.cobalt.node.usync.result;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.Node;

import java.util.Optional;

/**
 * Success result of the {@code WAWebUsyncBusiness.businessParser} parser.
 *
 * @apiNote
 * Surfaced by USync queries that include
 * {@code UsyncQuery.withBusinessProtocol()}; WA Web callers include the
 * contact-import verifier, the background contact sync, the per-contact
 * verified-name fetch in {@code WAWebGetOrQueryUsyncInfoContactAction}, and
 * the username probe in {@code WAWebQueryExistsJob}. The raw
 * {@code <verified_name>} {@link Node} is exposed so callers can pipe it
 * through their own verified-name protobuf decoder; WA Web hands the node off
 * to {@code WAWebCommonParsersVerifiedName} and Cobalt mirrors that
 * separation because the verified-name decoder lives in a sibling module that
 * is shared with notification handlers.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncBusiness")
public final class BusinessResult implements UsyncProtocolResponse {
    /**
     * The raw {@code <verified_name>} child {@link Node}, or {@code null}
     * when the relay did not return one for this peer.
     */
    private final Node verifiedName;

    /**
     * Creates a new business result.
     *
     * @apiNote
     * Instantiated by the business parser; embedders do not call this
     * directly.
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
     * @apiNote
     * Decode through the verified-name protobuf reader to extract the
     * business name, issuer, and serial. Absent when the peer is not a
     * verified business.
     *
     * @return the verified-name {@link Node}, or empty when absent
     */
    public Optional<Node> verifiedName() {
        return Optional.ofNullable(verifiedName);
    }
}
