package com.github.auties00.cobalt.model.message.interactive;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "ActionLink")
public final class InteractiveActionLink {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String url;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String buttonTitle;


    InteractiveActionLink(String url, String buttonTitle) {
        this.url = url;
        this.buttonTitle = buttonTitle;
    }

    public Optional<String> url() {
        return Optional.ofNullable(url);
    }

    public Optional<String> buttonTitle() {
        return Optional.ofNullable(buttonTitle);
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setButtonTitle(String buttonTitle) {
        this.buttonTitle = buttonTitle;
    }
}
