package com.github.auties00.cobalt.model.privacy;


import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

import java.util.*;

/**
 * Enumerates the privacy settings that a WhatsApp user can configure on their account.
 *
 * <p>Each constant identifies a distinct piece of information or behaviour that can be
 * restricted on a per-audience basis. Not every setting supports every possible audience:
 * {@link #supportedValues()} returns the set of {@link PrivacySettingValue} constants that
 * are accepted by the server for a given setting, and {@link #isSupported(PrivacySettingValue)}
 * can be used to validate a value before sending it.
 *
 * <p>The textual identifier returned by {@link #data()} matches the wire format used by the
 * WhatsApp servers and can be looked up with {@link #of(String)}.
 */
@ProtobufEnum
public enum PrivacySettingType {
    /**
     * Controls who can see the timestamp of the last time the user was online.
     *
     * <p>Supports the {@link PrivacySettingValue#EVERYONE}, {@link PrivacySettingValue#CONTACTS},
     * {@link PrivacySettingValue#CONTACTS_EXCEPT} and {@link PrivacySettingValue#NOBODY}
     * audiences.
     */
    LAST_SEEN(0, "last", Set.of(PrivacySettingValue.EVERYONE, PrivacySettingValue.CONTACTS, PrivacySettingValue.CONTACTS_EXCEPT, PrivacySettingValue.NOBODY)),
    /**
     * Controls who can see whether the user is currently online.
     *
     * <p>Supports only {@link PrivacySettingValue#EVERYONE} and
     * {@link PrivacySettingValue#MATCH_LAST_SEEN}. The latter keeps the online indicator
     * consistent with the {@link #LAST_SEEN} setting.
     */
    ONLINE(1, "online", Set.of(PrivacySettingValue.EVERYONE, PrivacySettingValue.MATCH_LAST_SEEN)),
    /**
     * Controls who can see the user's profile picture.
     *
     * <p>Supports the {@link PrivacySettingValue#EVERYONE}, {@link PrivacySettingValue#CONTACTS},
     * {@link PrivacySettingValue#CONTACTS_EXCEPT} and {@link PrivacySettingValue#NOBODY}
     * audiences.
     */
    PROFILE_PIC(2, "profile", Set.of(PrivacySettingValue.EVERYONE, PrivacySettingValue.CONTACTS, PrivacySettingValue.CONTACTS_EXCEPT, PrivacySettingValue.NOBODY)),
    /**
     * Controls who can see the user's status updates.
     *
     * <p>Supports {@link PrivacySettingValue#CONTACTS}, {@link PrivacySettingValue#CONTACTS_EXCEPT},
     * {@link PrivacySettingValue#CONTACTS_ONLY} and {@link PrivacySettingValue#NOBODY}. Unlike
     * other settings, status updates can be restricted to an explicit allowlist of contacts
     * rather than only a blocklist.
     */
    STATUS(3, "status", Set.of(PrivacySettingValue.CONTACTS, PrivacySettingValue.CONTACTS_EXCEPT, PrivacySettingValue.CONTACTS_ONLY, PrivacySettingValue.NOBODY)),
    /**
     * Controls who can add the user to new group chats without an explicit invitation.
     *
     * <p>Supports {@link PrivacySettingValue#EVERYONE}, {@link PrivacySettingValue#CONTACTS}
     * and {@link PrivacySettingValue#CONTACTS_EXCEPT}. Users who are not in the allowed
     * audience must send an invitation link instead of adding the user directly.
     */
    ADD_ME_TO_GROUPS(4, "groupadd", Set.of(PrivacySettingValue.EVERYONE, PrivacySettingValue.CONTACTS, PrivacySettingValue.CONTACTS_EXCEPT)),
    /**
     * Controls whether read receipts are exchanged for one-to-one chats.
     *
     * <p>Supports only {@link PrivacySettingValue#EVERYONE} and
     * {@link PrivacySettingValue#NOBODY}. When disabled the user neither sends nor receives
     * read receipts in private conversations, while read receipts in group chats are always
     * exchanged regardless of this setting.
     */
    READ_RECEIPTS(5, "readreceipts", Set.of(PrivacySettingValue.EVERYONE, PrivacySettingValue.NOBODY)),
    /**
     * Controls who can add the user to ongoing voice or video calls.
     *
     * <p>Only {@link PrivacySettingValue#EVERYONE} is currently accepted for this setting.
     */
    CALL_ADD(6, "calladd", Set.of(PrivacySettingValue.EVERYONE));

    /**
     * The numeric identifier used for protobuf serialization.
     *
     * <p>Matches the index declared on the wire format of privacy related messages.
     */
    final int index;

    /**
     * The textual identifier used by the WhatsApp servers to refer to this setting.
     *
     * <p>Examples include {@code "last"} for {@link #LAST_SEEN} and {@code "groupadd"} for
     * {@link #ADD_ME_TO_GROUPS}. This value is returned by {@link #data()} and accepted by
     * {@link #of(String)}.
     */
    private final String data;

    /**
     * The set of audiences that are accepted by the server for this setting.
     *
     * <p>Returned as an unmodifiable view by {@link #supportedValues()}.
     */
    private final Set<PrivacySettingValue> values;

    /**
     * Creates a new privacy setting type constant.
     *
     * @param index  the protobuf index assigned to the constant
     * @param data   the textual identifier used by the server
     * @param values the set of audiences accepted for the constant
     */
    PrivacySettingType(@ProtobufEnumIndex int index, String data, Set<PrivacySettingValue> values) {
        this.index = index;
        this.data = data;
        this.values = values;
    }

    /**
     * Resolves a privacy setting type from the textual identifier used by the server.
     *
     * <p>The identifier is compared case-sensitively against the value returned by
     * {@link #data()} for each constant.
     *
     * @param id the identifier to resolve, for example {@code "last"} or {@code "groupadd"}
     * @return an {@link Optional} containing the matching constant, or empty if no constant
     *         has the given identifier
     */
    public static Optional<PrivacySettingType> of(String id) {
        return Arrays.stream(values())
                .filter(entry -> Objects.equals(entry.data(), id))
                .findFirst();
    }

    /**
     * Returns the set of audiences that can be configured for this setting.
     *
     * <p>The returned set is unmodifiable and can be used to populate a UI or to validate a
     * user selection before calling a setter on the WhatsApp API.
     *
     * @return the unmodifiable set of supported {@link PrivacySettingValue} constants
     */
    public Set<PrivacySettingValue> supportedValues() {
        return Collections.unmodifiableSet(values);
    }

    /**
     * Returns whether the given audience can be configured for this setting.
     *
     * @param value the audience to test
     * @return {@code true} if the server accepts {@code value} for this setting,
     *         {@code false} otherwise
     */
    public boolean isSupported(PrivacySettingValue value) {
        return values.contains(value);
    }

    /**
     * Returns the textual identifier used by the WhatsApp servers to refer to this setting.
     *
     * <p>This is the inverse of {@link #of(String)}.
     *
     * @return the server side identifier, never {@code null}
     */
    public String data() {
        return this.data;
    }
}
