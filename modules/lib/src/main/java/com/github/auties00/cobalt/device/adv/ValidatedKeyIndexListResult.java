package com.github.auties00.cobalt.device.adv;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;

import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedSet;

/**
 * Decoded payload of a signed key index list after cryptographic validation.
 *
 * <p>A signed key index list accompanies every device list update and declares which
 * companion-device key indexes the account considers legitimate. After Cobalt
 * verifies the protobuf against the primary account's signature key, the validated
 * payload is exposed through this container so downstream code can reason about
 * companion device validity, account type (E2EE or HOSTED), and identity rotation
 * state without re-decoding the protobuf.
 *
 * @see DeviceADVValidator#decodeSignedKeyIndexBytes(com.github.auties00.cobalt.model.jid.Jid, byte[])
 * @see DeviceADVValidator#verifySKeyIndexWithAccSigKey(byte[])
 */
@WhatsAppWebModule(moduleName = "WAWebHandleAdvDeviceNotificationUtils")
public final class ValidatedKeyIndexListResult {
    /**
     * The raw identity id from the key index list.
     */
    private final long rawId;

    /**
     * The timestamp recorded inside the key index list.
     */
    private final Instant timestamp;

    /**
     * The set of key indexes the account currently considers valid.
     */
    private final SequencedSet<Integer> validIndexes;

    /**
     * The current key index counter.
     */
    private final int currentIndex;

    /**
     * The encryption type of the account (E2EE or HOSTED).
     */
    private final ADVEncryptionType accountType;

    /**
     * The 32-byte account signature key from the outer signed wrapper, or {@code null}
     * when the standard E2EE path verified against the locally-stored primary identity
     * and therefore has no embedded key to forward.
     */
    private final byte[] accountSignatureKey;

    /**
     * Constructs a new validated key index list result.
     *
     * @param rawId               the raw identity id
     * @param timestamp           the key index list timestamp
     * @param validIndexes        the set of valid key indexes
     * @param currentIndex        the current key index counter
     * @param accountType         the account encryption type
     * @param accountSignatureKey the 32-byte account signature key, or {@code null}
     *                            when the standard path verified against the locally-stored
     *                            primary identity
     * @throws NullPointerException if any of {@code timestamp}, {@code validIndexes}
     *                              or {@code accountType} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvDeviceNotificationUtils",
            exports = "verifySKeyIndexWithAccSigKey",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public ValidatedKeyIndexListResult(
            long rawId,
            Instant timestamp,
            SequencedSet<Integer> validIndexes,
            int currentIndex,
            ADVEncryptionType accountType,
            byte[] accountSignatureKey
    ) {
        this.rawId = rawId;
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp cannot be null");
        this.validIndexes = Objects.requireNonNull(validIndexes, "validIndexes cannot be null");
        this.currentIndex = currentIndex;
        this.accountType = Objects.requireNonNull(accountType, "accountType cannot be null");
        this.accountSignatureKey = accountSignatureKey;
    }

    /**
     * Returns the raw identity id from the key index list.
     *
     * @return the raw identity id
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvDeviceNotificationUtils",
            exports = "verifySKeyIndexWithAccSigKey",
            adaptation = WhatsAppAdaptation.DIRECT)
    public long rawId() {
        return rawId;
    }

    /**
     * Returns the timestamp recorded inside the key index list.
     *
     * @return the timestamp
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvDeviceNotificationUtils",
            exports = "verifySKeyIndexWithAccSigKey",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Instant timestamp() {
        return timestamp;
    }

    /**
     * Returns the set of key indexes the account currently considers valid.
     *
     * @return an unmodifiable view of the valid indexes
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvDeviceNotificationUtils",
            exports = "verifySKeyIndexWithAccSigKey",
            adaptation = WhatsAppAdaptation.DIRECT)
    public SequencedSet<Integer> validIndexes() {
        return Collections.unmodifiableSequencedSet(validIndexes);
    }

    /**
     * Returns the current key index counter.
     *
     * @return the current index
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvDeviceNotificationUtils",
            exports = "verifySKeyIndexWithAccSigKey",
            adaptation = WhatsAppAdaptation.DIRECT)
    public int currentIndex() {
        return currentIndex;
    }

    /**
     * Returns the account encryption type, either {@code E2EE} or {@code HOSTED}.
     *
     * @return the account type
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvDeviceNotificationUtils",
            exports = "verifySKeyIndexWithAccSigKey",
            adaptation = WhatsAppAdaptation.DIRECT)
    public ADVEncryptionType accountType() {
        return accountType;
    }

    /**
     * Returns the 32-byte account signature key from the outer signed wrapper.
     *
     * <p>Only populated by the hosted-business path that mirrors WA Web's
     * {@code verifySKeyIndexWithAccSigKey}; the standard E2EE path verifies against the
     * locally-stored primary identity and leaves this empty.
     *
     * @return the account signature key, or empty when not provided
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvDeviceNotificationUtils",
            exports = "verifySKeyIndexWithAccSigKey",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<byte[]> accountSignatureKey() {
        return Optional.ofNullable(accountSignatureKey);
    }
}
