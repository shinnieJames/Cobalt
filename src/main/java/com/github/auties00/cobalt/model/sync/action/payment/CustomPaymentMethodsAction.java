package com.github.auties00.cobalt.model.sync.action.payment;

import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncAction;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Collections;
import java.util.List;

@ProtobufMessage(name = "SyncActionValue.CustomPaymentMethodsAction")
public final class CustomPaymentMethodsAction implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "custom_payment_methods";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 7;

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
