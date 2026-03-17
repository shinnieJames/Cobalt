package com.github.auties00.cobalt.model.business;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.annotation.ProtobufSerializer;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * A signed certificate that attests a business's verified name on WhatsApp.
 *
 * <p>The certificate is composed of an opaque {@link #details()} payload
 * (a serialized {@link Details} message), a client {@link #signature()},
 * and a {@link #serverSignature()} produced by the WhatsApp server.
 * To inspect the certificate metadata, decode the {@code details} bytes
 * into a {@link Details} instance.
 *
 * @see Details
 */
@ProtobufMessage(name = "VerifiedNameCertificate")
public final class BusinessVerifiedNameCertificate {
    /**
     * The serialized {@link Details} payload of this certificate.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] details;

    /**
     * The client-side signature over the {@link #details()} payload.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] signature;

    /**
     * The server-side signature produced by WhatsApp over the {@link #details()} payload.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] serverSignature;

    /**
     * Constructs a new {@code VerifiedNameCertificate}.
     *
     * @param details         the serialized {@link Details} payload
     * @param signature       the client-side signature
     * @param serverSignature the server-side signature
     */
    BusinessVerifiedNameCertificate(byte[] details, byte[] signature, byte[] serverSignature) {
        this.details = details;
        this.signature = signature;
        this.serverSignature = serverSignature;
    }

    /**
     * Returns the serialized {@link Details} payload of this certificate.
     *
     * @return an {@code Optional} containing the details bytes, or empty if not set
     */
    public Optional<byte[]> details() {
        return Optional.ofNullable(details);
    }

    /**
     * Returns the client-side signature over the details payload.
     *
     * @return an {@code Optional} containing the signature bytes, or empty if not set
     */
    public Optional<byte[]> signature() {
        return Optional.ofNullable(signature);
    }

    /**
     * Returns the server-side signature produced by WhatsApp.
     *
     * @return an {@code Optional} containing the server signature bytes, or empty if not set
     */
    public Optional<byte[]> serverSignature() {
        return Optional.ofNullable(serverSignature);
    }

    /**
     * Sets the serialized {@link Details} payload.
     *
     * @param details the details bytes to set
     */
    public void setDetails(byte[] details) {
        this.details = details;
    }

    /**
     * Sets the client-side signature.
     *
     * @param signature the signature bytes to set
     */
    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    /**
     * Sets the server-side signature.
     *
     * @param serverSignature the server signature bytes to set
     */
    public void setServerSignature(byte[] serverSignature) {
        this.serverSignature = serverSignature;
    }

    /**
     * The decoded metadata of a {@link BusinessVerifiedNameCertificate}, containing the
     * business's verified name, serial number, issuer, localized name variants,
     * and the time the certificate was issued.
     */
    @ProtobufMessage(name = "VerifiedNameCertificate.Details")
    public static final class Details {
        /**
         * The serial number of this certificate, uniquely identifying it
         * within the issuer's domain.
         */
        @ProtobufProperty(index = 1, type = ProtobufType.UINT64)
        Long serial;

        /**
         * The issuer of this certificate, indicating the type of WhatsApp
         * Business account that produced it.
         */
        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        CertificateIssuer issuer;

        /**
         * The primary verified business name as approved by WhatsApp.
         */
        @ProtobufProperty(index = 4, type = ProtobufType.STRING)
        String verifiedName;

        /**
         * The localized variants of the verified business name, each targeting
         * a specific language and country combination.
         */
        @ProtobufProperty(index = 8, type = ProtobufType.MESSAGE)
        List<LocalizedName> localizedNames;

        /**
         * The instant at which this certificate was issued, in epoch seconds.
         */
        @ProtobufProperty(index = 10, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
        Instant issueTime;

        /**
         * Constructs a new {@code Details} with the specified fields.
         *
         * @param serial         the certificate serial number
         * @param issuer         the certificate issuer
         * @param verifiedName   the primary verified business name
         * @param localizedNames the localized name variants
         * @param issueTime      the time the certificate was issued
         */
        Details(Long serial, CertificateIssuer issuer, String verifiedName, List<LocalizedName> localizedNames, Instant issueTime) {
            this.serial = serial;
            this.issuer = issuer;
            this.verifiedName = verifiedName;
            this.localizedNames = localizedNames;
            this.issueTime = issueTime;
        }

        /**
         * Returns the serial number of this certificate.
         *
         * @return the serial number, or empty if not set
         */
        public OptionalLong serial() {
            return serial == null ? OptionalLong.empty() : OptionalLong.of(serial);
        }

        /**
         * Returns the issuer of this certificate.
         *
         * @return an {@code Optional} containing the {@link CertificateIssuer},
         *         or empty if not set
         */
        public Optional<CertificateIssuer> issuer() {
            return Optional.ofNullable(issuer);
        }

        /**
         * Returns the primary verified business name.
         *
         * @return an {@code Optional} containing the verified name, or empty if not set
         */
        public Optional<String> verifiedName() {
            return Optional.ofNullable(verifiedName);
        }

        /**
         * Returns the localized variants of the verified business name.
         *
         * @return an unmodifiable list of {@link LocalizedName} entries, never {@code null}
         */
        public List<LocalizedName> localizedNames() {
            return localizedNames == null ? List.of() : Collections.unmodifiableList(localizedNames);
        }

        /**
         * Returns the instant at which this certificate was issued.
         *
         * @return an {@code Optional} containing the issue time, or empty if not set
         */
        public Optional<Instant> issueTime() {
            return Optional.ofNullable(issueTime);
        }

        /**
         * Sets the serial number of this certificate.
         *
         * @param serial the serial number to set
         */
        public void setSerial(Long serial) {
            this.serial = serial;
    }

        /**
         * Sets the issuer of this certificate.
         *
         * @param issuer the {@link CertificateIssuer} to set
         */
        public void setIssuer(CertificateIssuer issuer) {
            this.issuer = issuer;
    }

        /**
         * Sets the primary verified business name.
         *
         * @param verifiedName the verified name to set
         */
        public void setVerifiedName(String verifiedName) {
            this.verifiedName = verifiedName;
    }

        /**
         * Sets the localized name variants.
         *
         * @param localizedNames the list of {@link LocalizedName} entries to set
         */
        public void setLocalizedNames(List<LocalizedName> localizedNames) {
            this.localizedNames = localizedNames;
    }

        /**
         * Sets the instant at which this certificate was issued.
         *
         * @param issueTime the issue time to set
         */
        public void setsueTime(Instant issueTime) {
            this.issueTime = issueTime;
    }
    }

    /**
     * A localized variant of a verified business name.
     *
     * <p>Each instance pairs a verified business name with its language and country,
     * allowing the same business to present its name in multiple locales.
     */
    @ProtobufMessage(name = "LocalizedName")
    public static final class LocalizedName {
        /**
         * The ISO 639-1 language code for this localized name, for example {@code "en"},
         * {@code "es"}, or {@code "pt"}.
         */
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String languageCode;

        /**
         * The ISO 3166-1 alpha-2 country code for this localized name, for example
         * {@code "US"}, {@code "BR"}, or {@code "GB"}.
         */
        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String countryCode;

        /**
         * The verified business name in the locale identified by {@link #languageCode()}
         * and {@link #countryCode()}.
         */
        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        String verifiedName;

        /**
         * Constructs a new {@code LocalizedName} with the specified language code, country code,
         * and verified name.
         *
         * @param languageCode the ISO 639-1 language code
         * @param countryCode  the ISO 3166-1 alpha-2 country code
         * @param verifiedName the verified business name in this locale
         */
        LocalizedName(String languageCode, String countryCode, String verifiedName) {
            this.languageCode = languageCode;
            this.countryCode = countryCode;
            this.verifiedName = verifiedName;
        }

        /**
         * Returns the ISO 639-1 language code for this localized name.
         *
         * @return an {@code Optional} containing the language code (e.g. {@code "en"}),
         *         or empty if not set
         */
        public Optional<String> languageCode() {
            return Optional.ofNullable(languageCode);
        }

        /**
         * Returns the ISO 3166-1 alpha-2 country code for this localized name.
         *
         * @return an {@code Optional} containing the country code (e.g. {@code "US"}),
         *         or empty if not set
         */
        public Optional<String> countryCode() {
            return Optional.ofNullable(countryCode);
        }

        /**
         * Returns the verified business name in the locale identified by
         * {@link #languageCode()} and {@link #countryCode()}.
         *
         * @return an {@code Optional} containing the localized verified name,
         *         or empty if not set
         */
        public Optional<String> verifiedName() {
            return Optional.ofNullable(verifiedName);
        }

        /**
         * Sets the ISO 639-1 language code for this localized name.
         *
         * @param languageCode the language code to set (e.g. {@code "en"})
         */
        public void setLanguageCode(String languageCode) {
            this.languageCode = languageCode;
    }

        /**
         * Sets the ISO 3166-1 alpha-2 country code for this localized name.
         *
         * @param countryCode the country code to set (e.g. {@code "US"})
         */
        public void setCountryCode(String countryCode) {
            this.countryCode = countryCode;
    }

        /**
         * Sets the verified business name in this locale.
         *
         * @param verifiedName the localized verified name to set
         */
        public void setVerifiedName(String verifiedName) {
            this.verifiedName = verifiedName;
    }
    }

    /**
     * The issuer of a {@link BusinessVerifiedNameCertificate}, identifying the type of
     * WhatsApp Business account that produced the certificate.
     *
     * <p>The WhatsApp platform distinguishes two issuer types:
     * <ul>
     * <li>{@link Enterprise} &mdash; certificates issued to businesses using the
     *     WhatsApp Business API (Cloud API or On-Premises API), identified by the
     *     wire value {@code "ent:wa"}.
     * <li>{@link SmallBusiness} &mdash; certificates issued to businesses using the
     *     WhatsApp Business App (SMB), identified by the wire value {@code "smb:wa"}.
     * </ul>
     */
    public sealed static interface CertificateIssuer {
        /**
         * Singleton for {@link Enterprise}.
         */
        Enterprise ENTERPRISE = new Enterprise();

        /**
         * Singleton for {@link SmallBusiness}.
         */
        SmallBusiness SMALL_BUSINESS = new SmallBusiness();

        /**
         * Returns the wire-format string value for this issuer.
         *
         * @return {@code "ent:wa"} for {@link Enterprise},
         *         {@code "smb:wa"} for {@link SmallBusiness}
         */
        @ProtobufSerializer
        String value();

        /**
         * Deserializes a wire-format string to the corresponding
         * {@code CertificateIssuer} subtype.
         *
         * @param value the wire value, may be {@code null}
         * @return the matching {@code CertificateIssuer}, or {@code null}
         *         if the input is {@code null}
         * @throws IllegalArgumentException if the value is not a recognized issuer
         */
        @ProtobufDeserializer
        static CertificateIssuer deserialize(String value) {
            if (value == null) {
                return null;
            }
            return switch (value) {
                case "ent:wa" -> new Enterprise();
                case "smb:wa" -> new SmallBusiness();
                default -> throw new IllegalArgumentException("Unknown certificate issuer: " + value);
            };
        }

        /**
         * A certificate issued to a business using the WhatsApp Business API
         * (Cloud API or On-Premises API).
         *
         * <p>The wire value is {@code "ent:wa"}.
         */
        final class Enterprise implements CertificateIssuer {
            private Enterprise() {

            }

            @Override
            public String value() {
                return "ent:wa";
            }

            @Override
            public String toString() {
                return value();
            }
        }

        /**
         * A certificate issued to a business using the WhatsApp Business App
         * (small and medium business).
         *
         * <p>The wire value is {@code "smb:wa"}.
         */
        final class SmallBusiness implements CertificateIssuer {
            private SmallBusiness() {

            }

            @Override
            public String value() {
                return "smb:wa";
            }

            @Override
            public String toString() {
                return value();
            }
        }
    }
}
