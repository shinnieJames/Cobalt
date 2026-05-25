package com.github.auties00.cobalt.exception;

import com.github.auties00.cobalt.model.jid.Jid;

import java.util.Objects;

/**
 * Sealed root for failures verifying the cryptographic identity of a
 * companion device through Advanced Device Verification (ADV).
 *
 * Every device linked to a WhatsApp account carries a triple of signatures
 * the primary device produces when the companion is paired. Before Cobalt
 * accepts a prekey bundle or an encrypted message from a remote device, it
 * re-checks those signatures and an HMAC over the serialized device
 * identity. Any failure raises one of the nested subtypes, each carrying
 * the {@link #jid()} of the device that failed. The permits list is
 * closed, so a {@code switch} over a {@code WhatsAppAdvValidationException}
 * can be exhaustive.
 *
 * @apiNote
 * Catch this base type to react to every ADV failure mode at once; switch
 * on the concrete subtype when distinct recovery is needed per failure.
 * Because every subtype is fatal, the configured error handler typically
 * tears the affected peer relationship down rather than retrying.
 *
 * @implNote
 * This implementation always reports the failure as fatal: accepting an
 * unverified device would allow a third party to inject itself into the
 * end-to-end-encrypted exchange.
 *
 * @see MissingDeviceIdentity
 * @see EmptyDeviceIdentity
 * @see AccountSignatureFailed
 * @see DeviceSignatureFailed
 * @see HmacValidationFailed
 * @see CryptoError
 */
public sealed abstract class WhatsAppAdvValidationException extends WhatsAppException
        permits WhatsAppAdvValidationException.MissingDeviceIdentity,
                WhatsAppAdvValidationException.EmptyDeviceIdentity,
                WhatsAppAdvValidationException.AccountSignatureFailed,
                WhatsAppAdvValidationException.DeviceSignatureFailed,
                WhatsAppAdvValidationException.HmacValidationFailed,
                WhatsAppAdvValidationException.CryptoError {

    /**
     * The JID of the device whose identity could not be verified.
     */
    private final Jid jid;

    /**
     * Constructs a new ADV validation exception with the specified message and device JID.
     *
     * @param message the detail message describing the validation failure
     * @param jid     the JID of the device that failed validation
     * @throws NullPointerException if {@code jid} is {@code null}
     */
    protected WhatsAppAdvValidationException(String message, Jid jid) {
        super(message);
        this.jid = Objects.requireNonNull(jid, "jid cannot be null");
    }

    /**
     * Constructs a new ADV validation exception with a message, device JID, and cause.
     *
     * @param message the detail message describing the validation failure
     * @param jid     the JID of the device that failed validation
     * @param cause   the underlying cause of the validation failure
     * @throws NullPointerException if {@code jid} is {@code null}
     */
    protected WhatsAppAdvValidationException(String message, Jid jid, Throwable cause) {
        super(message, cause);
        this.jid = Objects.requireNonNull(jid, "jid cannot be null");
    }

    /**
     * Returns the JID of the device that failed ADV validation.
     *
     * @return the device JID, never {@code null}
     */
    public Jid jid() {
        return jid;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always returns {@code true}: every ADV
     * validation failure leaves the peer on the other end untrusted as a
     * party in the end-to-end-encrypted exchange.
     */
    @Override
    public boolean isFatal() {
        return true;
    }

    /**
     * Thrown when a prekey response or encrypted-prekey message omits the
     * device-identity payload Cobalt needs to start ADV validation.
     *
     * WhatsApp guarantees this payload accompanies every prekey bundle
     * served by an ADV-capable device. Its absence indicates either a
     * malformed response from the server or a peer that does not speak ADV.
     */
    public static final class MissingDeviceIdentity extends WhatsAppAdvValidationException {
        /**
         * Constructs a new missing device identity exception.
         *
         * @param jid the JID of the device whose identity is missing
         */
        public MissingDeviceIdentity(Jid jid) {
            super("Missing device-identity in prekey response for " + jid, jid);
        }
    }

    /**
     * Thrown when the device-identity payload is present but empty or
     * syntactically broken.
     *
     * Cobalt rejects such payloads at validation time rather than silently
     * accepting them, since an empty identity cannot carry the signatures
     * ADV verifies.
     */
    public static final class EmptyDeviceIdentity extends WhatsAppAdvValidationException {
        /**
         * Constructs a new empty device identity exception.
         *
         * @param jid the JID of the device with empty identity
         */
        public EmptyDeviceIdentity(Jid jid) {
            super("Empty device-identity node for " + jid, jid);
        }
    }

    /**
     * Thrown when the account signature in the device identity does not
     * verify against the account's identity key.
     *
     * The account signature is produced by the primary device when a
     * companion is paired. A mismatch means the companion was not
     * authorized by the account owner and must not be trusted as a peer.
     */
    public static final class AccountSignatureFailed extends WhatsAppAdvValidationException {
        /**
         * Constructs a new account signature failed exception.
         *
         * @param jid the JID of the device whose account signature failed
         */
        public AccountSignatureFailed(Jid jid) {
            super("ADV account signature verification failed for " + jid, jid);
        }
    }

    /**
     * Thrown when the device signature in the device identity does not
     * verify against the device's own public key.
     *
     * The device signature is produced by the companion device itself to
     * prove it possesses the private key matching the public key it
     * announces. A mismatch means the device cannot prove that ownership.
     */
    public static final class DeviceSignatureFailed extends WhatsAppAdvValidationException {
        /**
         * Constructs a new device signature failed exception.
         *
         * @param jid the JID of the device whose device signature failed
         */
        public DeviceSignatureFailed(Jid jid) {
            super("ADV device signature verification failed for " + jid, jid);
        }
    }

    /**
     * Thrown when the HMAC stamped over the serialized device identity
     * does not match the value Cobalt computes from the shared secret.
     *
     * The HMAC protects the identity payload from in-flight tampering. A
     * mismatch means the bytes were modified or the verification key is out
     * of sync with the primary device's view.
     */
    public static final class HmacValidationFailed extends WhatsAppAdvValidationException {
        /**
         * Constructs a new HMAC validation failed exception.
         *
         * @param jid the JID of the device whose HMAC validation failed
         */
        public HmacValidationFailed(Jid jid) {
            super("ADV HMAC validation failed for " + jid, jid);
        }
    }

    /**
     * Thrown when a low-level cryptographic operation fails while
     * validating ADV signatures.
     *
     * Wraps unexpected JCE failures such as malformed key encodings,
     * missing algorithms, or provider misconfiguration as the
     * {@linkplain Throwable#getCause() cause}.
     *
     * @apiNote
     * Distinguishes a genuine signature mismatch (the other subtypes) from
     * an environment problem on the local machine, which a caller may want
     * to surface or escalate differently.
     */
    public static final class CryptoError extends WhatsAppAdvValidationException {
        /**
         * Constructs a new crypto error exception.
         *
         * @param jid   the JID of the device being validated when the error occurred
         * @param cause the underlying cryptographic exception
         */
        public CryptoError(Jid jid, Throwable cause) {
            super("ADV cryptographic operation failed for " + jid, jid, cause);
        }
    }
}
