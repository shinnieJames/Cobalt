package com.github.auties00.cobalt.model.sync.action.payment;

import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.PaymentInfoAction")
public final class PaymentInfoAction implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "payment_info";

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


    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String cpi;


    PaymentInfoAction(String cpi) {
        this.cpi = cpi;
    }

    public Optional<String> cpi() {
        return Optional.ofNullable(cpi);
    }

    public PaymentInfoAction setCpi(String cpi) {
        this.cpi = cpi;
        return this;
    }
}
