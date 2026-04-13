package com.github.auties00.cobalt.message.send.token;

import com.github.auties00.cobalt.model.jid.Jid;

import javax.crypto.KDF;
import javax.crypto.Mac;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Generates reporting tokens (franking tags) for outgoing messages.
 *
 * <p>A reporting token cryptographically binds a message's content to
 * its sender and recipient, enabling the server to verify abuse reports
 * without accessing plaintext.  The token is a 16-byte HMAC-SHA-256
 * truncation computed over the <em>reporting token content</em>
 * (a deterministic extract of the serialised protobuf), keyed by a
 * 32-byte key derived from the message secret via HKDF-SHA-256.
 *
 * @implNote WAWebReportingTokenUtils: provides {@code genReportingToken},
 * {@code genReportingTokenKeyFromMessageSecret}, and
 * {@code genReportingTokenBody}.
 */
public final class ReportingToken {
    /**
     * Output length for the HKDF-derived reporting token key.
     *
     * @implNote WAWebReportingTokenUtils: {@code REPORTING_TOKEN_KEY_SIZE = 32}
     */
    private static final int KEY_LENGTH = 32;

    /**
     * Number of leading HMAC bytes kept as the reporting token.
     *
     * @implNote WAWebReportingTokenUtils: {@code REPORTING_TOKEN_SIZE = 16},
     *          used as the third argument to {@code hmacSha256(key, content, 16)}.
     */
    private static final int TOKEN_LENGTH = 16;

    /**
     * The HKDF algorithm used for key derivation.
     *
     * @implNote WACryptoHkdf: uses HMAC-SHA-256 for both extract and expand steps.
     */
    private static final String HKDF_ALGORITHM = "HKDF-SHA256";

    /**
     * The HMAC algorithm used for token computation.
     *
     * @implNote WACryptoHmac.hmacSha256: uses HMAC-SHA-256.
     */
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * The use-case secret modification type string used in the HKDF
     * info parameter for reporting token key derivation.
     *
     * @implNote WAUseCaseSecret.UseCaseSecretModificationType.REPORT_TOKEN = "Report Token"
     */
    private static final String USE_CASE_TYPE = "Report Token";

    /**
     * Private constructor to prevent instantiation of this utility class.
     *
     * @implNote WAWebReportingTokenUtils: module-level functions, not a class.
     */
    private ReportingToken() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Generates a reporting token for an outgoing message.
     *
     * @param messageSecret         the 32-byte message secret
     * @param stanzaId              the message's stanza ID
     * @param senderJid             the sender's user JID
     * @param remoteJid             the remote JID (recipient for 1:1, or
     *                              self JID for groups/broadcasts)
     * @param reportingTokenContent the deterministic content extract from
     *                              the serialised protobuf, or {@code null}
     *                              if the message type is not compatible
     * @param version               the reporting token version
     * @return the reporting token, or empty if the content is {@code null}
     *         or empty
     * @throws NullPointerException     if a required argument is {@code null}
     * @throws GeneralSecurityException if a cryptographic operation fails
     *
     * @implNote WAWebReportingTokenUtils.genReportingToken: derives the key
     * via {@code genReportingTokenKeyFromMessageSecret}, then
     * {@code hmacSha256(key, content, 16)}.
     */
    public static Optional<ReportingTokenResult> generate(
            byte[] messageSecret,
            String stanzaId,
            Jid senderJid,
            Jid remoteJid,
            byte[] reportingTokenContent,
            int version
    ) throws GeneralSecurityException {
        Objects.requireNonNull(messageSecret, "messageSecret");
        Objects.requireNonNull(stanzaId, "stanzaId");
        Objects.requireNonNull(senderJid, "senderJid");
        Objects.requireNonNull(remoteJid, "remoteJid");

        if (reportingTokenContent == null || reportingTokenContent.length == 0) {
            return Optional.empty();
        }

        // WAWebReportingTokenUtils.genReportingTokenKeyFromMessageSecret
        var key = deriveKey(messageSecret, stanzaId, senderJid, remoteJid);

        // WAWebReportingTokenUtils: hmacSha256(key, content, 16)
        var token = hmacTruncated(key, reportingTokenContent);

        return Optional.of(new ReportingTokenResult(version, token));
    }

    /**
     * Derives the 32-byte reporting token key from the message secret
     * via HKDF-SHA-256 extract-and-expand.
     *
     * <p>The extract step uses a {@code null} salt (defaulting to a zero-filled
     * byte array of SHA-256 hash length) with the message secret as the input
     * keying material. The expand step uses the info parameter, which is the
     * binary concatenation of
     * {@code stanzaId || senderJid || remoteJid || "Report Token"},
     * all encoded as UTF-8.
     *
     * @param messageSecret the 32-byte message secret (IKM for HKDF extract)
     * @param stanzaId      the message's stanza ID
     * @param senderJid     the sender's user JID
     * @param remoteJid     the remote JID
     * @return a 32-byte key
     * @throws GeneralSecurityException if HKDF is unavailable
     *
     * @implNote WAWebReportingTokenUtils.genReportingTokenKeyFromMessageSecret:
     * {@code WABinary.Binary.build(stanzaId, senderJid, remoteJid, REPORT_TOKEN)}
     * produces the info, then
     * {@code WACryptoHkdf.extractAndExpand(messageSecret, info, 32)} which calls
     * {@code extractSha256(null, messageSecret)} followed by
     * {@code expand(prk, info, 32)}.
     */
    static byte[] deriveKey(
            byte[] messageSecret,
            String stanzaId,
            Jid senderJid,
            Jid remoteJid
    ) throws GeneralSecurityException {
        // WAWebReportingTokenUtils: Binary.build(stanzaId, senderJid, remoteJid, REPORT_TOKEN)
        var info = (stanzaId + senderJid + remoteJid + USE_CASE_TYPE)
                .getBytes(StandardCharsets.UTF_8);

        // WACryptoHkdf.extractAndExpand(messageSecret, info, 32):
        // Extract with null salt (zeros), then expand with info
        var kdf = KDF.getInstance(HKDF_ALGORITHM);
        var params = HKDFParameterSpec.ofExtract()
                .addIKM(messageSecret)
                .thenExpand(info, KEY_LENGTH);
        return kdf.deriveData(params);
    }

    /**
     * Computes HMAC-SHA-256 of {@code data} keyed by {@code key},
     * truncated to the first {@value #TOKEN_LENGTH} bytes.
     *
     * @param key  the HMAC key
     * @param data the data to authenticate
     * @return the first {@value #TOKEN_LENGTH} bytes of the HMAC
     * @throws GeneralSecurityException if HMAC computation fails
     *
     * @implNote WAWebReportingTokenUtils.genReportingToken:
     * {@code WACryptoHmac.hmacSha256(key, content, 16)}.
     */
    private static byte[] hmacTruncated(byte[] key, byte[] data) throws GeneralSecurityException {
        var mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
        var full = mac.doFinal(data);
        return Arrays.copyOf(full, TOKEN_LENGTH);
    }
}
