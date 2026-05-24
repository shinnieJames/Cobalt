package com.github.auties00.cobalt.wam.privatestats;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link WamPrivateStatsUploader}'s request assembly and
 * status-code mapping against a recording HTTP-client stub.
 *
 * @apiNote
 * Pins the URL, the {@code multipart/form-data} part order, the
 * boundary shape, and the HTTP-status to
 * {@link WamPrivateStatsUploadResult.Type} mapping so any regression
 * in the wire-level upload contract surfaces immediately.
 *
 * @implNote
 * This implementation drives a {@link RecordingHttpClient} that
 * captures the request without performing any network I/O; the
 * uploader's token-acquisition path uses the same scripted
 * {@link SecureRandom} as
 * {@code WamPrivateStatsTokenIssuerTest} so the captured KAT bytes
 * propagate through.
 */
@DisplayName("WamPrivateStatsUploader behavioural")
class WamPrivateStatsUploaderTest {
    /**
     * The destination URL the uploader must POST to.
     */
    private static final String ENDPOINT = "https://dit.whatsapp.net/deidentified_telemetry";

    /**
     * The hard-coded {@code access_token} the WhatsApp Web bundle
     * sends; tests assert the same value is sent by Cobalt.
     */
    private static final String ACCESS_TOKEN = "245118376424571|3e7d275052f1522bf3200afcf53841a7";

    /**
     * Verifies the request URL is the {@code dit.whatsapp.net}
     * endpoint and the {@code Content-Type} carries an explicit
     * WebKit-shaped boundary.
     */
    @Test
    @DisplayName("request URL and Content-Type match the documented contract")
    void requestUrlAndContentType() {
        var captured = uploadCapture(new byte[]{1, 2, 3, 4}, 200);
        assertEquals(URI.create(ENDPOINT), captured.request().uri(),
                "must POST to dit.whatsapp.net/deidentified_telemetry");
        assertEquals("POST", captured.request().method());

        var contentType = captured.request().headers().firstValue("Content-Type").orElseThrow();
        assertTrue(contentType.startsWith("multipart/form-data; boundary="),
                "Content-Type must be multipart/form-data with explicit boundary, was: " + contentType);
        var boundary = contentType.substring("multipart/form-data; boundary=".length());
        assertTrue(boundary.startsWith("----WebKitFormBoundary"),
                "boundary must follow the WebKit convention: " + boundary);
    }

    /**
     * Verifies the multipart body carries the four expected parts
     * in the expected order.
     */
    @Test
    @DisplayName("multipart body has the four documented parts in the documented order")
    void multipartBodyHasFourParts() {
        var captured = uploadCapture(new byte[]{1, 2, 3, 4, 5}, 200);
        var body = new String(captured.body(), StandardCharsets.ISO_8859_1);

        var accessTokenIdx = body.indexOf("name=\"access_token\"");
        var credentialIdx = body.indexOf("name=\"credential\"");
        var messageIdx = body.indexOf("name=\"message\"");
        var metaDataIdx = body.indexOf("name=\"meta_data\"");

        assertTrue(accessTokenIdx >= 0, "access_token part present");
        assertTrue(credentialIdx > accessTokenIdx, "credential part appears after access_token");
        assertTrue(messageIdx > credentialIdx, "message part appears after credential");
        assertTrue(metaDataIdx > messageIdx, "meta_data part appears after message");

        assertTrue(body.contains(ACCESS_TOKEN),
                "access_token value must be the documented Facebook app credential");

        assertTrue(body.contains("filename=\"WAMEventBuffer.dat\""),
                "message attachment must use the WAMEventBuffer.dat filename");
        assertTrue(body.contains("Content-Type: application/octet-stream"),
                "message attachment must declare application/octet-stream");

        var metaDataValueStart = body.indexOf("{\"t\":", metaDataIdx);
        assertTrue(metaDataValueStart > 0, "meta_data must include a JSON {t,p} value");
        var metaDataValueEnd = body.indexOf("}", metaDataValueStart) + 1;
        var metaDataJson = body.substring(metaDataValueStart, metaDataValueEnd);
        var parsed = JSON.parseObject(metaDataJson);
        assertNotNull(parsed.get("t"), "meta_data must carry 't' (Unix seconds)");
        assertEquals(0, parsed.getIntValue("p"),
                "meta_data 'p' is hardcoded to 0 in the WhatsApp Web bundle");
    }

    /**
     * Verifies the captured WAM buffer appears verbatim in the
     * multipart body without transformation.
     */
    @Test
    @DisplayName("buffer bytes appear verbatim in the multipart body")
    void bufferAppearsVerbatim() {
        var buffer = new byte[]{0x42, (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
        var captured = uploadCapture(buffer, 200);

        var body = captured.body();
        var matched = false;
        outer:
        for (var i = 0; i <= body.length - buffer.length; i++) {
            for (var j = 0; j < buffer.length; j++) {
                if (body[i + j] != buffer[j]) {
                    continue outer;
                }
            }
            matched = true;
            break;
        }
        assertTrue(matched, "buffer bytes must appear verbatim inside the multipart body");
    }

    /**
     * Verifies the HTTP-status to {@link WamPrivateStatsUploadResult.Type}
     * mapping documented by {@link WamPrivateStatsUploader#upload(byte[])}.
     */
    @Test
    @DisplayName("status codes map to documented result types")
    void statusCodeMapping() {
        assertEquals(WamPrivateStatsUploadResult.Type.SUCCESS,
                uploadCapture(new byte[]{1}, 200).result().result());
        assertEquals(WamPrivateStatsUploadResult.Type.ERROR_PARSING,
                uploadCapture(new byte[]{1}, 400).result().result());
        assertEquals(WamPrivateStatsUploadResult.Type.ERROR_ACCESS_TOKEN,
                uploadCapture(new byte[]{1}, 401).result().result());
        assertEquals(WamPrivateStatsUploadResult.Type.ERROR_SERVER_OTHER,
                uploadCapture(new byte[]{1}, 429).result().result());
        assertEquals(WamPrivateStatsUploadResult.Type.ERROR_SERVER_OTHER,
                uploadCapture(new byte[]{1}, 500).result().result());
        assertEquals(WamPrivateStatsUploadResult.Type.ERROR_OTHER,
                uploadCapture(new byte[]{1}, 404).result().result());
        assertEquals(WamPrivateStatsUploadResult.Type.ERROR_OTHER,
                uploadCapture(new byte[]{1}, 503).result().result());
    }

    /**
     * Verifies that a token issuance failure short-circuits to
     * {@link WamPrivateStatsUploadResult.Type#ERROR_CREDENTIAL}
     * without contacting the HTTP layer.
     */
    @Test
    @DisplayName("token issuer failure surfaces as ERROR_CREDENTIAL")
    void issuerFailureSurfacesAsErrorCredential() {
        var hitHttp = new AtomicReference<Boolean>(false);
        var client = TestWhatsAppClient.create()
                .withSendNodeHandler(builder -> new NodeBuilder()
                        .description("iq")
                        .attribute("type", "error")
                        .build());
        var issuer = new WamPrivateStatsTokenIssuer(client, scriptedRandom(new byte[32], new byte[32]));
        var http = new RecordingHttpClient(200, _ -> hitHttp.set(true));
        var uploader = new WamPrivateStatsUploader(issuer, http);

        var result = uploader.upload(new byte[]{1, 2, 3});
        assertEquals(WamPrivateStatsUploadResult.Type.ERROR_CREDENTIAL, result.result());
        assertFalse(hitHttp.get(),
                "HTTP client must not be invoked when the issuer fails");
    }

    /**
     * Runs one {@link WamPrivateStatsUploader#upload(byte[])} call
     * against a scripted issuer plus a fake HTTP client, returning
     * the captured request, body, and result.
     *
     * @apiNote
     * Centralises the test setup so every behavioural test focuses
     * on its specific assertion.
     *
     * @param buffer     the buffer to upload
     * @param httpStatus the canned HTTP status the fake client must
     *                   return
     * @return the captured tuple
     */
    private static CapturedUpload uploadCapture(byte[] buffer, int httpStatus) {
        var vector = loadFirstVector();
        var msg = HexFormat.of().parseHex(vector.msg);
        var scalar = HexFormat.of().parseHex(vector.scalar);
        var signed = HexFormat.of().parseHex(vector.signed);
        var pk = HexFormat.of().parseHex(vector.pk);
        var client = TestWhatsAppClient.create()
                .withSendNodeHandler(builder -> issuerResponse(signed, pk));
        var issuer = new WamPrivateStatsTokenIssuer(client, scriptedRandom(msg, scalar));
        var capturedReq = new AtomicReference<HttpRequest>();
        var capturedBody = new AtomicReference<byte[]>();
        var http = new RecordingHttpClient(httpStatus, request -> {
            capturedReq.set(request);
            capturedBody.set(extractBody(request));
        });
        var uploader = new WamPrivateStatsUploader(issuer, http);
        var result = uploader.upload(buffer);
        return new CapturedUpload(capturedReq.get(), capturedBody.get(), result);
    }

    /**
     * Builds a successful sign-credential IQ response.
     *
     * @apiNote
     * Used by the upload-capture helper so the issuer sees a
     * canned-correct response.
     *
     * @param signed the signed-credential bytes
     * @param pk     the ACS public key bytes
     * @return the synthetic success node
     */
    private static Node issuerResponse(byte[] signed, byte[] pk) {
        return new NodeBuilder()
                .description("iq")
                .attribute("type", "result")
                .content(new NodeBuilder()
                        .description("sign_credential")
                        .content(List.of(
                                new NodeBuilder()
                                        .description("signed_credential")
                                        .content(signed)
                                        .build(),
                                new NodeBuilder()
                                        .description("acs_public_key")
                                        .content(pk)
                                        .build()))
                        .build())
                .build();
    }

    /**
     * Returns a {@link SecureRandom} that delivers exactly two
     * scripted byte sequences.
     *
     * @apiNote
     * The token issuer draws from {@code nextBytes} once for the
     * token and once for the blinding factor.
     *
     * @param first  the first byte sequence to deliver
     * @param second the second byte sequence to deliver
     * @return the scripted {@link SecureRandom}
     */
    private static SecureRandom scriptedRandom(byte[] first, byte[] second) {
        return new SecureRandom() {
            private int call;

            @Override
            public void nextBytes(byte[] target) {
                var source = switch (call++) {
                    case 0 -> first;
                    case 1 -> second;
                    default -> throw new AssertionError("scripted SecureRandom exhausted");
                };
                System.arraycopy(source, 0, target, 0, source.length);
            }
        };
    }

    /**
     * Extracts the request body bytes from the captured
     * {@link HttpRequest} via the body publisher's
     * {@link Flow.Subscriber} contract.
     *
     * @apiNote
     * Needed because {@link HttpRequest.BodyPublisher} does not
     * expose its bytes directly; the test subscribes synchronously
     * and collects the {@link ByteBuffer} payloads into a
     * heap buffer.
     *
     * @param request the captured request
     * @return the body bytes
     */
    private static byte[] extractBody(HttpRequest request) {
        var publisher = request.bodyPublisher().orElseThrow();
        var collected = new ByteArrayOutputStream();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                var arr = new byte[item.remaining()];
                item.get(arr);
                collected.writeBytes(arr);
            }

            @Override
            public void onError(Throwable throwable) {
                throw new AssertionError("unexpected publisher error", throwable);
            }

            @Override
            public void onComplete() {
            }
        });
        return collected.toByteArray();
    }

    /**
     * Loads the first ed25519 vector from the shared fixture file.
     *
     * @apiNote
     * Reuses the same vector that
     * {@code WamPrivateStatsTokenIssuerTest} pins, so any divergence
     * between the upload and issuance paths surfaces.
     *
     * @return the deserialised vector
     */
    private static Vector loadFirstVector() {
        var resource = "/fixtures/wam/ed25519-live-bundle-vectors.json";
        try (var stream = WamPrivateStatsUploaderTest.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new AssertionError("ed25519 vectors fixture missing on classpath: " + resource);
            }
            var json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            var v = JSON.parseObject(json).getJSONArray("vectors").getJSONObject(0);
            return new Vector(v.getString("msg"), v.getString("scalar"),
                    v.getString("blinded"), v.getString("pk"),
                    v.getString("signed"), v.getString("unblinded"));
        } catch (IOException error) {
            throw new UncheckedIOException("failed to load " + resource, error);
        }
    }

    /**
     * A compact holder for an upload's captured request, body, and
     * the uploader's classified result.
     *
     * @param request the captured HTTP request
     * @param body    the captured body bytes
     * @param result  the uploader's result
     */
    private record CapturedUpload(HttpRequest request, byte[] body, WamPrivateStatsUploadResult result) {
    }

    /**
     * A compact holder for the ed25519 vector hex strings consumed
     * by the tests.
     *
     * @param msg       the message hex
     * @param scalar    the scalar hex
     * @param blinded   the blinded-credential hex
     * @param pk        the ACS public-key hex
     * @param signed    the signed-credential hex
     * @param unblinded the unblinded-token hex
     */
    private record Vector(String msg, String scalar, String blinded, String pk, String signed, String unblinded) {
    }

    /**
     * A minimal {@link HttpClient} stub that records the request and
     * returns a canned status code.
     *
     * @apiNote
     * Sufficient for the uploader's single
     * {@link HttpClient#send(HttpRequest, HttpResponse.BodyHandler)}
     * call; abstract methods unrelated to that call throw to surface
     * unintended use immediately.
     */
    private static final class RecordingHttpClient extends HttpClient {
        /**
         * The canned HTTP status returned to the uploader.
         */
        private final int status;

        /**
         * The hook invoked with the captured request, used by the
         * tests to record the request bytes.
         */
        private final Consumer<HttpRequest> onSend;

        /**
         * Constructs a recording client returning the given status.
         *
         * @param status the canned HTTP status
         * @param onSend the hook to invoke with the captured request
         */
        RecordingHttpClient(int status, Consumer<HttpRequest> onSend) {
            this.status = status;
            this.onSend = onSend;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation throws because the uploader has no
         * legitimate reason to inspect the stub's SSL context.
         */
        @Override
        public SSLContext sslContext() {
            throw new UnsupportedOperationException("RecordingHttpClient: sslContext() is not stubbed");
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation throws because the uploader has no
         * legitimate reason to inspect the stub's SSL parameters.
         */
        @Override
        public SSLParameters sslParameters() {
            throw new UnsupportedOperationException("RecordingHttpClient: sslParameters() is not stubbed");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Version version() {
            return Version.HTTP_2;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation records the request via {@link #onSend}
         * and returns a {@link CannedResponse} carrying the
         * preconfigured status.
         */
        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            onSend.accept(request);
            return new CannedResponse<>(request, status);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            return CompletableFuture.completedFuture(send(request, responseBodyHandler));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }
    }

    /**
     * A synthetic {@link HttpResponse} carrying only the status
     * code, since the uploader only consults
     * {@link HttpResponse#statusCode()}.
     *
     * @param <T> the body type
     */
    private static final class CannedResponse<T> implements HttpResponse<T> {
        /**
         * The originating request, echoed back through {@link #request()}.
         */
        private final HttpRequest request;

        /**
         * The HTTP status code returned to the uploader.
         */
        private final int status;

        /**
         * Constructs a canned response for the given request and
         * status.
         *
         * @param request the originating request
         * @param status  the HTTP status code
         */
        CannedResponse(HttpRequest request, int status) {
            this.request = request;
            this.status = status;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int statusCode() {
            return status;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public HttpRequest request() {
            return request;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (_, _) -> true);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public T body() {
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public URI uri() {
            return request.uri();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_2;
        }
    }
}
