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
 * Builds the outbound {@code <iq xmlns="disappearing_mode" type="set">} stanza that updates the
 * account's default disappearing-mode duration.
 *
 * <p>The duration becomes the per-chat default applied to newly-created chats; per-chat overrides
 * are left untouched. {@link Duration#ZERO} disables the feature. The stanza is addressed to
 * {@link JidServer#user()} and the matching reply is parsed by
 * {@link IqSetDisappearingModeResponse}.
 */
@WhatsAppWebModule(moduleName = "WAWebSetDisappearingModeJob")
public final class IqSetDisappearingModeRequest implements IqOperation.Request {
    /**
     * Holds the new default disappearing-mode duration to install.
     *
     * <p>{@link Duration#ZERO} encodes the off state; the value is never {@code null} and never
     * negative because the constructor rejects both.
     */
    private final Duration duration;

    /**
     * Constructs a set-disappearing-mode request bound to the given duration.
     *
     * <p>The duration is rounded down to whole seconds when emitted, so sub-second precision is
     * lost. Pass {@link Duration#ZERO} to disable the feature.
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
     * <p>The returned {@link NodeBuilder} is wire-ready except for the IQ {@code id} attribute,
     * which the dispatch layer assigns. The {@code duration} attribute carries the configured
     * duration as a seconds-valued string via {@link Duration#toSeconds()}.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and the {@code <disappearing_mode>}
     *         payload
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

    /**
     * Compares this request to another object for equality.
     *
     * <p>Two set-disappearing-mode requests are equal when they share the same runtime class and
     * the same bound {@link #duration()}.
     *
     * @param obj the object to compare against
     * @return {@code true} when {@code obj} is an equal set-disappearing-mode request
     */
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

    /**
     * Returns a hash code for this request derived from the bound {@link #duration()}.
     *
     * @return the duration-derived hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(duration);
    }

    /**
     * Returns a debug string carrying the bound {@link #duration()}.
     *
     * @return a string representation
     */
    @Override
    public String toString() {
        return "IqSetDisappearingModeRequest[duration=" + duration + ']';
    }
}
