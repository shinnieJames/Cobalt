package com.github.auties00.cobalt.model.message.interactive;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Represents the reply produced when a recipient taps a button on a {@link TemplateMessage}
 * or on an interactive carousel card.
 *
 * <p>When the user selects one of the template buttons, their client sends back this message
 * so the original sender can match the response to the button that was pressed. The reply
 * carries both the machine-readable identifier of the selected button
 * ({@link #selectedId()}), the human-readable label that was shown
 * ({@link #selectedDisplayText()}), and optional positional hints that are useful when
 * buttons are arranged in a list or across multiple carousel cards.
 *
 * <p>This message is contextual: like any other quoted reply it can carry a
 * {@link ContextInfo} pointing back to the originating template message.
 *
 * @implNote The WA Web generator {@code WAWebGenerateTemplateButtonReplyMessageProto}
 *           is a tiny factory that wraps an input {@code {contextInfo, json}} pair into
 *           {@code {templateButtonReplyMessage: {selectedId: json.selectedId,
 *           selectedIndex: json.selectedIndex,
 *           selectedCarouselCardIndex: json.selectedCarouselCardIndex,
 *           selectedDisplayText: json.body, contextInfo}}}. Note that the JS factory sources
 *           the display text from {@code json.body} rather than a field named
 *           {@code selectedDisplayText}; the proto field itself (index 2) keeps the
 *           {@code selectedDisplayText} name on the wire. Cobalt represents this structure
 *           statically: this protobuf message is the inner {@code {templateButtonReplyMessage:
 *           ...}} object and the surrounding wrapper is
 *           {@code MessageContainer.templateButtonReplyMessage}. Construction goes through
 *           the generated {@code TemplateButtonReplyMessageBuilder}, which is the direct
 *           analog of the JS factory call site.
 */
@ProtobufMessage(name = "Message.TemplateButtonReplyMessage")
@WhatsAppWebModule(moduleName = "WAWebGenerateTemplateButtonReplyMessageProto")
public final class TemplateButtonReplyMessage implements ContextualMessage {
    /**
     * The identifier of the button that the recipient selected, matching the {@code id}
     * field of the originating template button.
     *
     * @implNote The WA Web generator forwards {@code json.selectedId} onto this field
     *           verbatim.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    @WhatsAppWebExport(moduleName = "WAWebGenerateTemplateButtonReplyMessageProto", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    String selectedId;

    /**
     * The human-readable label shown on the selected button.
     *
     * @implNote The WA Web generator sources this field from {@code json.body} (not
     *           {@code json.selectedDisplayText}); the proto field itself keeps the
     *           {@code selectedDisplayText} name on the wire.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    @WhatsAppWebExport(moduleName = "WAWebGenerateTemplateButtonReplyMessageProto", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    String selectedDisplayText;

    /**
     * Contextual information that links this reply to the originating template message.
     *
     * @implNote The WA Web generator forwards its input {@code contextInfo} verbatim onto
     *           this field.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    @WhatsAppWebExport(moduleName = "WAWebGenerateTemplateButtonReplyMessageProto", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    ContextInfo contextInfo;

    /**
     * The zero-based index of the selected button within its parent list, when buttons are
     * ordered.
     *
     * @implNote The WA Web generator forwards {@code json.selectedIndex} onto this field
     *           verbatim.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.UINT32)
    @WhatsAppWebExport(moduleName = "WAWebGenerateTemplateButtonReplyMessageProto", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    Integer selectedIndex;

    /**
     * The zero-based index of the carousel card that contained the selected button, when the
     * reply originates from an {@link InteractiveMessage.CarouselMessage}.
     *
     * @implNote The WA Web generator forwards {@code json.selectedCarouselCardIndex} onto
     *           this field verbatim.
     */
    @ProtobufProperty(index = 5, type = ProtobufType.UINT32)
    @WhatsAppWebExport(moduleName = "WAWebGenerateTemplateButtonReplyMessageProto", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    Integer selectedCarouselCardIndex;


    /**
     * Constructs a new template button reply with the supplied selection metadata.
     *
     * @param selectedId                the identifier of the tapped button
     * @param selectedDisplayText       the label shown on the tapped button
     * @param contextInfo               the context linking this reply to the source message
     * @param selectedIndex             the button's position within its list, possibly {@code null}
     * @param selectedCarouselCardIndex the card index when the button is inside a carousel,
     *                                  possibly {@code null}
     */
    TemplateButtonReplyMessage(String selectedId, String selectedDisplayText, ContextInfo contextInfo, Integer selectedIndex, Integer selectedCarouselCardIndex) {
        this.selectedId = selectedId;
        this.selectedDisplayText = selectedDisplayText;
        this.contextInfo = contextInfo;
        this.selectedIndex = selectedIndex;
        this.selectedCarouselCardIndex = selectedCarouselCardIndex;
    }

    /**
     * Returns the identifier of the button that the recipient selected.
     *
     * @return an {@code Optional} containing the button identifier, or empty if not set
     */
    public Optional<String> selectedId() {
        return Optional.ofNullable(selectedId);
    }

    /**
     * Returns the human-readable label shown on the selected button.
     *
     * @return an {@code Optional} containing the display text, or empty if not set
     */
    public Optional<String> selectedDisplayText() {
        return Optional.ofNullable(selectedDisplayText);
    }

    /**
     * Returns the contextual information that links this reply back to the original template
     * message.
     *
     * @return an {@code Optional} containing the context, or empty if not set
     */
    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    /**
     * Returns the zero-based position of the selected button inside its parent list.
     *
     * @return an {@code OptionalInt} with the button index, or empty if not set
     */
    public OptionalInt selectedIndex() {
        return selectedIndex == null ? OptionalInt.empty() : OptionalInt.of(selectedIndex);
    }

    /**
     * Returns the zero-based index of the carousel card that contained the selected button.
     *
     * <p>Only present when the reply originates from an interactive carousel message.
     *
     * @return an {@code OptionalInt} with the carousel card index, or empty if not set
     */
    public OptionalInt selectedCarouselCardIndex() {
        return selectedCarouselCardIndex == null ? OptionalInt.empty() : OptionalInt.of(selectedCarouselCardIndex);
    }

    /**
     * Updates the identifier of the selected button.
     *
     * @param selectedId the new button identifier, or {@code null} to clear the field
     */
    public void setSelectedId(String selectedId) {
        this.selectedId = selectedId;
    }

    /**
     * Updates the display text shown on the selected button.
     *
     * @param selectedDisplayText the new display text, or {@code null} to clear the field
     */
    public void setSelectedDisplayText(String selectedDisplayText) {
        this.selectedDisplayText = selectedDisplayText;
    }

    /**
     * Updates the contextual information attached to this reply.
     *
     * @param contextInfo the new context, or {@code null} to clear the field
     */
    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    /**
     * Updates the zero-based position of the selected button.
     *
     * @param selectedIndex the new index, or {@code null} to clear the field
     */
    public void setSelectedIndex(Integer selectedIndex) {
        this.selectedIndex = selectedIndex;
    }

    /**
     * Updates the zero-based index of the carousel card that contained the selected button.
     *
     * @param selectedCarouselCardIndex the new carousel card index, or {@code null} to clear
     *                                  the field
     */
    public void setSelectedCarouselCardIndex(Integer selectedCarouselCardIndex) {
        this.selectedCarouselCardIndex = selectedCarouselCardIndex;
    }
}
