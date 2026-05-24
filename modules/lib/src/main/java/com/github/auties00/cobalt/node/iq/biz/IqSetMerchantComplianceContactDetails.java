package com.github.auties00.cobalt.node.iq.biz;

import java.util.Objects;
import java.util.Optional;

/**
 * Typed customer-care or grievance-officer contact triple carried inside
 * an {@link IqSetMerchantComplianceRequest}.
 *
 * @apiNote
 * Use this triple to set or update the contact email, landline and
 * mobile of the merchant's customer-care surface (regulatory-compliance
 * disclosure required in India and other markets); every field is
 * independently optional and an absent field is dropped from the wire
 * stanza rather than zeroed out.
 */
public final class IqSetMerchantComplianceContactDetails {
    /**
     * The optional contact email stamped into the {@code <email/>}
     * grandchild.
     */
    private final String email;

    /**
     * The optional landline phone number stamped into the
     * {@code <landline_number/>} grandchild.
     */
    private final String landlineNumber;

    /**
     * The optional mobile phone number stamped into the
     * {@code <mobile_number/>} grandchild.
     */
    private final String mobileNumber;

    /**
     * Constructs a triple.
     *
     * @apiNote
     * Pass each field that should appear in the mutation; pass
     * {@code null} for any field that should remain unchanged on the
     * merchant's compliance bundle.
     *
     * @param email          the email; may be {@code null}
     * @param landlineNumber the landline; may be {@code null}
     * @param mobileNumber   the mobile; may be {@code null}
     */
    public IqSetMerchantComplianceContactDetails(String email, String landlineNumber, String mobileNumber) {
        this.email = email;
        this.landlineNumber = landlineNumber;
        this.mobileNumber = mobileNumber;
    }

    /**
     * Returns the contact email.
     *
     * @apiNote
     * Use this getter to read back the email the mutation will stamp;
     * an empty optional means the field is dropped from the wire
     * stanza.
     *
     * @return an {@link Optional} carrying the email
     */
    public Optional<String> email() {
        return Optional.ofNullable(email);
    }

    /**
     * Returns the landline phone number.
     *
     * @apiNote
     * Use this getter to read back the landline the mutation will
     * stamp; an empty optional means the field is dropped from the
     * wire stanza.
     *
     * @return an {@link Optional} carrying the landline
     */
    public Optional<String> landlineNumber() {
        return Optional.ofNullable(landlineNumber);
    }

    /**
     * Returns the mobile phone number.
     *
     * @apiNote
     * Use this getter to read back the mobile the mutation will
     * stamp; an empty optional means the field is dropped from the
     * wire stanza.
     *
     * @return an {@link Optional} carrying the mobile
     */
    public Optional<String> mobileNumber() {
        return Optional.ofNullable(mobileNumber);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqSetMerchantComplianceContactDetails) obj;
        return Objects.equals(this.email, that.email)
                && Objects.equals(this.landlineNumber, that.landlineNumber)
                && Objects.equals(this.mobileNumber, that.mobileNumber);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(email, landlineNumber, mobileNumber);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "IqSetMerchantComplianceContactDetails[email=" + email
                + ", landlineNumber=" + landlineNumber
                + ", mobileNumber=" + mobileNumber + ']';
    }
}
