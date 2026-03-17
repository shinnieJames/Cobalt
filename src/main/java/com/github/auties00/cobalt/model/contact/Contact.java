package com.github.auties00.cobalt.model.contact;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidProvider;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A model representing a WhatsApp contact.
 *
 * <p>Each contact is uniquely identified by a phone-number-based {@link Jid} and may
 * optionally be associated with a LID (Linked Identifier) as part of the WhatsApp LID
 * migration. A contact carries three independent name fields that originate from different
 * sources: the {@linkplain #chosenName() chosen name} (also known as the "push name") is
 * the display name set by the contact on their own WhatsApp profile, the
 * {@linkplain #fullName() full name} is the name stored in the local device's address
 * book, and the {@linkplain #shortName() short name} is a shortened form of the address
 * book name (typically the first word).
 *
 * <p>Presence information ({@linkplain #lastKnownPresence() online status},
 * {@linkplain #lastSeen() last seen timestamp}) is maintained at the individual contact
 * level. This presence reflects only the 1:1 conversation state; for a contact's
 * presence within a group chat, use
 * {@link Chat#getPresence(JidProvider)} instead. By default, the server does not send
 * presence updates for every contact. To receive real-time updates, call
 * {@link WhatsAppClient#subscribeToPresence(JidProvider)}.
 *
 * <p>This class is a local model only. Modifying its fields does not send any request
 * to the WhatsApp servers; it simply reflects the locally cached state.
 *
 * @see ContactStatus
 * @see ContactCard
 */
@ProtobufMessage
public final class Contact implements JidProvider {
    /**
     * The non-{@code null} phone-number-based JID that uniquely identifies this contact.
     * In the WhatsApp Web contact database, this corresponds to the primary key
     * ({@code id}) column. The JID encodes the contact's phone number and server
     * information.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    final Jid jid;

    /**
     * The display name that this contact has chosen for their own WhatsApp profile,
     * commonly referred to as the "push name". This value is set by the contact
     * themselves during registration or through their profile settings. In the
     * WhatsApp Web contact database, this corresponds to the {@code pushname} column.
     * Although a push name is required at registration time, it can be removed later,
     * making this field nullable.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String chosenName;

    /**
     * The full name associated with this contact in the local device's address book.
     * In the WhatsApp Web contact database, this corresponds to the {@code name}
     * column. This value is {@code null} if the contact is not saved in the address
     * book.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String fullName;

    /**
     * A shortened form of the address-book name for this contact, typically the first
     * alphabetic word of the {@linkplain #fullName() full name}. In the WhatsApp Web
     * contact database, this corresponds to the {@code shortName} column. This value
     * is {@code null} if no full name is available or if the first word does not start
     * with an alphabetic character.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    String shortName;

    /**
     * The last known presence state of this contact in the 1:1 conversation. This
     * field tracks whether the contact is online, offline, typing, or recording audio.
     * It is not affected by the contact's activity in group chats. Defaults to
     * {@link ContactStatus#UNAVAILABLE} when no presence data has been received.
     */
    @ProtobufProperty(index = 5, type = ProtobufType.ENUM)
    ContactStatus lastKnownPresence;

    /**
     * The epoch-second timestamp of when this contact was last seen online, or
     * {@code null} if the information is unavailable. A contact may hide their
     * last-seen timestamp through their privacy settings, in which case this field
     * remains {@code null}. In the WhatsApp Web presence model, this corresponds
     * to the {@code t} field on the chatstate, and it is only shown when the
     * chatstate's {@code deny} flag is not set.
     */
    @ProtobufProperty(index = 6, type = ProtobufType.UINT64)
    Long lastSeenSeconds;

    /**
     * Whether this contact has been blocked by the current user. Blocked contacts
     * cannot send messages to the user and do not receive presence updates.
     */
    @ProtobufProperty(index = 7, type = ProtobufType.BOOL)
    boolean blocked;

    /**
     * Whether the status updates (stories) posted by this contact are muted. When
     * muted, the contact's status updates are hidden from the status tab but the
     * contact can still send and receive messages normally.
     */
    @ProtobufProperty(index = 8, type = ProtobufType.BOOL)
    boolean statusMuted;

    /**
     * The LID (Linked Identifier) associated with this contact, used as part of the
     * WhatsApp LID migration. The LID provides an alternative, stable identifier that
     * maps to the contact's phone-number-based JID. Once a LID is assigned, it
     * becomes the preferred identifier returned by {@link #toJid()}. This value is
     * {@code null} if the contact has not yet been assigned a LID.
     */
    @ProtobufProperty(index = 9, type = ProtobufType.STRING)
    Jid lid;

    /**
     * The optional username associated with this contact's WhatsApp account. Usernames
     * are user-chosen identifiers fetched via the USync protocol
     * ({@code WAWebUsyncUsername}). When present, the username is displayed in the
     * format {@code @username}. This value is {@code null} if the contact has not set
     * a username.
     */
    @ProtobufProperty(index = 10, type = ProtobufType.STRING)
    String username;

    /**
     * The ADV (Account Device Verification) encryption type for this contact, indicating
     * whether the contact uses standard end-to-end encryption ({@code E2EE}) or hosted
     * business API encryption ({@code HOSTED}). This information is updated during
     * device list synchronization. This value is {@code null} if the encryption type
     * has not been determined.
     */
    @ProtobufProperty(index = 11, type = ProtobufType.ENUM)
    ADVEncryptionType encryptionType;

    /**
     * Whether the local user has opted to share their own phone number with this
     * LID-identified contact. Populated by the {@code shareOwnPn} app state sync
     * action and used by the LID metadata update job to control phone number
     * visibility in LID-addressed conversations.
     */
    @ProtobufProperty(index = 12, type = ProtobufType.BOOL)
    boolean phoneNumberShared;

    /**
     * Whether this contact record was created through the username-based contact
     * discovery flow rather than through phone number lookup. Used to scope
     * {@code REMOVE} operations in {@link com.github.auties00.cobalt.sync.handler.LidContactHandler}:
     * only username-originated contacts have their name fields cleared on removal.
     */
    @ProtobufProperty(index = 13, type = ProtobufType.BOOL)
    boolean addedByUsername;

    /**
     * Constructs a new contact with the given field values.
     *
     * @param jid                the non-{@code null} JID uniquely identifying this contact
     * @param chosenName         the push name set by the contact, or {@code null}
     * @param fullName           the address-book full name, or {@code null}
     * @param shortName          the address-book short name, or {@code null}
     * @param lastKnownPresence  the last known presence state, or {@code null} (defaults
     *                           to {@link ContactStatus#UNAVAILABLE})
     * @param lastSeenSeconds    the epoch-second timestamp of last seen, or {@code null}
     * @param blocked            whether this contact is blocked
     * @param statusMuted        whether this contact's status updates are muted
     * @param lid                the LID for this contact, or {@code null}
     * @param username           the username for this contact, or {@code null}
     * @param encryptionType     the ADV encryption type, or {@code null}
     */
    Contact(Jid jid, String chosenName, String fullName, String shortName, ContactStatus lastKnownPresence, Long lastSeenSeconds, boolean blocked, boolean statusMuted, Jid lid, String username, ADVEncryptionType encryptionType, boolean phoneNumberShared, boolean addedByUsername) {
        this.jid = Objects.requireNonNull(jid, "value cannot be null");
        this.chosenName = chosenName;
        this.fullName = fullName;
        this.shortName = shortName;
        this.lastKnownPresence = Objects.requireNonNullElse(lastKnownPresence, ContactStatus.UNAVAILABLE);
        this.lastSeenSeconds = lastSeenSeconds;
        this.blocked = blocked;
        this.statusMuted = statusMuted;
        this.lid = lid;
        this.username = username;
        this.encryptionType = encryptionType;
        this.phoneNumberShared = phoneNumberShared;
        this.addedByUsername = addedByUsername;
    }

    /**
     * Returns the non-{@code null} JID that uniquely identifies this contact.
     *
     * @return the contact's phone-number-based JID
     */
    public Jid jid() {
        return this.jid;
    }

    /**
     * Returns the best available display name for this contact, resolving through
     * multiple name sources in order of preference.
     *
     * <p>The resolution order follows the WhatsApp Web name display logic: the short
     * name from the address book is preferred first, followed by the full address-book
     * name, then the push name (chosen name). If none of these are available, the user
     * part of the contact's JID is returned as a fallback.
     *
     * @return a non-{@code null} display name for this contact
     */
    public String name() {
        if (shortName != null) {
            return shortName;
        }

        if (fullName != null) {
            return fullName;
        }

        if (chosenName != null) {
            return chosenName;
        }

        return jid().user();
    }

    /**
     * Returns the push name that this contact has chosen for their WhatsApp profile.
     *
     * @return an {@code Optional} containing the chosen name, or empty if not available
     */
    public Optional<String> chosenName() {
        return Optional.ofNullable(this.chosenName);
    }

    /**
     * Sets the push name for this contact.
     *
     * @param chosenName the chosen name to set, or {@code null} to clear
     */
    public void setChosenName(String chosenName) {
        this.chosenName = chosenName;
    }

    /**
     * Returns the full name associated with this contact in the local address book.
     *
     * @return an {@code Optional} containing the full name, or empty if the contact
     *         is not saved in the address book
     */
    public Optional<String> fullName() {
        return Optional.ofNullable(this.fullName);
    }

    /**
     * Sets the address-book full name for this contact.
     *
     * @param fullName the full name to set, or {@code null} to clear
     */
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /**
     * Returns the shortened address-book name for this contact.
     *
     * @return an {@code Optional} containing the short name, or empty if not available
     */
    public Optional<String> shortName() {
        return Optional.ofNullable(this.shortName);
    }

    /**
     * Sets the address-book short name for this contact.
     *
     * @param shortName the short name to set, or {@code null} to clear
     */
    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    /**
     * Returns the last known presence state of this contact in the 1:1 conversation.
     *
     * @return the last known presence, never {@code null} (defaults to
     *         {@link ContactStatus#UNAVAILABLE})
     */
    public ContactStatus lastKnownPresence() {
        return this.lastKnownPresence;
    }

    /**
     * Sets the last known presence state for this contact.
     *
     * @param lastKnownPresence the presence state to set
     */
    public void setLastKnownPresence(ContactStatus lastKnownPresence) {
        this.lastKnownPresence = lastKnownPresence;
    }

    /**
     * Returns the timestamp of when this contact was last seen online.
     *
     * <p>This value is empty if the contact has never been seen online during this
     * session, or if the contact's privacy settings hide the last-seen information.
     *
     * @return an {@code Optional} containing the last-seen instant, or empty if
     *         unavailable
     */
    public Optional<Instant> lastSeen() {
        if (lastSeenSeconds == null || lastSeenSeconds <= 0) {
            return Optional.empty();
        }
        return Optional.of(Instant.ofEpochSecond(lastSeenSeconds));
    }

    /**
     * Sets the last-seen timestamp for this contact.
     *
     * @param lastSeen the instant when the contact was last seen online
     */
    public void setLastSeen(Instant lastSeen) {
        this.lastSeenSeconds = lastSeen.getEpochSecond();
    }

    /**
     * Returns whether this contact has been blocked by the current user.
     *
     * @return {@code true} if the contact is blocked, {@code false} otherwise
     */
    public boolean blocked() {
        return this.blocked;
    }

    /**
     * Sets whether this contact is blocked.
     *
     * @param blocked {@code true} to mark as blocked, {@code false} to unblock
     */
    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    /**
     * Returns whether this contact's status updates (stories) are muted.
     *
     * @return {@code true} if status updates are muted, {@code false} otherwise
     */
    public boolean statusMuted() {
        return statusMuted;
    }

    /**
     * Sets whether this contact's status updates are muted.
     *
     * @param statusMuted {@code true} to mute status updates, {@code false} to unmute
     */
    public void setStatusMuted(boolean statusMuted) {
        this.statusMuted = statusMuted;
    }

    /**
     * Returns the LID (Linked Identifier) associated with this contact.
     *
     * @return an {@code Optional} containing the LID, or empty if no LID has been
     *         assigned
     */
    public Optional<Jid> lid() {
        return Optional.ofNullable(lid);
    }

    /**
     * Sets the LID (Linked Identifier) for this contact.
     *
     * @param lid the LID to set, or {@code null} to clear
     */
    public void setLid(Jid lid) {
        this.lid = lid;
    }

    /**
     * Returns whether this contact has been assigned a LID (Linked Identifier).
     *
     * @return {@code true} if a LID is present, {@code false} otherwise
     */
    public boolean hasLid() {
        return lid != null;
    }

    /**
     * Returns the username associated with this contact's WhatsApp account.
     *
     * @return an {@code Optional} containing the username, or empty if the contact
     *         has not set a username
     */
    public Optional<String> username() {
        return Optional.ofNullable(username);
    }

    /**
     * Sets the username for this contact.
     *
     * @param username the username to set, or {@code null} to clear
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Returns whether this contact has a username set on their WhatsApp account.
     *
     * @return {@code true} if a username is present, {@code false} otherwise
     */
    public boolean hasUsername() {
        return username != null;
    }

    /**
     * Returns the ADV encryption type for this contact.
     *
     * @return an {@code Optional} containing the encryption type, or empty if the
     *         type has not been determined
     */
    public Optional<ADVEncryptionType> encryptionType() {
        return Optional.ofNullable(encryptionType);
    }

    /**
     * Sets the ADV encryption type for this contact.
     *
     * @param encryptionType the encryption type to set, or {@code null} to clear
     */
    public void setEncryptionType(ADVEncryptionType encryptionType) {
        this.encryptionType = encryptionType;
    }

    /**
     * Returns whether the local user has shared their phone number with this contact.
     *
     * @return {@code true} if the phone number has been shared
     */
    public boolean isPhoneNumberShared() {
        return phoneNumberShared;
    }

    /**
     * Sets whether the local user has shared their phone number with this contact.
     *
     * @param phoneNumberShared {@code true} if the phone number is shared
     */
    public void setPhoneNumberShared(boolean phoneNumberShared) {
        this.phoneNumberShared = phoneNumberShared;
    }

    /**
     * Returns whether this contact was added through username-based discovery.
     *
     * @return {@code true} if the contact was added by username
     */
    public boolean isAddedByUsername() {
        return addedByUsername;
    }

    /**
     * Sets whether this contact was added through username-based discovery.
     *
     * @param addedByUsername {@code true} if added by username
     */
    public void setAddedByUsername(boolean addedByUsername) {
        this.addedByUsername = addedByUsername;
    }

    /**
     * Returns whether any of the contact's name fields (full name, short name, or
     * chosen name) exactly matches the given string.
     *
     * @param name the name to check against, or {@code null}
     * @return {@code true} if the given name matches at least one of the contact's
     *         name fields, {@code false} if {@code name} is {@code null} or no match
     *         is found
     */
    public boolean hasName(String name) {
        return name != null
               && (name.equals(fullName) || name.equals(shortName) || name.equals(chosenName));
    }

    /**
     * Returns the preferred JID for this contact. If a LID has been assigned, the LID
     * is returned; otherwise, the phone-number-based JID is returned. This behavior
     * supports the WhatsApp LID migration, where LIDs progressively replace phone
     * number JIDs as the primary contact identifier.
     *
     * @return the LID if present, otherwise the phone-number-based JID
     */
    @Override
    public Jid toJid() {
        var lid = this.lid;
        if(lid != null) {
            return lid;
        } else {
            return jid;
        }
    }

    /**
     * Returns a hash code based on this contact's {@linkplain #jid() JID}.
     *
     * @return the hash code of the JID
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(this.jid());
    }

    /**
     * Returns whether this contact is equal to the given object. Two contacts are
     * considered equal if they have the same {@linkplain #jid() JID}.
     *
     * @param other the object to compare with
     * @return {@code true} if the other object is a {@code Contact} with the same JID
     */
    public boolean equals(Object other) {
        return other instanceof Contact that && Objects.equals(this.jid(), that.jid());
    }
}
