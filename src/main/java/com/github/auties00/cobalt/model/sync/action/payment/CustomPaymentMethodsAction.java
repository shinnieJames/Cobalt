package com.github.auties00.cobalt.model.sync.action.payment;

import com.github.auties00.cobalt.model.sync.SyncAction;

import java.util.Collections;
import java.util.List;

@ProtobufMessage(name = "SyncActionValue.CustomPaymentMethodsAction")
public final class CustomPaymentMethodsAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    List<CustomPaymentMethod> customPaymentMethods;


    CustomPaymentMethodsAction(List<CustomPaymentMethod> customPaymentMethods) {
        this.customPaymentMethods = customPaymentMethods;
    }

    public List<CustomPaymentMethod> customPaymentMethods() {
        return customPaymentMethods == null ? List.of() : Collections.unmodifiableList(customPaymentMethods);
    }

    public CustomPaymentMethodsAction setCustomPaymentMethods(List<CustomPaymentMethod> customPaymentMethods) {
        this.customPaymentMethods = customPaymentMethods;
        return this;
    }
}
