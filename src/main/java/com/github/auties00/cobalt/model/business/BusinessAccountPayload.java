package com.github.auties00.cobalt.model.business;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * A payload that bundles a {@link BusinessVerifiedNameCertificate} with the serialized
 * account link information for a WhatsApp Business account.
 *
 * <p>This message is used during business account provisioning to transmit
 * both the verified name certificate and the raw bytes of a
 * {@link AccountLinkInfo} in a single protobuf envelope.
 */
@ProtobufMessage(name = "BizAccountPayload")
public final class BusinessAccountPayload {
    /**
     * The verified name certificate for this business account.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    BusinessVerifiedNameCertificate verifiedNameCertificate;

    /**
     * The serialized {@link AccountLinkInfo} bytes linking this WhatsApp
     * account to its Facebook Business account.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] accountLinkInfo;

    /**
     * Constructs a new {@code BusinessAccountPayload}.
     *
     * @param verifiedNameCertificate the verified name certificate
     * @param accountLinkInfo         the serialized account link info bytes
     */
    BusinessAccountPayload(BusinessVerifiedNameCertificate verifiedNameCertificate, byte[] accountLinkInfo) {
        this.verifiedNameCertificate = verifiedNameCertificate;
        this.accountLinkInfo = accountLinkInfo;
    }

    /**
     * Returns the verified name certificate for this business account.
     *
     * @return an {@code Optional} containing the certificate, or empty if not set
     */
    public Optional<BusinessVerifiedNameCertificate> verifiedNameCertificate() {
        return Optional.ofNullable(verifiedNameCertificate);
    }

    /**
     * Returns the serialized account link info bytes.
     *
     * @return an {@code Optional} containing the raw bytes, or empty if not set
     */
    public Optional<byte[]> accountLinkInfo() {
        return Optional.ofNullable(accountLinkInfo);
    }

    /**
     * Sets the verified name certificate.
     *
     * @param verifiedNameCertificate the certificate to set
     */
    public void setVerifiedNameCertificate(BusinessVerifiedNameCertificate verifiedNameCertificate) {
        this.verifiedNameCertificate = verifiedNameCertificate;
    }

    /**
     * Sets the serialized account link info bytes.
     *
     * @param accountLinkInfo the raw bytes to set
     */
    public void setAccountLinkInfo(byte[] accountLinkInfo) {
        this.accountLinkInfo = accountLinkInfo;
    }

    /**
     * Information that links a WhatsApp account to a Facebook Business account.
     *
     * <p>This message associates a WhatsApp phone number with its corresponding
     * Facebook Business ID, records the time the link was established, and
     * specifies the hosting and account type configuration.
     */
    @ProtobufMessage(name = "BizAccountLinkInfo")
    public static final class AccountLinkInfo {
        /**
         * The Facebook Business ID (FBID) of the linked WhatsApp Business account,
         * for example {@code 123456789012345}.
         */
        @ProtobufProperty(index = 1, type = ProtobufType.UINT64)
        Long facebookBusinessId;

        /**
         * The WhatsApp phone number associated with this business account,
         * for example {@code "15551234567"}.
         */
        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String phoneNumber;

        /**
         * The instant at which the account link was established, in epoch seconds.
         */
        @ProtobufProperty(index = 3, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
        Instant issueTime;

        /**
         * The type of infrastructure hosting the business data.
         */
        @ProtobufProperty(index = 4, type = ProtobufType.ENUM)
        HostStorageType hostStorage;

        /**
         * The type of business account.
         */
        @ProtobufProperty(index = 5, type = ProtobufType.ENUM)
        AccountType accountType;

        /**
         * Constructs a new {@code BizAccountLinkInfo}.
         *
         * @param facebookBusinessId the Facebook Business ID
         * @param phoneNumber        the WhatsApp phone number
         * @param issueTime          the time the link was established
         * @param hostStorage        the hosting infrastructure type
         * @param accountType        the business account type
         */
        AccountLinkInfo(Long facebookBusinessId, String phoneNumber, Instant issueTime, HostStorageType hostStorage, AccountType accountType) {
            this.facebookBusinessId = facebookBusinessId;
            this.phoneNumber = phoneNumber;
            this.issueTime = issueTime;
            this.hostStorage = hostStorage;
            this.accountType = accountType;
        }

        /**
         * Returns the Facebook Business ID of the linked account.
         *
         * @return the FBID, or empty if not set
         */
        public OptionalLong facebookBusinessId() {
            return facebookBusinessId == null ? OptionalLong.empty() : OptionalLong.of(facebookBusinessId);
        }

        /**
         * Returns the WhatsApp phone number associated with this business account.
         *
         * @return an {@code Optional} containing the phone number (e.g. {@code "15551234567"}),
         *         or empty if not set
         */
        public Optional<String> phoneNumber() {
            return Optional.ofNullable(phoneNumber);
        }

        /**
         * Returns the instant at which the account link was established.
         *
         * @return an {@code Optional} containing the issue time, or empty if not set
         */
        public Optional<Instant> issueTime() {
            return Optional.ofNullable(issueTime);
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
         * Returns the type of business account.
         *
         * @return an {@code Optional} containing the {@link AccountType},
         *         or empty if not set
         */
        public Optional<AccountType> accountType() {
            return Optional.ofNullable(accountType);
        }

        /**
         * Sets the Facebook Business ID.
         *
         * @param facebookBusinessId the FBID to set
         */
        public void setFacebookBusinessId(Long facebookBusinessId) {
            this.facebookBusinessId = facebookBusinessId;
    }

        /**
         * Sets the WhatsApp phone number.
         *
         * @param phoneNumber the phone number to set
         */
        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
    }

        /**
         * Sets the instant at which the account link was established.
         *
         * @param issueTime the issue time to set
         */
        public void setsueTime(Instant issueTime) {
            this.issueTime = issueTime;
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
         * Sets the business account type.
         *
         * @param accountType the {@link AccountType} to set
         */
        public void setAccountType(AccountType accountType) {
            this.accountType = accountType;
    }

        /**
         * The type of WhatsApp Business account.
         */
        @ProtobufEnum(name = "BizAccountLinkInfo.AccountType")
        public enum AccountType {
            /**
             * An enterprise-tier WhatsApp Business account, typically using the
             * WhatsApp Business API.
             */
            ENTERPRISE(0);

            AccountType(@ProtobufEnumIndex int index) {
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
        @ProtobufEnum(name = "BizAccountLinkInfo.HostStorageType")
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
    }
}
