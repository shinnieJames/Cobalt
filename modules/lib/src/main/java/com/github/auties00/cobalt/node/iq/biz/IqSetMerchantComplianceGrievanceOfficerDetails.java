package com.github.auties00.cobalt.node.iq.biz;

import java.util.Objects;
import java.util.Optional;

/**
 * Typed grievance-officer block carried inside an
 * {@link IqSetMerchantComplianceRequest}.
 *
 * @apiNote
 * Use this block to set or update the grievance officer's display name
 * and contact triple (regulatory disclosure required for India sellers
 * by the Consumer Protection rules and similar markets); the name is
 * optional and an absent name is dropped from the wire stanza rather
 * than zeroed out.
 */
public final class IqSetMerchantComplianceGrievanceOfficerDetails {
    /**
     * The optional officer name stamped into the {@code <name/>}
     * grandchild.
     */
    private final String name;

    /**
     * The contact triple flattened into the {@code <email/>},
     * {@code <landline_number/>} and {@code <mobile_number/>}
     * grandchildren.
     */
    private final IqSetMerchantComplianceContactDetails contact;

    /**
     * Constructs a block.
     *
     * @apiNote
     * Pass the officer's contact triple; the name is optional and may
     * be left {@code null} when the merchant only wants to update the
     * contact channels.
     *
     * @param name    the name; may be {@code null}
     * @param contact the contact triple; never {@code null}
     * @throws NullPointerException if {@code contact} is {@code null}
     */
    public IqSetMerchantComplianceGrievanceOfficerDetails(String name, IqSetMerchantComplianceContactDetails contact) {
        this.name = name;
        this.contact = Objects.requireNonNull(contact, "contact cannot be null");
    }

    /**
     * Returns the officer name.
     *
     * @apiNote
     * Use this getter to read back the officer name the mutation will
     * stamp; an empty optional means the field is dropped from the
     * wire stanza.
     *
     * @return an {@link Optional} carrying the name
     */
    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    /**
     * Returns the contact triple.
     *
     * @apiNote
     * Use this getter to read back the officer's contact channels;
     * the triple itself follows the same field-by-field optionality.
     *
     * @return the triple; never {@code null}
     */
    public IqSetMerchantComplianceContactDetails contact() {
        return contact;
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
        var that = (IqSetMerchantComplianceGrievanceOfficerDetails) obj;
        return Objects.equals(this.name, that.name)
                && Objects.equals(this.contact, that.contact);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(name, contact);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "IqSetMerchantComplianceGrievanceOfficerDetails[name=" + name
                + ", contact=" + contact + ']';
    }
}
