package com.github.auties00.cobalt.model.sync.action.payment;

import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;

import java.util.Objects;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.MerchantPaymentPartnerAction")
public final class MerchantPaymentPartnerAction implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "merchant_payment_partner";

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

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public void setGatewayName(String gatewayName) {
        this.gatewayName = gatewayName;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
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
