package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.LabelOperations;
import com.github.auties00.cobalt.wam.type.LabelTargets;
import com.github.auties00.cobalt.wam.type.LastMessageDirection;

import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebLabelEventWamEvent")
@WamEvent(id = 1422)
public interface LabelEventEvent extends WamEventSpec {
    @WamProperty(index = 9, type = WamType.STRING)
    Optional<String> customLabelTitle();

    @WamProperty(index = 10, type = WamType.STRING)
    Optional<String> entryPointConversionSource();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt itemsLabeledCount();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt labelCount();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<LabelOperations> labelOperation();

    @WamProperty(index = 6, type = WamType.STRING)
    Optional<String> labelOperationEntryPoint();

    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<LabelTargets> labelTarget();

    @WamProperty(index = 14, type = WamType.ENUM)
    Optional<LastMessageDirection> lastMessageDirection();

    @WamProperty(index = 15, type = WamType.INTEGER)
    OptionalInt messageDepth();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt predefinedLabelNumber();

    @WamProperty(index = 11, type = WamType.STRING)
    Optional<String> threadCreationDate();

    @WamProperty(index = 12, type = WamType.STRING)
    Optional<String> threadId();

    @WamProperty(index = 13, type = WamType.STRING)
    Optional<String> threadIdHmac();
}
