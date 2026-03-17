package com.github.auties00.cobalt.model.newsletter;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.*;

/**
 * The metadata of a newsletter, containing its name, description, pictures,
 * handle, settings, invite code, verification status, creation time,
 * subscriber count, privacy level, and other administrative information.
 */
@ProtobufMessage
public final class NewsletterMetadata {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    NewsletterName name;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    NewsletterDescription description;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    NewsletterPicture picture;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    String handle;

    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    NewsletterSettings settings;

    @ProtobufProperty(index = 6, type = ProtobufType.STRING)
    String invite;

    @ProtobufProperty(index = 7, type = ProtobufType.STRING)
    NewsletterVerification verification;

    @ProtobufProperty(index = 8, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    Instant creationTimestamp;

    @ProtobufProperty(index = 9, type = ProtobufType.UINT64)
    Long subscribersCount;

    @ProtobufProperty(index = 10, type = ProtobufType.ENUM)
    NewsletterPrivacy privacy;

    @ProtobufProperty(index = 11, type = ProtobufType.BOOL)
    boolean hasLinkedAccounts;

    @ProtobufProperty(index = 12, type = ProtobufType.UINT64)
    Long adminCount;

    @ProtobufProperty(index = 13, type = ProtobufType.BOOL)
    boolean terminated;

    @ProtobufProperty(index = 14, type = ProtobufType.STRING)
    List<String> geosuspendedCountries;

    @ProtobufProperty(index = 15, type = ProtobufType.ENUM)
    List<NewsletterCapability> capabilities;

    @ProtobufProperty(index = 16, type = ProtobufType.STRING)
    String wamoSubPlanId;

    @ProtobufProperty(index = 17, type = ProtobufType.MESSAGE)
    NewsletterPicture previewPicture;

    /**
     * Constructs a new {@code NewsletterMetadata} with the specified fields.
     *
     * @param name                  the newsletter name, may be {@code null}
     * @param description           the newsletter description, may be {@code null}
     * @param picture               the full-resolution picture, may be {@code null}
     * @param handle                the newsletter handle, may be {@code null}
     * @param settings              the newsletter settings, may be {@code null}
     * @param invite                the invite code, may be {@code null}
     * @param verification          the verification status, may be {@code null}
     * @param creationTimestamp     the creation timestamp, may be {@code null}
     * @param subscribersCount      the number of subscribers, may be {@code null}
     * @param privacy               the privacy setting, may be {@code null}
     * @param hasLinkedAccounts     whether the newsletter has linked accounts
     * @param adminCount            the number of admins, may be {@code null}
     * @param terminated            whether the newsletter is terminated
     * @param geosuspendedCountries the list of countries where the newsletter is geo-suspended, may be {@code null}
     * @param capabilities          the list of enabled capabilities, may be {@code null}
     * @param wamoSubPlanId         the WAMO subscription plan identifier, may be {@code null}
     * @param previewPicture        the preview-resolution picture, may be {@code null}
     */
    NewsletterMetadata(NewsletterName name, NewsletterDescription description, NewsletterPicture picture, String handle, NewsletterSettings settings, String invite, NewsletterVerification verification, Instant creationTimestamp, Long subscribersCount, NewsletterPrivacy privacy, boolean hasLinkedAccounts, Long adminCount, boolean terminated, List<String> geosuspendedCountries, List<NewsletterCapability> capabilities, String wamoSubPlanId, NewsletterPicture previewPicture) {
        this.name = name;
        this.description = description;
        this.picture = picture;
        this.handle = handle;
        this.settings = settings;
        this.invite = invite;
        this.verification = verification;
        this.creationTimestamp = creationTimestamp;
        this.subscribersCount = subscribersCount;
        this.privacy = privacy;
        this.hasLinkedAccounts = hasLinkedAccounts;
        this.adminCount = adminCount;
        this.terminated = terminated;
        this.geosuspendedCountries = Objects.requireNonNullElseGet(geosuspendedCountries, ArrayList::new);
        this.capabilities = Objects.requireNonNullElseGet(capabilities, ArrayList::new);
        this.wamoSubPlanId = wamoSubPlanId;
        this.previewPicture = previewPicture;
    }

    /**
     * Returns the creation timestamp, if available.
     *
     * @return an {@link Optional} containing the creation timestamp,
     *         or empty if not set
     */
    public Optional<Instant> creationTimestamp() {
        return Optional.ofNullable(creationTimestamp);
    }

    /**
     * Returns the newsletter name, if available.
     *
     * @return an {@link Optional} containing the name, or empty if not set
     */
    public Optional<NewsletterName> name() {
        return Optional.ofNullable(name);
    }

    /**
     * Returns the newsletter description, if available.
     *
     * @return an {@link Optional} containing the description,
     *         or empty if not set
     */
    public Optional<NewsletterDescription> description() {
        return Optional.ofNullable(description);
    }

    /**
     * Returns the full-resolution newsletter picture, if available.
     *
     * @return an {@link Optional} containing the picture, or empty if not set
     */
    public Optional<NewsletterPicture> picture() {
        return Optional.ofNullable(picture);
    }

    /**
     * Returns the preview-resolution newsletter picture, if available.
     *
     * @return an {@link Optional} containing the preview picture,
     *         or empty if not set
     */
    public Optional<NewsletterPicture> previewPicture() {
        return Optional.ofNullable(previewPicture);
    }

    /**
     * Returns the newsletter handle, if available.
     *
     * @return an {@link Optional} containing the handle, or empty if not set
     */
    public Optional<String> handle() {
        return Optional.ofNullable(handle);
    }

    /**
     * Returns the newsletter settings, if available.
     *
     * @return an {@link Optional} containing the settings, or empty if not set
     */
    public Optional<NewsletterSettings> settings() {
        return Optional.ofNullable(settings);
    }

    /**
     * Returns the invite code, if available.
     *
     * @return an {@link Optional} containing the invite code, or empty if not set
     */
    public Optional<String> invite() {
        return Optional.ofNullable(invite);
    }

    /**
     * Returns the verification status, if available.
     *
     * @return an {@link Optional} containing the verification status,
     *         or empty if not set
     */
    public Optional<NewsletterVerification> verification() {
        return Optional.ofNullable(verification);
    }

    /**
     * Returns the subscriber count, if available.
     *
     * @return an {@link OptionalLong} containing the subscriber count,
     *         or empty if not set
     */
    public OptionalLong subscribersCount() {
        return subscribersCount == null ? OptionalLong.empty() : OptionalLong.of(subscribersCount);
    }

    /**
     * Returns the privacy setting, if available.
     *
     * @return an {@link Optional} containing the privacy setting,
     *         or empty if not set
     */
    public Optional<NewsletterPrivacy> privacy() {
        return Optional.ofNullable(privacy);
    }

    /**
     * Returns whether the newsletter has linked accounts.
     *
     * @return {@code true} if the newsletter has linked accounts
     */
    public boolean hasLinkedAccounts() {
        return hasLinkedAccounts;
    }

    /**
     * Returns the admin count, if available.
     *
     * @return an {@link OptionalLong} containing the admin count,
     *         or empty if not set
     */
    public OptionalLong adminCount() {
        return adminCount == null ? OptionalLong.empty() : OptionalLong.of(adminCount);
    }

    /**
     * Returns whether the newsletter has been terminated.
     *
     * @return {@code true} if the newsletter is terminated
     */
    public boolean terminated() {
        return terminated;
    }

    /**
     * Returns the list of countries where the newsletter is geo-suspended.
     *
     * @return an unmodifiable list of country codes, never {@code null}
     */
    public List<String> geosuspendedCountries() {
        return Collections.unmodifiableList(geosuspendedCountries);
    }

    /**
     * Returns the list of enabled capabilities.
     *
     * @return an unmodifiable list of capabilities, never {@code null}
     */
    public List<NewsletterCapability> capabilities() {
        return Collections.unmodifiableList(capabilities);
    }

    /**
     * Returns the WAMO subscription plan identifier, if available.
     *
     * @return an {@link Optional} containing the plan id, or empty if not set
     */
    public Optional<String> wamoSubPlanId() {
        return Optional.ofNullable(wamoSubPlanId);
    }

    /**
     * Sets the newsletter name.
     *
     * @param name the newsletter name
     */
    public void setName(NewsletterName name) {
        this.name = name;
    }

    /**
     * Sets the newsletter description.
     *
     * @param description the newsletter description
     */
    public void setDescription(NewsletterDescription description) {
        this.description = description;
    }

    /**
     * Sets the full-resolution newsletter picture.
     *
     * @param picture the newsletter picture
     */
    public void setPicture(NewsletterPicture picture) {
        this.picture = picture;
    }

    /**
     * Sets the preview-resolution newsletter picture.
     *
     * @param previewPicture the preview picture
     */
    public void setPreviewPicture(NewsletterPicture previewPicture) {
        this.previewPicture = previewPicture;
    }

    /**
     * Sets the newsletter handle.
     *
     * @param handle the handle
     */
    public void setHandle(String handle) {
        this.handle = handle;
    }

    /**
     * Sets the newsletter settings.
     *
     * @param settings the settings
     */
    public void setSettings(NewsletterSettings settings) {
        this.settings = settings;
    }

    /**
     * Sets the invite code.
     *
     * @param invite the invite code
     */
    public void setInvite(String invite) {
        this.invite = invite;
    }

    /**
     * Sets the verification status.
     *
     * @param verification the verification status
     */
    public void setVerification(NewsletterVerification verification) {
        this.verification = verification;
    }

    /**
     * Sets the creation timestamp.
     *
     * @param creationTimestamp the creation timestamp
     */
    public void setCreationTimestamp(Instant creationTimestamp) {
        this.creationTimestamp = creationTimestamp;
    }

    /**
     * Sets the subscriber count.
     *
     * @param subscribersCount the subscriber count
     */
    public void setSubscribersCount(Long subscribersCount) {
        this.subscribersCount = subscribersCount;
    }

    /**
     * Sets the privacy setting.
     *
     * @param privacy the privacy setting
     */
    public void setPrivacy(NewsletterPrivacy privacy) {
        this.privacy = privacy;
    }

    /**
     * Sets whether the newsletter has linked accounts.
     *
     * @param hasLinkedAccounts {@code true} if the newsletter has linked accounts
     */
    public void setHasLinkedAccounts(boolean hasLinkedAccounts) {
        this.hasLinkedAccounts = hasLinkedAccounts;
    }

    /**
     * Sets the admin count.
     *
     * @param adminCount the admin count
     */
    public void setAdminCount(Long adminCount) {
        this.adminCount = adminCount;
    }

    /**
     * Sets whether the newsletter is terminated.
     *
     * @param terminated {@code true} if the newsletter is terminated
     */
    public void setTerminated(boolean terminated) {
        this.terminated = terminated;
    }

    /**
     * Sets the list of countries where the newsletter is geo-suspended.
     *
     * @param geosuspendedCountries the list of country codes
     */
    public void setGeosuspendedCountries(List<String> geosuspendedCountries) {
        this.geosuspendedCountries = Objects.requireNonNullElseGet(geosuspendedCountries, ArrayList::new);
    }

    /**
     * Sets the list of enabled capabilities.
     *
     * @param capabilities the list of capabilities
     */
    public void setCapabilities(List<NewsletterCapability> capabilities) {
        this.capabilities = Objects.requireNonNullElseGet(capabilities, ArrayList::new);
    }

    /**
     * Sets the WAMO subscription plan identifier.
     *
     * @param wamoSubPlanId the plan id
     */
    public void setWamoSubPlanId(String wamoSubPlanId) {
        this.wamoSubPlanId = wamoSubPlanId;
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof NewsletterMetadata that
               && hasLinkedAccounts == that.hasLinkedAccounts
               && terminated == that.terminated
               && Objects.equals(name, that.name)
               && Objects.equals(description, that.description)
               && Objects.equals(picture, that.picture)
               && Objects.equals(previewPicture, that.previewPicture)
               && Objects.equals(handle, that.handle)
               && Objects.equals(settings, that.settings)
               && Objects.equals(invite, that.invite)
               && Objects.equals(verification, that.verification)
               && Objects.equals(creationTimestamp, that.creationTimestamp)
               && Objects.equals(subscribersCount, that.subscribersCount)
               && privacy == that.privacy
               && Objects.equals(adminCount, that.adminCount)
               && Objects.equals(geosuspendedCountries, that.geosuspendedCountries)
               && Objects.equals(capabilities, that.capabilities)
               && Objects.equals(wamoSubPlanId, that.wamoSubPlanId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, picture, previewPicture, handle, settings, invite, verification, creationTimestamp, subscribersCount, privacy, hasLinkedAccounts, adminCount, terminated, geosuspendedCountries, capabilities, wamoSubPlanId);
    }
}
