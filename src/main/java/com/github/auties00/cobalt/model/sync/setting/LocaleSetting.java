package com.github.auties00.cobalt.model.sync.setting;

import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.LocaleSetting")
public final class LocaleSetting implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String locale;


    LocaleSetting(String locale) {
        this.locale = locale;
    }

    public Optional<String> locale() {
        return Optional.ofNullable(locale);
    }

    public LocaleSetting setLocale(String locale) {
        this.locale = locale;
        return this;
    }
}
