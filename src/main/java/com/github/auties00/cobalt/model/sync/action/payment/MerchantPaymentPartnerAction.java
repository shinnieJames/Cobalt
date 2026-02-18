package com.github.auties00.cobalt.model.sync.action.payment;

import com.github.auties00.cobalt.model.sync.SyncAction;

import java.util.Objects;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.MerchantPaymentPartnerAction")
public final class MerchantPaymentPartnerAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    Status status;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String country;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String gatewayName;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    String credentialId;


    MerchantPaymentPartnerAction(Status status, String country, String gatewayName, String credentialId) {
        this.status = Objects.requireNonNull(status);
        this.country = Objects.requireNonNull(country);
        this.gatewayName = gatewayName;
        this.credentialId = credentialId;
    }

    public Status status() {
        return status;
    }

    public String country() {
        return country;
    }

    public Optional<String> gatewayName() {
        return Optional.ofNullable(gatewayName);
    }

    public Optional<String> credentialId() {
        return Optional.ofNullable(credentialId);
    }

    public MerchantPaymentPartnerAction setStatus(Status status) {
        this.status = status;
        return this;
    }

    public MerchantPaymentPartnerAction setCountry(String country) {
        this.country = country;
        return this;
    }

    public MerchantPaymentPartnerAction setGatewayName(String gatewayName) {
        this.gatewayName = gatewayName;
        return this;
    }

    public MerchantPaymentPartnerAction setCredentialId(String credentialId) {
        this.credentialId = credentialId;
        return this;
    }

    @ProtobufEnum(name = "SyncActionValue.MerchantPaymentPartnerAction.Status")
    public static enum Status {
        ACTIVE(0),
        INACTIVE(1);

        Status(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
