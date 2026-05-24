package com.github.auties00.cobalt.device.icdc;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Identity Change Detection Consistency data derived from a single user's
 * device list.
 *
 * @apiNote
 * Produced by {@link IcdcComputer#compute(com.github.auties00.cobalt.model.jid.Jid)}
 * and consumed by the outbound message encoder to populate the
 * {@code deviceListMetadata} field of {@code messageContextInfo} on every
 * outgoing multi-device message. Carries the four fields the recipient compares
 * against its own view: the truncated SHA-256 hash of companion identity keys,
 * the device-list snapshot timestamp, the indexes of devices included in the
 * hash (omitted when every device was included), and the hosted account type
 * when applicable.
 */
@WhatsAppWebModule(moduleName = "WAWebIdentityIcdcApi")
public final class IcdcResult {

    /**
     * The truncated SHA-256 hash of the sorted, concatenated identity keys, or
     * {@code null} when the user has no companion devices.
     */
    private final byte[] keyHash;

    /**
     * The device-list snapshot timestamp, or {@code null} when the user has no
     * companion devices and the timestamp is older than
     * {@link IcdcComputer}'s recent threshold.
     */
    private final Instant timestamp;

    /**
     * The indexes of devices whose identity keys were successfully retrieved,
     * or {@code null} when every device was included.
     */
    private final List<Integer> keyIndexes;

    /**
     * The hosted account encryption type, or {@code null} for non-hosted
     * accounts.
     */
    private final ADVEncryptionType accountType;

    /**
     * Constructs a new ICDC result.
     *
     * @apiNote
     * Package-private constructor invoked only by
     * {@link IcdcComputer#computeFromDeviceList} and, on the test classpath, by
     * {@link com.github.auties00.cobalt.device.icdc.TestIcdcResults}.
     *
     * @param keyHash     the truncated identity key hash, or {@code null}
     * @param timestamp   the device-list snapshot timestamp, or {@code null}
     * @param keyIndexes  the indexes of included devices, or {@code null}
     * @param accountType the hosted account encryption type, or {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebIdentityIcdcApi",
            exports = "getICDCMetaFromDeviceRecord",
            adaptation = WhatsAppAdaptation.ADAPTED)
    IcdcResult(
            byte[] keyHash,
            Instant timestamp,
            List<Integer> keyIndexes,
            ADVEncryptionType accountType
    ) {
        this.keyHash = keyHash;
        this.timestamp = timestamp;
        this.keyIndexes = keyIndexes;
        this.accountType = accountType;
    }

    /**
     * Returns the truncated SHA-256 hash of the participant's identity keys.
     *
     * @apiNote
     * Empty when the user has no companion devices (no identity keys to hash).
     * Present otherwise, even when only a subset of identity keys could be
     * resolved locally (the {@link #keyIndexes()} field then signals which
     * subset).
     *
     * @return the key hash, or empty when no companion devices were found
     */
    @WhatsAppWebExport(moduleName = "WAWebIdentityIcdcApi",
            exports = "getICDCMetaFromDeviceRecord",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<byte[]> keyHash() {
        return Optional.ofNullable(keyHash);
    }

    /**
     * Returns the device-list snapshot timestamp.
     *
     * @apiNote
     * Present when the user has companion devices, or when the timestamp is
     * within {@link IcdcComputer}'s 720-hour recent window. Empty for a
     * primary-only list whose snapshot timestamp is stale.
     *
     * @return the timestamp, or empty when stale and no companion devices
     */
    @WhatsAppWebExport(moduleName = "WAWebIdentityIcdcApi",
            exports = "getICDCMetaFromDeviceRecord",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<Instant> timestamp() {
        return Optional.ofNullable(timestamp);
    }

    /**
     * Returns the indexes of devices whose identity keys were included in the
     * hash.
     *
     * @apiNote
     * Empty when every device in the device list was included (so the indexes
     * do not need to be transmitted); populated when some companion devices
     * had no locally-cached identity key and were omitted from the hash.
     *
     * @return an unmodifiable list of key indexes, or empty when every device
     *         was included
     */
    @WhatsAppWebExport(moduleName = "WAWebIdentityIcdcApi",
            exports = "getICDCMetaFromDeviceRecord",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<Integer> keyIndexes() {
        return keyIndexes != null
                ? Collections.unmodifiableList(keyIndexes)
                : List.of();
    }

    /**
     * Returns the hosted account encryption type for this participant.
     *
     * @apiNote
     * Cobalt collapses WA Web's {@code senderAccountType} and
     * {@code receiverAccountType} pair onto a single field; the caller decides
     * which role this result represents based on which side of the message it
     * is encoding for. Empty for non-hosted accounts and whenever the
     * {@code adv_accept_hosted_devices} AB prop is off.
     *
     * @return the account type, or empty when the account is not hosted
     */
    @WhatsAppWebExport(moduleName = "WAWebIdentityIcdcApi",
            exports = "getICDCMetaFromDeviceRecord",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<ADVEncryptionType> accountType() {
        return Optional.ofNullable(accountType);
    }

    /**
     * Returns a diagnostic string representation suitable for logs.
     *
     * @apiNote
     * The hash is summarised by length to keep log lines short and to avoid
     * leaking the bytes to any log sink that might persist them.
     *
     * @return the diagnostic string
     */
    @Override
    public String toString() {
        return "IcdcResult[" +
                "keyHash=" + (keyHash != null ? keyHash.length + " bytes" : "null") +
                ", timestamp=" + timestamp +
                ", keyIndexes=" + keyIndexes +
                ", accountType=" + accountType +
                ']';
    }
}
