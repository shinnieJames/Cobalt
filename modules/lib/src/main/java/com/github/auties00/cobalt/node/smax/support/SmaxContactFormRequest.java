package com.github.auties00.cobalt.node.smax.support;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

/**
 * Outbound {@code smax_id=3} IQ that submits the user's free-form support contact form to the
 * {@code fb:thrift_iq} relay.
 *
 * @apiNote
 * Drives the WhatsApp "Help" / "Contact us" surface invoked by
 * {@link SmaxContactFormResponse}; WA Web's
 * {@code WAWebSendSupportRequestJob} builds an instance of this request, dispatches it, and
 * translates the parsed response into a UI ticket-id banner.
 *
 * @implNote
 * This implementation flattens the WA Web mixin chain
 * ({@code WASmaxOutSupportHackBaseIQSetRequestMixin} over
 * {@code WASmaxOutSupportBaseIQSetRequestMixin}) into a single {@link NodeBuilder} that pins
 * {@code xmlns="fb:thrift_iq"}, {@code smax_id=3}, {@code to=Jid.userServer()} and
 * {@code type="set"}. Optional children are only attached when the corresponding constructor
 * argument is non-{@code null}.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutSupportContactFormRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutSupportHackBaseIQSetRequestMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutSupportBaseIQSetRequestMixin")
public final class SmaxContactFormRequest implements SmaxOperation.Request {
    /**
     * The optional {@code from} JID forwarded verbatim into the IQ envelope.
     *
     * @apiNote
     * Set this only when the dispatcher needs to attribute the form to a non-default identity;
     * WA Web's {@code WAWebSendSupportRequestJob} leaves it unset and lets the relay infer the
     * sender from the connection.
     */
    private final Jid iqFrom;

    /**
     * The textarea content that the user typed into the support form.
     *
     * @apiNote
     * Routed verbatim into the {@code <description/>} child; never {@code null}.
     */
    private final String descriptionElementValue;

    /**
     * The optional pre-filled topic title (the dropdown label visible to the user).
     *
     * @apiNote
     * Routed into the {@code <topic/>} child when present.
     */
    private final String topicElementValue;

    /**
     * The optional canonical topic id (the dropdown value, opaque to the user).
     *
     * @apiNote
     * Routed into the {@code <topic_id/>} child when present.
     */
    private final String topicIdElementValue;

    /**
     * The optional debug-information JSON blob captured by the client.
     *
     * @apiNote
     * Routed into the {@code <debug_information_json/>} child when present and consumed by the
     * support backend to populate diagnostic fields on the ticket.
     */
    private final String debugInformationJsonElementValue;

    /**
     * The optional handle returned by a prior crashlog upload.
     *
     * @apiNote
     * Routed into the {@code <uploaded_logs_id/>} child when present and links the ticket back to
     * the previously uploaded crashlog.
     */
    private final String uploadedLogsIdElementValue;

    /**
     * The optional {@code context_flow} marker that names the user-facing flow that opened the
     * support form.
     *
     * @apiNote
     * Routed into {@code <additional_attributes context_flow="..."/>} when present.
     */
    private final String additionalAttributesContextFlow;

    /**
     * Constructs a contact-form request from the supplied scalar fields.
     *
     * @apiNote
     * Typically invoked by a UI submit handler that has collected the form values; only
     * {@code descriptionElementValue} is required and {@code null} for any of the others omits
     * the corresponding child or attribute.
     *
     * @param iqFrom                           the optional sender JID; may be {@code null}
     * @param descriptionElementValue          the free-form description; never {@code null}
     * @param topicElementValue                the optional topic title; may be {@code null}
     * @param topicIdElementValue              the optional topic id; may be {@code null}
     * @param debugInformationJsonElementValue the optional debug-information JSON blob; may be
     *                                         {@code null}
     * @param uploadedLogsIdElementValue       the optional uploaded-crashlog handle; may be
     *                                         {@code null}
     * @param additionalAttributesContextFlow  the optional context-flow marker; may be
     *                                         {@code null}
     * @throws NullPointerException if {@code descriptionElementValue} is {@code null}
     */
    public SmaxContactFormRequest(Jid iqFrom,
                   String descriptionElementValue,
                   String topicElementValue,
                   String topicIdElementValue,
                   String debugInformationJsonElementValue,
                   String uploadedLogsIdElementValue,
                   String additionalAttributesContextFlow) {
        this.iqFrom = iqFrom;
        this.descriptionElementValue = Objects.requireNonNull(descriptionElementValue,
                "descriptionElementValue cannot be null");
        this.topicElementValue = topicElementValue;
        this.topicIdElementValue = topicIdElementValue;
        this.debugInformationJsonElementValue = debugInformationJsonElementValue;
        this.uploadedLogsIdElementValue = uploadedLogsIdElementValue;
        this.additionalAttributesContextFlow = additionalAttributesContextFlow;
    }

    /**
     * Returns the optional {@code from} JID forwarded into the IQ envelope.
     *
     * @apiNote
     * Empty when WA Web's default behaviour of letting the relay infer the sender is desired.
     *
     * @return an {@link Optional} carrying the JID, or empty when omitted
     */
    public Optional<Jid> iqFrom() {
        return Optional.ofNullable(iqFrom);
    }

    /**
     * Returns the user-typed support description.
     *
     * @apiNote
     * Surfaces the textarea content captured at form submission.
     *
     * @return the description; never {@code null}
     */
    public String descriptionElementValue() {
        return descriptionElementValue;
    }

    /**
     * Returns the optional topic title.
     *
     * @apiNote
     * Surfaces the visible dropdown label; empty when the form was submitted without a topic.
     *
     * @return an {@link Optional} carrying the title, or empty when omitted
     */
    public Optional<String> topicElementValue() {
        return Optional.ofNullable(topicElementValue);
    }

    /**
     * Returns the optional canonical topic id.
     *
     * @apiNote
     * Surfaces the opaque dropdown value paired with {@link #topicElementValue()}.
     *
     * @return an {@link Optional} carrying the id, or empty when omitted
     */
    public Optional<String> topicIdElementValue() {
        return Optional.ofNullable(topicIdElementValue);
    }

    /**
     * Returns the optional debug-information JSON blob.
     *
     * @apiNote
     * Surfaces the client-side diagnostic snapshot attached to the ticket.
     *
     * @return an {@link Optional} carrying the JSON blob, or empty when omitted
     */
    public Optional<String> debugInformationJsonElementValue() {
        return Optional.ofNullable(debugInformationJsonElementValue);
    }

    /**
     * Returns the optional uploaded-crashlog handle.
     *
     * @apiNote
     * Surfaces the handle returned by a prior crashlog upload that links the ticket back to it.
     *
     * @return an {@link Optional} carrying the handle, or empty when omitted
     */
    public Optional<String> uploadedLogsIdElementValue() {
        return Optional.ofNullable(uploadedLogsIdElementValue);
    }

    /**
     * Returns the optional {@code context_flow} marker.
     *
     * @apiNote
     * Surfaces the user-facing flow that opened the form.
     *
     * @return an {@link Optional} carrying the marker, or empty when omitted
     */
    public Optional<String> additionalAttributesContextFlow() {
        return Optional.ofNullable(additionalAttributesContextFlow);
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Emits the outbound contact-form IQ ready for {@link com.github.auties00.cobalt.node.smax}
     * dispatch.
     *
     * @implNote
     * This implementation appends each optional child to the IQ payload only when the matching
     * scalar field is non-{@code null} and forwards {@link #iqFrom} into the envelope's
     * {@code from} attribute (the {@link NodeBuilder#attribute} call accepts a {@code null} JID).
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutSupportContactFormRequest",
            exports = "makeContactFormRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var children = new ArrayList<Node>();
        children.add(new NodeBuilder()
                .description("description")
                .content(descriptionElementValue)
                .build());
        if (topicElementValue != null) {
            children.add(new NodeBuilder()
                    .description("topic")
                    .content(topicElementValue)
                    .build());
        }
        if (topicIdElementValue != null) {
            children.add(new NodeBuilder()
                    .description("topic_id")
                    .content(topicIdElementValue)
                    .build());
        }
        if (debugInformationJsonElementValue != null) {
            children.add(new NodeBuilder()
                    .description("debug_information_json")
                    .content(debugInformationJsonElementValue)
                    .build());
        }
        if (uploadedLogsIdElementValue != null) {
            children.add(new NodeBuilder()
                    .description("uploaded_logs_id")
                    .content(uploadedLogsIdElementValue)
                    .build());
        }
        if (additionalAttributesContextFlow != null) {
            children.add(new NodeBuilder()
                    .description("additional_attributes")
                    .attribute("context_flow", additionalAttributesContextFlow)
                    .build());
        }
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "fb:thrift_iq")
                .attribute("smax_id", 3)
                .attribute("from", iqFrom)
                .attribute("to", Jid.userServer())
                .attribute("type", "set")
                .content(children);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxContactFormRequest) obj;
        return Objects.equals(this.iqFrom, that.iqFrom)
                && Objects.equals(this.descriptionElementValue, that.descriptionElementValue)
                && Objects.equals(this.topicElementValue, that.topicElementValue)
                && Objects.equals(this.topicIdElementValue, that.topicIdElementValue)
                && Objects.equals(this.debugInformationJsonElementValue, that.debugInformationJsonElementValue)
                && Objects.equals(this.uploadedLogsIdElementValue, that.uploadedLogsIdElementValue)
                && Objects.equals(this.additionalAttributesContextFlow, that.additionalAttributesContextFlow);
    }

    @Override
    public int hashCode() {
        return Objects.hash(iqFrom, descriptionElementValue, topicElementValue, topicIdElementValue,
                debugInformationJsonElementValue, uploadedLogsIdElementValue,
                additionalAttributesContextFlow);
    }

    @Override
    public String toString() {
        return "SmaxContactFormRequest[iqFrom=" + iqFrom
                + ", descriptionElementValue=" + descriptionElementValue
                + ", topicElementValue=" + topicElementValue
                + ", topicIdElementValue=" + topicIdElementValue
                + ", debugInformationJsonElementValue=" + debugInformationJsonElementValue
                + ", uploadedLogsIdElementValue=" + uploadedLogsIdElementValue
                + ", additionalAttributesContextFlow=" + additionalAttributesContextFlow + ']';
    }
}
