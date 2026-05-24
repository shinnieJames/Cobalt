package com.github.auties00.cobalt.node.smax.psa;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.Objects;
import java.util.Optional;

/**
 * The two documented values of the {@code <blocking status="...">}
 * attribute on a PSA chat-block IQ result.
 *
 * @apiNote
 * Surfaces the boolean
 * {@code WAWebQueryBlockListJob.getBlockingStatusForPSAUser} return value:
 * {@code true} when {@link #BLOCKED} is returned, {@code false} when
 * {@link #UNBLOCKED} is returned. The WA Web validator
 * {@code WASmaxInPsaEnums.ENUM_BLOCKED_UNBLOCKED} accepts only these two
 * literals; anything else fails the parse.
 */
@WhatsAppWebModule(moduleName = "WASmaxInPsaEnums")
public enum SmaxPsaChatBlockGetBlockingStatus {
    /**
     * The PSA broadcast channel is currently muted server-side for this
     * user.
     */
    BLOCKED("blocked"),
    /**
     * The PSA broadcast channel is currently delivering to this user.
     */
    UNBLOCKED("unblocked");

    /**
     * The wire-level literal carried by the {@code status} attribute.
     */
    private final String wireValue;

    /**
     * Constructs an enum constant.
     *
     * @param wireValue the wire-level literal; never {@code null}
     */
    SmaxPsaChatBlockGetBlockingStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the wire-level literal.
     *
     * @return the wire-level literal; never {@code null}
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * Resolves a constant from its wire-level literal.
     *
     * @apiNote
     * Mirrors the {@code ENUM_BLOCKED_UNBLOCKED} admit set used by
     * {@code WASmaxInPsaChatBlockGetResponseSuccess.parseChatBlockGetResponseSuccess}
     * and the {@code Set} sibling. An unknown literal returns
     * {@link Optional#empty()}, which the caller maps to a parse miss.
     *
     * @param wireValue the wire-level literal; may be {@code null}
     * @return an {@link Optional} carrying the resolved enum constant, or
     *         empty when the literal is not one of the documented values
     */
    public static Optional<SmaxPsaChatBlockGetBlockingStatus> ofWire(String wireValue) {
        if (wireValue == null) {
            return Optional.empty();
        }
        for (var value : values()) {
            if (value.wireValue.equals(wireValue)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
