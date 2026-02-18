package com.github.auties00.cobalt.model.sync.action.payment;

import com.github.auties00.cobalt.model.sync.SyncAction;

import java.util.Objects;

@ProtobufMessage(name = "SyncActionValue.CustomPaymentMethodMetadata")
public final class CustomPaymentMethodMetadata implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String key;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String value;


    CustomPaymentMethodMetadata(String key, String value) {
        this.key = Objects.requireNonNull(key);
        this.value = Objects.requireNonNull(value);
    }

    public String key() {
        return key;
    }

    public String value() {
        return value;
    }

    public CustomPaymentMethodMetadata setKey(String key) {
        this.key = key;
        return this;
    }

    public CustomPaymentMethodMetadata setValue(String value) {
        this.value = value;
        return this;
    }
}
