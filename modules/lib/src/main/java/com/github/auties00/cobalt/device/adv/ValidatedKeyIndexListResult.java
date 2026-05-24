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
 * Decoded payload of a signed key-index list after cryptographic validation.
 *
 * @apiNote
 * Returned by {@link DeviceADVValidator#decodeSignedKeyIndexBytes} and
 * {@link DeviceADVValidator#verifySKeyIndexWithAccSigKey}. Carries the fields
 * downstream device-list appliers need to reason about companion-device validity
 * (the {@link #validIndexes() validIndexes} set, the {@link #currentIndex()
 * currentIndex} counter, the {@link #accountType()} flag for hosted accounts,
 * and the {@link #timestamp()} / {@link #rawId()} pair that detects identity
 * rotation) without having to re-decode the protobuf or re-verify the signature.
 *
 * @see DeviceADVValidator#decodeSignedKeyIndexBytes(com.github.auties00.cobalt.model.jid.Jid, byte[])
 * @see DeviceADVValidator#verifySKeyIndexWithAccSigKey(byte[])
 */
@WhatsAppWebModule(moduleName = "WAWebHandleAdvDeviceNotificationUtils")
public final class ValidatedKeyIndexListResult {
    /**
     * The raw identity id lifted from the inner {@code ADVKeyIndexList.rawId}.
     */
    private final long rawId;

    /**
     * The snapshot timestamp lifted from the inner
     * {@code ADVKeyIndexList.timestamp}.
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
     * The account encryption type, defaulting to E2EE when absent.
     */
    private final ADVEncryptionType accountType;

    /**
     * The 32-byte account signature key from the outer signed wrapper, or
     * {@code null} when the standard E2EE path verified against the
     * locally-stored primary identity and therefore has no embedded key to
     * forward.
     */
    private final byte[] accountSignatureKey;

    /**
     * Constructs a new validated key-index list result.
     *
     * @apiNote
     * Built by {@link DeviceADVValidator}'s verify-and-build helper after the
     * signature has been verified and the inner protobuf decoded; callers do
     * not normally construct one directly.
     *
     * @param rawId               the raw identity id
     * @param timestamp           the key-index list timestamp
     * @param validIndexes        the set of valid key indexes
     * @param currentIndex        the current key index counter
     * @param accountType         the account encryption type
     * @param accountSignatureKey the 32-byte account signature key, or
     *                            {@code null} when the standard E2EE path
     *                            verified against the locally-stored primary
     *                            identity
     * @throws NullPointerException if any of {@code timestamp},
     *                              {@code validIndexes} or {@code accountType}
     *                              is {@code null}
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
     * Returns the raw identity id from the key-index list.
     *
     * @apiNote
     * Compared against the cached device list's {@code rawId} by the device-list
     * applier; a mismatch is interpreted as an identity rotation and the cached
     * record is cleared.
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
     * Returns the snapshot timestamp from the key-index list.
     *
     * @apiNote
     * The device-list applier compares this timestamp against the cached
     * snapshot timestamp; a backwards-going timestamp causes the slot to be
     * dropped. Also fed into the expected-timestamp tracking that gates the ADV
     * check job.
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
     * @apiNote
     * Used to filter the cached device list down to companions whose key index
     * still appears in the account's signed key-index list; companions whose
     * index is no longer valid are evicted.
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
     * @apiNote
     * Used by the device-list applier as the cutoff for accepting cached
     * companion entries whose own key index is greater than the counter: those
     * entries are retained because they represent rotations not yet reflected
     * in the signed list.
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
     * Returns the account encryption type, either E2EE or HOSTED.
     *
     * @apiNote
     * Drives the hosted business-coexistence branch in the device-list applier:
     * a HOSTED type signals that the user is a hosted business account and
     * triggers cache invalidation when the cached and incoming types disagree.
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
     * @apiNote
     * Populated only by the hosted business-coexistence path
     * ({@link DeviceADVValidator#verifySKeyIndexWithAccSigKey(byte[])}); the
     * standard E2EE path verifies against the locally-stored primary identity
     * and leaves this empty. When present, callers persist the key as the
     * primary identity for the user.
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
