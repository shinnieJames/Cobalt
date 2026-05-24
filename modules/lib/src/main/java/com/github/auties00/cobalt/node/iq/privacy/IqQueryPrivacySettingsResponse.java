package com.github.auties00.cobalt.node.iq.privacy;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.iq.IqOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed family of inbound reply variants produced by the relay for an
 * {@link IqQueryPrivacySettingsRequest}.
 *
 * @apiNote
 * Pattern match against the three permitted subtypes ({@link Success}, {@link ClientError},
 * {@link ServerError}) to surface the per-category visibility map or the error envelope to the
 * caller; only one variant ever materialises per reply.
 *
 * @implNote
 * This implementation collapses WA Web's split parse-and-throw flow (the
 * {@code privacyParser} WAP parser plus the {@code ServerStatusCodeError} thrown by
 * {@code WAWebQueryPrivacySettingsJob.getPrivacy} on a {@code 4xx}/{@code 5xx} reply) into a
 * single sealed hierarchy; an error reply is a typed value here rather than an exception.
 */
@WhatsAppWebModule(moduleName = "WAWebQueryPrivacySettingsJob")
public sealed interface IqQueryPrivacySettingsResponse extends IqOperation.Response
        permits IqQueryPrivacySettingsResponse.Success,
        IqQueryPrivacySettingsResponse.ClientError,
        IqQueryPrivacySettingsResponse.ServerError {

    /**
     * Tries each {@link IqQueryPrivacySettingsResponse} variant in priority order and returns the
     * first that parses cleanly.
     *
     * @apiNote
     * The dispatcher calls this immediately after receiving an inbound {@code <iq>} stanza whose
     * id matches an outstanding {@link IqQueryPrivacySettingsRequest}; the empty
     * {@link Optional} indicates the stanza did not match any documented schema (caller may want
     * to log and drop).
     *
     * @implNote
     * This implementation tries {@link Success} first, then {@link ClientError}
     * ({@code 4xx} envelope), then {@link ServerError} ({@code 5xx} envelope); the success
     * parser performs the {@code <iq type="result">} validation via
     * {@link SmaxIqResultResponseMixin#validate(Node, Node)} before reading children.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return the parsed variant, or {@link Optional#empty()} when no documented variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryPrivacySettingsJob",
            exports = "getPrivacy", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends IqQueryPrivacySettingsResponse> of(Node node, Node request) {
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
     * The {@code Success} reply variant; the relay returned a {@code <privacy>} envelope listing
     * every privacy category and its current value.
     *
     * @apiNote
     * The {@link #categories()} map is the typed snapshot consumers should write to the local
     * privacy store; an absent key means the relay did not return a value for that category (e.g.
     * because the relay rejected it with {@code value="error"} which the parser silently drops).
     */
    @WhatsAppWebModule(moduleName = "WAWebQueryPrivacySettingsJob")
    final class Success implements IqQueryPrivacySettingsResponse {
        /**
         * The parsed per-category settings; never {@code null}. May be empty when the relay
         * returned no {@code <category>} children.
         */
        private final Map<IqQueryPrivacySettingsCategoryName, IqQueryPrivacySettingsVisibility> categories;

        /**
         * Constructs a {@code Success} reply.
         *
         * @apiNote
         * Embedders normally obtain instances via {@link #of(Node, Node)}; this constructor is
         * also reachable for tests and synthetic fixtures.
         *
         * @implNote
         * This implementation defensively copies {@code categories} via {@link Map#copyOf(Map)};
         * the resulting instance is immutable and safe to publish.
         *
         * @param categories the per-category settings; never {@code null}
         * @throws NullPointerException if {@code categories} is {@code null}
         */
        public Success(Map<IqQueryPrivacySettingsCategoryName, IqQueryPrivacySettingsVisibility> categories) {
            Objects.requireNonNull(categories, "categories cannot be null");
            this.categories = Map.copyOf(categories);
        }

        /**
         * Returns the parsed per-category settings.
         *
         * @apiNote
         * Key into the map with the {@link IqQueryPrivacySettingsCategoryName} constants; a
         * missing key means the relay either did not return a value or returned the
         * {@code value="error"} sentinel which is silently dropped during parsing.
         *
         * @return an unmodifiable map keyed by category; never {@code null}
         */
        public Map<IqQueryPrivacySettingsCategoryName, IqQueryPrivacySettingsVisibility> categories() {
            return categories;
        }

        /**
         * Tries to parse a {@link Success} variant from the given inbound stanza.
         *
         * @apiNote
         * The {@link Optional#empty()} return signals that the stanza is not a success envelope
         * (either {@code type != "result"} or no {@code <privacy>} child); callers should fall
         * through to {@link ClientError#of(Node, Node)} and {@link ServerError#of(Node, Node)}.
         *
         * @implNote
         * This implementation iterates every {@code <category>} child, projects the
         * {@code name} attribute via {@link IqQueryPrivacySettingsCategoryName#fromWire(String)}
         * and the {@code value} attribute via
         * {@link IqQueryPrivacySettingsVisibility#fromWire(String)}, and silently skips entries
         * whose name or value cannot be resolved. WA Web's
         * {@code WAWebPrivacySettings.*_WITH_ERROR} tables include an {@code error} marker that
         * the parser logs but drops; Cobalt collapses that path into the silent skip.
         *
         * @param node    the inbound IQ stanza; never {@code null}
         * @param request the original outbound request; never {@code null}
         * @return the parsed variant, or {@link Optional#empty()} when the stanza does not match
         *         the success schema
         */
        @WhatsAppWebExport(moduleName = "WAWebQueryPrivacySettingsJob",
                exports = "privacyParser", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var privacy = node.getChild("privacy").orElse(null);
            if (privacy == null) {
                return Optional.empty();
            }
            var map = new EnumMap<IqQueryPrivacySettingsCategoryName, IqQueryPrivacySettingsVisibility>(IqQueryPrivacySettingsCategoryName.class);
            for (var category : privacy.getChildren("category")) {
                var name = category.getAttributeAsString("name")
                        .flatMap(IqQueryPrivacySettingsCategoryName::fromWire)
                        .orElse(null);
                if (name == null) {
                    continue;
                }
                var value = category.getAttributeAsString("value")
                        .flatMap(IqQueryPrivacySettingsVisibility::fromWire)
                        .orElse(null);
                if (value == null) {
                    continue;
                }
                map.put(name, value);
            }
            return Optional.of(new Success(map));
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation compares the categories map by value.
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
            return Objects.equals(this.categories, that.categories);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation hashes the categories map consistently with
         * {@link #equals(Object)}.
         */
        @Override
        public int hashCode() {
            return Objects.hash(categories);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation emits a debug-only representation of the categories map; the
         * format is not stable and must not be parsed.
         */
        @Override
        public String toString() {
            return "IqQueryPrivacySettingsResponse.Success[categories=" + categories + ']';
        }
    }

    /**
     * The {@code ClientError} reply variant; the relay rejected the request with a {@code 4xx}
     * error code.
     *
     * @apiNote
     * Surfaces the {@code <error code=... text=.../>} envelope as typed fields so the caller can
     * decide whether to retry, escalate, or surface to the UI; WA Web's
     * {@code WAWebQueryPrivacySettingsJob.getPrivacy} throws a {@code ServerStatusCodeError} for
     * the same payload.
     */
    @WhatsAppWebModule(moduleName = "WAWebQueryPrivacySettingsJob")
    final class ClientError implements IqQueryPrivacySettingsResponse {
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
         * @apiNote
         * The {@link Optional#empty()} return signals that the stanza is not a {@code 4xx}
         * error envelope; callers should fall through to {@link ServerError#of(Node, Node)}.
         *
         * @implNote
         * This implementation delegates the envelope match to
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}, which centralises the
         * {@code <iq type="error">} plus {@code <error>} child validation.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return the parsed variant, or {@link Optional#empty()} when the stanza does not match
         *         the client-error schema
         */
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
            return "IqQueryPrivacySettingsResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant; the relay encountered a transient internal failure
     * ({@code 5xx} error code).
     *
     * @apiNote
     * Distinguished from {@link ClientError} so callers can choose a different retry policy;
     * transient failures normally warrant a backoff-and-retry, client errors normally do not.
     */
    @WhatsAppWebModule(moduleName = "WAWebQueryPrivacySettingsJob")
    final class ServerError implements IqQueryPrivacySettingsResponse {
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
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}, which centralises the
         * {@code <iq type="error">} plus {@code <error>} child validation.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return the parsed variant, or {@link Optional#empty()} when the stanza does not match
         *         the server-error schema
         */
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
            return "IqQueryPrivacySettingsResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
