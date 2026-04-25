package com.github.auties00.cobalt.message.send.token;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;

import javax.crypto.KDF;
import javax.crypto.Mac;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;

/**
 * Generates per-recipient content-binding tokens (RCAT) for URL messages.
 *
 * <p>Content binding associates an outgoing URL message with a
 * cryptographic tag that lets the server verify the content without
 * learning the plaintext URL.  Each recipient gets a unique 8-byte tag
 * derived from:
 * <ol>
 *   <li>A 32-byte <em>nonce</em> produced by HKDF-SHA-256 extract-and-expand
 *       over the message secret (with a zero salt for the extract phase), keyed
 *       by {@code msgId || senderJid || recipientJid || "Rcat"} as the info
 *       parameter.</li>
 *   <li>An HMAC-SHA-256 of the URL content ID using that nonce,
 *       truncated to the first 8 bytes.</li>
 * </ol>
 *
 * @implNote WAWebMsgRcatUtils: provides {@code genContentBindingForMsg},
 * {@code deriveNonce}, {@code deriveNonceString}, {@code getContentIdString},
 * {@code genNonceForMsg}, and the HMAC truncation logic.
 */
@WhatsAppWebModule(moduleName = "WAWebMsgRcatUtils")
@WhatsAppWebModule(moduleName = "WAWebUtilsYoutubeUrlParser")
public final class ContentBindingToken {
    /**
     * The info-suffix appended when deriving the per-recipient nonce.
     *
     * @implNote WAWebMsgRcatUtils: module-level constant {@code s = "Rcat"},
     * used in {@code deriveNonce} to join
     * {@code [msgId, senderJid, recipientJid, "Rcat"]}.
     */
    private static final String NONCE_INFO_SUFFIX = "Rcat";

    /**
     * Output length for the HKDF-derived nonce.
     *
     * @implNote WAWebMsgRcatUtils: module-level constant {@code u = 32}.
     */
    private static final int NONCE_LENGTH = 32;

    /**
     * Number of leading HMAC bytes kept as the content-binding tag.
     *
     * @implNote WAWebMsgRcatUtils: {@code hmacSha256(nonce, contentId).slice(0, 8)}.
     */
    private static final int TAG_LENGTH = 8;

    /**
     * The HKDF algorithm used for nonce derivation.
     *
     * @implNote WACryptoHkdf.extractAndExpand: uses HMAC-SHA-256 for both extract and expand.
     */
    private static final String HKDF_ALGORITHM = "HKDF-SHA256";

    /**
     * The HMAC algorithm used for tag computation.
     *
     * @implNote WACryptoHmac.hmacSha256: HMAC with SHA-256.
     */
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * Length of a YouTube video ID.
     *
     * @implNote WAWebUtilsYoutubeUrlParser.parseYoutubeVideoId: YouTube video IDs are 11 characters.
     */
    private static final int YT_VIDEO_ID_LENGTH = 11;

    /**
     * Prevents instantiation of this utility class.
     *
     * @implNote NO_WA_BASIS: Java utility class pattern.
     */
    private ContentBindingToken() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Resolves the content ID string from a matched URL text.
     *
     * <p>If the URL is a YouTube link, extracts the 11-character video
     * ID.  Otherwise returns the full matched text.
     *
     * <p>Uses direct string inspection instead of regex for performance:
     * YouTube video IDs are always exactly 11 characters and appear at
     * a fixed position relative to the host/path prefix.
     *
     * @param matchedText  the matched URL text from the message
     * @param youtubeOnly  if {@code true}, returns the YouTube video ID only
     *                     (may be {@code null} if the URL is not YouTube);
     *                     if {@code false}, falls back to the full matched text
     *                     when no YouTube ID is found
     * @return the content ID string, or {@code null} if {@code youtubeOnly} is
     *         {@code true} and the URL is not a recognised YouTube link
     *
     * @implNote WAWebMsgRcatUtils.getContentIdString: calls
     * {@code parseYoutubeVideoId(matchedText)}; when the second parameter
     * is {@code true}, always returns the YouTube ID (possibly {@code null});
     * when {@code false}, falls back to the full matched text.
     * WAWebUtilsYoutubeUrlParser.parseYoutubeVideoId: matches
     * youtu.be/ID, youtube.com/watch?v=ID, youtube.com/shorts/ID.
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgRcatUtils", exports = "getContentIdString",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static String getContentIdString(String matchedText, boolean youtubeOnly) {
        if (matchedText == null || matchedText.isEmpty()) {
            return null;
        }
        // WAWebMsgRcatUtils.getContentIdString
        var videoId = parseYoutubeVideoId(matchedText);
        return (youtubeOnly || videoId != null) ? videoId : matchedText;
    }

    /**
     * Resolves the content ID from a matched URL text as UTF-8 bytes.
     *
     * <p>Calls {@link #getContentIdString(String, boolean)} with
     * {@code youtubeOnly = false}, then encodes the result as UTF-8.
     *
     * @param matchedText the matched URL text from the message
     * @return the content ID bytes
     * @throws NullPointerException if {@code matchedText} is {@code null}
     *
     * @implNote WAWebMsgRcatUtils internal function {@code h}:
     * calls {@code getContentIdString(msg, false)} then
     * {@code new TextEncoder().encode(result)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgRcatUtils", exports = "getContentIdString",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static byte[] resolveContentId(String matchedText) {
        Objects.requireNonNull(matchedText, "matchedText");
        // WAWebMsgRcatUtils.h: g(e, false) then encode
        var contentId = getContentIdString(matchedText, false);
        if (contentId == null) {
            return null;
        }
        return contentId.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Extracts the YouTube video ID from a URL, or returns {@code null}
     * if the URL is not a recognised YouTube format.
     *
     * <p>Recognised formats (with optional {@code www.} and {@code m.}):
     * <ul>
     *   <li>{@code http(s)://youtu.be/XXXXXXXXXXX}</li>
     *   <li>{@code http(s)://youtube.com/watch?v=XXXXXXXXXXX}</li>
     *   <li>{@code http(s)://youtube.com/shorts/XXXXXXXXXXX}</li>
     * </ul>
     *
     * @param url the URL to parse
     * @return the 11-character video ID, or {@code null}
     *
     * @implNote WAWebUtilsYoutubeUrlParser.parseYoutubeVideoId: matches
     * against {@code WAWebPipConst.URL_PATTERNS.ONLINE_VIDEO_URL.YOUTUBE}
     * patterns after stripping {@code www.} via {@code WAWebURLUtils.withoutWww}.
     * The JS regexes are:
     * <pre>
     *   /^https?:\/\/youtu\.be\/(.{11})/
     *   /^https?:\/\/(m\.)?youtube\.com\/watch\?v=(.{11})/
     *   /^https?:\/\/(m\.)?youtube\.com\/shorts\/(.{11})/
     * </pre>
     * WAWebURLUtils.withoutWww: {@code t.split("www.").join("")} — removes
     * every occurrence of {@code "www."} from the string before matching.
     */
    @WhatsAppWebExport(moduleName = "WAWebUtilsYoutubeUrlParser", exports = "parseYoutubeVideoId",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static String parseYoutubeVideoId(String url) {
        if (url == null) {
            return null;
        }

        // WAWebURLUtils.withoutWww: t.split("www.").join("") — strips every "www." occurrence.
        var stripped = url.contains("www.") ? url.replace("www.", "") : url;

        // WAWebUtilsYoutubeUrlParser.parseYoutubeVideoId: regexes are anchored at ^https?://
        int hostStart;
        if (stripped.startsWith("https://")) {
            hostStart = 8;
        } else if (stripped.startsWith("http://")) {
            hostStart = 7;
        } else {
            return null;
        }

        // /^https?:\/\/youtu\.be\/(.{11})/
        if (stripped.startsWith("youtu.be/", hostStart)) {
            return extractVideoId(stripped, hostStart + 9);
        }

        // /^https?:\/\/(m\.)?youtube\.com\/... — optional "m." prefix on youtube.com only
        if (stripped.startsWith("m.", hostStart)) {
            hostStart += 2;
        }

        if (!stripped.startsWith("youtube.com/", hostStart)) {
            return null;
        }
        var pathStart = hostStart + 12;

        // /^https?:\/\/(m\.)?youtube\.com\/watch\?v=(.{11})/
        if (stripped.startsWith("watch?v=", pathStart)) {
            return extractVideoId(stripped, pathStart + 8);
        }

        // /^https?:\/\/(m\.)?youtube\.com\/shorts\/(.{11})/
        if (stripped.startsWith("shorts/", pathStart)) {
            return extractVideoId(stripped, pathStart + 7);
        }

        return null;
    }

    /**
     * Extracts exactly {@value #YT_VIDEO_ID_LENGTH} characters starting
     * at {@code offset}, or returns {@code null} if insufficient characters
     * remain.
     *
     * @param url    the full URL string
     * @param offset the character offset where the video ID starts
     * @return the video ID substring, or {@code null}
     *
     * @implNote ADAPTED: WAWebUtilsYoutubeUrlParser.parseYoutubeVideoId:
     * the regex capture group extracts the video ID; this method replicates
     * the same extraction using substring instead of regex.
     */
    private static String extractVideoId(String url, int offset) {
        if (offset + YT_VIDEO_ID_LENGTH > url.length()) {
            return null;
        }
        return url.substring(offset, offset + YT_VIDEO_ID_LENGTH);
    }

    /**
     * Generates content-binding tags for each recipient of a URL message.
     *
     * <p>For each recipient, derives a 32-byte nonce via HKDF-SHA-256
     * extract-and-expand, then computes an 8-byte HMAC tag over the
     * content ID using that nonce as key.
     *
     * @param messageId     the outgoing message's stanza ID
     * @param messageSecret the 32-byte message secret
     * @param senderJid     the sender's user JID
     * @param recipientJids the recipients' user JIDs
     * @param contentId     the URL content identifier (matched URL text or
     *                      YouTube video ID), encoded as UTF-8
     * @return an unmodifiable map from recipient JID to its 8-byte tag
     * @throws NullPointerException     if any argument is {@code null}
     * @throws GeneralSecurityException if a cryptographic operation fails
     *
     * @implNote WAWebMsgRcatUtils.genContentBindingForMsg: iterates over
     * recipients, derives a nonce per recipient via {@code deriveNonce},
     * then computes {@code hmacSha256(nonce, contentId).slice(0, 8)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgRcatUtils", exports = "genContentBindingForMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static Map<Jid, byte[]> generate(
            String messageId,
            byte[] messageSecret,
            Jid senderJid,
            Collection<Jid> recipientJids,
            byte[] contentId
    ) throws GeneralSecurityException {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(messageSecret, "messageSecret");
        Objects.requireNonNull(senderJid, "senderJid");
        Objects.requireNonNull(recipientJids, "recipientJids");
        Objects.requireNonNull(contentId, "contentId");

        var result = new LinkedHashMap<Jid, byte[]>(recipientJids.size());
        for (var recipientJid : recipientJids) {
            // WAWebMsgRcatUtils.genContentBindingForMsg: deriveNonce then hmac
            var nonce = deriveNonce(messageId, messageSecret, senderJid, recipientJid);

            // WAWebMsgRcatUtils: hmacSha256(nonce, contentId).slice(0, 8)
            var tag = hmacTruncated(nonce, contentId);
            result.put(recipientJid, tag);
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Derives the per-recipient 32-byte nonce via HKDF-SHA-256
     * extract-and-expand.
     *
     * <p>The extract phase uses a zero-filled 32-byte salt to derive a
     * pseudorandom key (PRK) from the message secret. The expand phase
     * uses the UTF-8 encoding of
     * {@code msgId + senderJid + recipientJid + "Rcat"} as the info
     * parameter.
     *
     * @param messageId     the message stanza ID
     * @param messageSecret the 32-byte message secret (input keying material)
     * @param senderJid     the sender's user JID
     * @param recipientJid  the recipient's user JID
     * @return a 32-byte nonce
     * @throws GeneralSecurityException if HKDF is unavailable
     *
     * @implNote WAWebMsgRcatUtils.deriveNonce:
     * {@code info = encode([msgId, senderJid, recipientJid, "Rcat"].join(""))},
     * then {@code WACryptoHkdf.extractAndExpand(messageSecret, info, 32)}.
     * WACryptoHkdf.extractAndExpand: calls {@code extractSha256(null, ikm)}
     * (HMAC-SHA256 with zero salt) then {@code expand(prk, info, length)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgRcatUtils", exports = "deriveNonce",
            adaptation = WhatsAppAdaptation.DIRECT)
    static byte[] deriveNonce(
            String messageId,
            byte[] messageSecret,
            Jid senderJid,
            Jid recipientJid
    ) throws GeneralSecurityException {
        // WAWebMsgRcatUtils.deriveNonce: [msgId, senderJid, recipientJid, "Rcat"].join("")
        var info = (messageId + senderJid + recipientJid + NONCE_INFO_SUFFIX)
                .getBytes(StandardCharsets.UTF_8);

        // WACryptoHkdf.extractAndExpand: extractSha256(null, messageSecret) then expand(prk, info, 32)
        var kdf = KDF.getInstance(HKDF_ALGORITHM);
        var params = HKDFParameterSpec.ofExtract()
                .addIKM(messageSecret)
                .thenExpand(info, NONCE_LENGTH);
        return kdf.deriveData(params);
    }

    /**
     * Derives the per-recipient nonce and returns it as a URL-safe
     * Base64-encoded string (with padding).
     *
     * @param messageId     the message stanza ID
     * @param messageSecret the 32-byte message secret (input keying material)
     * @param senderJid     the sender's user JID
     * @param recipientJid  the recipient's user JID
     * @return the nonce as a URL-safe Base64 string with padding
     * @throws GeneralSecurityException if HKDF is unavailable
     *
     * @implNote WAWebMsgRcatUtils.deriveNonceString: calls {@code deriveNonce}
     * then {@code WABase64.encodeB64UrlSafe(nonce, true)} (with padding).
     */
    @WhatsAppWebExport(moduleName = "WAWebMsgRcatUtils", exports = "deriveNonceString",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static String deriveNonceString(
            String messageId,
            byte[] messageSecret,
            Jid senderJid,
            Jid recipientJid
    ) throws GeneralSecurityException {
        // WAWebMsgRcatUtils.deriveNonceString
        var nonce = deriveNonce(messageId, messageSecret, senderJid, recipientJid);
        // WABase64.encodeB64UrlSafe(nonce, true): second arg true means include padding
        return Base64.getUrlEncoder().encodeToString(nonce);
    }

    /**
     * Computes HMAC-SHA-256 of {@code data} keyed by {@code key},
     * truncated to the first {@value #TAG_LENGTH} bytes.
     *
     * @param key  the HMAC key (the derived nonce)
     * @param data the data to authenticate (the content ID)
     * @return the first 8 bytes of the HMAC
     * @throws GeneralSecurityException if the HMAC algorithm is unavailable
     *
     * @implNote WAWebMsgRcatUtils internal function {@code y}:
     * {@code hmacSha256(nonce, contentId).slice(0, 8)}.
     * WACryptoHmac.hmacSha256: first argument is the key, second is the data.
     */
    private static byte[] hmacTruncated(byte[] key, byte[] data) throws GeneralSecurityException {
        var mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
        var full = mac.doFinal(data);
        return Arrays.copyOf(full, TAG_LENGTH);
    }
}
