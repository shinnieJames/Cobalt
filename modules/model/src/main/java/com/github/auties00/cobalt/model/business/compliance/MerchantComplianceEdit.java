package com.github.auties00.cobalt.model.business.compliance;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;
import java.util.Optional;

/**
 * Partial merchant-compliance update used as the input to
 * {@code WhatsAppClient.editMerchantCompliance}. Fields not set are left
 * untouched on the published merchant-compliance disclosure.
 */
@ProtobufMessage
public final class MerchantComplianceEdit {
    /**
     * Display name of the registered legal entity, or {@code null} to
     * leave the field unchanged.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    final String entityName;

    /**
     * Wire-level entity type category, or {@code null} to leave the
     * field unchanged.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    final String entityType;

    /**
     * Free-form entity-type description used when {@link #entityType} is
     * set to a custom-bucket sentinel, or {@code null} to leave the
     * field unchanged.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    final String entityTypeCustom;

    /**
     * Whether the business is declared as registered. Defaults to
     * {@code false} when the model is built without setting this field.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
    final boolean registered;

    /**
     * Customer-care contact block, or {@code null} to leave the
     * existing block unchanged.
     */
    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    final BusinessContactDetails customerCareDetails;

    /**
     * Grievance-officer block, or {@code null} to leave the existing
     * block unchanged.
     */
    @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
    final BusinessGrievanceOfficerDetails grievanceOfficerDetails;

    /**
     * Constructs a new {@code MerchantComplianceEdit}.
     *
     * @param entityName              the legal-entity name, or {@code null}
     * @param entityType              the wire-level entity-type code, or {@code null}
     * @param entityTypeCustom        the custom entity-type description, or {@code null}
     * @param registered              whether the business is declared registered
     * @param customerCareDetails     the customer-care contact block, or {@code null}
     * @param grievanceOfficerDetails the grievance-officer block, or {@code null}
     */
    MerchantComplianceEdit(String entityName, String entityType, String entityTypeCustom,
                           boolean registered, BusinessContactDetails customerCareDetails,
                           BusinessGrievanceOfficerDetails grievanceOfficerDetails) {
        this.entityName = entityName;
        this.entityType = entityType;
        this.entityTypeCustom = entityTypeCustom;
        this.registered = registered;
        this.customerCareDetails = customerCareDetails;
        this.grievanceOfficerDetails = grievanceOfficerDetails;
    }

    /**
     * Returns the legal-entity name.
     *
     * @return an {@code Optional} carrying the entity name, or empty when unset
     */
    public Optional<String> entityName() {
        return Optional.ofNullable(entityName);
    }

    /**
     * Returns the wire-level entity-type code.
     *
     * @return an {@code Optional} carrying the entity type, or empty when unset
     */
    public Optional<String> entityType() {
        return Optional.ofNullable(entityType);
    }

    /**
     * Returns the free-form entity-type description.
     *
     * @return an {@code Optional} carrying the custom description, or empty when unset
     */
    public Optional<String> entityTypeCustom() {
        return Optional.ofNullable(entityTypeCustom);
    }

    /**
     * Returns whether the business is declared as registered.
     *
     * @return the registration flag
     */
    public boolean registered() {
        return registered;
    }

    /**
     * Returns the customer-care contact block.
     *
     * @return an {@code Optional} carrying the contact block, or empty when unset
     */
    public Optional<BusinessContactDetails> customerCareDetails() {
        return Optional.ofNullable(customerCareDetails);
    }

    /**
     * Returns the grievance-officer block.
     *
     * @return an {@code Optional} carrying the officer block, or empty when unset
     */
    public Optional<BusinessGrievanceOfficerDetails> grievanceOfficerDetails() {
        return Optional.ofNullable(grievanceOfficerDetails);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (MerchantComplianceEdit) obj;
        return Objects.equals(this.entityName, that.entityName) &&
                Objects.equals(this.entityType, that.entityType) &&
                Objects.equals(this.entityTypeCustom, that.entityTypeCustom) &&
                this.registered == that.registered &&
                Objects.equals(this.customerCareDetails, that.customerCareDetails) &&
                Objects.equals(this.grievanceOfficerDetails, that.grievanceOfficerDetails);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityName, entityType, entityTypeCustom, registered,
                customerCareDetails, grievanceOfficerDetails);
    }

    @Override
    public String toString() {
        return "MerchantComplianceEdit[" +
                "entityName=" + entityName + ", " +
                "entityType=" + entityType + ", " +
                "entityTypeCustom=" + entityTypeCustom + ", " +
                "registered=" + registered + ", " +
                "customerCareDetails=" + customerCareDetails + ", " +
                "grievanceOfficerDetails=" + grievanceOfficerDetails + ']';
    }
}
