package com.github.auties00.cobalt.node.usync;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.usync.protocol.UsyncBotProfileProtocol;
import com.github.auties00.cobalt.node.usync.protocol.UsyncBusinessProtocol;
import com.github.auties00.cobalt.node.usync.protocol.UsyncContactProtocol;
import com.github.auties00.cobalt.node.usync.protocol.UsyncDeviceProtocol;
import com.github.auties00.cobalt.node.usync.protocol.UsyncDisappearingModeProtocol;
import com.github.auties00.cobalt.node.usync.protocol.UsyncFeatureProtocol;
import com.github.auties00.cobalt.node.usync.protocol.UsyncLidProtocol;
import com.github.auties00.cobalt.node.usync.protocol.UsyncPictureProtocol;
import com.github.auties00.cobalt.node.usync.protocol.UsyncStatusProtocol;
import com.github.auties00.cobalt.node.usync.protocol.UsyncTextStatusProtocol;
import com.github.auties00.cobalt.node.usync.protocol.UsyncUsernameProtocol;

import java.util.Optional;

/**
 * Sealed interface implemented by every USync protocol descriptor.
 *
 * <p>A USync stanza is composed of one or more <em>protocol</em> elements
 * that describe what the client wants to learn about each user, plus one
 * {@code <user>} entry per peer with optional protocol-specific child
 * elements. Each protocol declares three things:
 *
 * <ol>
 *   <li>its wire {@link #name()} (the literal tag name on the
 *       {@code <query>} child),</li>
 *   <li>a {@link #buildQueryElement()} that emits the {@code <query>} child
 *       (often empty, sometimes carrying the protocol's request shape),</li>
 *   <li>a {@link #buildUserElement(UsyncUser)} that emits an optional child
 *       inside each {@code <user>} entry (returns {@link Optional#empty()}
 *       when the protocol has no per-user payload).</li>
 * </ol>
 *
 * <p>Protocols also expose a {@link #parseUserResult(Node)} that consumes a
 * {@code <user>}-child node and returns the protocol-specific result. The
 * shared result type is {@link UsyncProtocolResult}.
 *
 * <p>The 11 permitted implementations correspond one-to-one with the 11
 * {@code WAWebUsync*Protocol} JS classes.
 *
 * @implNote WAWebUsync: the JS module enumerates the protocols in the
 *     constants object {@code c}. Cobalt keeps the same closed enumeration
 *     by sealing this interface. The
 *     {@link com.github.auties00.cobalt.node.usync.protocol} sub-package
 *     holds the implementations and
 *     {@link com.github.auties00.cobalt.node.usync.result} the result
 *     types so this file stays a thin contract surface.
 */
@WhatsAppWebModule(moduleName = "WAWebUsync")
public sealed interface UsyncProtocol permits
        UsyncContactProtocol,
        UsyncDeviceProtocol,
        UsyncFeatureProtocol,
        UsyncBusinessProtocol,
        UsyncPictureProtocol,
        UsyncStatusProtocol,
        UsyncDisappearingModeProtocol,
        UsyncLidProtocol,
        UsyncBotProfileProtocol,
        UsyncUsernameProtocol,
        UsyncTextStatusProtocol {

    /**
     * Returns the literal protocol name as it appears on the wire.
     * Examples: {@code "contact"}, {@code "devices"}, {@code "feature"}.
     *
     * @return the protocol's tag name
     * @implNote each {@code WAWebUsync*Protocol.getName} in WhatsApp Web
     *     returns the same literal string.
     */
    String name();

    /**
     * Builds the protocol's child of the {@code <query>} element.
     *
     * <p>Most protocols emit an empty element such as {@code <picture/>};
     * the device protocol carries a {@code version="2"} attribute, the
     * contact protocol optionally carries
     * {@code addressing_mode="lid"}, and the feature protocol carries one
     * empty child per requested feature key.
     *
     * @return the query-element node
     * @implNote each {@code WAWebUsync*Protocol.getQueryElement} in
     *     WhatsApp Web returns a {@code WAWap.wap(...)} expression.
     */
    Node buildQueryElement();

    /**
     * Builds the optional per-user child inside a {@code <user>} entry.
     *
     * <p>Many protocols return {@link Optional#empty()} because they have
     * no per-user payload — the protocol's mere presence in the
     * {@code <query>} element is enough.
     *
     * @param user the user the {@code <user>} entry refers to
     * @return the protocol-specific child element, or empty
     * @implNote each {@code WAWebUsync*Protocol.getUserElement} in WhatsApp
     *     Web either returns a {@code WAWap.wap(...)} expression or
     *     {@code null}; {@link Optional#empty()} mirrors {@code null}.
     */
    Optional<Node> buildUserElement(UsyncUser user);

    /**
     * Parses the protocol's child of a {@code <user>} response into a
     * Java result.
     *
     * <p>If the relay returned a per-protocol error, the implementation
     * returns a {@link UsyncProtocolError}. Otherwise it returns the
     * protocol-specific success variant declared by the corresponding
     * permit of {@link UsyncProtocolResult}.
     *
     * @param userChild the child node tagged with this protocol's
     *                  {@link #name()}, located inside a {@code <user>}
     *                  result entry
     * @return the parsed result, never {@code null}
     * @implNote the corresponding {@code *Parser} export in each
     *     {@code WAWebUsync*Protocol} module.
     */
    UsyncProtocolResult parseUserResult(Node userChild);
}
