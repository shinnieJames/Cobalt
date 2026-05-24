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
 * USync {@code status} protocol descriptor.
 *
 * @apiNote
 * Asks the relay for each peer's legacy "about" string; used by
 * {@code WAWebGetAboutQueryJob.getAbout} and bundled into the larger
 * {@code WAWebContactSyncApi} contact sync. The response distinguishes
 * "no status set" from "status hidden by privacy" via a {@code code="401"}
 * marker.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncStatus")
public final class UsyncStatusProtocol implements UsyncProtocol {
    /**
     * Wire literal for the protocol tag name.
     */
    public static final String NAME = "status";

    /**
     * Builds a default status-protocol descriptor.
     *
     * @apiNote
     * The descriptor is stateless; pair it with any {@link UsyncUser} that
     * carries an addressing slot and, when required by the relay, a
     * trusted-contact token through
     * {@link UsyncUser#withTrustedContactToken(byte[])}.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncStatus",
            exports = "USyncStatusProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncStatusProtocol() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncStatus",
            exports = "USyncStatusProtocol.getName", adaptation = WhatsAppAdaptation.DIRECT)
    public String name() {
        return NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation emits an empty {@code <status/>} element,
     * matching the JS {@code wap("status", null)} shape.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncStatus",
            exports = "USyncStatusProtocol.getQueryElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Node buildQueryElement() {
        return new NodeBuilder().description(NAME).build();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation emits a per-user {@code <tctoken>} carrying the
     * trusted-contact token whenever the user has one set, unconditionally
     * of WA Web's {@code WAWebPrivacyGatingUtils.isProfileScrappingProtectionInUsyncEnabled()}
     * gate; the JS path returns {@code null} for the per-user element when
     * the gate is off. Cobalt always ships the token when present and lets
     * the relay enforce the policy.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncStatus",
            exports = "USyncStatusProtocol.getUserElement", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<Node> buildUserElement(UsyncUser user) {
        return user.trustedContactToken().map(token -> new NodeBuilder()
                .description("tctoken")
                .content(token)
                .build());
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation distinguishes three response shapes, matching the
     * JS {@code statusParser} branches: inline content yields the live
     * status text (or {@code null} when the content is the empty string,
     * preserving the JS {@code length === 0 ? "" : content}); no content
     * with {@code code="401"} yields the empty string to mark "hidden by
     * peer privacy"; any other shape yields {@code null} to mark "no status
     * set".
     */
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
