package com.github.auties00.cobalt.model.sync.action.payment;

import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncAction;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@ProtobufMessage(name = "SyncActionValue.CustomPaymentMethod")
public final class CustomPaymentMethod implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "custom_payment_method";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 1;

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
    String credentialId;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String country;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String type;

    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    List<CustomPaymentMethodMetadata> metadata;


    CustomPaymentMethod(String credentialId, String country, String type, List<CustomPaymentMethodMetadata> metadata) {
        this.credentialId = Objects.requireNonNull(credentialId);
        this.country = Objects.requireNonNull(country);
        this.type = Objects.requireNonNull(type);
        this.metadata = metadata;
    }

    public String credentialId() {
        return credentialId;
    }

    public String country() {
        return country;
    }

    public String type() {
        return type;
    }

    public List<CustomPaymentMethodMetadata> metadata() {
        return metadata == null ? List.of() : Collections.unmodifiableList(metadata);
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setMetadata(List<CustomPaymentMethodMetadata> metadata) {
        this.metadata = metadata;
    }
}
