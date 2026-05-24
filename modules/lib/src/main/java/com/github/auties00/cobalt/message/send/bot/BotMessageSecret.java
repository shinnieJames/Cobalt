package com.github.auties00.cobalt.message.send.bot;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import javax.crypto.KDF;
import javax.crypto.spec.HKDFParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Objects;

/**
 * Derives the bot-side message secret from a user message secret.
 *
 * @apiNote
 * Used internally by the bot-message encryption pipeline so plaintext sent to
 * a bot is encrypted with a key the user can rotate without exposing the
 * original {@code messageSecret} value the {@link com.github.auties00.cobalt.model.chat.ChatMessageContextInfo}
 * carries. Cobalt embedders do not typically call this directly; it is invoked
 * by {@link BotProtobufTransform#transformForCapi} when preparing an outbound
 * CAPI bot message.
 */
@WhatsAppWebModule(moduleName = "WAWebBotMessageSecret")
public final class BotMessageSecret {
    /**
     * The HKDF algorithm identifier used for the derivation.
     */
    private static final String HKDF_ALGORITHM = "HKDF-SHA256";

    /**
     * The HKDF info string used during the expand step.
     */
    private static final String INFO = "Bot Message";

    /**
     * The length, in bytes, of the derived secret.
     */
    private static final int SECRET_LENGTH = 32;

    /**
     * Prevents instantiation of this utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private BotMessageSecret() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Derives the 32-byte bot message secret from the supplied base message secret.
     *
     * @apiNote
     * HKDF-SHA-256 is invoked with an all-zero salt for the extract step and
     * the fixed info string {@code "Bot Message"} for the expand step, matching
     * WA Web's {@code genBotMsgSecretFromMsgSecret}. The output is the key
     * material that the bot encryption layer feeds into AES-GCM along with the
     * envelope's {@code encIv}/{@code encPayload}; producing different bytes
     * here means the bot cannot decrypt the message at all, so callers must
     * not mutate {@code messageSecret} concurrently with this call.
     * @implNote
     * This implementation accepts any IKM length because HKDF-Extract collapses
     * arbitrary input to a fixed-size PRK; WA Web passes a 32-byte secret in
     * practice. The output length is pinned at {@value #SECRET_LENGTH} to match
     * the {@code u=32} constant in {@code WAWebBotMessageSecret}.
     *
     * @param messageSecret the base message secret bytes, typically 32 bytes
     * @return the derived 32-byte bot message secret
     * @throws NullPointerException     if {@code messageSecret} is {@code null}
     * @throws GeneralSecurityException if HKDF-SHA-256 is unavailable
     */
    @WhatsAppWebExport(moduleName = "WAWebBotMessageSecret", exports = "genBotMsgSecretFromMsgSecret",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static byte[] derive(byte[] messageSecret) throws GeneralSecurityException {
        Objects.requireNonNull(messageSecret, "messageSecret");
        var kdf = KDF.getInstance(HKDF_ALGORITHM);
        var params = HKDFParameterSpec.ofExtract()
                .addIKM(messageSecret)
                .thenExpand(INFO.getBytes(StandardCharsets.UTF_8), SECRET_LENGTH);
        return kdf.deriveData(params);
    }
}
