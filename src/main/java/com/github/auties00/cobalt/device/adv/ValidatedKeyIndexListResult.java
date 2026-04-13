package com.github.auties00.cobalt.device.adv;

import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;

import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.SequencedSet;

/**
 * Result of validating a signed key index list protobuf.
 *
 * @implNote WAWebHandleAdvDeviceNotificationUtils.verifySKeyIndexWithAccSigKey: decodes
 * and validates the signed key index list, extracting these fields after signature verification.
 * The WA Web return value also includes an {@code identityUpdatePromise} for saving the identity
 * key, which in Cobalt is handled by the caller ({@code DeviceService}).
 */
public final class ValidatedKeyIndexListResult {
    private final long rawId;
    private final Instant timestamp;
    private final SequencedSet<Integer> validIndexes;
    private final int currentIndex;
    private final ADVEncryptionType accountType;
    private final byte[] accountSignatureKey;

    /**
     * Creates a new validated key index list result.
     *
     * @implNote WAWebHandleAdvDeviceNotificationUtils.verifySKeyIndexWithAccSigKey: constructs
     * the return object from decoded {@code ADVKeyIndexListSpec} fields plus the
     * {@code accountSignatureKey} from the outer {@code ADVSignedKeyIndexListSpec}.
     * @param rawId               the raw identity ID from the key index list
     * @param timestamp           the timestamp from the key index list
     * @param validIndexes        the list of valid key indexes for device validation
     * @param currentIndex        the current key index counter
     * @param accountType         the account type (E2EE or HOSTED)
     * @param accountSignatureKey the account signature key (32 bytes)
     */
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
        this.accountSignatureKey = Objects.requireNonNull(accountSignatureKey, "accountSignatureKey cannot be null");
    }

    /**
     * Returns the raw identity ID from the key index list.
     *
     * @implNote WAWebHandleAdvDeviceNotificationUtils.verifySKeyIndexWithAccSigKey:
     * {@code rawId} field from decoded {@code ADVKeyIndexListSpec}.
     * @return the raw identity ID
     */
    public long rawId() {
        return rawId;
    }

    /**
     * Returns the timestamp from the key index list.
     *
     * @implNote WAWebHandleAdvDeviceNotificationUtils.verifySKeyIndexWithAccSigKey:
     * {@code timestamp} field from decoded {@code ADVKeyIndexListSpec}.
     * @return the timestamp
     */
    public Instant timestamp() {
        return timestamp;
    }

    /**
     * Returns the list of valid key indexes for device validation.
     *
     * @implNote WAWebHandleAdvDeviceNotificationUtils.verifySKeyIndexWithAccSigKey:
     * {@code validIndexes} field from decoded {@code ADVKeyIndexListSpec}.
     * @return an unmodifiable view of the valid indexes
     */
    public SequencedSet<Integer> validIndexes() {
        return Collections.unmodifiableSequencedSet(validIndexes);
    }

    /**
     * Returns the current key index counter.
     *
     * @implNote WAWebHandleAdvDeviceNotificationUtils.verifySKeyIndexWithAccSigKey:
     * {@code currentIndex} field from decoded {@code ADVKeyIndexListSpec}.
     * @return the current index
     */
    public int currentIndex() {
        return currentIndex;
    }

    /**
     * Returns the account type (E2EE or HOSTED).
     *
     * @implNote WAWebHandleAdvDeviceNotificationUtils.verifySKeyIndexWithAccSigKey:
     * {@code accountType} field from decoded {@code ADVKeyIndexListSpec}.
     * @return the account type
     */
    public ADVEncryptionType accountType() {
        return accountType;
    }

    /**
     * Returns the account signature key (32 bytes).
     *
     * @implNote WAWebHandleAdvDeviceNotificationUtils.verifySKeyIndexWithAccSigKey:
     * {@code accountSignatureKey} field from outer {@code ADVSignedKeyIndexListSpec}.
     * @return the account signature key
     */
    public byte[] accountSignatureKey() {
        return accountSignatureKey;
    }
}
