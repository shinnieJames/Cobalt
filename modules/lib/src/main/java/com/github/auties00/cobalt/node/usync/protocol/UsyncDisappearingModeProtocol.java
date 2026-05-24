package com.github.auties00.cobalt.node.usync.protocol;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.usync.UsyncProtocol;
import com.github.auties00.cobalt.node.usync.UsyncProtocolResult;
import com.github.auties00.cobalt.node.usync.UsyncUser;
import com.github.auties00.cobalt.node.usync.result.DisappearingModeResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * USync {@code disappearing_mode} protocol descriptor.
 *
 * @apiNote
 * Asks the relay for each peer's current disappearing-message timer; used
 * by {@code WAWebGetDisappearingModeJob.getDisappearingMode} and bundled
 * into the larger {@code WAWebContactSyncApi} contact sync.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncDisappearingMode")
public final class UsyncDisappearingModeProtocol implements UsyncProtocol {
    /**
     * Wire literal for the protocol tag name.
     */
    public static final String NAME = "disappearing_mode";

    /**
     * Builds a default disappearing-mode-protocol descriptor.
     *
     * @apiNote
     * The descriptor is stateless; pair it with any {@link UsyncUser} that
     * carries an addressing slot.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncDisappearingMode",
            exports = "USyncDisappearingModeProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncDisappearingModeProtocol() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncDisappearingMode",
            exports = "USyncDisappearingModeProtocol.getName", adaptation = WhatsAppAdaptation.DIRECT)
    public String name() {
        return NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation emits an empty {@code <disappearing_mode/>}
     * element, matching the JS {@code wap("disappearing_mode", null)} shape.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncDisappearingMode",
            exports = "USyncDisappearingModeProtocol.getQueryElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Node buildQueryElement() {
        return new NodeBuilder().description(NAME).build();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always returns {@link Optional#empty()} because
     * the disappearing-mode protocol has no per-user payload on the request
     * side, matching the JS {@code null} return in
     * {@code USyncDisappearingModeProtocol.getUserElement}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncDisappearingMode",
            exports = "USyncDisappearingModeProtocol.getUserElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<Node> buildUserElement(UsyncUser user) {
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation parses the {@code duration} attribute (seconds,
     * defaulting to zero), the {@code t} timestamp, and the
     * {@code ephemerality_disabled} flag unconditionally; the JS parser
     * only sets the flag when
     * {@code WAWebPrivacyGatingUtils.isPAASupportForDisabledEphemeralityEnabled}
     * returns true, but exposing the raw wire value here keeps the parser
     * independent of client-side gating.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncDisappearingMode",
            exports = "disappearingModeParser", adaptation = WhatsAppAdaptation.ADAPTED)
    public UsyncProtocolResult parseUserResult(Node child) {
        if (!child.hasDescription(NAME)) {
            throw new IllegalStateException("expected <" + NAME + ">, got <" + child.description() + ">");
        }
        var error = UsyncContactProtocol.parseError(child);
        if (error.isPresent()) {
            return error.get();
        }
        var duration = Duration.ofSeconds(child.getAttributeAsLong("duration", 0L));
        var timestamp = Instant.ofEpochSecond(child.getRequiredAttributeAsLong("t"));
        var ephemeralityDisabled = "true".equals(child.getAttributeAsString("ephemerality_disabled", ""));
        return new DisappearingModeResult(duration, timestamp, ephemeralityDisabled);
    }
}
