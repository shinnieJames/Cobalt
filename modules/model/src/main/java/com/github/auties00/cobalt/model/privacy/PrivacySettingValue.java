package com.github.auties00.cobalt.model.privacy;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Enumerates the audiences that can be selected for a {@link PrivacySettingType}.
 *
 * <p>Each constant describes a group of users that is allowed to see or interact with a
 * piece of information controlled by a privacy setting. Not every value is valid for every
 * setting: {@link PrivacySettingType#supportedValues()} exposes the subset of values that
 * can be configured for a given setting.
 *
 * <p>The textual identifier returned by {@link #data()} matches the wire format used by the
 * WhatsApp servers and can be looked up with {@link #of(String)}.
 */
@ProtobufEnum
public enum PrivacySettingValue {
    /**
     * Any WhatsApp user, including strangers that are not in the address book.
     */
    EVERYONE(0, "all"),
    /**
     * Every contact that is saved in the user's address book.
     */
    CONTACTS(1, "contacts"),
    /**
     * Every contact in the address book except for an explicit blocklist.
     *
     * <p>When this value is used the associated blocklist of {@link com.github.auties00.cobalt.model.jid.Jid}
     * values is stored in the corresponding {@link PrivacySettingEntry#excluded()} list.
     */
    CONTACTS_EXCEPT(2, "contact_blacklist"),
    /**
     * An explicit allowlist of contacts.
     *
     * <p>Only the contacts listed in the corresponding {@link PrivacySettingEntry#excluded()}
     * list can see the information. This value is currently used for
     * {@link PrivacySettingType#STATUS}.
     */
    CONTACTS_ONLY(5, "contact_whitelist"),
    /**
     * No other user at all.
     */
    NOBODY(3, "none"),
    /**
     * Mirrors the audience configured for {@link PrivacySettingType#LAST_SEEN}.
     *
     * <p>Used exclusively by {@link PrivacySettingType#ONLINE} so that the online indicator
     * cannot be seen by users that are not allowed to see the last seen timestamp.
     */
    MATCH_LAST_SEEN(4, "match_last_seen");

    /**
     * The numeric identifier used for protobuf serialization.
     *
     * <p>Matches the index declared on the wire format of privacy related messages.
     */
    final int index;

    /**
     * The textual identifier used by the WhatsApp servers to refer to this audience.
     *
     * <p>Examples include {@code "all"} for {@link #EVERYONE} and {@code "contact_blacklist"}
     * for {@link #CONTACTS_EXCEPT}.
     */
    private final String data;

    /**
     * Creates a new privacy setting value constant.
     *
     * @param index the protobuf index assigned to the constant
     * @param data  the textual identifier used by the server
     */
    PrivacySettingValue(@ProtobufEnumIndex int index, String data) {
        this.index = index;
        this.data = data;
    }

    /**
     * Resolves a privacy setting value from the textual identifier used by the server.
     *
     * <p>The identifier is compared case-sensitively against the value returned by
     * {@link #data()} for each constant.
     *
     * @param id the identifier to resolve, for example {@code "all"} or {@code "contact_blacklist"}
     * @return an {@link Optional} containing the matching constant, or empty if no constant
     *         has the given identifier
     */
    public static Optional<PrivacySettingValue> of(String id) {
        return Arrays.stream(values())
                .filter(entry -> Objects.equals(entry.data(), id))
                .findFirst();
    }

    /**
     * Returns the textual identifier used by the WhatsApp servers to refer to this audience.
     *
     * <p>This is the inverse of {@link #of(String)}.
     *
     * @return the server side identifier, never {@code null}
     */
    public String data() {
        return this.data;
    }
}
