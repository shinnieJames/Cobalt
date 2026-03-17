package com.github.auties00.cobalt.model.contact;

import com.github.auties00.cobalt.model.jid.Jid;
import ezvcard.Ezvcard;
import ezvcard.VCard;
import ezvcard.VCardVersion;
import ezvcard.property.Telephone;
import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufSerializer;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * A representation of a contact's vCard (Virtual Contact File) as used in WhatsApp
 * contact messages.
 *
 * <p>When a user shares a contact through WhatsApp, the contact information is encoded
 * as a vCard string in the {@code contactMessage.vcard} protobuf field. This sealed
 * interface provides two representations of that data: {@link Parsed}, which breaks the
 * vCard into structured fields (name, phone numbers, business name), and {@link Raw},
 * which preserves the original vCard string verbatim when parsing fails or the vCard
 * library is unavailable.
 *
 * <p>The vCard format follows the standard RFC 6350 specification. WhatsApp extends
 * the standard with custom properties: {@code X-WA-BIZ-NAME} stores the business
 * display name, and the {@code WAID} parameter on telephone entries stores the
 * WhatsApp user identifier (phone number without the leading {@code +}).
 *
 * <p>Instances are serialized to and deserialized from the protobuf wire format as
 * plain {@code String} values using the {@link ProtobufSerializer} and
 * {@link ProtobufDeserializer} annotations.
 *
 * @see com.github.auties00.cobalt.model.message.contact.ContactMessage
 * @see com.github.auties00.cobalt.model.message.contact.ContactsArrayMessage
 */
public sealed interface ContactCard {
    /**
     * The vCard extended property name used by WhatsApp to store a business's
     * display name.
     */
    String BUSINESS_NAME_VCARD_PROPERTY = "X-WA-BIZ-NAME";

    /**
     * The vCard parameter name used by WhatsApp to store the phone number's
     * WhatsApp user identifier on {@code TEL} entries.
     */
    String PHONE_NUMBER_VCARD_PROPERTY = "WAID";

    /**
     * The default telephone type assigned to phone numbers when no explicit type
     * is specified. Corresponds to the vCard {@code CELL} type.
     */
    String DEFAULT_NUMBER_VCARD_TYPE = "CELL";

    /**
     * Parses a vCard string into a structured {@link Parsed} representation.
     *
     * <p>The parsing extracts the formatted name, telephone numbers (filtered to
     * entries that have both a recognized type and a {@code WAID} parameter), and
     * the optional {@code X-WA-BIZ-NAME} extended property. If parsing fails for
     * any reason (e.g. malformed vCard, missing dependency), a {@link Raw}
     * representation preserving the original string is returned instead.
     *
     * @param vcard the vCard string to parse, or {@code null}
     * @return a {@code Parsed} instance if parsing succeeds, a {@code Raw} instance
     *         if parsing fails, or {@code null} if {@code vcard} is {@code null}
     */
    @ProtobufDeserializer
    static ContactCard of(String vcard) {
        try {
            if(vcard == null) {
                return null;
            }

            var parsed = Ezvcard.parse(vcard).first();
            var version = Objects.requireNonNullElse(parsed.getVersion().getVersion(), VCardVersion.V3_0.getVersion());
            var name = parsed.getFormattedName().getValue();
            var phoneNumbers = parsed.getTelephoneNumbers()
                    .stream()
                    .filter(ContactCard::isValidPhoneNumber)
                    .collect(Collectors.toUnmodifiableMap(ContactCard::getPhoneType, ContactCard::getPhoneValue, ContactCard::joinPhoneNumbers));
            var businessName = parsed.getExtendedProperty(BUSINESS_NAME_VCARD_PROPERTY);
            return new Parsed(version, name, phoneNumbers, businessName != null ? businessName.getValue() : null);
        } catch (Throwable ignored) {
            return new Raw(vcard);
        }
    }

    /**
     * Creates a new {@link Parsed} contact card with the given name and phone number,
     * using the default vCard version ({@code 3.0}) and the default phone type
     * ({@code CELL}).
     *
     * @param name        the display name of the contact, or {@code null}
     * @param phoneNumber the non-{@code null} phone number JID of the contact
     * @return a new {@code Parsed} contact card
     */
    static ContactCard of(String name, Jid phoneNumber) {
        return of(name, phoneNumber, null);
    }

    /**
     * Creates a new {@link Parsed} contact card with the given name, phone number,
     * and optional business name, using the default vCard version ({@code 3.0}) and
     * the default phone type ({@code CELL}).
     *
     * @param name         the display name of the contact, or {@code null}
     * @param phoneNumber  the non-{@code null} phone number JID of the contact
     * @param businessName the business display name, or {@code null}
     * @return a new {@code Parsed} contact card
     */
    static ContactCard of(String name, Jid phoneNumber, String businessName) {
        return new Parsed(
                VCardVersion.V3_0.getVersion(),
                name,
                Map.of(DEFAULT_NUMBER_VCARD_TYPE, List.of(Objects.requireNonNull(phoneNumber))),
                businessName
        );
    }

    /**
     * Returns whether the given telephone entry has both a recognized type and a
     * {@code WAID} parameter identifying the WhatsApp user.
     *
     * @param entry the telephone property to validate
     * @return {@code true} if the entry has a type and a WAID parameter
     */
    private static boolean isValidPhoneNumber(Telephone entry) {
        return getPhoneType(entry) != null && entry.getParameter(PHONE_NUMBER_VCARD_PROPERTY) != null;
    }

    /**
     * Extracts the telephone type from a vCard telephone entry.
     *
     * @param entry the telephone property
     * @return the type string (e.g. {@code "CELL"}, {@code "HOME"}), or {@code null}
     */
    private static String getPhoneType(Telephone entry) {
        return entry.getParameters().getType();
    }

    /**
     * Extracts the WhatsApp JID from a vCard telephone entry's {@code WAID}
     * parameter.
     *
     * @param entry the telephone property
     * @return a singleton list containing the JID derived from the WAID value
     */
    private static List<Jid> getPhoneValue(Telephone entry) {
        return List.of(Jid.of(entry.getParameter(PHONE_NUMBER_VCARD_PROPERTY)));
    }

    /**
     * Merges two phone number lists into a single unmodifiable list.
     *
     * @param first  the first list of JIDs
     * @param second the second list of JIDs
     * @return a combined unmodifiable list containing all JIDs from both lists
     */
    private static List<Jid> joinPhoneNumbers(List<Jid> first, List<Jid> second) {
        return Stream.of(first, second).flatMap(Collection::stream).toList();
    }

    /**
     * Serializes this contact card to its vCard string representation.
     *
     * @return the vCard string
     */
    @ProtobufSerializer
    String toVcard();

    /**
     * A structured representation of a parsed vCard, providing typed access to the
     * contact's name, phone numbers, version, and optional business name.
     *
     * <p>Phone numbers are organized by their vCard type (e.g. {@code "CELL"},
     * {@code "HOME"}, {@code "WORK"}) and mapped to their corresponding WhatsApp
     * {@link Jid} values extracted from the {@code WAID} parameter.
     *
     * <p>This class can be serialized back to a valid vCard string via
     * {@link #toVcard()}.
     */
    final class Parsed implements ContactCard {
        /**
         * The vCard version string (e.g. {@code "3.0"}, {@code "4.0"}).
         */
        String version;

        /**
         * The formatted display name from the vCard's {@code FN} property,
         * or {@code null} if not present.
         */
        String name;

        /**
         * The phone numbers associated with this contact, grouped by their vCard
         * telephone type (e.g. {@code "CELL"}, {@code "HOME"}). Each type maps to
         * an unmodifiable list of {@link Jid} values derived from the {@code WAID}
         * parameter on the corresponding {@code TEL} entries.
         */
        Map<String, List<Jid>> phoneNumbers;

        /**
         * The business display name from the {@code X-WA-BIZ-NAME} extended vCard
         * property, or {@code null} if the contact is not a business account.
         */
        String businessName;

        /**
         * Constructs a new parsed contact card with the given values.
         *
         * @param version       the vCard version string
         * @param name          the formatted display name, or {@code null}
         * @param phoneNumbers  the phone numbers grouped by type
         * @param businessName  the business display name, or {@code null}
         */
        Parsed(String version, String name, Map<String, List<Jid>> phoneNumbers, String businessName) {
            this.version = version;
            this.name = name;
            this.phoneNumbers = phoneNumbers;
            this.businessName = businessName;
        }

        /**
         * Returns the vCard version string.
         *
         * @return the version (e.g. {@code "3.0"})
         */
        public String version() {
            return version;
        }

        /**
         * Sets the vCard version string.
         *
         * @param version the version to set
         * @return this parsed contact card instance
         */
        public void setVersion(String version) {
            this.version = version;
    }

        /**
         * Returns the formatted display name from the vCard.
         *
         * @return an {@code Optional} containing the name, or empty if not present
         */
        public Optional<String> name() {
            return Optional.ofNullable(name);
        }

        /**
         * Sets the formatted display name for this contact card.
         *
         * @param name the name to set, or {@code null} to clear
         * @return this parsed contact card instance
         */
        public void setName(String name) {
            this.name = name;
    }

        /**
         * Returns the business display name from the {@code X-WA-BIZ-NAME} property.
         *
         * @return an {@code Optional} containing the business name, or empty if the
         *         contact is not a business account
         */
        public Optional<String> businessName() {
            return Optional.ofNullable(businessName);
        }

        /**
         * Sets the business display name for this contact card.
         *
         * @param businessName the business name to set, or {@code null} to clear
         * @return this parsed contact card instance
         */
        public void setBusinessName(String businessName) {
            this.businessName = businessName;
    }

        /**
         * Returns the phone numbers associated with the given vCard telephone type.
         *
         * @param type the telephone type (e.g. {@code "CELL"}, {@code "HOME"})
         * @return an unmodifiable list of JIDs for the given type, or an empty list
         *         if no numbers are registered under that type
         */
        public List<Jid> phoneNumbers(String type) {
            return phoneNumbers.getOrDefault(type, List.of());
        }

        /**
         * Returns the phone numbers associated with the default telephone type
         * ({@code CELL}).
         *
         * @return an unmodifiable list of JIDs, or an empty list if no cell numbers
         *         are present
         */
        public List<Jid> phoneNumbers() {
            return Objects.requireNonNullElseGet(phoneNumbers.get(DEFAULT_NUMBER_VCARD_TYPE), List::of);
        }

        /**
         * Adds a phone number under the default telephone type ({@code CELL}).
         *
         * @param contact the non-{@code null} JID to add
         */
        public void addPhoneNumber(Jid contact) {
            addPhoneNumber(DEFAULT_NUMBER_VCARD_TYPE, contact);
        }

        /**
         * Adds a phone number under the specified telephone type.
         *
         * @param category the telephone type (e.g. {@code "CELL"}, {@code "HOME"})
         * @param contact  the non-{@code null} JID to add
         */
        public void addPhoneNumber(String category, Jid contact) {
            var oldValue = phoneNumbers.get(category);
            if (oldValue == null) {
                phoneNumbers.put(category, List.of(contact));
                return;
            }

            var values = new ArrayList<>(oldValue);
            values.add(contact);
            phoneNumbers.put(category, Collections.unmodifiableList(values));
        }

        /**
         * Serializes this parsed contact card to a valid vCard string. The output
         * includes the version, formatted name, telephone entries with their
         * {@code WAID} parameters, and the {@code X-WA-BIZ-NAME} property if a
         * business name is present.
         *
         * @return a non-{@code null} vCard string
         */
        @Override
        @ProtobufSerializer
        public String toVcard() {
            var vcard = new VCard();
            vcard.setVersion(VCardVersion.valueOfByStr(version()));
            vcard.setFormattedName(name);
            phoneNumbers.forEach((type, contacts) -> {
                for(var contact : contacts) {
                    contact.toPhoneNumber().ifPresent(phoneNumber -> {
                        var telephone = new Telephone(phoneNumber);
                        telephone.getParameters().setType(type);
                        telephone.getParameters().put(PHONE_NUMBER_VCARD_PROPERTY, contact.user());
                        vcard.addTelephoneNumber(telephone);
                    });
                }
            });
            if(businessName != null) {
                vcard.addExtendedProperty(BUSINESS_NAME_VCARD_PROPERTY, businessName);
            }
            return Ezvcard.write(vcard)
                    .go();
        }

        /**
         * Returns whether this parsed contact card is equal to the given object.
         * Two parsed contact cards are considered equal if they have the same
         * version, name, phone numbers, and business name.
         *
         * @param o the object to compare with
         * @return {@code true} if the other object is a {@code Parsed} instance
         *         with identical field values
         */
        @Override
        public boolean equals(Object o) {
            return o instanceof Parsed parsed
                    && Objects.equals(version, parsed.version)
                    && Objects.equals(name, parsed.name)
                    && Objects.equals(phoneNumbers, parsed.phoneNumbers)
                    && Objects.equals(businessName, parsed.businessName);
        }

        /**
         * Returns a hash code based on the version, name, phone numbers, and
         * business name fields.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(version, name, phoneNumbers, businessName);
        }

        /**
         * Returns a string representation of this parsed contact card, including
         * all field values.
         *
         * @return a descriptive string
         */
        @Override
        public String toString() {
            return "ContactCard[" +
                    "version=" + version + ", " +
                    "name=" + name + ", " +
                    "phoneNumbers=" + phoneNumbers + ", " +
                    "businessName=" + businessName + ']';
        }
    }

    /**
     * A raw, unparsed representation of a vCard string. This variant is used as a
     * fallback when the vCard string cannot be parsed into a {@link Parsed} instance,
     * either because the vCard format is malformed or because the parsing library is
     * unavailable.
     *
     * <p>The original vCard string is preserved verbatim and returned by
     * {@link #toVcard()}.
     */
    final class Raw implements ContactCard {
        /**
         * The original, unparsed vCard string.
         */
        String toVcard;

        /**
         * Constructs a new raw contact card wrapping the given vCard string.
         *
         * @param toVcard the raw vCard string
         */
        Raw(String toVcard) {
            this.toVcard = toVcard;
        }

        /**
         * Returns the original, unparsed vCard string.
         *
         * @return the raw vCard string
         */
        @Override
        @ProtobufSerializer
        public String toVcard() {
            return toVcard;
        }

        /**
         * Returns whether this raw contact card is equal to the given object. Two
         * raw contact cards are considered equal if they wrap the same vCard string.
         *
         * @param o the object to compare with
         * @return {@code true} if the other object is a {@code Raw} instance with
         *         the same vCard string
         */
        @Override
        public boolean equals(Object o) {
            return o instanceof Raw raw
                    && Objects.equals(toVcard, raw.toVcard);
        }

        /**
         * Returns a hash code based on the raw vCard string.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hashCode(toVcard);
        }

        /**
         * Returns a string representation of this raw contact card.
         *
         * @return a descriptive string including the raw vCard content
         */
        @Override
        public String toString() {
            return "Raw[" +
                    "toVcard=" + toVcard + ']';
        }
    }
}
