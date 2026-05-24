package com.github.auties00.cobalt.node.smax.waffle;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * The sealed family of inbound replies to a
 * {@link SmaxWaffleGetCertificateRequest}.
 *
 * @apiNote
 * Mirrors WA Web's three documented {@code GetCertificate} reply
 * shapes: a {@link Success} carrying the requested PEM subset under a
 * {@code <reply timestamp/>} envelope (consumed by
 * {@code WAWebAccountLinkingAPI.fetchValidCertificate}), a
 * {@link ClientError} for malformed or unauthorised requests, and a
 * {@link ServerError} for transient relay failures.
 */
public sealed interface SmaxWaffleGetCertificateResponse extends SmaxOperation.Response
        permits SmaxWaffleGetCertificateResponse.Success, SmaxWaffleGetCertificateResponse.ClientError, SmaxWaffleGetCertificateResponse.ServerError {

    /**
     * Tries each {@link SmaxWaffleGetCertificateResponse} variant in
     * priority order and returns the first that parses cleanly.
     *
     * @apiNote
     * Mirrors WA Web's {@code sendGetCertificateRPC} dispatch: the
     * incoming stanza is offered to the {@link Success} parser first,
     * then the {@link ClientError} parser, then the
     * {@link ServerError} parser.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or
     *         empty when none of the three parsers matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxWaffleGetCertificateRPC",
            exports = "sendGetCertificateRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxWaffleGetCertificateResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var success = Success.of(node, request);
        if (success.isPresent()) {
            return success;
        }
        var clientError = ClientError.of(node, request);
        if (clientError.isPresent()) {
            return clientError;
        }
        return ServerError.of(node, request);
    }

    /**
     * One PEM child carried inside the {@link Success} reply.
     *
     * @apiNote
     * Models the shape shared by {@code <encryption_pem/>},
     * {@code <signature_pem/>}, and {@code <password_pem/>}: a
     * positive {@code ttl} in seconds, an optional {@code key_id} (set
     * only on the password PEM), and the raw PEM content as bytes.
     * The PEM content itself is ASCII text but is modelled as raw
     * bytes to match WA Web's
     * {@code WASmaxParseUtils.contentBytesRange} parser.
     *
     * @param ttl   the per-PEM time-to-live in seconds; always
     *              positive
     * @param keyId the per-PEM key id; non-{@code null} only on the
     *              password PEM
     * @param pem   the raw PEM content bytes
     */
    record Pem(int ttl, Integer keyId, byte[] pem) {

        /**
         * Returns the optional key id.
         *
         * @apiNote
         * {@link Optional#empty()} represents the wire absence rather
         * than a sentinel zero; on the encryption and signature PEMs
         * the attribute is never present, while on the password PEM it
         * is mandatory.
         *
         * @return an {@link Optional} carrying the key id, or empty
         *         when absent
         */
        public Optional<Integer> keyIdAsOptional() {
            return Optional.ofNullable(keyId);
        }

        /**
         * Returns whether the given object is a {@link Pem} with
         * equal payload fields.
         *
         * @param obj the candidate; may be {@code null}
         * @return {@code true} when ttl, key id, and PEM bytes all
         *         match
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Pem) obj;
            return this.ttl == that.ttl
                    && Objects.equals(this.keyId, that.keyId)
                    && Arrays.equals(this.pem, that.pem);
        }

        /**
         * Returns a hash code derived from the three payload fields.
         *
         * @return a content-based hash consistent with
         *         {@link #equals(Object)}
         */
        @Override
        public int hashCode() {
            var result = Objects.hash(ttl, keyId);
            result = 31 * result + Arrays.hashCode(pem);
            return result;
        }

        /**
         * Returns a debug rendering that summarises the PEM bytes as
         * a length rather than as the raw content.
         *
         * @return a human-readable summary; never {@code null}
         */
        @Override
        public String toString() {
            return "SmaxWaffleGetCertificateResponse.Pem[ttl=" + ttl
                    + ", keyId=" + keyId
                    + ", pem=" + (pem != null ? pem.length + " bytes" : "null") + ']';
        }
    }

    /**
     * The {@code Success} reply variant: the relay returned the
     * requested PEM subset under a {@code <reply timestamp/>}
     * envelope.
     *
     * @apiNote
     * Consumed by {@code WAWebAccountLinkingAPI.fetchValidCertificate}.
     * Each of the three PEM children is independently optional; the
     * caller only sees PEMs whose corresponding marker was set on the
     * request. The {@link #replyTimestamp()} is the server-stamped
     * issue time used to compute the per-PEM expiry from
     * {@link Pem#ttl()}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInWaffleGetCertificateResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInWaffleGetCertificateResponseMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInWaffleIQResultResponseMixin")
    final class Success implements SmaxWaffleGetCertificateResponse {
        /**
         * The relay-stamped reply timestamp, in seconds since the Unix
         * epoch.
         */
        private final long replyTimestamp;

        /**
         * The encryption PEM, or {@code null} when the relay omitted
         * it.
         */
        private final Pem encryptionPem;

        /**
         * The signature PEM, or {@code null} when the relay omitted
         * it.
         */
        private final Pem signaturePem;

        /**
         * The password PEM, or {@code null} when the relay omitted
         * it.
         */
        private final Pem passwordPem;

        /**
         * Constructs a new success projection.
         *
         * @apiNote
         * Called by {@link #of(Node, Node)} after the envelope and
         * payload have been validated; embedders typically do not
         * instantiate this directly.
         *
         * @param replyTimestamp the relay-stamped reply timestamp
         * @param encryptionPem  the encryption PEM, or {@code null}
         *                       when absent
         * @param signaturePem   the signature PEM, or {@code null}
         *                       when absent
         * @param passwordPem    the password PEM, or {@code null} when
         *                       absent
         */
        public Success(long replyTimestamp, Pem encryptionPem, Pem signaturePem, Pem passwordPem) {
            this.replyTimestamp = replyTimestamp;
            this.encryptionPem = encryptionPem;
            this.signaturePem = signaturePem;
            this.passwordPem = passwordPem;
        }

        /**
         * Returns the relay-stamped reply timestamp.
         *
         * @return the timestamp as supplied by the relay
         */
        public long replyTimestamp() {
            return replyTimestamp;
        }

        /**
         * Returns the encryption PEM when the relay supplied one.
         *
         * @return an {@link Optional} carrying the PEM, or empty when
         *         absent
         */
        public Optional<Pem> encryptionPem() {
            return Optional.ofNullable(encryptionPem);
        }

        /**
         * Returns the signature PEM when the relay supplied one.
         *
         * @return an {@link Optional} carrying the PEM, or empty when
         *         absent
         */
        public Optional<Pem> signaturePem() {
            return Optional.ofNullable(signaturePem);
        }

        /**
         * Returns the password PEM when the relay supplied one.
         *
         * @return an {@link Optional} carrying the PEM, or empty when
         *         absent
         */
        public Optional<Pem> passwordPem() {
            return Optional.ofNullable(passwordPem);
        }

        /**
         * Tries to parse a {@link Success} variant from the inbound
         * stanza.
         *
         * @apiNote
         * Returns {@link Optional#empty()} when the envelope check
         * fails, when the {@code <reply/>} child is missing, or when
         * the inner {@code timestamp} attribute is absent or
         * non-positive.
         *
         * @implNote
         * This implementation parses each of the three PEM children
         * through {@link #parsePem(Node)}, which gates on the
         * {@code ttl} attribute being at least {@code 1} and silently
         * drops the PEM otherwise (matching WA Web's
         * {@code attrIntRange(name, 1, undefined)}). The four
         * per-blob byte-range checks WA Web applies (each PEM 1-4092
         * bytes) are not re-applied here.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty on no-match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInWaffleGetCertificateResponseSuccess",
                exports = "parseGetCertificateResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var reply = node.getChild("reply").orElse(null);
            if (reply == null) {
                return Optional.empty();
            }
            var timestamp = reply.getAttributeAsLong("timestamp").orElse(-1L);
            if (timestamp < 1) {
                return Optional.empty();
            }
            var encryptionPem = parsePem(reply.getChild("encryption_pem").orElse(null));
            var signaturePem = parsePem(reply.getChild("signature_pem").orElse(null));
            var passwordPem = parsePem(reply.getChild("password_pem").orElse(null));
            return Optional.of(new Success(timestamp, encryptionPem, signaturePem, passwordPem));
        }

        /**
         * Parses a single PEM child node into a {@link Pem} record.
         *
         * @apiNote
         * Internal helper for {@link #of(Node, Node)}; returns
         * {@code null} (rather than an empty {@link Optional}) so the
         * three PEM slots on {@link Success} can be assigned directly.
         *
         * @implNote
         * This implementation accepts both the
         * {@code (ttl, key_id, content)} shape of the password PEM and
         * the {@code (ttl, content)} shape of the encryption and
         * signature PEMs by treating {@code key_id} as optional; WA Web
         * uses three distinct parser functions, one per child.
         *
         * @param pemNode the PEM child node, or {@code null}
         * @return the parsed {@link Pem}, or {@code null} when the
         *         node is missing or malformed
         */
        private static Pem parsePem(Node pemNode) {
            if (pemNode == null) {
                return null;
            }
            var ttl = pemNode.getAttributeAsInt("ttl").orElse(-1);
            if (ttl < 1) {
                return null;
            }
            var keyId = pemNode.getAttributeAsInt("key_id").isPresent()
                    ? pemNode.getAttributeAsInt("key_id").getAsInt()
                    : null;
            var bytes = pemNode.toContentBytes().orElse(null);
            if (bytes == null) {
                return null;
            }
            return new Pem(ttl, keyId, bytes);
        }

        /**
         * Returns whether the given object is a {@link Success} with
         * equal payload fields.
         *
         * @param obj the candidate; may be {@code null}
         * @return {@code true} when timestamp and the three PEMs all
         *         match
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Success) obj;
            return this.replyTimestamp == that.replyTimestamp
                    && Objects.equals(this.encryptionPem, that.encryptionPem)
                    && Objects.equals(this.signaturePem, that.signaturePem)
                    && Objects.equals(this.passwordPem, that.passwordPem);
        }

        /**
         * Returns a hash code derived from the four payload fields.
         *
         * @return a content-based hash consistent with
         *         {@link #equals(Object)}
         */
        @Override
        public int hashCode() {
            return Objects.hash(replyTimestamp, encryptionPem, signaturePem, passwordPem);
        }

        /**
         * Returns a debug rendering of this success variant.
         *
         * @return a human-readable summary; never {@code null}
         */
        @Override
        public String toString() {
            return "SmaxWaffleGetCertificateResponse.Success[replyTimestamp=" + replyTimestamp
                    + ", encryptionPem=" + encryptionPem
                    + ", signaturePem=" + signaturePem
                    + ", passwordPem=" + passwordPem + ']';
        }
    }

    /**
     * The {@code ClientError} reply variant: the relay rejected the
     * request with a code below {@code 500}.
     *
     * @apiNote
     * Surfaces malformed-request and unauthorised rejections from the
     * Waffle backend; {@code WAWebAccountLinkingAPI.fetchValidCertificate}
     * logs {@code [WAFFLE] GetCertificate RPC failed} and returns
     * {@code null} so the caller treats the certificate cache as
     * stale.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInWaffleGetCertificateResponseError")
    final class ClientError implements SmaxWaffleGetCertificateResponse {
        /**
         * The numeric error code.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text.
         */
        private final String errorText;

        /**
         * Constructs a new client-error reply.
         *
         * @apiNote
         * Called by {@link #of(Node, Node)} after the envelope shape
         * has been validated; embedders typically do not instantiate
         * this directly.
         *
         * @param errorCode the numeric error code
         * @param errorText the human-readable text, or {@code null}
         *                  when absent
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @return the code as supplied by the relay
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the text, or empty when
         *         the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant from the
         * inbound stanza.
         *
         * @apiNote
         * Delegates the envelope and code-range check to
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)},
         * which only matches codes below {@code 500}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty on no-match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInWaffleGetCertificateResponseError",
                exports = "parseGetCertificateResponseError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ClientError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseClientError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ClientError(envelope.code(), envelope.text()));
        }

        /**
         * Returns whether the given object is a {@link ClientError}
         * with equal code and text.
         *
         * @param obj the candidate; may be {@code null}
         * @return {@code true} when both code and text match
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (ClientError) obj;
            return this.errorCode == that.errorCode && Objects.equals(this.errorText, that.errorText);
        }

        /**
         * Returns a hash code derived from the code and text.
         *
         * @return a content-based hash consistent with
         *         {@link #equals(Object)}
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * Returns a debug rendering of this client-error variant.
         *
         * @return a human-readable summary; never {@code null}
         */
        @Override
        public String toString() {
            return "SmaxWaffleGetCertificateResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant: the relay rejected the
     * request with a code of {@code 500} or above.
     *
     * @apiNote
     * Indicates a transient relay-side failure.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInWaffleGetCertificateResponseError")
    final class ServerError implements SmaxWaffleGetCertificateResponse {
        /**
         * The numeric error code.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text.
         */
        private final String errorText;

        /**
         * Constructs a new server-error reply.
         *
         * @apiNote
         * Called by {@link #of(Node, Node)} after the envelope shape
         * has been validated; embedders typically do not instantiate
         * this directly.
         *
         * @param errorCode the numeric error code
         * @param errorText the human-readable text, or {@code null}
         *                  when absent
         */
        public ServerError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @return the code as supplied by the relay
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the text, or empty when
         *         the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant from the
         * inbound stanza.
         *
         * @apiNote
         * Delegates the envelope and code-range check to
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)},
         * which only matches codes at or above {@code 500}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty on no-match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInWaffleGetCertificateResponseError",
                exports = "parseGetCertificateResponseError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ServerError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseServerError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ServerError(envelope.code(), envelope.text()));
        }

        /**
         * Returns whether the given object is a {@link ServerError}
         * with equal code and text.
         *
         * @param obj the candidate; may be {@code null}
         * @return {@code true} when both code and text match
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (ServerError) obj;
            return this.errorCode == that.errorCode && Objects.equals(this.errorText, that.errorText);
        }

        /**
         * Returns a hash code derived from the code and text.
         *
         * @return a content-based hash consistent with
         *         {@link #equals(Object)}
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * Returns a debug rendering of this server-error variant.
         *
         * @return a human-readable summary; never {@code null}
         */
        @Override
        public String toString() {
            return "SmaxWaffleGetCertificateResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
