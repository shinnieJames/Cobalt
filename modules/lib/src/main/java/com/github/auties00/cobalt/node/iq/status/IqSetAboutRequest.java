package com.github.auties00.cobalt.node.iq.status;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;
import java.util.Objects;

/**
 * Outbound {@code <iq xmlns="status" type="set">} stanza setting the calling user's "about"
 * text (the short bio shown under the contact name in the profile screen).
 *
 * @apiNote
 * Used by the Settings "edit about" surface via WA Web's
 * {@code WAWebContactStatusBridge}, which wraps the call in a persisted-job envelope so the
 * change is retried across reconnects; the persisted-job definition lives in
 * {@code WAWebPersistedJobDefinitions.setAbout} and the runtime resolution in
 * {@code WAWebPersistedJobInitializers.setAbout}.
 */
@WhatsAppWebModule(moduleName = "WAWebSetAboutJob")
public final class IqSetAboutRequest implements IqOperation.Request {
    /**
     * New about-text value (UTF-8).
     *
     * @apiNote
     * Routed verbatim into the {@code <status>} child content; an empty string clears the
     * about field, and the relay enforces its own length cap (typically 139 codepoints,
     * surfacing as a {@code 406} error when exceeded).
     */
    private final String about;

    /**
     * Constructs a new set-about request.
     *
     * @param about the new about text
     * @throws NullPointerException if {@code about} is {@code null}
     */
    public IqSetAboutRequest(String about) {
        this.about = Objects.requireNonNull(about, "about cannot be null");
    }

    /**
     * Returns the new about text.
     *
     * @return the about-text string, never {@code null}
     */
    public String about() {
        return about;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Produces a {@code <iq xmlns="status" type="set">} envelope addressed to
     * {@link JidServer#user()} and wrapping a single {@code <status>} child whose content is
     * the new about text.
     *
     * @return a {@link NodeBuilder} carrying the {@code <iq>} envelope and the
     *         {@code <status>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSetAboutJob",
            exports = "setAbout", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var statusNode = new NodeBuilder()
                .description("status")
                .content(about)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "status")
                .attribute("to", JidServer.user())
                .attribute("type", "set")
                .content(statusNode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqSetAboutRequest) obj;
        return Objects.equals(this.about, that.about);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(about);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "IqSetAboutRequest[about=" + about + ']';
    }
}
