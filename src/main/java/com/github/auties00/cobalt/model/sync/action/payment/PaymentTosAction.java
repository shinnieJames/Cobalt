package com.github.auties00.cobalt.model.sync.action.payment;

import com.github.auties00.cobalt.model.sync.SyncAction;

import java.util.Objects;

@ProtobufMessage(name = "SyncActionValue.PaymentTosAction")
public final class PaymentTosAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    PaymentNotice paymentNotice;

    @ProtobufProperty(index = 2, type = ProtobufType.BOOL)
    Boolean accepted;


    PaymentTosAction(PaymentNotice paymentNotice, Boolean accepted) {
        this.paymentNotice = Objects.requireNonNull(paymentNotice);
        this.accepted = Objects.requireNonNull(accepted);
    }

    public PaymentNotice paymentNotice() {
        return paymentNotice;
    }

    public Boolean accepted() {
        return accepted;
    }

    public PaymentTosAction setPaymentNotice(PaymentNotice paymentNotice) {
        this.paymentNotice = paymentNotice;
        return this;
    }

    public PaymentTosAction setAccepted(Boolean accepted) {
        this.accepted = accepted;
        return this;
    }

    @ProtobufEnum(name = "SyncActionValue.PaymentTosAction.PaymentNotice")
    public static enum PaymentNotice {
        BR_PAY_PRIVACY_POLICY(0);

        PaymentNotice(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
