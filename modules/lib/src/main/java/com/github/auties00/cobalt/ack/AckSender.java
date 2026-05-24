package com.github.auties00.cobalt.ack;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;

import java.util.Objects;

/**
 * Single point of entry for shipping outbound {@code <ack>} stanzas
 * (positive acks and {@code <ack error=...>} nacks) in response to an
 * inbound stanza.
 *
 * @apiNote
 * Stream handlers receive this service via constructor injection; they
 * call {@link #sendAck(AckClass, Node)} or
 * {@link #sendNack(AckClass, Node, NackReason)} for the common shapes
 * and obtain a {@link AckBuilder} via
 * {@link #ack(AckClass, Node)} for any call site that needs to override
 * attributes (custom type, explicit participant, additional child
 * nodes, custom {@code from}). The class consolidates the eighteen
 * hand-rolled {@link com.github.auties00.cobalt.node.NodeBuilder}
 * constructions that previously lived across the message, receipt,
 * notification and call handlers.
 *
 * @implNote
 * This implementation collapses three WA Web entry points:
 * {@code WAWebHandleMsgSendAck.sendAck} for the positive ack path,
 * {@code WAWebReceiptAck.buildReceiptAck} for the receipt-class
 * sub-shape and {@code WAWebCreateNackFromStanza.createNackFromStanza}
 * for the synthesised-nack path. Per-class default behaviour for the
 * {@code type} and {@code participant} attributes is handled by
 * {@link AckBuilder}; see its javadoc for the resolution rules.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleMsgSendAck")
@WhatsAppWebModule(moduleName = "WAWebReceiptAck")
@WhatsAppWebModule(moduleName = "WAWebCreateNackFromStanza")
public final class AckSender {
    /**
     * The {@link WhatsAppClient} that ships the assembled ack stanza
     * fire-and-forget through
     * {@link WhatsAppClient#sendNodeWithNoResponse(Node)}.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a new sender bound to the given
     * {@link WhatsAppClient}.
     *
     * @apiNote
     * One {@code AckSender} per logical client; the lifecycle wiring
     * threads the same instance into every stream handler that needs
     * to emit an ack.
     *
     * @param whatsapp the {@link WhatsAppClient} used to dispatch the
     *                 assembled ack stanza
     */
    public AckSender(WhatsAppClient whatsapp) {
        this.whatsapp = Objects.requireNonNull(whatsapp, "whatsapp");
    }

    /**
     * Ships a plain {@code <ack>} stanza for the given inbound stanza,
     * applying the per-class defaults for {@code type} and
     * {@code participant} without further overrides.
     *
     * @apiNote
     * The shortcut form for the common case where the handler just
     * needs to confirm receipt of an inbound stanza. The stanza is
     * dropped silently when the inbound stanza lacks either an
     * {@code id} or a {@code from} attribute, matching WA Web's
     * fast-path drop in {@code createNackFromStanza}.
     *
     * @param cls     the {@link AckClass} for the {@code class}
     *                attribute on the outbound ack
     * @param inbound the inbound stanza being acknowledged
     * @return {@code true} when the ack was dispatched, {@code false}
     *         when it was dropped due to a missing {@code id} or
     *         {@code from}
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgSendAck", exports = "sendAck",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean sendAck(AckClass cls, Node inbound) {
        return ack(cls, inbound).send();
    }

    /**
     * Ships an {@code <ack error="N">} stanza (a NACK) for the given
     * inbound stanza, applying the per-class defaults for
     * {@code type} and {@code participant} without further overrides.
     *
     * @apiNote
     * The shortcut form for the common case where a handler classifies
     * the inbound stanza as unprocessable and the reason maps directly
     * to a known {@link NackReason}. For
     * {@link NackReason#INVALID_PROTOBUF} use
     * {@link #ack(AckClass, Node)} instead and add the failure reason
     * via {@link AckBuilder#failureReason(String)}.
     *
     * @param cls     the {@link AckClass} for the {@code class}
     *                attribute on the outbound ack
     * @param inbound the inbound stanza being nacked
     * @param reason  the {@link NackReason} stamped into the
     *                {@code error} attribute
     * @return {@code true} when the ack was dispatched, {@code false}
     *         when it was dropped due to a missing {@code id} or
     *         {@code from}
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgSendAck", exports = "sendNack",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean sendNack(AckClass cls, Node inbound, NackReason reason) {
        return ack(cls, inbound).error(reason).send();
    }

    /**
     * Returns a fluent {@link AckBuilder} bound to this sender, the
     * given stanza class and the inbound stanza.
     *
     * @apiNote
     * Use this entry point when the outbound ack needs any deviation
     * from the per-class defaults: an explicit {@code type},
     * a non-default {@code participant}, a custom {@code from},
     * appended child nodes, or a NACK with an
     * {@code <meta failure_reason=...>} child. The builder defers
     * the actual stanza assembly and dispatch to its
     * {@link AckBuilder#send()} method, so callers can short-circuit
     * by simply not calling {@code send()}.
     *
     * @param cls     the {@link AckClass} for the {@code class}
     *                attribute on the outbound ack
     * @param inbound the inbound stanza being acknowledged
     * @return a fresh {@link AckBuilder}
     */
    public AckBuilder ack(AckClass cls, Node inbound) {
        return new AckBuilder(this, cls, inbound);
    }

    /**
     * Synthesises a NACK for an inbound stanza of unknown or
     * uncategorised origin, mirroring WA Web's
     * {@code WAWebCreateNackFromStanza.createNackFromStanza} entry
     * point.
     *
     * @apiNote
     * Used by the central socket-stream error model when a stanza
     * cannot be routed to a registered handler at all. The
     * outbound stanza's {@code class} attribute is derived from the
     * inbound stanza tag:
     * {@code <message>}-{@code <receipt>}-{@code <notification>}
     * map to {@link AckClass#MESSAGE}, {@link AckClass#RECEIPT} and
     * {@link AckClass#NOTIFICATION} respectively. For
     * {@link NackReason#UNRECOGNIZED_STANZA} (488) WA Web reuses
     * the inbound stanza tag verbatim as the
     * {@code class} attribute via {@code CUSTOM_STRING}; that case is
     * out of scope for the current Cobalt model and is treated as
     * an unsupported tag (returns {@code false}).
     *
     * @param inbound the inbound stanza for which to synthesise a
     *                NACK
     * @param reason  the {@link NackReason} stamped into the
     *                {@code error} attribute
     * @return {@code true} when the NACK was dispatched,
     *         {@code false} when the inbound stanza tag is not one
     *         of the three supported classes or when the inbound
     *         stanza lacks an {@code id} or {@code from} attribute
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateNackFromStanza",
            exports = "createNackFromStanza", adaptation = WhatsAppAdaptation.DIRECT)
    public boolean synthesiseNack(Node inbound, NackReason reason) {
        var cls = switch (inbound.description()) {
            case "message" -> AckClass.MESSAGE;
            case "receipt" -> AckClass.RECEIPT;
            case "notification" -> AckClass.NOTIFICATION;
            default -> null;
        };
        if (cls == null) {
            return false;
        }
        return ack(cls, inbound).error(reason).send();
    }

    /**
     * Dispatches the given assembled ack stanza through the
     * underlying {@link WhatsAppClient}.
     *
     * @apiNote
     * Called only from {@link AckBuilder#send()}; package-private so
     * callers cannot bypass the builder's id/from precondition.
     *
     * @implNote
     * This implementation routes through
     * {@link WhatsAppClient#sendNodeWithNoResponse(Node)} so the ack
     * is sent fire-and-forget; the socket layer raises a
     * {@link com.github.auties00.cobalt.exception.WhatsAppSessionException.Closed}
     * if the connection is down at the time of the send.
     *
     * @param ack the assembled {@code <ack>} stanza
     */
    void dispatch(Node ack) {
        whatsapp.sendNodeWithNoResponse(ack);
    }
}
