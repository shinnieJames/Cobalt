package com.github.auties00.cobalt.model.sync.action.payment;

import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.PaymentInfoAction")
public final class PaymentInfoAction implements SyncAction {
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
