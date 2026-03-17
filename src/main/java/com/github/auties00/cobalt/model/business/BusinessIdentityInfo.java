package com.github.auties00.cobalt.model.business;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Identity and verification metadata for a WhatsApp Business account.
 *
 * <p>This message describes the verification state of a business, including
 * its {@link #verificationLevel()}, its {@link #verifiedNameCertificate()},
 * the privacy mode configuration ({@link #actualActors()},
 * {@link #hostStorage()}, {@link #privacyModeTimestamp()}), and whether
 * the identity has been {@link #signed()} or {@link #revoked()}.
 */
@ProtobufMessage(name = "BizIdentityInfo")
public final class BusinessIdentityInfo {
    /**
     * The verification level of this business identity.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    VerificationLevel verificationLevel;

    /**
     * The verified name certificate associated with this business identity.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    BusinessVerifiedNameCertificate verifiedNameCertificate;

    /**
     * Whether this identity information has been cryptographically signed.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.BOOL)
    Boolean signed;

    /**
     * Whether this business identity has been revoked.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
    Boolean revoked;

    /**
     * The type of infrastructure hosting the business data.
     */
    @ProtobufProperty(index = 5, type = ProtobufType.ENUM)
    HostStorageType hostStorage;

    /**
     * The entity that actually processes messages on behalf of the business.
     */
    @ProtobufProperty(index = 6, type = ProtobufType.ENUM)
    ActualActorsType actualActors;

    /**
     * The instant at which the privacy mode was last changed, in epoch seconds.
     *
     * <p>Together with {@link #actualActors()} and {@link #hostStorage()}, this
     * field forms the privacy mode triplet used to determine the business's
     * messaging privacy level (E2EE, BSP, Meta-hosted, or Cloud API).
     */
    @ProtobufProperty(index = 7, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    Instant privacyModeTimestamp;

    /**
     * A bitmask of feature control flags for this business identity.
     */
    @ProtobufProperty(index = 8, type = ProtobufType.UINT64)
    Long featureControls;

    /**
     * Constructs a new {@code BizIdentityInfo}.
     *
     * @param verificationLevel     the verification level
     * @param verifiedNameCertificate the verified name certificate
     * @param signed                whether the identity is signed
     * @param revoked               whether the identity is revoked
     * @param hostStorage           the hosting infrastructure type
     * @param actualActors          the entity processing messages
     * @param privacyModeTimestamp  the privacy mode change timestamp
     * @param featureControls       the feature control bitmask
     */
    BusinessIdentityInfo(VerificationLevel verificationLevel, BusinessVerifiedNameCertificate verifiedNameCertificate, Boolean signed, Boolean revoked, HostStorageType hostStorage, ActualActorsType actualActors, Instant privacyModeTimestamp, Long featureControls) {
        this.verificationLevel = verificationLevel;
        this.verifiedNameCertificate = verifiedNameCertificate;
        this.signed = signed;
        this.revoked = revoked;
        this.hostStorage = hostStorage;
        this.actualActors = actualActors;
        this.privacyModeTimestamp = privacyModeTimestamp;
        this.featureControls = featureControls;
    }

    /**
     * Returns the verification level of this business identity.
     *
     * @return an {@code Optional} containing the {@link VerificationLevel},
     *         or empty if not set
     */
    public Optional<VerificationLevel> verificationLevel() {
        return Optional.ofNullable(verificationLevel);
    }

    /**
     * Returns the verified name certificate associated with this identity.
     *
     * @return an {@code Optional} containing the {@link BusinessVerifiedNameCertificate},
     *         or empty if not set
     */
    public Optional<BusinessVerifiedNameCertificate> verifiedNameCertificate() {
        return Optional.ofNullable(verifiedNameCertificate);
    }

    /**
     * Returns whether this identity information has been cryptographically signed.
     *
     * @return {@code true} if the identity is signed, {@code false} otherwise
     */
    public boolean signed() {
        return signed != null && signed;
    }

    /**
     * Returns whether this business identity has been revoked.
     *
     * @return {@code true} if the identity is revoked, {@code false} otherwise
     */
    public boolean revoked() {
        return revoked != null && revoked;
    }

    /**
     * Returns the type of infrastructure hosting the business data.
     *
     * @return an {@code Optional} containing the {@link HostStorageType},
     *         or empty if not set
     */
    public Optional<HostStorageType> hostStorage() {
        return Optional.ofNullable(hostStorage);
    }

    /**
     * Returns the entity that actually processes messages on behalf of the business.
     *
     * @return an {@code Optional} containing the {@link ActualActorsType},
     *         or empty if not set
     */
    public Optional<ActualActorsType> actualActors() {
        return Optional.ofNullable(actualActors);
    }

    /**
     * Returns the instant at which the privacy mode was last changed.
     *
     * @return an {@code Optional} containing the privacy mode timestamp,
     *         or empty if not set
     */
    public Optional<Instant> privacyModeTimestamp() {
        return Optional.ofNullable(privacyModeTimestamp);
    }

    /**
     * Returns the feature control bitmask for this business identity.
     *
     * @return the bitmask value, or empty if not set
     */
    public OptionalLong featureControls() {
        return featureControls == null ? OptionalLong.empty() : OptionalLong.of(featureControls);
    }

    /**
     * Sets the verification level.
     *
     * @param verificationLevel the {@link VerificationLevel} to set
     */
    public void setVerificationLevel(VerificationLevel verificationLevel) {
        this.verificationLevel = verificationLevel;
    }

    /**
     * Sets the verified name certificate.
     *
     * @param verifiedNameCertificate the {@link BusinessVerifiedNameCertificate} to set
     */
    public void setVerifiedNameCertificate(BusinessVerifiedNameCertificate verifiedNameCertificate) {
        this.verifiedNameCertificate = verifiedNameCertificate;
    }

    /**
     * Sets whether this identity is cryptographically signed.
     *
     * @param signed {@code true} if signed
     */
    public void setSigned(Boolean signed) {
        this.signed = signed;
    }

    /**
     * Sets whether this identity has been revoked.
     *
     * @param revoked {@code true} if revoked
     */
    public void setRevoked(Boolean revoked) {
        this.revoked = revoked;
    }

    /**
     * Sets the hosting infrastructure type.
     *
     * @param hostStorage the {@link HostStorageType} to set
     */
    public void setHostStorage(HostStorageType hostStorage) {
        this.hostStorage = hostStorage;
    }

    /**
     * Sets the entity that processes messages on behalf of the business.
     *
     * @param actualActors the {@link ActualActorsType} to set
     */
    public void setActualActors(ActualActorsType actualActors) {
        this.actualActors = actualActors;
    }

    /**
     * Sets the privacy mode change timestamp.
     *
     * @param privacyModeTimestamp the timestamp to set
     */
    public void setPrivacyModeTimestamp(Instant privacyModeTimestamp) {
        this.privacyModeTimestamp = privacyModeTimestamp;
    }

    /**
     * Sets the feature control bitmask.
     *
     * @param featureControls the bitmask value to set
     */
    public void setFeatureControls(Long featureControls) {
        this.featureControls = featureControls;
    }

    /**
     * The entity that actually processes messages on behalf of the business.
     *
     * <p>This determines the messaging privacy level:
     * <ul>
     * <li>{@link #SELF} &mdash; the business itself handles messages (end-to-end encrypted).
     * <li>{@link #BSP} &mdash; a Business Solution Provider handles messages on
     *     behalf of the business.
     * </ul>
     */
    @ProtobufEnum(name = "BizIdentityInfo.ActualActorsType")
    public enum ActualActorsType {
        /**
         * The business itself processes messages directly (end-to-end encrypted).
         */
        SELF(0),

        /**
         * A Business Solution Provider (BSP) processes messages on behalf of
         * the business.
         */
        BSP(1);

        ActualActorsType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        /**
         * Returns the protobuf index of this enum constant.
         *
         * @return the protobuf index
         */
        public int index() {
            return this.index;
        }
    }

    /**
     * The type of infrastructure hosting the business account data.
     */
    @ProtobufEnum(name = "BizIdentityInfo.HostStorageType")
    public enum HostStorageType {
        /**
         * Data is hosted on the business's own infrastructure (on-premises).
         */
        ON_PREMISE(0),

        /**
         * Data is hosted on Meta (Facebook) infrastructure.
         */
        FACEBOOK(1);

        HostStorageType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        /**
         * Returns the protobuf index of this enum constant.
         *
         * @return the protobuf index
         */
        public int index() {
            return this.index;
        }
    }

    /**
     * The verification level of a WhatsApp Business identity.
     *
     * <p>Higher verification levels indicate stronger identity verification
     * performed by WhatsApp.
     */
    @ProtobufEnum(name = "BizIdentityInfo.VerifiedLevelValue")
    public enum VerificationLevel {
        /**
         * The verification level is unknown or has not been determined.
         */
        UNKNOWN(0),

        /**
         * A low level of verification, indicating basic identity checks.
         */
        LOW(1),

        /**
         * A high level of verification, indicating thorough identity validation.
         */
        HIGH(2);

        VerificationLevel(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        /**
         * Returns the protobuf index of this enum constant.
         *
         * @return the protobuf index
         */
        public int index() {
            return this.index;
        }
    }
}
