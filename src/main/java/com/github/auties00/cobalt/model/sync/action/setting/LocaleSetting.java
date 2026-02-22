package com.github.auties00.cobalt.model.sync.action.setting;

import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.LocaleSetting")
public final class LocaleSetting implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "setting_locale";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 3;

    /**
     * {@inheritDoc}
     */
    @Override
    public String actionName() {
        return ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int actionVersion() {
        return ACTION_VERSION;
    }

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
