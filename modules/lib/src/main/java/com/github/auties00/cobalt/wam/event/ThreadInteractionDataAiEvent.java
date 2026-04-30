package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.AiChatOriginsType;

import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebThreadInteractionDataAiWamEvent")
@WamEvent(id = 6410)
public interface ThreadInteractionDataAiEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<AiChatOriginsType> aiChatOrigins();

    @WamProperty(index = 29, type = WamType.STRING)
    Optional<String> aiDiscoveryTab();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt bottomSheetAnimatedSent();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt bottomSheetEditedAnimatedSent();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt bottomSheetEditedSent();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt bottomSheetImagesGenerated();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt bottomSheetMemuInitiated();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt bottomSheetMemuMessagesSent();

    @WamProperty(index = 8, type = WamType.INTEGER)
    OptionalInt bottomSheetMessagesSent();

    @WamProperty(index = 9, type = WamType.INTEGER)
    OptionalInt bottomSheetPromptsInitiated();

    @WamProperty(index = 10, type = WamType.INTEGER)
    OptionalInt bottomSheetRegeneratedSent();

    @WamProperty(index = 11, type = WamType.INTEGER)
    OptionalInt commandSheetShow();

    @WamProperty(index = 14, type = WamType.INTEGER)
    OptionalInt imagineCommandClick();

    @WamProperty(index = 15, type = WamType.INTEGER)
    OptionalInt imagineMeMessagesSent();

    @WamProperty(index = 16, type = WamType.INTEGER)
    OptionalInt imagineMePromptsInitiatedCount();

    @WamProperty(index = 19, type = WamType.INTEGER)
    OptionalInt metaAiMentionClick();

    @WamProperty(index = 20, type = WamType.INTEGER)
    OptionalInt metaAiMentionShow();

    @WamProperty(index = 21, type = WamType.INTEGER)
    OptionalInt suggestionPromptsClick();

    @WamProperty(index = 22, type = WamType.INTEGER)
    OptionalInt suggestionPromptsShow();

    @WamProperty(index = 28, type = WamType.STRING)
    Optional<String> threadCreationDate();

    @WamProperty(index = 17, type = WamType.STRING)
    Optional<String> threadDs();

    @WamProperty(index = 26, type = WamType.STRING)
    Optional<String> threadId();

    @WamProperty(index = 27, type = WamType.STRING)
    Optional<String> threadIdByLid();

    @WamProperty(index = 24, type = WamType.INTEGER)
    OptionalInt totalMessageFromAgentCnt();

    @WamProperty(index = 25, type = WamType.INTEGER)
    OptionalInt totalMessageToAgentCnt();
}
