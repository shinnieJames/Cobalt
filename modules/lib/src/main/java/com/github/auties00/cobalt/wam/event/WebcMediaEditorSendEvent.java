package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;

import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebWebcMediaEditorSendWamEvent")
@WamEvent(id = 2890)
public interface WebcMediaEditorSendEvent extends WamEventSpec {
    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt blurImageCount();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt editedImageCount();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt emojiLayerCount();

    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt imageCount();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt paintedImageCount();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt stickerLayerCount();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt textLayerCount();
}
