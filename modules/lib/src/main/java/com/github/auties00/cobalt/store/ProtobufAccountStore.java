package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientDevice;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientType;
import com.github.auties00.cobalt.client.linked.info.LinkedWhatsAppClientInfo;
import com.github.auties00.cobalt.model.business.profile.BusinessCategory;
import com.github.auties00.cobalt.model.contact.ContactTextStatus;
import com.github.auties00.cobalt.model.device.pairing.ClientAppVersion;
import com.github.auties00.cobalt.model.device.pairing.ClientPayload.ClientReleaseChannel;
import com.github.auties00.cobalt.model.device.pairing.LinkedPrimaryPlatform;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import com.github.auties00.cobalt.model.sync.action.device.WaffleAccountLinkStateAction;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.UUID;

import static java.util.Objects.requireNonNullElse;
import static java.util.Objects.requireNonNullElseGet;

/**
 * The protobuf-backed {@link AccountStore} holding this session's identity and profile state.
 *
 * <p>This is a nested {@code MESSAGE} sub-store of {@link ProtobufWhatsAppStore}; it owns the session
 * UUID, phone number, client type, the phone-number JID and LID, the device descriptor and release
 * channel, the local and companion app versions, the display/verified names, profile picture and
 * "About" text, the WhatsApp Business profile fields, and the registration, presence, ADV-check and
 * linked-Meta-account state.
 *
 * @implNote
 * This implementation defaults the initialization timestamp ({@code Instant.now()}) and release
 * channel ({@link ClientReleaseChannel#RELEASE}) when absent and lazily derives
 * {@link #clientVersion()} from the device platform under {@link #clientVersionLock}. The linked-Meta
 * fields and the lock are transient (not persisted).
 */
@ProtobufMessage
@SuppressWarnings({"unused", "UnusedReturnValue"})
public final class ProtobufAccountStore implements AccountStore {
    /**
     * The stable per-account identifier scoping on-disk state, log tags and the pairing handshake.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    private final UUID uuid;

    /**
     * The E.164 phone number of the logged-in account, or {@code null} until the server confirms registration.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.UINT64)
    private Long phoneNumber;

    /**
     * The flavour of WhatsApp client this store was initialised for.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
    private final LinkedWhatsAppClientType clientType;

    /**
     * The wall-clock timestamp at which this store was first created.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    private final Instant initializationTimeStamp;

    /**
     * The device descriptor advertised during pairing and bundled into every client payload.
     */
    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    private LinkedWhatsAppClientDevice device;

    /**
     * The release channel advertised to the server during connection.
     */
    @ProtobufProperty(index = 6, type = ProtobufType.ENUM)
    private ClientReleaseChannel releaseChannel;

    /**
     * Whether the local account is currently advertising itself as online via presence stanzas.
     */
    @ProtobufProperty(index = 7, type = ProtobufType.BOOL)
    private boolean online;

    /**
     * The IETF language tag advertised to the server for server-rendered notifications.
     */
    @ProtobufProperty(index = 8, type = ProtobufType.STRING)
    private String locale;

    /**
     * The pushname (display name) the account broadcasts to its peers.
     */
    @ProtobufProperty(index = 9, type = ProtobufType.STRING)
    private String name;

    /**
     * The server-attested business display name on Business Verified accounts.
     */
    @ProtobufProperty(index = 10, type = ProtobufType.STRING)
    private String verifiedName;

    /**
     * The CDN URI for the most recently fetched copy of the local account profile picture.
     */
    @ProtobufProperty(index = 11, type = ProtobufType.STRING)
    private URI profilePicture;

    /**
     * The local account published "About" text status.
     */
    @ProtobufProperty(index = 12, type = ProtobufType.MESSAGE)
    private ContactTextStatus selfTextStatus;

    /**
     * The local account phone-number JID.
     */
    @ProtobufProperty(index = 13, type = ProtobufType.STRING)
    private Jid jid;

    /**
     * The local account LID, the opaque identifier that hides the phone number in groups.
     */
    @ProtobufProperty(index = 14, type = ProtobufType.STRING)
    private Jid lid;

    /**
     * The free-text street address shown on the business profile.
     */
    @ProtobufProperty(index = 15, type = ProtobufType.STRING)
    private String businessAddress;

    /**
     * The longitude of the business profile map pin.
     */
    @ProtobufProperty(index = 16, type = ProtobufType.DOUBLE)
    private Double businessLongitude;

    /**
     * The latitude of the business profile map pin.
     */
    @ProtobufProperty(index = 17, type = ProtobufType.DOUBLE)
    private Double businessLatitude;

    /**
     * The free-text "About this business" description rendered on the business profile.
     */
    @ProtobufProperty(index = 18, type = ProtobufType.STRING)
    private String businessDescription;

    /**
     * The ordered list of website URIs published on the business profile.
     */
    @ProtobufProperty(index = 19, type = ProtobufType.STRING)
    private List<URI> businessWebsites;

    /**
     * The contact email shown on the business profile.
     */
    @ProtobufProperty(index = 20, type = ProtobufType.STRING)
    private String businessEmail;

    /**
     * The Meta-defined business categories assigned to the business profile.
     */
    @ProtobufProperty(index = 21, type = ProtobufType.MESSAGE)
    private List<BusinessCategory> businessCategories;

    /**
     * Whether this device has completed the pairing handshake and is registered with the server.
     */
    @ProtobufProperty(index = 22, type = ProtobufType.BOOL)
    private boolean registered;

    /**
     * The advertised application version this client identifies itself with on the server.
     */
    @ProtobufProperty(index = 23, type = ProtobufType.MESSAGE)
    private volatile ClientAppVersion clientVersion;

    /**
     * The currently observed application version of the paired primary device.
     */
    @ProtobufProperty(index = 24, type = ProtobufType.MESSAGE)
    private ClientAppVersion companionVersion;

    /**
     * The timestamp of the most recent successful ADV revalidation against the primary device.
     */
    @ProtobufProperty(index = 25, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    private Instant lastAdvCheckTime;

    /**
     * The platform of the linked primary device, captured at pair-success.
     *
     * <p>Exposed through {@link AccountStore#primaryPlatform()}.
     */
    @ProtobufProperty(index = 26, type = ProtobufType.ENUM)
    private LinkedPrimaryPlatform primaryPlatform;

    /**
     * The monitor guarding the lazy initialisation of {@link #clientVersion}; not persisted.
     */
    private final Object clientVersionLock;

    /**
     * The link state between this WhatsApp account and a Meta account; not persisted.
     */
    private volatile WaffleAccountLinkStateAction.AccountLinkState linkedMetaAccountState;

    /**
     * The timestamp of the most recent {@link #linkedMetaAccountState} transition; not persisted.
     */
    private volatile Instant linkedMetaAccountStateTimestamp;

    /**
     * Constructs an account sub-store, defaulting the initialization timestamp, release channel and
     * business collections when absent.
     *
     * @param uuid                    the session UUID, never {@code null}
     * @param phoneNumber             the phone number, or {@code null}
     * @param clientType              the client type, never {@code null}
     * @param initializationTimeStamp the creation timestamp, or {@code null} for now
     * @param device                  the device descriptor, never {@code null}
     * @param releaseChannel          the release channel, or {@code null} for {@link ClientReleaseChannel#RELEASE}
     * @param online                  whether the account is advertised online
     * @param locale                  the locale, or {@code null}
     * @param name                    the display name, or {@code null}
     * @param verifiedName            the verified business name, or {@code null}
     * @param profilePicture          the profile picture URI, or {@code null}
     * @param selfTextStatus          the self text status, or {@code null}
     * @param jid                     the account JID, or {@code null}
     * @param lid                     the account LID, or {@code null}
     * @param businessAddress         the business address, or {@code null}
     * @param businessLongitude       the business longitude, or {@code null}
     * @param businessLatitude        the business latitude, or {@code null}
     * @param businessDescription     the business description, or {@code null}
     * @param businessWebsites        the business websites, or {@code null} for an empty list
     * @param businessEmail           the business email, or {@code null}
     * @param businessCategories      the business categories, or {@code null} for an empty list
     * @param registered              whether the device is registered
     * @param clientVersion           the advertised client version, or {@code null} for the derived default
     * @param companionVersion        the observed companion version, or {@code null}
     * @param lastAdvCheckTime        the last ADV check time, or {@code null}
     * @param primaryPlatform         the linked-primary platform, or {@code null}
     */
    ProtobufAccountStore(UUID uuid, Long phoneNumber, LinkedWhatsAppClientType clientType, Instant initializationTimeStamp, LinkedWhatsAppClientDevice device, ClientReleaseChannel releaseChannel, boolean online, String locale, String name, String verifiedName, URI profilePicture, ContactTextStatus selfTextStatus, Jid jid, Jid lid, String businessAddress, Double businessLongitude, Double businessLatitude, String businessDescription, List<URI> businessWebsites, String businessEmail, List<BusinessCategory> businessCategories, boolean registered, ClientAppVersion clientVersion, ClientAppVersion companionVersion, Instant lastAdvCheckTime, LinkedPrimaryPlatform primaryPlatform) {
        this.uuid = Objects.requireNonNull(uuid, "uuid cannot be null");
        this.phoneNumber = phoneNumber;
        this.clientType = Objects.requireNonNull(clientType, "clientType cannot be null");
        this.initializationTimeStamp = requireNonNullElseGet(initializationTimeStamp, Instant::now);
        this.device = Objects.requireNonNull(device, "device cannot be null");
        this.releaseChannel = requireNonNullElse(releaseChannel, ClientReleaseChannel.RELEASE);
        this.online = online;
        this.locale = locale;
        this.name = name;
        this.verifiedName = verifiedName;
        this.profilePicture = profilePicture;
        this.selfTextStatus = selfTextStatus;
        this.jid = jid;
        this.lid = lid;
        this.businessAddress = businessAddress;
        this.businessLongitude = businessLongitude;
        this.businessLatitude = businessLatitude;
        this.businessDescription = businessDescription;
        this.businessWebsites = requireNonNullElseGet(businessWebsites, ArrayList::new);
        this.businessEmail = businessEmail;
        this.businessCategories = requireNonNullElseGet(businessCategories, ArrayList::new);
        this.registered = registered;
        this.clientVersion = clientVersion;
        this.companionVersion = companionVersion;
        this.lastAdvCheckTime = lastAdvCheckTime;
        this.primaryPlatform = primaryPlatform;
        this.clientVersionLock = new Object();
    }

    @Override
    public UUID uuid() {
        return uuid;
    }

    @Override
    public OptionalLong phoneNumber() {
        return phoneNumber == null ? OptionalLong.empty() : OptionalLong.of(phoneNumber);
    }

    @Override
    public AccountStore setPhoneNumber(Long phoneNumber) {
        this.phoneNumber = phoneNumber;
        return this;
    }

    @Override
    public LinkedWhatsAppClientType clientType() {
        return clientType;
    }

    @Override
    public Instant initializationTimeStamp() {
        return initializationTimeStamp;
    }

    @Override
    public LinkedWhatsAppClientDevice device() {
        return device;
    }

    @Override
    public AccountStore setDevice(LinkedWhatsAppClientDevice device) {
        this.device = Objects.requireNonNull(device, "device cannot be null");
        return this;
    }

    @Override
    public ClientReleaseChannel releaseChannel() {
        return releaseChannel;
    }

    @Override
    public AccountStore setReleaseChannel(ClientReleaseChannel releaseChannel) {
        this.releaseChannel = Objects.requireNonNull(releaseChannel, "releaseChannel cannot be null");
        return this;
    }

    @Override
    public boolean online() {
        return online;
    }

    @Override
    public AccountStore setOnline(boolean online) {
        this.online = online;
        return this;
    }

    @Override
    public Optional<String> locale() {
        return Optional.ofNullable(locale);
    }

    @Override
    public AccountStore setLocale(String locale) {
        this.locale = locale;
        return this;
    }

    @Override
    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    @Override
    public AccountStore setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public Optional<String> verifiedName() {
        return Optional.ofNullable(verifiedName);
    }

    @Override
    public AccountStore setVerifiedName(String verifiedName) {
        this.verifiedName = verifiedName;
        return this;
    }

    @Override
    public Optional<LinkedPrimaryPlatform> primaryPlatform() {
        return Optional.ofNullable(primaryPlatform);
    }

    @Override
    public AccountStore setPrimaryPlatform(LinkedPrimaryPlatform primaryPlatform) {
        this.primaryPlatform = primaryPlatform;
        return this;
    }

    @Override
    public Optional<URI> profilePicture() {
        return Optional.ofNullable(profilePicture);
    }

    @Override
    public AccountStore setProfilePicture(URI profilePicture) {
        this.profilePicture = profilePicture;
        return this;
    }

    @Override
    public Optional<ContactTextStatus> selfTextStatus() {
        return Optional.ofNullable(selfTextStatus);
    }

    @Override
    public AccountStore setSelfTextStatus(ContactTextStatus selfTextStatus) {
        this.selfTextStatus = selfTextStatus;
        return this;
    }

    @Override
    public Optional<Jid> jid() {
        return Optional.ofNullable(jid);
    }

    @Override
    public AccountStore setJid(Jid jid) {
        this.jid = jid;
        return this;
    }

    @Override
    public Optional<Jid> lid() {
        return Optional.ofNullable(lid);
    }

    @Override
    public AccountStore setLid(Jid lid) {
        this.lid = lid;
        return this;
    }

    @Override
    public Optional<String> businessAddress() {
        return Optional.ofNullable(businessAddress);
    }

    @Override
    public AccountStore setBusinessAddress(String businessAddress) {
        this.businessAddress = businessAddress;
        return this;
    }

    @Override
    public OptionalDouble businessLongitude() {
        return businessLongitude == null ? OptionalDouble.empty() : OptionalDouble.of(businessLongitude);
    }

    @Override
    public AccountStore setBusinessLongitude(Double businessLongitude) {
        this.businessLongitude = businessLongitude;
        return this;
    }

    @Override
    public OptionalDouble businessLatitude() {
        return businessLatitude == null ? OptionalDouble.empty() : OptionalDouble.of(businessLatitude);
    }

    @Override
    public AccountStore setBusinessLatitude(Double businessLatitude) {
        this.businessLatitude = businessLatitude;
        return this;
    }

    @Override
    public Optional<String> businessDescription() {
        return Optional.ofNullable(businessDescription);
    }

    @Override
    public AccountStore setBusinessDescription(String businessDescription) {
        this.businessDescription = businessDescription;
        return this;
    }

    @Override
    public List<URI> businessWebsites() {
        return businessWebsites == null ? List.of() : List.copyOf(businessWebsites);
    }

    @Override
    public AccountStore setBusinessWebsites(List<URI> businessWebsites) {
        this.businessWebsites = businessWebsites;
        return this;
    }

    @Override
    public Optional<String> businessEmail() {
        return Optional.ofNullable(businessEmail);
    }

    @Override
    public AccountStore setBusinessEmail(String businessEmail) {
        this.businessEmail = businessEmail;
        return this;
    }

    @Override
    public List<BusinessCategory> businessCategories() {
        return businessCategories == null ? List.of() : List.copyOf(businessCategories);
    }

    @Override
    public AccountStore setBusinessCategories(List<BusinessCategory> businessCategories) {
        this.businessCategories = businessCategories;
        return this;
    }

    @Override
    public boolean registered() {
        return registered;
    }

    @Override
    public AccountStore setRegistered(boolean registered) {
        this.registered = registered;
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation lazily derives the version from the {@link #device()} platform on first
     * access under {@link #clientVersionLock} using double-checked locking, so the probe runs at
     * most once per store instance.
     */
    @Override
    public ClientAppVersion clientVersion() {
        if (clientVersion == null) {
            synchronized (clientVersionLock) {
                if (clientVersion == null) {
                    clientVersion = LinkedWhatsAppClientInfo.of(device.platform()).version();
                }
            }
        }
        return clientVersion;
    }

    @Override
    public AccountStore setClientVersion(ClientAppVersion version) {
        this.clientVersion = version;
        return this;
    }

    @Override
    public Optional<ClientAppVersion> companionVersion() {
        return Optional.ofNullable(companionVersion);
    }

    @Override
    public AccountStore setCompanionVersion(ClientAppVersion version) {
        this.companionVersion = version;
        return this;
    }

    @Override
    public Optional<Instant> lastAdvCheckTime() {
        return Optional.ofNullable(lastAdvCheckTime);
    }

    @Override
    public void updateAdvCheckTime() {
        this.lastAdvCheckTime = Instant.now();
    }

    @Override
    public Optional<WaffleAccountLinkStateAction.AccountLinkState> linkedMetaAccountState() {
        return Optional.ofNullable(linkedMetaAccountState);
    }

    @Override
    public AccountStore setLinkedMetaAccountState(WaffleAccountLinkStateAction.AccountLinkState state) {
        this.linkedMetaAccountState = state;
        return this;
    }

    @Override
    public Optional<Instant> linkedMetaAccountStateTimestamp() {
        return Optional.ofNullable(linkedMetaAccountStateTimestamp);
    }

    @Override
    public AccountStore setLinkedMetaAccountStateTimestamp(Instant timestamp) {
        this.linkedMetaAccountStateTimestamp = timestamp;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProtobufAccountStore that)) {
            return false;
        }
        return online == that.online
               && registered == that.registered
               && Objects.equals(uuid, that.uuid)
               && Objects.equals(phoneNumber, that.phoneNumber)
               && clientType == that.clientType
               && Objects.equals(initializationTimeStamp, that.initializationTimeStamp)
               && Objects.equals(device, that.device)
               && releaseChannel == that.releaseChannel
               && Objects.equals(locale, that.locale)
               && Objects.equals(name, that.name)
               && Objects.equals(verifiedName, that.verifiedName)
               && Objects.equals(profilePicture, that.profilePicture)
               && Objects.equals(selfTextStatus, that.selfTextStatus)
               && Objects.equals(jid, that.jid)
               && Objects.equals(lid, that.lid)
               && Objects.equals(businessAddress, that.businessAddress)
               && Objects.equals(businessLongitude, that.businessLongitude)
               && Objects.equals(businessLatitude, that.businessLatitude)
               && Objects.equals(businessDescription, that.businessDescription)
               && Objects.equals(businessWebsites, that.businessWebsites)
               && Objects.equals(businessEmail, that.businessEmail)
               && Objects.equals(businessCategories, that.businessCategories)
               && Objects.equals(clientVersion, that.clientVersion)
               && Objects.equals(companionVersion, that.companionVersion)
               && Objects.equals(lastAdvCheckTime, that.lastAdvCheckTime)
               && Objects.equals(primaryPlatform, that.primaryPlatform)
               && Objects.equals(linkedMetaAccountState, that.linkedMetaAccountState)
               && Objects.equals(linkedMetaAccountStateTimestamp, that.linkedMetaAccountStateTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, phoneNumber, clientType, initializationTimeStamp, device, releaseChannel,
                online, locale, name, verifiedName, profilePicture, selfTextStatus, jid, lid,
                businessAddress, businessLongitude, businessLatitude, businessDescription, businessWebsites,
                businessEmail, businessCategories, registered, clientVersion, companionVersion, lastAdvCheckTime,
                primaryPlatform, linkedMetaAccountState, linkedMetaAccountStateTimestamp);
    }
}
