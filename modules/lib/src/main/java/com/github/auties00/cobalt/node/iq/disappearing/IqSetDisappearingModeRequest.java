package com.github.auties00.cobalt.node.iq.disappearing;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;
import java.time.Duration;
import java.util.Objects;

/**
 * Outbound {@code <iq xmlns="disappearing_mode" type="set">} stanza that updates the user's
 * default disappearing-mode duration.
 *
 * @apiNote
 * Use this to back the "default message timer" setting in WA Web's privacy drawer; the
 * relay applies the duration as the per-chat default for newly-created chats while
 * leaving per-chat overrides untouched. Pass {@link Duration#ZERO} to disable the
 * feature. The reply is parsed by {@link IqSetDisappearingModeResponse}.
 *
 * @implNote
 * This implementation mirrors WA Web's {@code WAWebSetDisappearingModeJob.setDisappearingMode}
 * verbatim, emitting the {@code duration} attribute as the seconds-as-string.
 */
@WhatsAppWebModule(moduleName = "WAWebSetDisappearingModeJob")
public final class IqSetDisappearingModeRequest implements IqOperation.Request {
    /**
     * Holds the new default disappearing-mode duration to install; {@link Duration#ZERO}
     * disables the feature.
     */
    private final Duration duration;

    /**
     * Constructs a new set-disappearing-mode request bound to the given duration.
     *
     * @apiNote
     * The duration is rounded down to seconds before being emitted; sub-second
     * precision is lost. Pass {@link Duration#ZERO} to disable the feature.
     *
     * @param duration the new default duration; never {@code null}
     * @throws NullPointerException     if {@code duration} is {@code null}
     * @throws IllegalArgumentException if {@code duration} is negative
     */
    public IqSetDisappearingModeRequest(Duration duration) {
        Objects.requireNonNull(duration, "duration cannot be null");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration cannot be negative");
        }
        this.duration = duration;
    }

    /**
     * Returns the bound default disappearing-mode duration.
     *
     * @return the duration; never {@code null}
     */
    public Duration duration() {
        return duration;
    }

    /**
     * Builds the outbound {@code <iq>} stanza wrapping the
     * {@code <disappearing_mode duration=SECONDS/>} payload.
     *
     * @apiNote
     * The resulting {@link NodeBuilder} is wire-ready except for the IQ {@code id}
     * attribute, which the dispatch layer assigns.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and the
     *         {@code <disappearing_mode>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSetDisappearingModeJob",
            exports = "setDisappearingMode",
            adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var dmNode = new NodeBuilder()
                .description("disappearing_mode")
                .attribute("duration", String.valueOf(duration.toSeconds()))
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "disappearing_mode")
                .attribute("to", JidServer.user())
                .attribute("type", "set")
                .content(dmNode);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqSetDisappearingModeRequest) obj;
        return Objects.equals(this.duration, that.duration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(duration);
    }

    @Override
    public String toString() {
        return "IqSetDisappearingModeRequest[duration=" + duration + ']';
    }
}
