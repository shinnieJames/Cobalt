package com.github.auties00.cobalt.device.phash;

import java.util.Objects;

/**
 * Validates phash values between sent messages and server acknowledgments.
 */
public final class DevicePhashValidator {
    private DevicePhashValidator() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Validates that the sent phash matches the server's expected phash.
     *
     * @param sentPhash the phash that was sent with the message
     * @param ackPhash  the phash returned in the server acknowledgment (may be null)
     * @return the validation result
     */
    public static boolean validate(String sentPhash, String ackPhash) {
        // No phash in ACK means single device or phash not required
        if (ackPhash == null) {
            return true;
        }

        // Compare phashes
        if (Objects.equals(sentPhash, ackPhash)) {
            return true;
        }

        // Mismatch detected
        return false;
    }
}
