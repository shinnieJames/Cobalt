package com.github.auties00.cobalt.message.send.bot;

import javax.crypto.KDF;
import javax.crypto.spec.HKDFParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Objects;

/**
 * Derives the bot message secret from a message's base secret.
 *
 * <p>Bot messages use a derived secret rather than the original
 * message secret, so that the user's message secret is not leaked
 * to the bot.  The derivation uses HKDF-SHA-256 extract (with a
 * null/zero salt) followed by expand with the info string
 * {@code "Bot Message"} and an output length of 32 bytes.
 *
 * @implNote WAWebBotMessageSecret.genBotMsgSecretFromMsgSecret:
 * {@code WACryptoHkdf.extractAndExpand(messageSecret, "Bot Message", 32)},
 * which calls {@code extractSha256(null, messageSecret)} (HKDF-Extract
 * with a 32-byte zero salt) and then {@code expand(prk, "Bot Message", 32)}
 * (HKDF-Expand).
 */
public final class BotMessageSecret {
    /**
     * The HKDF algorithm identifier used for key derivation.
     *
     * @implNote WAWebBotMessageSecret: uses {@code WACryptoHkdf}
     * which is HKDF-SHA-256.
     */
    private static final String HKDF_ALGORITHM = "HKDF-SHA256";

    /**
     * The HKDF info string used during expand.
     *
     * @implNote WAWebBotMessageSecret: module-level constant
     * {@code d = "Bot Message"}.
     */
    private static final String INFO = "Bot Message";

    /**
     * The length of the derived secret in bytes.
     *
     * @implNote WAWebBotMessageSecret: module-level constant
     * {@code c = 32}.
     */
    private static final int SECRET_LENGTH = 32;

    /**
     * Prevents instantiation of this utility class.
     *
     * @implNote NO_WA_BASIS
     */
    private BotMessageSecret() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Derives the bot message secret from the base message secret.
     *
     * <p>Performs HKDF-Extract with a null (all-zero) salt over the
     * raw {@code messageSecret}, then HKDF-Expand with the info
     * string {@code "Bot Message"} to produce 32 bytes.
     *
     * @param messageSecret the 32-byte base message secret
     * @return the 32-byte derived bot message secret
     * @throws GeneralSecurityException if HKDF fails
     *
     * @implNote WAWebBotMessageSecret.genBotMsgSecretFromMsgSecret:
     * {@code WACryptoHkdf.extractAndExpand(new Uint8Array(messageSecret), "Bot Message", 32)}.
     * The {@code extractAndExpand} function calls
     * {@code extractSha256(null, ikm)} which uses a 32-byte zero salt,
     * then {@code expand(prk, info, length)}.
     */
    public static byte[] derive(byte[] messageSecret) throws GeneralSecurityException {
        Objects.requireNonNull(messageSecret, "messageSecret");
        var kdf = KDF.getInstance(HKDF_ALGORITHM);
        // WAWebBotMessageSecret.genBotMsgSecretFromMsgSecret
        var params = HKDFParameterSpec.ofExtract() // WACryptoHkdf.extractSha256(null, messageSecret) — extract with null salt
                .addIKM(messageSecret)
                .thenExpand(INFO.getBytes(StandardCharsets.UTF_8), SECRET_LENGTH); // WACryptoHkdf.expand(prk, "Bot Message", 32)
        return kdf.deriveData(params);
    }
}
