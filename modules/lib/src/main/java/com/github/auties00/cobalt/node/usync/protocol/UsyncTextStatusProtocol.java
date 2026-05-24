package com.github.auties00.cobalt.node.usync.protocol;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.usync.UsyncProtocol;
import com.github.auties00.cobalt.node.usync.UsyncProtocolResult;
import com.github.auties00.cobalt.node.usync.UsyncUser;
import com.github.auties00.cobalt.node.usync.result.TextStatusResult;

import java.time.Duration;
import java.util.Optional;

/**
 * USync {@code text_status} protocol descriptor.
 *
 * @apiNote
 * Asks the relay for each peer's modern text-status payload (text, emoji,
 * ephemeral lifetime, last-update timestamp); used by the contact-sync
 * pipeline when {@code WAWebTextStatusGatingUtils.receiveTextStatusEnabled()}
 * is on.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncTextStatus")
public final class UsyncTextStatusProtocol implements UsyncProtocol {
    /**
     * Wire literal for the protocol tag name.
     */
    public static final String NAME = "text_status";

    /**
     * Builds a default text-status-protocol descriptor.
     *
     * @apiNote
     * The descriptor is stateless; pair it with any {@link UsyncUser} that
     * carries an addressing slot.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncTextStatus",
            exports = "USyncTextStatusProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncTextStatusProtocol() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncTextStatus",
            exports = "USyncTextStatusProtocol.getName", adaptation = WhatsAppAdaptation.DIRECT)
    public String name() {
        return NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation emits an empty {@code <text_status/>} element,
     * matching the JS {@code wap("text_status", null)} shape.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncTextStatus",
            exports = "USyncTextStatusProtocol.getQueryElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Node buildQueryElement() {
        return new NodeBuilder().description(NAME).build();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always returns {@link Optional#empty()} because
     * the text-status protocol has no per-user payload on the request side,
     * matching the JS {@code null} return in
     * {@code USyncTextStatusProtocol.getUserElement}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncTextStatus",
            exports = "USyncTextStatusProtocol.getUserElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<Node> buildUserElement(UsyncUser user) {
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation parses the {@code text} attribute, the
     * {@code content} attribute of the optional {@code <emoji>} child, the
     * {@code ephemeral_duration_sec} attribute (boxed into a
     * {@link Duration}), and the {@code last_update_time} attribute as a
     * raw string, matching the JS {@code textStatusParser} field set.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncTextStatus",
            exports = "textStatusParser", adaptation = WhatsAppAdaptation.ADAPTED)
    public UsyncProtocolResult parseUserResult(Node child) {
        if (!child.hasDescription(NAME)) {
            throw new IllegalStateException("expected <" + NAME + ">, got <" + child.description() + ">");
        }
        var error = UsyncContactProtocol.parseError(child);
        if (error.isPresent()) {
            return error.get();
        }
        var text = child.getAttributeAsString("text").orElse(null);
        var emoji = child.getChild("emoji")
                .flatMap(e -> e.getAttributeAsString("content")).orElse(null);
        var ephemeralDuration = child.getAttributeAsLong("ephemeral_duration_sec").stream().boxed()
                .map(Duration::ofSeconds).findFirst().orElse(null);
        var lastUpdateTime = child.getAttributeAsString("last_update_time").orElse(null);
        return new TextStatusResult(text, emoji, ephemeralDuration, lastUpdateTime);
    }
}
