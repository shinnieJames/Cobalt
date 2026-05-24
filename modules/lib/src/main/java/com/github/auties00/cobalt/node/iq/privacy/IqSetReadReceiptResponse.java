package com.github.auties00.cobalt.node.iq.privacy;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.iq.IqOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed family of inbound reply variants produced by the relay for an
 * {@link IqSetReadReceiptRequest}.
 *
 * @apiNote
 * Pattern match against the three permitted subtypes ({@link Success}, {@link ClientError},
 * {@link ServerError}) to surface either the echoed read-receipts state or the error envelope.
 *
 * @implNote
 * This implementation collapses WA Web's split parse-and-throw flow ({@code photoResponseParser},
 * an inherited copy-paste name in WA Web for the read-receipts parser, plus the
 * {@code ServerStatusCodeError} thrown by {@code WAWebSetReadReceiptJob} on a
 * {@code 4xx}/{@code 5xx} reply) into a single sealed hierarchy.
 */
public sealed interface IqSetReadReceiptResponse extends IqOperation.Response
        permits IqSetReadReceiptResponse.Success, IqSetReadReceiptResponse.ClientError, IqSetReadReceiptResponse.ServerError {

    /**
     * Tries each {@link IqSetReadReceiptResponse} variant in priority order and returns the first
     * that parses cleanly.
     *
     * @apiNote
     * The dispatcher calls this immediately after receiving an inbound {@code <iq>} stanza whose
     * id matches an outstanding {@link IqSetReadReceiptRequest}; the empty {@link Optional}
     * indicates the stanza did not match any documented schema.
     *
     * @implNote
     * This implementation tries {@link Success} first, then {@link ClientError}, then
     * {@link ServerError}.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return the parsed variant, or {@link Optional#empty()} when no documented variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSetReadReceiptJob",
            exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends IqSetReadReceiptResponse> of(Node node, Node request) {
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
     * The {@code Success} reply variant; the relay echoes the new read-receipts category value.
     *
     * @apiNote
     * Consumers should write the echoed {@link #enabled()} state back to the local privacy
     * snapshot; WA Web's {@code WAWebUserPrefsMultiDeviceDebug.setDebugReadReceipt} caller does
     * this for the debug surface.
     */
    @WhatsAppWebModule(moduleName = "WAWebSetReadReceiptJob")
    final class Success implements IqSetReadReceiptResponse {
        /**
         * The new read-receipts state echoed by the relay.
         */
        private final boolean enabled;

        /**
         * Constructs a successful reply.
         *
         * @apiNote
         * Embedders normally obtain instances via {@link #of(Node, Node)}; this constructor is
         * also reachable for tests and synthetic fixtures.
         *
         * @param enabled the echoed read-receipts state
         */
        public Success(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Returns the echoed read-receipts state.
         *
         * @return {@code true} when read receipts are enabled, {@code false} when disabled
         */
        public boolean enabled() {
            return enabled;
        }

        /**
         * Tries to parse a {@link Success} variant from the given inbound stanza.
         *
         * @apiNote
         * The {@link Optional#empty()} return signals the stanza is not a success envelope or
         * the embedded category was not the expected {@code readreceipts} row; callers should
         * fall through to {@link ClientError#of(Node, Node)} and
         * {@link ServerError#of(Node, Node)}.
         *
         * @implNote
         * This implementation first asserts the {@code <iq type="result">} envelope via
         * {@link SmaxIqResultResponseMixin#validate(Node, Node)}, then walks
         * {@code <privacy>/<category>} and verifies the {@code name} attribute is
         * {@code "readreceipts"}; only the literal {@code "all"} and {@code "none"} values
         * resolve to {@code true} and {@code false}, matching WA Web's
         * {@code value!=="error"} branch in {@code photoResponseParser}. Any other value
         * (including the WA Web {@code "error"} sentinel) maps to {@link Optional#empty()},
         * which the caller will treat as schema mismatch and fall through to the error
         * variants.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return the parsed variant, or {@link Optional#empty()} when the stanza does not match
         *         the success schema
         */
        @WhatsAppWebExport(moduleName = "WAWebSetReadReceiptJob",
                exports = "photoResponseParser",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var privacy = node.getChild("privacy").orElse(null);
            if (privacy == null) {
                return Optional.empty();
            }
            var category = privacy.getChild("category").orElse(null);
            if (category == null) {
                return Optional.empty();
            }
            var name = category.getAttributeAsString("name").orElse(null);
            if (!"readreceipts".equals(name)) {
                return Optional.empty();
            }
            var value = category.getAttributeAsString("value").orElse(null);
            if (value == null) {
                return Optional.empty();
            }
            if ("all".equals(value)) {
                return Optional.of(new Success(true));
            }
            if ("none".equals(value)) {
                return Optional.of(new Success(false));
            }
            return Optional.empty();
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation compares the toggle state by value.
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
            return this.enabled == that.enabled;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation hashes the toggle state consistently with {@link #equals(Object)}.
         */
        @Override
        public int hashCode() {
            return Objects.hash(enabled);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation emits a debug-only representation; the format is not stable and
         * must not be parsed.
         */
        @Override
        public String toString() {
            return "IqSetReadReceiptResponse.Success[enabled=" + enabled + ']';
        }
    }

    /**
     * The {@code ClientError} reply variant; the relay rejected the request with a {@code 4xx}
     * error code.
     *
     * @apiNote
     * Surfaces the {@code <error code=... text=.../>} envelope as typed fields so the caller can
     * decide whether to retry, escalate, or surface to the UI; WA Web's
     * {@code WAWebSetReadReceiptJob} rejects the returned promise with a
     * {@code ServerStatusCodeError} on the same payload.
     */
    @WhatsAppWebModule(moduleName = "WAWebSetReadReceiptJob")
    final class ClientError implements IqSetReadReceiptResponse {
        /**
         * The numeric server-side error code (typically {@code 4xx}).
         */
        private final int errorCode;

        /**
         * The optional human-readable error text echoed back by the relay.
         */
        private final String errorText;

        /**
         * Constructs a client-error reply.
         *
         * @apiNote
         * Embedders normally obtain instances via {@link #of(Node, Node)}; this constructor is
         * also reachable for tests and synthetic fixtures.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be {@code null}
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional error text.
         *
         * @return the text, or {@link Optional#empty()} when omitted
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant from the given inbound stanza.
         *
         * @implNote
         * This implementation delegates the envelope match to
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return the parsed variant, or {@link Optional#empty()} when the schema does not match
         */
        @WhatsAppWebExport(moduleName = "WAWebSetReadReceiptJob",
                exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ClientError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseClientError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ClientError(envelope.code(), envelope.text()));
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation compares both error code and error text by value.
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
            return this.errorCode == that.errorCode
                    && Objects.equals(this.errorText, that.errorText);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation hashes both fields consistently with {@link #equals(Object)}.
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation emits a debug-only representation; the format is not stable and
         * must not be parsed.
         */
        @Override
        public String toString() {
            return "IqSetReadReceiptResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant; the relay encountered a transient internal failure
     * ({@code 5xx} error code).
     *
     * @apiNote
     * Distinguished from {@link ClientError} so callers can choose a different retry policy.
     */
    @WhatsAppWebModule(moduleName = "WAWebSetReadReceiptJob")
    final class ServerError implements IqSetReadReceiptResponse {
        /**
         * The numeric server-side error code (typically {@code 5xx}).
         */
        private final int errorCode;

        /**
         * The optional human-readable error text echoed back by the relay.
         */
        private final String errorText;

        /**
         * Constructs a server-error reply.
         *
         * @apiNote
         * Embedders normally obtain instances via {@link #of(Node, Node)}; this constructor is
         * also reachable for tests and synthetic fixtures.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be {@code null}
         */
        public ServerError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional error text.
         *
         * @return the text, or {@link Optional#empty()} when omitted
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant from the given inbound stanza.
         *
         * @implNote
         * This implementation delegates the envelope match to
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return the parsed variant, or {@link Optional#empty()} when the schema does not match
         */
        @WhatsAppWebExport(moduleName = "WAWebSetReadReceiptJob",
                exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ServerError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseServerError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ServerError(envelope.code(), envelope.text()));
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation compares both error code and error text by value.
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
            return this.errorCode == that.errorCode
                    && Objects.equals(this.errorText, that.errorText);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation hashes both fields consistently with {@link #equals(Object)}.
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation emits a debug-only representation; the format is not stable and
         * must not be parsed.
         */
        @Override
        public String toString() {
            return "IqSetReadReceiptResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
