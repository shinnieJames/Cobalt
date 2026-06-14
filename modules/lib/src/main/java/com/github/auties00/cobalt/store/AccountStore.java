package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientDevice;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientType;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.business.profile.BusinessCategory;
import com.github.auties00.cobalt.model.contact.ContactTextStatus;
import com.github.auties00.cobalt.model.device.pairing.ClientAppVersion;
import com.github.auties00.cobalt.model.device.pairing.ClientPayload.ClientReleaseChannel;
import com.github.auties00.cobalt.model.device.pairing.LinkedPrimaryPlatform;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.action.device.WaffleAccountLinkStateAction;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * The account-identity and profile state of a WhatsApp client session.
 *
 * <p>This is the sub-store that owns who this session is: the stable session
 * UUID, the registered phone number, the phone-number {@link Jid} and the
 * privacy-preserving LID, the advertised device descriptor and release channel,
 * the local and observed companion app versions, the display name, profile
 * picture and "About" text, and the WhatsApp Business profile fields (address,
 * location, description, websites, email and categories). It also tracks the
 * online presence flag, the registration state, the last ADV revalidation time
 * and the linked-Meta-account state.
 *
 * @apiNote
 * Embedders reach this through {@link LinkedWhatsAppStore#accountStore()}. Identity
 * and profile values are the most commonly read store data.
 *
 * @see LinkedWhatsAppStore
 */
@WhatsAppWebModule(moduleName = "WAWebUserPrefsLoginKeys")
@WhatsAppWebModule(moduleName = "WAWebUserPrefsKeys")
@SuppressWarnings({"unused", "UnusedReturnValue"})
public interface AccountStore {
    /**
     * Returns the stable per-account identifier scoping this session's on-disk state and log tags.
     *
     * @return the session UUID, never {@code null}
     */
    UUID uuid();

    /**
     * Returns the E.164 phone number of the logged-in account.
     *
     * @return the phone number, or an empty {@link OptionalLong} until the server confirms registration
     */
    OptionalLong phoneNumber();

    /**
     * Sets the account phone number.
     *
     * @param phoneNumber the phone number, or {@code null} to clear
     * @return this store instance for method chaining
     */
    AccountStore setPhoneNumber(Long phoneNumber);

    /**
     * Returns the flavour of WhatsApp client this store was initialised for.
     *
     * @return the client type, never {@code null}
     */
    LinkedWhatsAppClientType clientType();

    /**
     * Returns the wall-clock timestamp at which this store was first created.
     *
     * @return the initialization timestamp, never {@code null}
     */
    Instant initializationTimeStamp();

    /**
     * Returns the device descriptor advertised during pairing and bundled into every client payload.
     *
     * @return the device descriptor, never {@code null}
     */
    LinkedWhatsAppClientDevice device();

    /**
     * Sets the device descriptor.
     *
     * @param device the device descriptor, never {@code null}
     * @return this store instance for method chaining
     */
    AccountStore setDevice(LinkedWhatsAppClientDevice device);

    /**
     * Returns the release channel advertised to the server during connection.
     *
     * @return the release channel, never {@code null}
     */
    ClientReleaseChannel releaseChannel();

    /**
     * Sets the release channel.
     *
     * @param releaseChannel the release channel, never {@code null}
     * @return this store instance for method chaining
     */
    AccountStore setReleaseChannel(ClientReleaseChannel releaseChannel);

    /**
     * Returns whether the local account is currently advertising itself as online via presence stanzas.
     *
     * @return {@code true} if the account is advertised online
     */
    boolean online();

    /**
     * Sets the online presence flag.
     *
     * @param online whether to advertise the account as online
     * @return this store instance for method chaining
     */
    AccountStore setOnline(boolean online);

    /**
     * Returns the IETF language tag advertised to the server for server-rendered notifications.
     *
     * @return the locale, or empty if none is set
     */
    Optional<String> locale();

    /**
     * Sets the locale.
     *
     * @param locale the IETF language tag, or {@code null} to clear
     * @return this store instance for method chaining
     */
    AccountStore setLocale(String locale);

    /**
     * Returns the pushname (display name) the account broadcasts to its peers.
     *
     * @return the display name, or empty if none is set
     */
    Optional<String> name();

    /**
     * Sets the display name.
     *
     * @param name the display name, or {@code null} to clear
     * @return this store instance for method chaining
     */
    AccountStore setName(String name);

    /**
     * Returns the server-attested business display name on Business Verified accounts.
     *
     * @return the verified business name, or empty if none is set
     */
    Optional<String> verifiedName();

    /**
     * Sets the verified business name.
     *
     * @param verifiedName the verified business name, or {@code null} to clear
     * @return this store instance for method chaining
     */
    AccountStore setVerifiedName(String verifiedName);

    /**
     * Returns the platform of the linked primary device that performed the pair-success handshake.
     *
     * <p>Set on WEB clients from the {@code <platform name="..."/>} child of the
     * {@code <pair-success>} IQ stanza at first link. Empty on MOBILE clients (which are themselves
     * the primary) and on WEB sessions that have not yet completed the initial pair-success.
     *
     * @return the linked primary platform, or empty when unknown
     */
    Optional<LinkedPrimaryPlatform> primaryPlatform();

    /**
     * Sets the linked primary device platform.
     *
     * @param primaryPlatform the platform, or {@code null} to clear
     * @return this store instance for method chaining
     */
    AccountStore setPrimaryPlatform(LinkedPrimaryPlatform primaryPlatform);

    /**
     * Returns the CDN URI for the most recently fetched copy of the local account profile picture.
     *
     * @return the profile picture URI, or empty if none has been fetched
     */
    Optional<URI> profilePicture();

    /**
     * Sets the profile picture URI.
     *
     * @param profilePicture the profile picture URI, or {@code null} to clear
     * @return this store instance for method chaining
     */
    AccountStore setProfilePicture(URI profilePicture);

    /**
     * Returns the local account published "About" text status.
     *
     * @return the self text status, or empty if none is set
     */
    Optional<ContactTextStatus> selfTextStatus();

    /**
     * Sets the local account "About" text status.
     *
     * @param selfTextStatus the self text status, or {@code null} to clear
     * @return this store instance for method chaining
     */
    AccountStore setSelfTextStatus(ContactTextStatus selfTextStatus);

    /**
     * Returns the local account phone-number JID (for example {@code 1234567890@s.whatsapp.net}).
     *
     * @return the account JID, or empty until pairing completes
     */
    Optional<Jid> jid();

    /**
     * Sets the account phone-number JID.
     *
     * @param jid the account JID, or {@code null} to clear
     * @return this store instance for method chaining
     */
    AccountStore setJid(Jid jid);

    /**
     * Returns the local account LID, the opaque {@code @lid} identifier that hides the phone number in groups.
     *
     * @return the account LID, or empty on pre-LID sessions
     */
    Optional<Jid> lid();

    /**
     * Sets the account LID.
     *
     * @param lid the account LID, or {@code null} to clear
     * @return this store instance for method chaining
     */
    AccountStore setLid(Jid lid);

    /**
     * Returns the free-text street address shown on the business profile.
     *
     * @return the business address, or empty if none is set
     */
    Optional<String> businessAddress();

    /**
     * Sets the business profile street address.
     *
     * @param businessAddress the business address, or {@code null} to clear
     * @return this store instance for method chaining
     */
    AccountStore setBusinessAddress(String businessAddress);

    /**
     * Returns the longitude of the business profile map pin.
     *
     * @return the business longitude, or empty if no location is published
     */
    OptionalDouble businessLongitude();

    /**
     * Sets the business profile longitude.
     *
     * @param businessLongitude the longitude, or {@code null} to clear
     * @return this store instance for method chaining
     */
    AccountStore setBusinessLongitude(Double businessLongitude);

    /**
     * Returns the latitude of the business profile map pin.
     *
     * @return the business latitude, or empty if no location is published
     */
    OptionalDouble businessLatitude();

    /**
     * Sets the business profile latitude.
     *
     * @param businessLatitude the latitude, or {@code null} to clear
     * @return this store instance for method chaining
     */
    AccountStore setBusinessLatitude(Double businessLatitude);

    /**
     * Returns the free-text "About this business" description rendered on the business profile.
     *
     * @return the business description, or empty if none is set
     */
    Optional<String> businessDescription();

    /**
     * Sets the business profile description.
     *
     * @param businessDescription the description, or {@code null} to clear
     * @return this store instance for method chaining
     */
    AccountStore setBusinessDescription(String businessDescription);

    /**
     * Returns the ordered list of website URIs published on the business profile.
     *
     * @return the business websites, never {@code null}
     */
    List<URI> businessWebsites();

    /**
     * Sets the business profile website list.
     *
     * @param businessWebsites the website URIs, or {@code null} to clear
     * @return this store instance for method chaining
     */
    AccountStore setBusinessWebsites(List<URI> businessWebsites);

    /**
     * Returns the contact email shown on the business profile.
     *
     * @return the business email, or empty if none is set
     */
    Optional<String> businessEmail();

    /**
     * Sets the business profile email.
     *
     * @param businessEmail the email, or {@code null} to clear
     * @return this store instance for method chaining
     */
    AccountStore setBusinessEmail(String businessEmail);

    /**
     * Returns the Meta-defined business categories assigned to the business profile.
     *
     * @return the business categories, never {@code null}
     */
    List<BusinessCategory> businessCategories();

    /**
     * Sets the business profile categories.
     *
     * @param businessCategories the categories, or {@code null} to clear
     * @return this store instance for method chaining
     */
    AccountStore setBusinessCategories(List<BusinessCategory> businessCategories);

    /**
     * Returns whether this device has completed the pairing handshake and is registered with the server.
     *
     * @return {@code true} if the device is registered
     */
    boolean registered();

    /**
     * Sets the registration state.
     *
     * @param registered whether the device is registered
     * @return this store instance for method chaining
     */
    AccountStore setRegistered(boolean registered);

    /**
     * Returns the advertised application version this client identifies itself with on the server.
     *
     * @apiNote
     * Lazily derived from the {@link #device()} platform on first access when not explicitly set.
     *
     * @return the client version, never {@code null}
     */
    ClientAppVersion clientVersion();

    /**
     * Sets the advertised client version.
     *
     * @param version the client version, or {@code null} to fall back to the derived default
     * @return this store instance for method chaining
     */
    AccountStore setClientVersion(ClientAppVersion version);

    /**
     * Returns the currently observed application version of the paired primary device.
     *
     * @return the companion version, or empty if not yet observed
     */
    Optional<ClientAppVersion> companionVersion();

    /**
     * Sets the observed companion (primary device) version.
     *
     * @param version the companion version, or {@code null} to clear
     * @return this store instance for method chaining
     */
    AccountStore setCompanionVersion(ClientAppVersion version);

    /**
     * Returns the timestamp of the most recent successful ADV revalidation against the primary device.
     *
     * @return the last ADV check time, or empty if never checked
     */
    Optional<Instant> lastAdvCheckTime();

    /**
     * Updates the last ADV check time to now.
     */
    void updateAdvCheckTime();

    /**
     * Returns the link state between this WhatsApp account and a Meta (Facebook/Instagram) account.
     *
     * @return the linked-Meta-account state, or empty if unknown
     */
    Optional<WaffleAccountLinkStateAction.AccountLinkState> linkedMetaAccountState();

    /**
     * Sets the linked-Meta-account state.
     *
     * @param state the link state, or {@code null} to clear
     * @return this store instance for method chaining
     */
    AccountStore setLinkedMetaAccountState(WaffleAccountLinkStateAction.AccountLinkState state);

    /**
     * Returns the timestamp of the most recent linked-Meta-account state transition.
     *
     * @return the transition timestamp, or empty if never set
     */
    Optional<Instant> linkedMetaAccountStateTimestamp();

    /**
     * Sets the timestamp of the most recent linked-Meta-account state transition.
     *
     * @param timestamp the transition timestamp, or {@code null} to clear
     * @return this store instance for method chaining
     */
    AccountStore setLinkedMetaAccountStateTimestamp(Instant timestamp);
}
