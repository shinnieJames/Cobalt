package com.github.auties00.cobalt.node.smax.presence;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <presence type? name?/>} availability broadcast.
 *
 * @apiNote
 * Drives WA Web's
 * {@code WASmaxPresenceAvailabilityRPC.sendAvailabilityRPC}, the
 * fire-and-forget {@code castSmaxStanza} surface invoked by
 * {@code WASendPresenceStatusProtocol.sendPresenceStatusProtocol} when
 * the local user transitions between
 * {@code available}/{@code unavailable} or republishes their push name;
 * Cobalt embedders dispatch one of these to announce their own
 * presence to peers subscribed via {@link SmaxSubscribeRequest}.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutPresenceAvailabilityRequest")
public final class SmaxAvailabilityRequest implements SmaxOperation.Request {
    /**
     * The optional presence type.
     *
     * @apiNote
     * Routed verbatim into the {@code type} attribute as an
     * {@code OPTIONAL(CUSTOM_STRING, presenceType)}; typically
     * {@code "available"} or {@code "unavailable"}, though the relay
     * accepts any string. When {@code null} the stanza degenerates to
     * a pure name republish.
     */
    private final String presenceType;

    /**
     * The optional push-name to advertise.
     *
     * @apiNote
     * Routed verbatim into the {@code name} attribute as an
     * {@code OPTIONAL(CUSTOM_STRING, presenceName)}; the relay reuses
     * the previously-broadcast value when {@code null}.
     */
    private final String presenceName;

    /**
     * Constructs a new availability broadcast.
     *
     * @apiNote
     * Both fields are optional so callers may craft pure
     * type-transition, pure name-republish, or combined stanzas in a
     * single dispatch.
     *
     * @param presenceType the optional presence type; may be
     *                     {@code null}
     * @param presenceName the optional push-name; may be {@code null}
     */
    public SmaxAvailabilityRequest(String presenceType, String presenceName) {
        this.presenceType = presenceType;
        this.presenceName = presenceName;
    }

    /**
     * Returns the optional presence type.
     *
     * @apiNote
     * Empty when this broadcast does not change the user's
     * available/unavailable status.
     *
     * @return an {@link Optional} carrying the type
     */
    public Optional<String> presenceType() {
        return Optional.ofNullable(presenceType);
    }

    /**
     * Returns the optional push-name.
     *
     * @apiNote
     * Empty when this broadcast does not republish the user's display
     * name.
     *
     * @return an {@link Optional} carrying the name
     */
    public Optional<String> presenceName() {
        return Optional.ofNullable(presenceName);
    }

    /**
     * Builds the outbound presence stanza ready for dispatch.
     *
     * @apiNote
     * Returned unbuilt so the dispatch path can stamp a fresh stanza
     * id before flushing; null-valued attributes are dropped at
     * render time, matching the WA Web {@code OPTIONAL} attribute
     * semantics.
     *
     * @return a {@link NodeBuilder} carrying the
     *         {@code <presence type? name?/>} envelope
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutPresenceAvailabilityRequest",
            exports = "makeAvailabilityRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        return new NodeBuilder()
                .description("presence")
                .attribute("type", presenceType)
                .attribute("name", presenceName);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxAvailabilityRequest) obj;
        return Objects.equals(this.presenceType, that.presenceType)
                && Objects.equals(this.presenceName, that.presenceName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(presenceType, presenceName);
    }

    @Override
    public String toString() {
        return "SmaxAvailabilityRequest[presenceType=" + presenceType
                + ", presenceName=" + presenceName + ']';
    }
}
