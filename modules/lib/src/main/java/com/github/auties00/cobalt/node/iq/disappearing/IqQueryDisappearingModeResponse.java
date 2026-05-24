package com.github.auties00.cobalt.node.iq.disappearing;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.iq.IqOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed family of inbound reply variants produced by the relay in response to an
 * {@link IqQueryDisappearingModeRequest}.
 *
 * @apiNote
 * Switch on the returned variant to discriminate the relay outcome: a {@link Success}
 * carries the current default duration and the wall-clock at which it was last
 * applied, a {@link ClientError} surfaces a relay rejection, and a {@link ServerError}
 * surfaces a transient relay failure (WA Web wraps the latter in a
 * {@code ServerStatusCodeError} that the account-sync warmup propagates).
 *
 * @implNote
 * This implementation mirrors WA Web's {@code dmParser} which projects the
 * {@code <disappearing_mode duration t/>} payload into a {@code (duration, t)} tuple,
 * plus the standard SMAX server-error envelope.
 */
@WhatsAppWebModule(moduleName = "WAWebQueryDisappearingModeJob")
public sealed interface IqQueryDisappearingModeResponse extends IqOperation.Response
        permits IqQueryDisappearingModeResponse.Success, IqQueryDisappearingModeResponse.ClientError, IqQueryDisappearingModeResponse.ServerError {

    /**
     * Parses the inbound stanza into the first matching
     * {@link IqQueryDisappearingModeResponse} variant.
     *
     * @apiNote
     * Try this once per inbound reply; the priority ordering (success, then
     * client-error, then server-error) matches the wire shape and never returns
     * ambiguous matches.
     *
     * @implNote
     * This implementation calls each variant's {@code of(node, request)} in turn
     * and returns the first present result.
     *
     * @param node    the inbound IQ stanza received from the relay; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or empty when no
     *         documented variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryDisappearingModeJob",
            exports = "queryDisappearingMode",
            adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends IqQueryDisappearingModeResponse> of(Node node, Node request) {
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
     * Success variant. The relay returned the current default disappearing-mode
     * duration and the wall-clock at which it was last applied.
     *
     * @apiNote
     * Inspect {@link #duration()} for the per-chat default duration ({@link Duration#ZERO}
     * means the feature is off) and {@link #appliedAtSeconds()} for the relay's
     * wall-clock anchor.
     */
    @WhatsAppWebModule(moduleName = "WAWebQueryDisappearingModeJob")
    final class Success implements IqQueryDisappearingModeResponse {
        /**
         * Holds the default disappearing-mode duration applied to newly-created
         * chats; {@link Duration#ZERO} encodes the off state.
         */
        private final Duration duration;

        /**
         * Holds the wall-clock at which the duration was last applied, in
         * seconds since epoch.
         */
        private final long appliedAtSeconds;

        /**
         * Constructs a successful reply bound to the given duration and
         * apply-timestamp.
         *
         * @param duration         the default duration; never {@code null}
         * @param appliedAtSeconds the wall-clock the duration was last applied
         * @throws NullPointerException if {@code duration} is {@code null}
         */
        public Success(Duration duration, long appliedAtSeconds) {
            this.duration = Objects.requireNonNull(duration, "duration cannot be null");
            this.appliedAtSeconds = appliedAtSeconds;
        }

        /**
         * Returns the bound default disappearing-mode duration.
         *
         * @return the duration; never {@code null}
         */
        public Duration duration() {
            return duration;
        }

        /**
         * Returns the wall-clock at which the duration was last applied.
         *
         * @return seconds since epoch
         */
        public long appliedAtSeconds() {
            return appliedAtSeconds;
        }

        /**
         * Parses the inbound stanza into a {@link Success} variant when it
         * matches the success schema.
         *
         * @apiNote
         * Returns empty when the SMAX result-envelope check fails, when the
         * {@code <disappearing_mode>} child is absent, or when either the
         * {@code duration} or {@code t} attribute is missing.
         *
         * @implNote
         * This implementation reads both attributes as {@code long}; WA Web's
         * {@code dmParser} reads {@code duration} and {@code t} as {@code int}
         * but the underlying wire range is identical.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty
         *         when the stanza does not match the success schema
         */
        @WhatsAppWebExport(moduleName = "WAWebQueryDisappearingModeJob",
                exports = "dmParser",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var dmNode = node.getChild("disappearing_mode").orElse(null);
            if (dmNode == null) {
                return Optional.empty();
            }
            var durationAttr = dmNode.getAttributeAsLong("duration");
            if (durationAttr.isEmpty()) {
                return Optional.empty();
            }
            var tAttr = dmNode.getAttributeAsLong("t");
            if (tAttr.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Success(Duration.ofSeconds(durationAttr.getAsLong()),
                    tAttr.getAsLong()));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Success) obj;
            return this.appliedAtSeconds == that.appliedAtSeconds
                    && Objects.equals(this.duration, that.duration);
        }

        @Override
        public int hashCode() {
            return Objects.hash(duration, appliedAtSeconds);
        }

        @Override
        public String toString() {
            return "IqQueryDisappearingModeResponse.Success[duration=" + duration
                    + ", appliedAtSeconds=" + appliedAtSeconds + ']';
        }
    }

    /**
     * Client-error variant. The relay rejected the query with a {@code 4xx} code.
     *
     * @apiNote
     * A client-error here is uncommon since the request has no payload; treat it as
     * an authorisation or session-state issue.
     */
    @WhatsAppWebModule(moduleName = "WAWebQueryDisappearingModeJob")
    final class ClientError implements IqQueryDisappearingModeResponse {
        /**
         * Holds the numeric server-side error code.
         */
        private final int errorCode;

        /**
         * Holds the optional human-readable error text.
         */
        private final String errorText;

        /**
         * Constructs a client-error reply carrying the relay-echoed envelope.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be {@code null}
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric server-side error code.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the text, or empty when omitted
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Parses the inbound stanza into a {@link ClientError} variant when it
         * matches the standard SMAX client-error envelope.
         *
         * @apiNote
         * Returns empty when the envelope check fails; delegates entirely to
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty
         *         when the stanza does not match the client-error schema
         */
        @WhatsAppWebExport(moduleName = "WAWebQueryDisappearingModeJob",
                exports = "queryDisappearingMode",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ClientError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseClientError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ClientError(envelope.code(), envelope.text()));
        }

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

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        @Override
        public String toString() {
            return "IqQueryDisappearingModeResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * Server-error variant. The relay encountered a transient {@code 5xx} failure
     * processing the query.
     *
     * @apiNote
     * WA Web wraps this in {@code WAWebBackendErrors.ServerStatusCodeError}; the
     * account-sync warmup catches it and skips the disappearing-mode propagation
     * for the current cycle.
     */
    @WhatsAppWebModule(moduleName = "WAWebQueryDisappearingModeJob")
    final class ServerError implements IqQueryDisappearingModeResponse {
        /**
         * Holds the numeric server-side error code.
         */
        private final int errorCode;

        /**
         * Holds the optional human-readable error text.
         */
        private final String errorText;

        /**
         * Constructs a server-error reply carrying the relay-echoed envelope.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be {@code null}
         */
        public ServerError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric server-side error code.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the text, or empty when omitted
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Parses the inbound stanza into a {@link ServerError} variant when it
         * matches the standard SMAX server-error envelope.
         *
         * @apiNote
         * Returns empty when the envelope check fails; delegates entirely to
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty
         *         when the stanza does not match the server-error schema
         */
        @WhatsAppWebExport(moduleName = "WAWebQueryDisappearingModeJob",
                exports = "queryDisappearingMode",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ServerError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseServerError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ServerError(envelope.code(), envelope.text()));
        }

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

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        @Override
        public String toString() {
            return "IqQueryDisappearingModeResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
