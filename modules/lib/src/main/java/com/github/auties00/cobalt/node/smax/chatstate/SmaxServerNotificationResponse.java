package com.github.auties00.cobalt.node.smax.chatstate;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * The inbound projection of a {@code <chatstate>} stanza pushed by the
 * relay when a peer or group participant raises a typing or paused event.
 *
 * @apiNote
 * Consumed by the Cobalt analogue of
 * {@code WACreateHandleChatState.createHandleChatState}: the dispatcher
 * derives the {@code "composing"} or {@code "paused"} indicator from
 * {@link #stateType()} and routes it to the 1:1 handler when
 * {@link #stateSource()} resolves to {@link SmaxServerNotificationStateSource.FromUser}
 * or to the group handler when it resolves to
 * {@link SmaxServerNotificationStateSource.FromGroup}.
 */
@WhatsAppWebModule(moduleName = "WASmaxInChatstateServerNotificationRequest")
public final class SmaxServerNotificationResponse implements SmaxOperation.Response {
    /**
     * The state-source disjunction identifying who raised the event.
     */
    private final SmaxServerNotificationStateSource stateSource;

    /**
     * The state-type disjunction identifying which indicator was raised
     * (composing or paused).
     */
    private final SmaxServerNotificationStateType stateType;

    /**
     * The optional dev-only {@code <test config="..."/>} attribute carried
     * by the internal-test mixin.
     *
     * @apiNote
     * The mixin is only ever populated by internal WhatsApp builds for
     * regression testing; production stanzas omit the {@code <test/>} child
     * and this field stays empty.
     */
    private final String testConfig;

    /**
     * Constructs an inbound projection.
     *
     * @param stateSource the source disjunction; never {@code null}
     * @param stateType   the state-type disjunction; never {@code null}
     * @param testConfig  the optional dev-only test config; may be {@code null}
     * @throws NullPointerException if {@code stateSource} or {@code stateType} is {@code null}
     */
    public SmaxServerNotificationResponse(SmaxServerNotificationStateSource stateSource, SmaxServerNotificationStateType stateType, String testConfig) {
        this.stateSource = Objects.requireNonNull(stateSource, "stateSource cannot be null");
        this.stateType = Objects.requireNonNull(stateType, "stateType cannot be null");
        this.testConfig = testConfig;
    }

    /**
     * Returns the state-source disjunction.
     *
     * @return the source; never {@code null}
     */
    public SmaxServerNotificationStateSource stateSource() {
        return stateSource;
    }

    /**
     * Returns the state-type disjunction.
     *
     * @return the type; never {@code null}
     */
    public SmaxServerNotificationStateType stateType() {
        return stateType;
    }

    /**
     * Returns the optional dev-only test config.
     *
     * @return an {@link Optional} carrying the value, or empty when the
     *         {@code <test/>} child was absent
     */
    public Optional<String> testConfig() {
        return Optional.ofNullable(testConfig);
    }

    /**
     * Parses a {@code <chatstate>} stanza into an inbound projection.
     *
     * @apiNote
     * Mirrors the entry point at
     * {@code WASmaxChatstateServerNotificationRPC.receiveServerNotificationRPC};
     * Cobalt returns {@link Optional#empty()} on schema mismatch instead of
     * throwing the JS {@code SmaxParsingFailure} so callers can route the
     * stanza through the configured error policy.
     *
     * @implNote
     * This implementation requires the {@code <chatstate>} tag, a parseable
     * {@link SmaxServerNotificationStateSource} (either {@code FromUser} or
     * {@code FromGroup}), and a parseable {@link SmaxServerNotificationStateType}
     * (either {@code Composing} or {@code Paused}); the dev-only
     * {@code <test config="..."/>} child is best-effort and absence does
     * not fail the parse.
     *
     * @param node the inbound {@code <chatstate/>} stanza; never {@code null}
     * @return an {@link Optional} carrying the projection, or empty on schema mismatch
     * @throws NullPointerException if {@code node} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxChatstateServerNotificationRPC",
            exports = "receiveServerNotificationRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInChatstateServerNotificationRequest",
            exports = "parseServerNotificationRequest",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInChatstateInternalTestMixin",
            exports = "parseInternalTestMixin",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<SmaxServerNotificationResponse> of(Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        if (!node.hasDescription("chatstate")) {
            return Optional.empty();
        }
        var stateSource = SmaxServerNotificationStateSource.of(node).orElse(null);
        if (stateSource == null) {
            return Optional.empty();
        }
        var stateType = SmaxServerNotificationStateType.of(node).orElse(null);
        if (stateType == null) {
            return Optional.empty();
        }
        var testConfig = node.getChild("test")
                .flatMap(testNode -> testNode.getAttributeAsString("config"))
                .orElse(null);
        return Optional.of(new SmaxServerNotificationResponse(stateSource, stateType, testConfig));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxServerNotificationResponse) obj;
        return Objects.equals(this.stateSource, that.stateSource)
                && Objects.equals(this.stateType, that.stateType)
                && Objects.equals(this.testConfig, that.testConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stateSource, stateType, testConfig);
    }

    @Override
    public String toString() {
        return "SmaxServerNotificationResponse[stateSource=" + stateSource
                + ", stateType=" + stateType
                + ", testConfig=" + testConfig + ']';
    }
}
