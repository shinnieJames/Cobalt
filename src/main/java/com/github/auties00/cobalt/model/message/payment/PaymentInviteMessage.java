package com.github.auties00.cobalt.model.message.payment;

import com.github.auties00.cobalt.model.message.Message;

import java.time.Instant;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.PaymentInviteMessage")
public final class PaymentInviteMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    ServiceType serviceType;

    @ProtobufProperty(index = 2, type = ProtobufType.INT64, mixins = InstantProtobufMixin.class)
    Instant expiryTimestamp;

    @ProtobufProperty(index = 3, type = ProtobufType.BOOL)
    Boolean incentiveEligible;


    PaymentInviteMessage(ServiceType serviceType, Instant expiryTimestamp, Boolean incentiveEligible) {
        this.serviceType = serviceType;
        this.expiryTimestamp = expiryTimestamp;
        this.incentiveEligible = incentiveEligible;
    }

    public Optional<ServiceType> serviceType() {
        return Optional.ofNullable(serviceType);
    }

    public Optional<Instant> expiryTimestamp() {
        return Optional.ofNullable(expiryTimestamp);
    }

    public boolean incentiveEligible() {
        return incentiveEligible != null && incentiveEligible;
    }

    public PaymentInviteMessage setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
        return this;
    }

    public PaymentInviteMessage setExpiryTimestamp(Instant expiryTimestamp) {
        this.expiryTimestamp = expiryTimestamp;
        return this;
    }

    public PaymentInviteMessage setIncentiveEligible(Boolean incentiveEligible) {
        this.incentiveEligible = incentiveEligible;
        return this;
    }

    @ProtobufEnum(name = "Message.PaymentInviteMessage.ServiceType")
    public static enum ServiceType {
        UNKNOWN(0),
        FBPAY(1),
        NOVI(2),
        UPI(3);

        ServiceType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
