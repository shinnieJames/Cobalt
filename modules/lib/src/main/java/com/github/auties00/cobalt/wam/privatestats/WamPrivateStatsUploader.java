package com.github.auties00.cobalt.wam.privatestats;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.util.DataUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * Uploads one WAM private-stats buffer per call to the
 * {@code https://dit.whatsapp.net/deidentified_telemetry} endpoint.
 *
 * <p>The request is a {@code multipart/form-data} POST with four
 * named parts that mirror the body the WhatsApp Web JavaScript bundle
 * sends via its opaque {@code privateStatsUpload} dependency:
 *
 * <ul>
 *   <li>{@code access_token}: the hard-coded Facebook-style app
 *       credential ({@value #ACCESS_TOKEN}) that gates the endpoint.</li>
 *   <li>{@code credential}: {@code base64UrlSafe(token) + "+" +
 *       base64UrlSafe(HMAC-SHA256(sharedSecret, buffer))} where
 *       {@code (token, sharedSecret)} comes from a fresh
 *       {@link WamPrivateStatsTokenIssuer#issue} round-trip.</li>
 *   <li>{@code message}: the encoded WAM buffer attached as an
 *       {@code application/octet-stream} file named
 *       {@value #BUFFER_FILE_NAME}.</li>
 *   <li>{@code meta_data}: the JSON object
 *       {@code {"t": unixSeconds, "p": 0}}.</li>
 * </ul>
 *
 * @apiNote
 * Used by the WAM flush loop to ship each private-channel buffer one
 * at a time after a successful {@link WamPrivateStatsTokenIssuer#issue}.
 * The endpoint, access token, file name, and metadata layout were
 * recovered from the live JavaScript bundle on 2026-04-27; the WA Web
 * source manifest does not expose them because {@code privateStatsUpload}
 * is an external dependency not bundled into the analysed module set.
 *
 * @implNote
 * This implementation hand-assembles the multipart body because
 * Java's {@link HttpClient} has no native {@code multipart/form-data}
 * support. The boundary follows the WebKit/Chromium convention
 * ({@value #BOUNDARY_PREFIX} plus {@value #BOUNDARY_SUFFIX_LENGTH}
 * random alphanumeric characters) so a Cobalt-issued request is
 * byte-indistinguishable from a Chrome {@code FormData} POST.
 */
@WhatsAppWebModule(moduleName = "WAWebUploadPrivateStatsBackend")
public final class WamPrivateStatsUploader {
    /**
     * The destination URL accepting the upload.
     */
    private static final URI ENDPOINT = URI.create("https://dit.whatsapp.net/deidentified_telemetry");

    /**
     * The hard-coded Facebook {@code app_id|app_secret} pair that
     * gates the endpoint.
     *
     * @apiNote
     * This is not a per-user secret; the same value is shipped by
     * every WA Web client. The 401 response on rejection maps to
     * {@link WamPrivateStatsUploadResult.Type#ERROR_ACCESS_TOKEN}.
     */
    private static final String ACCESS_TOKEN = "245118376424571|3e7d275052f1522bf3200afcf53841a7";

    /**
     * The filename advertised for the buffer attachment in the
     * multipart body.
     */
    private static final String BUFFER_FILE_NAME = "WAMEventBuffer.dat";

    /**
     * The constant value written into the {@code p} field of the
     * meta-data JSON.
     *
     * @apiNote
     * Treated by the WhatsApp Web bundle as an opaque tag; always
     * zero on every observed upload.
     */
    private static final int META_PRIORITY = 0;

    /**
     * The padding-free URL-safe Base64 encoder used for the
     * {@code credential} field.
     *
     * @apiNote
     * Matches the {@code WABase64.encodeB64UrlSafe(bytes, true)} call
     * used inside WA Web.
     */
    private static final Base64.Encoder URL_BASE64 = Base64.getUrlEncoder().withoutPadding();

    /**
     * The alphabet used to randomise the multipart-boundary suffix.
     *
     * @apiNote
     * Mixed-case ASCII alphanumeric, no special characters, matching
     * the WebKit/Chromium convention.
     */
    private static final char[] BOUNDARY_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    /**
     * The length in characters of the random suffix appended to the
     * boundary prefix.
     */
    private static final int BOUNDARY_SUFFIX_LENGTH = 16;

    /**
     * The boundary prefix recognised by the WebKit and Chromium
     * {@code FormData} serialisers.
     *
     * @apiNote
     * The boundary string only needs to be unique within a request
     * and absent from any part. The shape is arbitrary; matching the
     * browser convention is the lowest-risk choice for an endpoint
     * that primarily serves browser traffic.
     */
    private static final String BOUNDARY_PREFIX = "----WebKitFormBoundary";

    /**
     * The token issuer used to acquire a fresh
     * {@link WamPrivateStatsToken} on every upload.
     */
    private final WamPrivateStatsTokenIssuer issuer;

    /**
     * The HTTP client used for the {@code POST}, reused across
     * uploads for connection pooling.
     */
    private final HttpClient httpClient;

    /**
     * Constructs a new uploader bound to a token issuer and a
     * default-configured {@link HttpClient}.
     *
     * @apiNote
     * Public entry point used by {@code WhatsAppClient} when wiring
     * the private-stats subsystem.
     *
     * @param issuer the token issuer
     * @throws NullPointerException if {@code issuer} is {@code null}
     */
    public WamPrivateStatsUploader(WamPrivateStatsTokenIssuer issuer) {
        this(issuer, HttpClient.newHttpClient());
    }

    /**
     * Constructs a new uploader bound to a token issuer and a
     * caller-supplied HTTP client.
     *
     * @apiNote
     * Intended for tests that drive the uploader with a recording
     * {@link HttpClient} stub, or for embedders that want to share a
     * connection pool with other Cobalt subsystems.
     *
     * @param issuer     the token issuer
     * @param httpClient the HTTP client to use
     * @throws NullPointerException if either argument is {@code null}
     */
    public WamPrivateStatsUploader(WamPrivateStatsTokenIssuer issuer, HttpClient httpClient) {
        this.issuer = Objects.requireNonNull(issuer, "issuer must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    /**
     * Uploads one WAM private-stats buffer and returns the
     * categorised outcome.
     *
     * @apiNote
     * Acquires a fresh token via {@link WamPrivateStatsTokenIssuer#issue},
     * authenticates the buffer with {@code HMAC-SHA256(sharedSecret, buffer)},
     * POSTs the multipart body, and reports the result. The caller
     * owns retry policy; this method never retries.
     *
     * @implNote
     * This implementation diverges from the WA Web
     * {@code privateStatsUpload} dependency, which loops with
     * exponential backoff up to 12 attempts per buffer (see
     * {@link WhatsAppWebModule WAWebUploadPrivateStatsBackend}'s
     * {@code d} helper). Cobalt makes exactly one HTTP attempt;
     * transport exceptions and unmapped status codes collapse to
     * {@link WamPrivateStatsUploadResult.Type#ERROR_OTHER}. A token
     * issuance failure short-circuits to
     * {@link WamPrivateStatsUploadResult.Type#ERROR_CREDENTIAL}
     * without contacting the HTTP layer.
     *
     * @param buffer the encoded WAM buffer to ship
     * @return the categorised upload outcome
     * @throws NullPointerException if {@code buffer} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebUploadPrivateStatsBackend", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public WamPrivateStatsUploadResult upload(byte[] buffer) {
        Objects.requireNonNull(buffer, "buffer must not be null");

        WamPrivateStatsToken token;
        try {
            token = issuer.issue();
        } catch (RuntimeException e) {
            return new WamPrivateStatsUploadResult(WamPrivateStatsUploadResult.Type.ERROR_CREDENTIAL, -1);
        }

        var credential = buildCredential(token, buffer);
        var boundary = generateBoundary();
        var body = buildMultipartBody(boundary, credential, buffer);

        var request = HttpRequest.newBuilder(ENDPOINT)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            var status = response.statusCode();
            return switch (status) {
                case 200 -> new WamPrivateStatsUploadResult(WamPrivateStatsUploadResult.Type.SUCCESS, status);
                case 400 -> new WamPrivateStatsUploadResult(WamPrivateStatsUploadResult.Type.ERROR_PARSING, status);
                case 401 -> new WamPrivateStatsUploadResult(WamPrivateStatsUploadResult.Type.ERROR_ACCESS_TOKEN, status);
                case 429, 500 -> new WamPrivateStatsUploadResult(WamPrivateStatsUploadResult.Type.ERROR_SERVER_OTHER, status);
                default -> new WamPrivateStatsUploadResult(WamPrivateStatsUploadResult.Type.ERROR_OTHER, status);
            };
        } catch (Throwable _) {
            return new WamPrivateStatsUploadResult(WamPrivateStatsUploadResult.Type.ERROR_OTHER, -1);
        }
    }

    /**
     * Generates a fresh per-request multipart boundary.
     *
     * @implNote
     * This implementation appends {@value #BOUNDARY_SUFFIX_LENGTH}
     * random characters drawn from {@link #BOUNDARY_ALPHABET} to the
     * {@value #BOUNDARY_PREFIX} prefix, matching the WebKit/Chromium
     * shape so the request is byte-indistinguishable from a browser
     * {@code FormData} POST.
     *
     * @return a fresh boundary string
     */
    private String generateBoundary() {
        var suffix = new char[BOUNDARY_SUFFIX_LENGTH];
        for (var i = 0; i < BOUNDARY_SUFFIX_LENGTH; i++) {
            suffix[i] = BOUNDARY_ALPHABET[DataUtils.randomInt(BOUNDARY_ALPHABET.length)];
        }
        return BOUNDARY_PREFIX + new String(suffix);
    }

    /**
     * Builds the {@code credential} multipart field value as
     * {@code base64UrlSafe(token) + "+" + base64UrlSafe(HMAC-SHA256(sharedSecret, buffer))}.
     *
     * @implNote
     * This implementation keeps HMAC-SHA256 inline so the
     * {@link NoSuchAlgorithmException} and {@link InvalidKeyException}
     * catch blocks live with the only call site that can throw them;
     * an unavailable HMAC algorithm is fatal per JCE conformance and
     * surfaces as {@link AssertionError}.
     *
     * @param token  the issued token, used both as the secret and as
     *               the nonce concatenated into the credential
     * @param buffer the buffer being uploaded
     * @return the credential field value
     */
    private static String buildCredential(WamPrivateStatsToken token, byte[] buffer) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(token.sharedSecret(), "HmacSHA256"));
            var hmac = mac.doFinal(buffer);
            return URL_BASE64.encodeToString(token.token())
                    + "+"
                    + URL_BASE64.encodeToString(hmac);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("HmacSHA256 must be available on every JVM", e);
        } catch (InvalidKeyException e) {
            throw new IllegalStateException("HMAC key rejected", e);
        }
    }

    /**
     * Assembles the {@code multipart/form-data} request body.
     *
     * @apiNote
     * The body layout is:
     * {@snippet :
     *     --boundary
     *     Content-Disposition: form-data; name="access_token"
     *
     *     245118376424571|3e7d275052f1522bf3200afcf53841a7
     *     --boundary
     *     Content-Disposition: form-data; name="credential"
     *
     *     {credential}
     *     --boundary
     *     Content-Disposition: form-data; name="message"; filename="WAMEventBuffer.dat"
     *     Content-Type: application/octet-stream
     *
     *     {buffer bytes}
     *     --boundary
     *     Content-Disposition: form-data; name="meta_data"
     *
     *     {"t":unixSeconds,"p":0}
     *     --boundary--
     * }
     *
     * @implNote
     * This implementation computes the total length up front and
     * writes the body directly into a single heap-allocated
     * {@code byte[]} so the result can be handed to
     * {@link HttpRequest.BodyPublishers#ofByteArray(byte[])} without
     * an extra copy.
     *
     * @param boundary   the per-request boundary string; must be
     *                   absent from every embedded part
     * @param credential the {@code credential} field value
     * @param buffer     the encoded WAM buffer
     * @return the assembled multipart body bytes
     */
    private static byte[] buildMultipartBody(String boundary, String credential, byte[] buffer) {
        var partSeparator = ("--" + boundary + "\r\n").getBytes(StandardCharsets.US_ASCII);
        var closingBoundary = ("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        var crlf = "\r\n".getBytes(StandardCharsets.US_ASCII);

        var accessTokenHeader = "Content-Disposition: form-data; name=\"access_token\"\r\n\r\n"
                .getBytes(StandardCharsets.US_ASCII);
        var accessTokenValue = ACCESS_TOKEN.getBytes(StandardCharsets.US_ASCII);

        var credentialHeader = "Content-Disposition: form-data; name=\"credential\"\r\n\r\n"
                .getBytes(StandardCharsets.US_ASCII);
        var credentialValue = credential.getBytes(StandardCharsets.US_ASCII);

        var messageHeader = ("Content-Disposition: form-data; name=\"message\"; filename=\""
                + BUFFER_FILE_NAME + "\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);

        var metaDataHeader = "Content-Disposition: form-data; name=\"meta_data\"\r\n\r\n"
                .getBytes(StandardCharsets.US_ASCII);
        var metaDataValue = ("{\"t\":" + Instant.now().getEpochSecond() + ",\"p\":" + META_PRIORITY + "}")
                .getBytes(StandardCharsets.US_ASCII);

        var totalLength = 4 * partSeparator.length
                + accessTokenHeader.length + accessTokenValue.length + crlf.length
                + credentialHeader.length + credentialValue.length + crlf.length
                + messageHeader.length + buffer.length + crlf.length
                + metaDataHeader.length + metaDataValue.length + crlf.length
                + closingBoundary.length;

        var body = new byte[totalLength];
        var offset = 0;

        System.arraycopy(partSeparator, 0, body, offset, partSeparator.length);
        offset = offset + partSeparator.length;
        System.arraycopy(accessTokenHeader, 0, body, offset, accessTokenHeader.length);
        offset = offset + accessTokenHeader.length;
        System.arraycopy(accessTokenValue, 0, body, offset, accessTokenValue.length);
        offset = offset + accessTokenValue.length;
        System.arraycopy(crlf, 0, body, offset, crlf.length);
        offset = offset + crlf.length;

        System.arraycopy(partSeparator, 0, body, offset, partSeparator.length);
        offset = offset + partSeparator.length;
        System.arraycopy(credentialHeader, 0, body, offset, credentialHeader.length);
        offset = offset + credentialHeader.length;
        System.arraycopy(credentialValue, 0, body, offset, credentialValue.length);
        offset = offset + credentialValue.length;
        System.arraycopy(crlf, 0, body, offset, crlf.length);
        offset = offset + crlf.length;

        System.arraycopy(partSeparator, 0, body, offset, partSeparator.length);
        offset = offset + partSeparator.length;
        System.arraycopy(messageHeader, 0, body, offset, messageHeader.length);
        offset = offset + messageHeader.length;
        System.arraycopy(buffer, 0, body, offset, buffer.length);
        offset = offset + buffer.length;
        System.arraycopy(crlf, 0, body, offset, crlf.length);
        offset = offset + crlf.length;

        System.arraycopy(partSeparator, 0, body, offset, partSeparator.length);
        offset = offset + partSeparator.length;
        System.arraycopy(metaDataHeader, 0, body, offset, metaDataHeader.length);
        offset = offset + metaDataHeader.length;
        System.arraycopy(metaDataValue, 0, body, offset, metaDataValue.length);
        offset = offset + metaDataValue.length;
        System.arraycopy(crlf, 0, body, offset, crlf.length);
        offset = offset + crlf.length;

        System.arraycopy(closingBoundary, 0, body, offset, closingBoundary.length);
        return body;
    }
}
