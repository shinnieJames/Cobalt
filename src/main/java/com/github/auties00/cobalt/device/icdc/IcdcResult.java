package com.github.auties00.cobalt.device.icdc;

import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Result of computing ICDC (Identity Change Detection Consistency) metadata
 * for a single user (sender or recipient).
 *
 * <p>Contains the identity key hash, device timestamp, key indexes for
 * devices whose identity keys were found, and the optional hosted account type.
 *
 * @implNote WAWebIdentityIcdcApi.getICDCMetaFromDeviceRecord: returns
 * {@code {keyHash, timestamp, keyIndexes, senderAccountType, receiverAccountType}}.
 * Cobalt uses a single {@code accountType} field because the caller
 * ({@code IcdcEnricher}) maps sender/receiver based on which JID was used
 * to compute the ICDC metadata.
 */
public final class IcdcResult {

    /**
     * The truncated SHA-256 hash of sorted, concatenated identity keys,
     * or {@code null} if no companion devices were found.
     *
     * @implNote WAWebIdentityIcdcApi.getICDCMetaFromDeviceRecord: {@code n.keyHash}.
     */
    private final byte[] keyHash;

    /**
     * The device list timestamp, or {@code null} if the user has no
     * companion devices and the timestamp is not recent.
     *
     * @implNote WAWebIdentityIcdcApi.getICDCMetaFromDeviceRecord: {@code n.timestamp}.
     */
    private final Instant timestamp;

    /**
     * The key indexes of devices whose identity keys were successfully
     * retrieved, or {@code null} if all devices were included.
     *
     * @implNote WAWebIdentityIcdcApi.getICDCMetaFromDeviceRecord: {@code n.keyIndexes},
     * only set when {@code y.length !== i.length}.
     */
    private final List<Integer> keyIndexes;

    /**
     * The hosted account encryption type, or {@code null} if the account
     * is not a hosted business account.
     *
     * @implNote WAWebIdentityIcdcApi.getICDCMetaFromDeviceRecord:
     * {@code n.senderAccountType} (for self) or {@code n.receiverAccountType}
     * (for non-self). Cobalt collapses both into a single field since the
     * caller determines which role (sender/receiver) this result represents.
     */
    private final ADVEncryptionType accountType;

    /**
     * Constructs an ICDC result with the given components.
     *
     * @param keyHash    the truncated identity key hash, or {@code null}
     * @param timestamp  the device list timestamp, or {@code null}
     * @param keyIndexes the key indexes of included devices, or {@code null}
     * @param accountType the hosted account encryption type, or {@code null}
     * @implNote WAWebIdentityIcdcApi.getICDCMetaFromDeviceRecord: constructs
     * the result object {@code n = {keyHash, timestamp, keyIndexes, senderAccountType, receiverAccountType}}.
     */
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
     * Returns the SHA-256 hash of sorted, concatenated identity keys,
     * truncated to the configured hash length (minimum 8 bytes).
     *
     * @return the key hash, or empty if no companion devices were found
     * @implNote WAWebIdentityIcdcApi.getICDCMetaFromDeviceRecord: {@code n.keyHash},
     * computed via {@code computeIdentityHash(identityKeysToBinary(curveKeys), hashLength)}.
     */
    public Optional<byte[]> keyHash() {
        return Optional.ofNullable(keyHash);
    }

    /**
     * Returns the device list timestamp.
     *
     * <p>Present when the user has companion devices, or when the
     * timestamp is recent (within 720 hours per WA Web).
     *
     * @return the timestamp, or empty if stale and no companion devices
     * @implNote WAWebIdentityIcdcApi.getICDCMetaFromDeviceRecord: {@code n.timestamp},
     * included when {@code hasMultipleDevices || isRecent(timestamp)}.
     */
    public Optional<Instant> timestamp() {
        return Optional.ofNullable(timestamp);
    }

    /**
     * Returns the key indexes of devices whose identity keys were
     * successfully retrieved.
     *
     * <p>When all devices in the list had their identity keys available,
     * this returns an empty list to avoid sending redundant data.
     *
     * @return an unmodifiable list of key indexes, or an empty list if
     *         all devices were included
     * @implNote WAWebIdentityIcdcApi.getICDCMetaFromDeviceRecord: {@code n.keyIndexes},
     * only set when {@code y.length !== i.length}.
     */
    public List<Integer> keyIndexes() {
        return keyIndexes != null
                ? Collections.unmodifiableList(keyIndexes)
                : List.of();
    }

    /**
     * Returns the account encryption type for hosted device detection.
     *
     * <p>For the sender, this is {@link ADVEncryptionType#HOSTED} when
     * the sender's own account is hosted.  For the recipient, this is
     * {@link ADVEncryptionType#HOSTED} when the recipient's account type
     * is hosted.
     *
     * @return the account type, or empty if not a hosted account
     * @implNote WAWebIdentityIcdcApi.getICDCMetaFromDeviceRecord:
     * {@code n.senderAccountType} or {@code n.receiverAccountType},
     * gated by {@code WAWebBizCoexGatingUtils.bizHostedDevicesEnabled()}.
     */
    public Optional<ADVEncryptionType> accountType() {
        return Optional.ofNullable(accountType);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote NO_WA_BASIS: Java-specific toString for debugging.
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
