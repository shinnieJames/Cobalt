package com.github.auties00.cobalt.device.phash;

import com.github.auties00.cobalt.model.jid.Jid;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;

/**
 * Calculates participant hash (phash) for group messages.
 * <p>
 * The phash is used to verify that the sender and server agree on the
 * list of participants/devices that should receive a group message.
 */
public final class DevicePhashCalculator {
    private static final String PHASH_PREFIX = "2:";
    private static final int HASH_BYTES_TO_USE = 6;

    private DevicePhashCalculator() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Calculates the phash v2 for a collection of device JIDs.
     *
     * @param deviceJids the device JIDs to include in the hash
     * @return the phash string (e.g., "2:q83vEjRW")
     * @throws NoSuchAlgorithmException if SHA-256 is not available
     */
    public static String calculateV2(Collection<Jid> deviceJids) throws NoSuchAlgorithmException {
        var legacyJids = deviceJids.stream()
                .map(DevicePhashCalculator::toLegacyJidString)
                .sorted(Comparator.naturalOrder())
                .toList();

        var digest = MessageDigest.getInstance("SHA-256");
        for(var legacyJid : legacyJids) {
            digest.update(legacyJid.getBytes(StandardCharsets.UTF_8));
        }
        var hash = digest.digest();

        var truncated = new byte[HASH_BYTES_TO_USE];
        System.arraycopy(hash, 0, truncated, 0, HASH_BYTES_TO_USE);

        var base64 = Base64.getEncoder().encodeToString(truncated);
        return PHASH_PREFIX + base64;
    }

    // Converts a device JID to legacy format for phash calculation.
    private static String toLegacyJidString(Jid jid) {
        var user = jid.user();
        var server = jid.server().address();
        var device = jid.device();

        if (device == 0) {
            return user + "@" + server;
        } else {
            return user + "." + device + "@" + server;
        }
    }
}
