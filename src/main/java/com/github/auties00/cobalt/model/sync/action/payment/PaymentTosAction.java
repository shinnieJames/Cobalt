package com.github.auties00.cobalt.model.sync.action.payment;

import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;

@ProtobufMessage(name = "SyncActionValue.PaymentTosAction")
public final class PaymentTosAction implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "payment_tos";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 7;

    /**
     * Canonical WhatsApp Web collection name for this action type.
     */
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR_LOW;

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

    public void setPaymentNotice(PaymentNotice paymentNotice) {
        this.paymentNotice = paymentNotice;
    }

    public void setAccepted(Boolean accepted) {
        this.accepted = accepted;
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
