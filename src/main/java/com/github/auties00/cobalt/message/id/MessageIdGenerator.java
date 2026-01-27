package com.github.auties00.cobalt.message.id;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.util.SecureBytes;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Generates WhatsApp message IDs
 */
public final class MessageIdGenerator {
    private static final String MESSAGE_ID_PREFIX = "3EB0";
    private static final int RANDOM_BYTES_LENGTH = 16;
    private static final int HASH_BYTES_TO_USE = 9;
    private static final int HASH_HEX_LENGTH = HASH_BYTES_TO_USE * 2;
    private static final int MESSAGE_ID_LENGTH = 22;

    private static final HexFormat HEX_FORMAT = HexFormat.of().withUpperCase();

    private static final VarHandle ARRAY_AS_INT64 = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    private MessageIdGenerator() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Generates a V1 message ID using simple random hex generation.
     *
     * @return a 22-character message ID starting with "3EB0"
     * @deprecated Use {@link #generateIdV2(Jid)} instead
     */
    @Deprecated
    public static String generateIdV1() {
        var suffix = SecureBytes.randomHex(HASH_HEX_LENGTH);
        return MESSAGE_ID_PREFIX + suffix;
    }

    /**
     * Generates a new message ID using the SHA-256 based algorithm.
     *
     * @param userJid the user's JID including device information (e.g., "1234567890.0:0@s.whatsapp.net")
     * @return a 22-character message ID starting with "3EB0"
     * @throws NoSuchAlgorithmException if no SHA-256 algorithm is available
     */
    public static String generateIdV2(Jid userJid) throws NoSuchAlgorithmException {
        var timestamp = Instant.now().getEpochSecond();
        var userString = userJid.toString();
        var userBytes = userString.getBytes(StandardCharsets.UTF_8);

        var digest = MessageDigest.getInstance("SHA-256");

        var timestampBytes = new byte[Long.BYTES];
        ARRAY_AS_INT64.set(timestampBytes, 0, timestamp);
        digest.update(timestampBytes);

        var userBytesLength = userBytes.length;
        while (userBytesLength >= 0x80) {
            digest.update((byte) ((userBytesLength & 0x7F) | 0x80));
            userBytesLength >>>= 7;
        }
        digest.update((byte) userBytesLength);
        digest.update(userBytes);

        digest.update(SecureBytes.random(RANDOM_BYTES_LENGTH));

        var hash = digest.digest();
        return MESSAGE_ID_PREFIX + HEX_FORMAT.formatHex(hash, 0, HASH_BYTES_TO_USE);
    }

    /**
     * Validates that a message ID conforms to the expected WhatsApp format.
     *
     * @param messageId the message ID to validate
     * @return {@code true} if the message ID is valid, {@code false} otherwise
     */
    public static boolean isValid(String messageId) {
        if (messageId == null || messageId.length() != MESSAGE_ID_LENGTH) {
            return false;
        }

        if(!messageId.startsWith(MESSAGE_ID_PREFIX)) {
            return false;
        }

        for (var i = 4; i < MESSAGE_ID_LENGTH; i++) {
            var c = messageId.charAt(i);
            if (!Character.isDigit(c) && !isUpperCaseHexDigit(c)) {
                return false;
            }
        }

        return true;
    }

    // The Character class doesn't have a utility for this exact case
    private static boolean isUpperCaseHexDigit(char c) {
        return c >= 'A' && c <= 'F';
    }
}
