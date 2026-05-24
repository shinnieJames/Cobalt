package com.github.auties00.cobalt.node.smax.inappcomms;

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
 * The outbound {@code <iq xmlns="w:comms" type="set" to="s.whatsapp.net">}
 * stanza carrying a quick-promotion event report.
 *
 * @apiNote
 * Backs the WAM-comms event surface that WA Web populates from the
 * quick-promotion (QP) job pipeline: the four documented {@code eventType}
 * values are {@code "impression"} (emitted by
 * {@code WAWebJobImpressionOnQuickPromotion}), {@code "exposure"}
 * ({@code WAWebJobUserExposureToQuickPromotion}),
 * {@code "primary_click"}
 * ({@code WAWebJobPrimaryActionClickInQuickPromotion}), and
 * {@code "dismiss"} ({@code WAWebJobDismissQuickPromotion}). Cobalt
 * exposes the request through
 * {@link com.github.auties00.cobalt.client.WhatsAppClient}'s
 * {@code reportInAppCommsEvent} entry point.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutInAppCommsEventRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutInAppCommsBaseIQSetRequestMixin")
public final class SmaxInAppCommsEventRequest implements SmaxOperation.Request {
    /**
     * The promotion id the event is being reported against.
     *
     * @implNote
     * This implementation stores the value as a plain {@link String};
     * WA Web wraps it through {@code WAWap.CUSTOM_STRING} at
     * serialisation time but the wire form is identical.
     */
    private final String eventPromotionId;

    /**
     * The discriminator naming what kind of interaction occurred.
     *
     * @implNote
     * This implementation accepts any {@link String} verbatim because
     * the relay validates the enum server-side; the four documented
     * values ({@code "impression"}, {@code "exposure"},
     * {@code "primary_click"}, {@code "dismiss"}) are listed at the
     * class level.
     */
    private final String eventType;

    /**
     * The event timestamp in seconds since the Unix epoch.
     */
    private final long eventTimestampSec;

    /**
     * The event-specific opaque log payload.
     *
     * @implNote
     * This implementation is intentionally schema-agnostic: WA Web
     * builds the payload string at the call site (typically a
     * stringified JSON object or a comma-separated token list) and
     * Cobalt forwards it verbatim through the {@code logdata} attribute.
     */
    private final String eventLogdata;

    /**
     * Constructs a request reporting a quick-promotion event.
     *
     * @apiNote
     * The four typed fields map one-for-one to the keys WA Web's
     * {@code WAWebJob*QuickPromotion} jobs pass to
     * {@code WASmaxInAppCommsEventRPC.sendEventRPC}.
     *
     * @param eventPromotionId the promotion id; never {@code null}
     * @param eventType the event type; never {@code null}
     * @param eventTimestampSec the event timestamp in seconds since the
     *                          Unix epoch
     * @param eventLogdata the opaque log payload; never {@code null}
     * @throws NullPointerException if any string argument is
     *                              {@code null}
     */
    public SmaxInAppCommsEventRequest(String eventPromotionId, String eventType,
                   long eventTimestampSec, String eventLogdata) {
        this.eventPromotionId = Objects.requireNonNull(eventPromotionId, "eventPromotionId cannot be null");
        this.eventType = Objects.requireNonNull(eventType, "eventType cannot be null");
        this.eventTimestampSec = eventTimestampSec;
        this.eventLogdata = Objects.requireNonNull(eventLogdata, "eventLogdata cannot be null");
    }

    /**
     * Returns the promotion id.
     *
     * @return the promotion id; never {@code null}
     */
    public String eventPromotionId() {
        return eventPromotionId;
    }

    /**
     * Returns the event type discriminator.
     *
     * @return the event type; never {@code null}
     */
    public String eventType() {
        return eventType;
    }

    /**
     * Returns the event timestamp in seconds since the Unix epoch.
     *
     * @return the timestamp
     */
    public long eventTimestampSec() {
        return eventTimestampSec;
    }

    /**
     * Returns the opaque log payload.
     *
     * @return the log payload; never {@code null}
     */
    public String eventLogdata() {
        return eventLogdata;
    }

    /**
     * Builds the outbound IQ stanza ready for dispatch.
     *
     * @apiNote
     * Produces
     * {@code <iq xmlns="w:comms" type="set" to="s.whatsapp.net"><event promotion_id type timestamp_sec logdata/></iq>};
     * the envelope's {@code id} is stamped by the dispatch path. The
     * reply is parsed via {@link SmaxInAppCommsEventResponse}.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and the
     *         {@code <event/>} child
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutInAppCommsEventRequest",
            exports = "makeEventRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var eventNode = new NodeBuilder()
                .description("event")
                .attribute("promotion_id", eventPromotionId)
                .attribute("type", eventType)
                .attribute("timestamp_sec", eventTimestampSec)
                .attribute("logdata", eventLogdata)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:comms")
                .attribute("to", JidServer.user())
                .attribute("type", "set")
                .content(eventNode);
    }

    /**
     * Returns whether the given object is a
     * {@link SmaxInAppCommsEventRequest} with equal typed fields.
     *
     * @param obj the candidate; may be {@code null}
     * @return {@code true} when all four fields match
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxInAppCommsEventRequest) obj;
        return this.eventTimestampSec == that.eventTimestampSec
                && Objects.equals(this.eventPromotionId, that.eventPromotionId)
                && Objects.equals(this.eventType, that.eventType)
                && Objects.equals(this.eventLogdata, that.eventLogdata);
    }

    /**
     * Returns a hash code derived from the four typed fields.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(eventPromotionId, eventType, eventTimestampSec, eventLogdata);
    }

    /**
     * Returns a debug-friendly textual representation of this request.
     *
     * @return the textual representation
     */
    @Override
    public String toString() {
        return "SmaxInAppCommsEventRequest[eventPromotionId=" + eventPromotionId
                + ", eventType=" + eventType
                + ", eventTimestampSec=" + eventTimestampSec
                + ", eventLogdata=" + eventLogdata + ']';
    }
}
