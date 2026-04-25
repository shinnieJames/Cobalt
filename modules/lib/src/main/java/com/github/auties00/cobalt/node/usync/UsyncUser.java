package com.github.auties00.cobalt.node.usync;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One user entry in a {@link UsyncQuery}.
 *
 * <p>A USync request carries a list of user entries; each entry tells the
 * relay <em>which</em> peer the per-protocol elements apply to. WhatsApp
 * Web accepts four ways to address a peer: by canonical {@link Jid id},
 * by phone number, by username, or by a phone-JID hint. To make the
 * "at least one addressing identifier" invariant impossible to violate at
 * compile time, instances are created through one of the four
 * {@code by*} static factories instead of a public no-arg constructor.
 *
 * <p>Once created, callers chain {@code with*} setters to attach optional
 * fields (LID, device-list hashes, persona id, contact type, trusted-
 * contact token). Per-protocol payload data is read back by each
 * {@code UsyncProtocol.buildUserElement} when the query stanza is
 * assembled.
 *
 * @implNote WAWebUsyncUser.USyncUser: builder class with private numeric
 *     slots {@code $1..$12}. Cobalt names each slot for readability and
 *     promotes nullable slots to {@link Optional} accessors.
 *     {@code WAWebUsyncUser.validate} is removed — its only failure mode
 *     ("user must have an id, phone, username or pnJid") is unreachable
 *     by construction here. The runtime call to
 *     {@code WAWebWidValidator.validateWid} is also dropped because
 *     Cobalt's {@link Jid} factories validate at parse time.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncUser")
public final class UsyncUser {
    /** The canonical JID. */
    private Jid id;

    /** The LID JID. */
    private Jid lid;

    /** The phone-number JID. */
    private Jid phoneJid;

    /** The phone number, in E.164 form without the leading {@code +}. */
    private String phoneNumber;

    /** The cached device-list hash, base64-encoded. */
    private String deviceHash;

    /** The timestamp the local cache last refreshed at. */
    private Instant timestamp;

    /** The timestamp the relay should compare against. */
    private Instant expectedTimestamp;

    /** The persona id used by the bot-profile protocol. */
    private String personaId;

    /** The username used by the contact protocol's username addressing. */
    private String username;

    /** The username PIN that accompanies a username addressing. */
    private String pin;

    /** The contact-protocol type discriminator (e.g. {@code "in"}). */
    private String contactType;

    /** The trusted-contact token used by the status protocol. */
    private byte[] trustedContactToken;

    /**
     * Hidden constructor; use the {@code by*} factories.
     */
    private UsyncUser() {
    }

    /**
     * Creates a user entry addressed by canonical JID.
     *
     * @param id the canonical JID; must not be {@code null}
     * @return a fresh entry
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.withId", adaptation = WhatsAppAdaptation.ADAPTED)
    public static UsyncUser byId(Jid id) {
        Objects.requireNonNull(id, "id cannot be null");
        var user = new UsyncUser();
        user.id = id;
        return user;
    }

    /**
     * Creates a user entry addressed by phone number.
     *
     * @param phoneNumber the phone number, in E.164 form without the
     *                    leading {@code +}; must not be {@code null}
     * @return a fresh entry
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.withPhone", adaptation = WhatsAppAdaptation.ADAPTED)
    public static UsyncUser byPhoneNumber(String phoneNumber) {
        Objects.requireNonNull(phoneNumber, "phoneNumber cannot be null");
        var user = new UsyncUser();
        user.phoneNumber = phoneNumber;
        return user;
    }

    /**
     * Creates a user entry addressed by username.
     *
     * @param username the username (without leading {@code @}); must not
     *                 be {@code null}
     * @return a fresh entry
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.withUsername", adaptation = WhatsAppAdaptation.ADAPTED)
    public static UsyncUser byUsername(String username) {
        Objects.requireNonNull(username, "username cannot be null");
        var user = new UsyncUser();
        user.username = username;
        return user;
    }

    /**
     * Creates a user entry addressed by phone-JID hint. The relay maps
     * the hint to a canonical JID before processing the per-protocol
     * elements.
     *
     * @param phoneJid the phone-number JID; must not be {@code null}
     * @return a fresh entry
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.withPnJid", adaptation = WhatsAppAdaptation.ADAPTED)
    public static UsyncUser byPhoneJid(Jid phoneJid) {
        Objects.requireNonNull(phoneJid, "phoneJid cannot be null");
        var user = new UsyncUser();
        user.phoneJid = phoneJid;
        return user;
    }

    /**
     * Attaches a canonical JID alongside the primary addressing slot.
     *
     * @param id the canonical JID
     * @return this builder
     * @implNote WAWebUsyncUser.withId.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.withId", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncUser withId(Jid id) {
        this.id = id;
        return this;
    }

    /**
     * Attaches the LID identifier for this user.
     *
     * @param lid the LID JID
     * @return this builder
     * @implNote WAWebUsyncUser.withLid.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.withLid", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncUser withLid(Jid lid) {
        this.lid = lid;
        return this;
    }

    /**
     * Attaches a phone-JID hint.
     *
     * @param phoneJid the phone-number JID
     * @return this builder
     * @implNote WAWebUsyncUser.withPnJid.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.withPnJid", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncUser withPhoneJid(Jid phoneJid) {
        this.phoneJid = phoneJid;
        return this;
    }

    /**
     * Sets the cached device-list hash. Used by the device protocol to
     * obtain a {@code <devices type="omitted">} response when the local
     * cache is still in sync with the server.
     *
     * @param deviceHash the hash, base64-encoded
     * @return this builder
     * @implNote WAWebUsyncUser.withDeviceHash. Cobalt accepts any non-blank
     *     string; WhatsApp Web pre-filters through
     *     {@code WAWebNonEmptyString.asMaybeNonEmptyString} which collapses
     *     empty strings to {@code undefined}.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.withDeviceHash", adaptation = WhatsAppAdaptation.ADAPTED)
    public UsyncUser withDeviceHash(String deviceHash) {
        this.deviceHash = (deviceHash == null || deviceHash.isBlank()) ? null : deviceHash;
        return this;
    }

    /**
     * Sets the timestamp the local cache last refreshed at.
     *
     * @param timestamp the cache timestamp
     * @return this builder
     * @implNote WAWebUsyncUser.withTs: the JS slot stores Unix epoch
     *     seconds; Cobalt promotes it to {@link Instant}.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.withTs", adaptation = WhatsAppAdaptation.ADAPTED)
    public UsyncUser withTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    /**
     * Sets the expected timestamp the relay should compare against.
     *
     * @param expectedTimestamp the expected timestamp
     * @return this builder
     * @implNote WAWebUsyncUser.withExpectedTs.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.withExpectedTs", adaptation = WhatsAppAdaptation.ADAPTED)
    public UsyncUser withExpectedTimestamp(Instant expectedTimestamp) {
        this.expectedTimestamp = expectedTimestamp;
        return this;
    }

    /**
     * Sets the persona id used by the bot-profile protocol.
     *
     * @param personaId the persona identifier
     * @return this builder
     * @implNote WAWebUsyncUser.withPersonaId.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.withPersonaId", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncUser withPersonaId(String personaId) {
        this.personaId = personaId;
        return this;
    }

    /**
     * Sets the username PIN accompanying a username addressing scheme.
     *
     * @param pin the username PIN
     * @return this builder
     * @implNote WAWebUsyncUser.withUsernameKey: the JS getter is named
     *     {@code getPin}; Cobalt unifies the name to {@code pin} on both
     *     sides.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.withUsernameKey", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncUser withPin(String pin) {
        this.pin = pin;
        return this;
    }

    /**
     * Sets the contact-protocol type discriminator.
     *
     * @param contactType the type literal (e.g. {@code "in"}, {@code "out"})
     * @return this builder
     * @implNote WAWebUsyncUser.withType.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.withType", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncUser withContactType(String contactType) {
        this.contactType = contactType;
        return this;
    }

    /**
     * Sets the trusted-contact token attached to status queries.
     *
     * @param trustedContactToken the raw token bytes
     * @return this builder
     * @implNote WAWebUsyncUser.withTcToken.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.withTcToken", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncUser withTrustedContactToken(byte[] trustedContactToken) {
        this.trustedContactToken = trustedContactToken;
        return this;
    }

    /** @return the canonical JID, when present */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.getId", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<Jid> id() {
        return Optional.ofNullable(id);
    }

    /** @return the LID, when present */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.getLid", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<Jid> lid() {
        return Optional.ofNullable(lid);
    }

    /** @return the phone-number JID, when present */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.getPnJid", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<Jid> phoneJid() {
        return Optional.ofNullable(phoneJid);
    }

    /** @return the phone number, when present */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.getPhone", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<String> phoneNumber() {
        return Optional.ofNullable(phoneNumber);
    }

    /** @return the cached device-list hash, when present */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.getDeviceHash", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<String> deviceHash() {
        return Optional.ofNullable(deviceHash);
    }

    /** @return the cache timestamp, when present */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.getTs", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<Instant> timestamp() {
        return Optional.ofNullable(timestamp);
    }

    /** @return the expected timestamp, when present */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.getExpectedTs", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<Instant> expectedTimestamp() {
        return Optional.ofNullable(expectedTimestamp);
    }

    /** @return the persona id, when present */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.getPersonaId", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<String> personaId() {
        return Optional.ofNullable(personaId);
    }

    /** @return the username, when present */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.getUsername", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<String> username() {
        return Optional.ofNullable(username);
    }

    /** @return the username PIN, when present */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.getPin", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<String> pin() {
        return Optional.ofNullable(pin);
    }

    /** @return the contact-protocol type, when present */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.getType", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<String> contactType() {
        return Optional.ofNullable(contactType);
    }

    /** @return the trusted-contact token, when present */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUser",
            exports = "USyncUser.getTcToken", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<byte[]> trustedContactToken() {
        return Optional.ofNullable(trustedContactToken);
    }
}
