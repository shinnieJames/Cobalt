package com.github.auties00.cobalt.model.message.system;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

/**
 * A system message that asks the recipient to share their phone number inside
 * the current conversation.
 *
 * <p>WhatsApp uses this prompt in chats based on Linked Identifiers (LID)
 * where the phone number is hidden by default, such as community
 * announcements or certain privacy-preserving group chats, to let
 * participants explicitly request contact details from each other.
 *
 * <p>The message carries only the quoted-message context so that clients can
 * thread the request underneath the original interaction that triggered it.
 *
 * @implNote The WA Web generator {@code WAWebGenerateRequestPhoneNumberMessageProto}
 *           is a tiny factory of the shape
 *           {@code function(e){var t=e.contextInfo;return{requestPhoneNumberMessage:{contextInfo:t}}}}.
 *           It takes an input object holding a {@code contextInfo}, then wraps
 *           it as an outer {@code Message} protobuf whose only populated field
 *           is {@code requestPhoneNumberMessage}, which in turn carries the
 *           same {@code contextInfo}. Cobalt represents the structure
 *           statically: this class is the inner
 *           {@code {requestPhoneNumberMessage: {contextInfo}}} object and the
 *           surrounding wrapper is
 *           {@code MessageContainer.requestPhoneNumberMessage} at proto index
 *           54. Construction goes through the generated
 *           {@code RequestPhoneNumberMessageBuilder}, which is the direct
 *           analog of the JS factory call site.
 */
@ProtobufMessage(name = "Message.RequestPhoneNumberMessage")
@WhatsAppWebModule(moduleName = "WAWebGenerateRequestPhoneNumberMessageProto")
public final class RequestPhoneNumberMessage implements ContextualMessage {
    /**
     * The conversational context attached to this request, including the
     * quoted message if any.
     *
     * @implNote The WA Web generator
     *           {@code WAWebGenerateRequestPhoneNumberMessageProto} forwards
     *           its input {@code contextInfo} verbatim onto this field.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    @WhatsAppWebExport(moduleName = "WAWebGenerateRequestPhoneNumberMessageProto", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    ContextInfo contextInfo;


    /**
     * Constructs a new request phone number message.
     *
     * @param contextInfo the conversational context, may be {@code null}
     */
    RequestPhoneNumberMessage(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    /**
     * Returns the conversational context attached to this request.
     *
     * @return an {@link Optional} containing the {@link ContextInfo}, or
     *         {@link Optional#empty()} if no context is set
     */
    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    /**
     * Sets the conversational context attached to this request.
     *
     * @param contextInfo the new context, or {@code null} to clear it
     */
    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }
}
