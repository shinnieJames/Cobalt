package com.github.auties00.cobalt.ack;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fluent builder for the outbound {@code <ack>} stanza shipped in
 * response to an inbound stanza.
 *
 * @apiNote
 * Obtain a builder through {@link AckSender#ack(AckClass, Node)}, layer
 * the per-call overrides on top of the per-class defaults, and ship the
 * stanza by calling {@link #send()}. The builder honours per-class
 * attribute defaults so the common shapes ({@code <ack class="message">},
 * {@code <ack class="notification" type="xxx">}) require zero override
 * calls; consult {@link AckSender#ack(AckClass, Node)} for the per-class
 * defaults applied to each {@code type} and {@code participant}
 * attribute.
 *
 * @implNote
 * This implementation mirrors WA Web's
 * {@code WAWebHandleMsgSendAck.sendAck},
 * {@code WAWebReceiptAck.buildReceiptAck} and the
 * {@code <meta failure_reason=...>} append on
 * {@code WAWebCreateNackFromStanza}'s {@code InvalidProtobuf} path; the
 * builder collapses all four call shapes into a single override matrix
 * so consumers no longer hand-roll a {@link NodeBuilder} per call site.
 * The builder is not thread-safe; create a fresh instance per ack.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleMsgSendAck")
@WhatsAppWebModule(moduleName = "WAWebReceiptAck")
@WhatsAppWebModule(moduleName = "WAWebCreateNackFromStanza")
public final class AckBuilder {
    /**
     * The {@link AckSender} that dispatches the assembled stanza on
     * {@link #send()}.
     */
    private final AckSender owner;

    /**
     * The class of stanza this ack acknowledges. Drives the
     * {@code class} attribute and the per-class default behaviour of
     * {@code type} and {@code participant} when not overridden.
     */
    private final AckClass ackClass;

    /**
     * The inbound stanza being acknowledged. Used to source the
     * {@code id} attribute, the {@code to} attribute (from inbound
     * {@code from}), and to look up the inherited {@code type} and
     * {@code participant} when the per-class default is INHERIT.
     */
    private final Node inbound;

    /**
     * Tracks whether {@link #type(String)} has been called so the
     * resolver can distinguish "not set" (fall back to the per-class
     * default) from "explicitly set to {@code null}" (force the
     * attribute to be omitted).
     */
    private boolean typeOverrideSet = false;

    /**
     * Holds the explicit {@code type} override; only meaningful when
     * {@link #typeOverrideSet} is {@code true}. A {@code null} value
     * combined with {@code typeOverrideSet=true} forces the attribute
     * to be omitted.
     */
    private String typeOverride = null;

    /**
     * Tracks whether {@link #participant(Jid)} has been called.
     * Mutually exclusive with {@link #participantIfDifferentSet}.
     */
    private boolean participantOverrideSet = false;

    /**
     * Holds the explicit {@code participant} override; only
     * meaningful when {@link #participantOverrideSet} is {@code true}.
     */
    private Jid participantOverride = null;

    /**
     * Tracks whether {@link #participantIfDifferent(Jid)} has been
     * called so the if-different branch can distinguish "not set"
     * from "set to {@code null}". Mutually exclusive with
     * {@link #participantOverrideSet}.
     */
    private boolean participantIfDifferentSet = false;

    /**
     * Holds the conditional {@code participant} value to be written
     * only when it differs from the resolved {@code to} value,
     * mirroring {@code WAWebReceiptAck.buildReceiptAck}'s
     * {@code participant !== to} guard. Only meaningful when
     * {@link #participantIfDifferentSet} is {@code true}.
     */
    private Jid participantIfDifferent = null;

    /**
     * Tracks whether {@link #to(Jid)} has been called so the resolver
     * can distinguish "fall back to the inbound stanza's {@code from}
     * attribute" from "the caller forced an explicit {@code to}".
     */
    private boolean toOverrideSet = false;

    /**
     * Holds the explicit {@code to} override; only meaningful when
     * {@link #toOverrideSet} is {@code true}. The default {@code to}
     * value is the inbound stanza's {@code from} attribute.
     */
    private Jid toOverride = null;

    /**
     * Optional {@code from} attribute on the outbound ack. WA Web
     * defaults this to the local device JID for class
     * {@code message}; Cobalt leaves it omitted unless explicitly set
     * by a caller (notably {@code CallReceiptReceiver}, which forces
     * the local user PN).
     */
    private Jid fromAttribute = null;

    /**
     * The {@link NackReason} that drives the {@code error} attribute,
     * or {@code null} when the stanza is a plain ack.
     */
    private NackReason error = null;

    /**
     * The {@code failure_reason} string carried on a child
     * {@code <meta>} node for the {@link NackReason#INVALID_PROTOBUF}
     * nack, or {@code null} when no such child is required.
     */
    private String failureReason = null;

    /**
     * Optional list of arbitrary child nodes appended to the
     * outbound ack stanza, in insertion order.
     *
     * @apiNote
     * Used by the business-notification ack path to append a
     * {@code <user side_list="out"/>} hint to the server.
     */
    private List<Node> children = null;

    /**
     * Constructs a new builder bound to the given {@link AckSender},
     * stanza class and inbound stanza.
     *
     * @apiNote
     * Cobalt callers obtain a builder via
     * {@link AckSender#ack(AckClass, Node)}; this constructor is
     * package-private to keep instance creation centralised on
     * {@code AckSender}.
     *
     * @param owner    the {@link AckSender} that will dispatch the
     *                 assembled stanza on {@link #send()}
     * @param ackClass the {@link AckClass} written into the
     *                 {@code class} attribute
     * @param inbound  the inbound stanza being acknowledged
     */
    AckBuilder(AckSender owner, AckClass ackClass, Node inbound) {
        this.owner = owner;
        this.ackClass = ackClass;
        this.inbound = inbound;
    }

    /**
     * Overrides the {@code type} attribute on the outbound ack.
     *
     * @apiNote
     * Pass an explicit value (for example {@code "retry"} on a
     * retry-receipt ack, or {@code "account_sync"} on an account-sync
     * notification ack) to write that exact value. Pass {@code null}
     * to force the attribute to be omitted even when the per-class
     * default would inherit it from the inbound stanza.
     *
     * @param type the explicit {@code type} value, or {@code null} to
     *             omit the attribute entirely
     * @return this builder for chaining
     */
    public AckBuilder type(String type) {
        this.typeOverride = type;
        this.typeOverrideSet = true;
        return this;
    }

    /**
     * Overrides the {@code participant} attribute on the outbound ack.
     *
     * @apiNote
     * Pass an explicit {@link Jid} to write that exact value, or
     * {@code null} to force the attribute to be omitted even when the
     * per-class default would inherit it from the inbound stanza.
     * Mutually exclusive with
     * {@link #participantIfDifferent(Jid)}: calling either clears the
     * other.
     *
     * @param participant the explicit {@link Jid}, or {@code null} to
     *                    omit the attribute
     * @return this builder for chaining
     */
    public AckBuilder participant(Jid participant) {
        this.participantOverride = participant;
        this.participantOverrideSet = true;
        this.participantIfDifferent = null;
        this.participantIfDifferentSet = false;
        return this;
    }

    /**
     * Sets the {@code participant} attribute to {@code participant}
     * only when it differs from the resolved {@code to} value.
     *
     * @apiNote
     * Mirrors WA Web's {@code WAWebReceiptAck.buildReceiptAck} guard
     * where the participant attribute is dropped when it would equal
     * the {@code to} attribute. Mutually exclusive with
     * {@link #participant(Jid)}: calling either clears the other.
     *
     * @param participant the candidate {@link Jid}; when {@code null}
     *                    or equal to the resolved {@code to}, the
     *                    attribute is omitted
     * @return this builder for chaining
     */
    @WhatsAppWebExport(moduleName = "WAWebReceiptAck", exports = "buildReceiptAck",
            adaptation = WhatsAppAdaptation.DIRECT)
    public AckBuilder participantIfDifferent(Jid participant) {
        this.participantIfDifferent = participant;
        this.participantIfDifferentSet = true;
        this.participantOverride = null;
        this.participantOverrideSet = false;
        return this;
    }

    /**
     * Overrides the {@code to} attribute on the outbound ack.
     *
     * @apiNote
     * The default behaviour is to set {@code to} to the inbound
     * stanza's {@code from} attribute. Pass an explicit {@link Jid}
     * when the target identity differs from the inbound sender (for
     * example {@code WAWebHandleDeviceNotification}, which echoes the
     * user-level form of the inbound device JID rather than the raw
     * device JID). Passing {@code null} forces the {@code to}
     * attribute to be unset, in which case {@link #send()} will drop
     * the ack as if the inbound {@code from} were missing.
     *
     * @param to the explicit {@link Jid} for the {@code to} attribute,
     *           or {@code null} to clear
     * @return this builder for chaining
     */
    public AckBuilder to(Jid to) {
        this.toOverride = to;
        this.toOverrideSet = true;
        return this;
    }

    /**
     * Sets the {@code from} attribute on the outbound ack.
     *
     * @apiNote
     * Only needed for stanza classes where the client must echo a
     * specific identity back to the server. The current Cobalt
     * caller is {@code CallReceiptReceiver}, which sets the local
     * user PN explicitly; all other call sites omit {@code from} so
     * the server fills it in.
     *
     * @param from the {@link Jid} to write as the {@code from}
     *             attribute, or {@code null} to clear a previous
     *             override
     * @return this builder for chaining
     */
    public AckBuilder from(Jid from) {
        this.fromAttribute = from;
        return this;
    }

    /**
     * Marks the outbound ack as a NACK with the given
     * {@link NackReason}.
     *
     * @apiNote
     * Writes the integer error code returned by
     * {@link NackReason#code()} into the {@code error} attribute. For
     * {@link NackReason#INVALID_PROTOBUF} the caller should also
     * provide an {@code e2eFailureReason} via
     * {@link #failureReason(String)} so the server receives the
     * mandatory {@code <meta failure_reason="..."/>} child node.
     *
     * @param reason the {@link NackReason} stamped into the
     *               {@code error} attribute, or {@code null} to clear
     *               a previous error
     * @return this builder for chaining
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateNackFromStanza",
            exports = "createNackFromStanza", adaptation = WhatsAppAdaptation.DIRECT)
    public AckBuilder error(NackReason reason) {
        this.error = reason;
        return this;
    }

    /**
     * Appends a {@code <meta failure_reason="..."/>} child node to the
     * outbound NACK.
     *
     * @apiNote
     * Required by WA Web only when the NACK reason is
     * {@link NackReason#INVALID_PROTOBUF}; the server logs the value
     * against the offending stanza. Passing {@code null} clears any
     * previous failure reason.
     *
     * @param e2eFailureReason the textual failure-reason hint, or
     *                         {@code null} to clear
     * @return this builder for chaining
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateNackFromStanza",
            exports = "createNackFromStanza", adaptation = WhatsAppAdaptation.DIRECT)
    public AckBuilder failureReason(String e2eFailureReason) {
        this.failureReason = e2eFailureReason;
        return this;
    }

    /**
     * Appends an arbitrary child node to the outbound ack stanza.
     *
     * @apiNote
     * Used by the business-notification ack path to append a
     * {@code <user side_list="out"/>} hint. Multiple calls preserve
     * insertion order. A {@code null} argument is ignored.
     *
     * @param child the {@link Node} to append, or {@code null} to skip
     * @return this builder for chaining
     */
    public AckBuilder child(Node child) {
        if (child == null) {
            return this;
        }
        if (children == null) {
            children = new ArrayList<>(1);
        }
        children.add(child);
        return this;
    }

    /**
     * Builds the outbound ack stanza and dispatches it through the
     * owning {@link AckSender}.
     *
     * @apiNote
     * The stanza is shipped fire-and-forget. When the inbound stanza
     * lacks either the {@code id} or the {@code from} attribute the
     * ack is silently dropped and this method returns {@code false};
     * this matches WA Web's
     * {@code WAWebCreateNackFromStanza}, which also fast-paths to
     * {@code NO_ACK} on the same precondition.
     *
     * @implNote
     * This implementation resolves the per-class defaults for
     * {@code type} and {@code participant} before applying the
     * fluent overrides, then writes the {@code error} and
     * {@code <meta failure_reason=...>} child when present. For
     * {@link NackReason#INVALID_PROTOBUF} without a failure reason,
     * the {@code <meta>} child is omitted to match WA Web's
     * {@code invalid-protobuf-nack-missing-failure-reason}
     * fallback.
     *
     * @return {@code true} when the stanza was dispatched,
     *         {@code false} when it was dropped due to a missing
     *         {@code id} or {@code from} on the inbound stanza
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleMsgSendAck", exports = "sendAck",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebReceiptAck", exports = "buildReceiptAck",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebCreateNackFromStanza",
            exports = "createNackFromStanza", adaptation = WhatsAppAdaptation.DIRECT)
    public boolean send() {
        var id = inbound.getAttributeAsString("id", null);
        var to = toOverrideSet ? toOverride : inbound.getAttributeAsJid("from").orElse(null);
        if (id == null || to == null) {
            return false;
        }

        var resolvedType = resolveType();
        var resolvedParticipant = resolveParticipant(to);

        var builder = new NodeBuilder()
                .description("ack")
                .attribute("id", id)
                .attribute("class", ackClass.wireToken())
                .attribute("to", to)
                .attribute("type", resolvedType)
                .attribute("from", fromAttribute)
                .attribute("participant", resolvedParticipant);

        if (error != null) {
            builder.attribute("error", error.code());
        }

        var metaChild = buildMetaChild();
        if (metaChild != null) {
            builder.content(metaChild);
        }
        if (children != null) {
            for (var child : children) {
                builder.content(child);
            }
        }

        owner.dispatch(builder.build());
        return true;
    }

    /**
     * Resolves the value to write for the {@code type} attribute,
     * applying the per-class default when no override has been
     * specified.
     *
     * @apiNote
     * The default is INHERIT for every {@link AckClass} today: the
     * value of the {@code type} attribute on the inbound stanza is
     * copied verbatim when present. Callers override via
     * {@link #type(String)} when the desired type is computed
     * elsewhere (for example, the call-payload tag echoed back by
     * {@code CallReceiver}).
     *
     * @return the resolved {@code type} value, or {@code null} to
     *         omit the attribute
     */
    private String resolveType() {
        if (typeOverrideSet) {
            return typeOverride;
        }
        return inbound.getAttributeAsString("type", null);
    }

    /**
     * Resolves the value to write for the {@code participant}
     * attribute, applying the per-class default when no override has
     * been specified.
     *
     * @apiNote
     * Defaults per {@link AckClass}: MESSAGE inherits from inbound,
     * RECEIPT inherits from inbound but drops the attribute when the
     * value equals the resolved {@code to}, NOTIFICATION omits, and
     * CALL omits.
     *
     * @param to the resolved {@code to} value, used by the RECEIPT
     *           default to enforce the {@code participant !== to}
     *           guard
     * @return the resolved {@code participant} value, or {@code null}
     *         to omit the attribute
     */
    private Jid resolveParticipant(Jid to) {
        if (participantIfDifferentSet) {
            var candidate = participantIfDifferent;
            if (candidate == null || Objects.equals(candidate, to)) {
                return null;
            }
            return candidate;
        }
        if (participantOverrideSet) {
            return participantOverride;
        }
        return switch (ackClass) {
            case MESSAGE -> inbound.getAttributeAsJid("participant").orElse(null);
            case RECEIPT -> {
                var inherited = inbound.getAttributeAsJid("participant").orElse(null);
                yield inherited != null && !Objects.equals(inherited, to) ? inherited : null;
            }
            case NOTIFICATION, CALL -> null;
        };
    }

    /**
     * Builds the optional {@code <meta failure_reason="..."/>} child
     * node, or returns {@code null} when no such child is needed.
     *
     * @apiNote
     * WA Web only adds the {@code <meta>} child for the
     * {@link NackReason#INVALID_PROTOBUF} reason and only when a
     * non-{@code null} failure reason was supplied. The validation
     * system logs a missing failure reason as
     * {@code invalid-protobuf-nack-missing-failure-reason} but still
     * ships the bare ack without the child.
     *
     * @return the {@code <meta>} child, or {@code null}
     */
    private Node buildMetaChild() {
        if (error != NackReason.INVALID_PROTOBUF || failureReason == null) {
            return null;
        }
        return new NodeBuilder()
                .description("meta")
                .attribute("failure_reason", failureReason)
                .build();
    }
}
