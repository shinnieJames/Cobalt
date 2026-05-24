package com.github.auties00.cobalt.node.smax.pushconfig;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed family of inbound reply variants for
 * {@link SmaxPushConfigSetRequest}.
 *
 * @apiNote
 * Mirrors WA Web's {@code WASmaxPushConfigSetRPC} dispatch:
 * {@link Success} (relay accepted the change), {@link InternalServerError}
 * (transient 500), and {@link Conflict} (409 when another registration
 * holds the same push channel). WA Web's
 * {@code WAWebSetPushConfigJob.setPushConfig} returns the {@code (code,
 * text)} pair to the caller on both error variants and logs the failure
 * under the {@code push-notification} tag; Cobalt embedders that wire
 * server push to their own application typically follow the same
 * pattern.
 */
public sealed interface SmaxPushConfigSetResponse extends SmaxOperation.Response
        permits SmaxPushConfigSetResponse.Success, SmaxPushConfigSetResponse.InternalServerError, SmaxPushConfigSetResponse.Conflict {

    /**
     * Tries each {@link SmaxPushConfigSetResponse} variant in priority
     * order.
     *
     * @apiNote
     * The dispatcher entry point used by Cobalt's SMAX layer to lift an
     * inbound stanza into the sealed disjunction.
     *
     * @implNote
     * This implementation tries {@link Success}, then
     * {@link InternalServerError}, then {@link Conflict}, mirroring the
     * WA Web call order in
     * {@code WASmaxPushConfigSetRPC.sendSetRPC}.
     *
     * @param node    the inbound IQ stanza
     * @param request the original outbound stanza, used to validate
     *                echoed identifiers
     * @return an {@link Optional} carrying the parsed variant, or
     *         {@link Optional#empty()} on no-match
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxPushConfigSetRPC",
            exports = "sendSetRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxPushConfigSetResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var success = Success.of(node, request);
        if (success.isPresent()) {
            return success;
        }
        var internalServerError = InternalServerError.of(node, request);
        if (internalServerError.isPresent()) {
            return internalServerError;
        }
        return Conflict.of(node, request);
    }

    /**
     * The {@code Success} reply variant.
     *
     * @apiNote
     * Signals that the relay accepted the push-config change; carries
     * no payload beyond the envelope echo.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInPushConfigSetResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInPushConfigIQResultResponseMixin")
    final class Success implements SmaxPushConfigSetResponse {
        /**
         * Constructs a successful reply.
         *
         * @apiNote
         * Used by {@link #of(Node, Node)} after envelope validation; the
         * type carries no per-instance state.
         */
        public Success() {
        }

        /**
         * Tries to parse a {@link Success} variant.
         *
         * @apiNote
         * Returns {@link Optional#empty()} when the envelope is not a
         * well-formed {@code <iq type="result">} echoing the request
         * identifiers.
         *
         * @implNote
         * This implementation delegates the envelope check to
         * {@link SmaxIqResultResponseMixin#validate(Node, Node)} because
         * the success shape carries no further state to extract.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInPushConfigSetResponseSuccess",
                exports = "parseSetResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            return Optional.of(new Success());
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation treats every instance as equal to every
         * other because the type carries no state.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            return obj != null && obj.getClass() == this.getClass();
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation returns a class-level hash to stay
         * consistent with {@link #equals(Object)}.
         */
        @Override
        public int hashCode() {
            return Success.class.hashCode();
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation mirrors the record-like rendering used
         * across the {@code Smax*} response family.
         */
        @Override
        public String toString() {
            return "SmaxPushConfigSetResponse.Success[]";
        }
    }

    /**
     * The {@code InternalServerError} reply variant carrying
     * {@code (500, "internal-server-error")}.
     *
     * @apiNote
     * Signals a transient relay-side failure; embedders should retry
     * with backoff. WA Web's
     * {@code WAWebSetPushConfigJob.setPushConfig} logs the failure but
     * does not retry inside the job; the caller decides.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInPushConfigSetResponseInternalServerError")
    @WhatsAppWebModule(moduleName = "WASmaxInPushConfigIQErrorInternalServerErrorMixin")
    final class InternalServerError implements SmaxPushConfigSetResponse {
        /**
         * Constructs an internal-server-error reply.
         *
         * @apiNote
         * The shape carries no payload beyond the asserted
         * {@code (500, "internal-server-error")} pair, so callers
         * normally let {@link #of(Node, Node)} build the instance.
         */
        public InternalServerError() {
        }

        /**
         * Returns the numeric error code.
         *
         * @apiNote
         * Always {@code 500}; included for symmetry with the
         * {@code (code, text)} pair surfaced by other SMAX error
         * variants.
         *
         * @return the code
         */
        public int errorCode() {
            return 500;
        }

        /**
         * Returns the error text.
         *
         * @apiNote
         * Always {@code "internal-server-error"}; included for symmetry
         * with the {@code (code, text)} pair surfaced by other SMAX
         * error variants.
         *
         * @return the text
         */
        public String errorText() {
            return "internal-server-error";
        }

        /**
         * Tries to parse an {@link InternalServerError} variant.
         *
         * @apiNote
         * Returns {@link Optional#empty()} for any reply whose error
         * envelope does not carry exactly
         * {@code (500, "internal-server-error")}.
         *
         * @implNote
         * This implementation re-uses
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}
         * for envelope validation and then asserts the literal
         * {@code (500, "internal-server-error")} pair.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInPushConfigSetResponseInternalServerError",
                exports = "parseSetResponseInternalServerError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<InternalServerError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseServerError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            if (envelope.code() != 500 || !"internal-server-error".equals(envelope.text())) {
                return Optional.empty();
            }
            return Optional.of(new InternalServerError());
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation treats every instance as equal to every
         * other because the type carries no per-instance state.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            return obj != null && obj.getClass() == this.getClass();
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation returns a class-level hash to stay
         * consistent with {@link #equals(Object)}.
         */
        @Override
        public int hashCode() {
            return InternalServerError.class.hashCode();
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation mirrors the record-like rendering used
         * across the {@code Smax*} response family.
         */
        @Override
        public String toString() {
            return "SmaxPushConfigSetResponse.InternalServerError[]";
        }
    }

    /**
     * The {@code Conflict} reply variant carrying
     * {@code (409, "conflict")}.
     *
     * @apiNote
     * The requested push registration collides with an existing one
     * (typically two devices vying for the same push token); the relay
     * keeps the existing registration. Embedders surfacing push
     * registration UIs should ask the user to drop the conflicting
     * device or call the {@code Clear} variant of
     * {@link SmaxPushConfigSetRequest} before retrying.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInPushConfigSetResponseConflict")
    @WhatsAppWebModule(moduleName = "WASmaxInPushConfigIQErrorConflictMixin")
    final class Conflict implements SmaxPushConfigSetResponse {
        /**
         * Constructs a conflict reply.
         *
         * @apiNote
         * The shape carries no payload beyond the asserted
         * {@code (409, "conflict")} pair, so callers normally let
         * {@link #of(Node, Node)} build the instance.
         */
        public Conflict() {
        }

        /**
         * Returns the numeric error code.
         *
         * @apiNote
         * Always {@code 409}; included for symmetry with the
         * {@code (code, text)} pair surfaced by other SMAX error
         * variants.
         *
         * @return the code
         */
        public int errorCode() {
            return 409;
        }

        /**
         * Returns the error text.
         *
         * @apiNote
         * Always {@code "conflict"}; included for symmetry with the
         * {@code (code, text)} pair surfaced by other SMAX error
         * variants.
         *
         * @return the text
         */
        public String errorText() {
            return "conflict";
        }

        /**
         * Tries to parse a {@link Conflict} variant.
         *
         * @apiNote
         * Returns {@link Optional#empty()} for any reply whose error
         * envelope does not carry exactly {@code (409, "conflict")}.
         *
         * @implNote
         * This implementation re-uses
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}
         * for envelope validation and then asserts the literal
         * {@code (409, "conflict")} pair.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInPushConfigSetResponseConflict",
                exports = "parseSetResponseConflict",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Conflict> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseClientError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            if (envelope.code() != 409 || !"conflict".equals(envelope.text())) {
                return Optional.empty();
            }
            return Optional.of(new Conflict());
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation treats every instance as equal to every
         * other because the type carries no per-instance state.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            return obj != null && obj.getClass() == this.getClass();
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation returns a class-level hash to stay
         * consistent with {@link #equals(Object)}.
         */
        @Override
        public int hashCode() {
            return Conflict.class.hashCode();
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation mirrors the record-like rendering used
         * across the {@code Smax*} response family.
         */
        @Override
        public String toString() {
            return "SmaxPushConfigSetResponse.Conflict[]";
        }
    }
}
