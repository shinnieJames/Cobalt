package com.github.auties00.cobalt.model.message.interactive;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "TapLinkAction")
public final class TapLinkAction implements InteractiveAction {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String title;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String tapUrl;


    TapLinkAction(String title, String tapUrl) {
        this.title = title;
        this.tapUrl = tapUrl;
    }

    public Optional<String> title() {
        return Optional.ofNullable(title);
    }

    public Optional<String> tapUrl() {
        return Optional.ofNullable(tapUrl);
    }

    public TapLinkAction setTitle(String title) {
        this.title = title;
        return this;
    }

    public TapLinkAction setTapUrl(String tapUrl) {
        this.tapUrl = tapUrl;
        return this;
    }
}
