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
import java.util.Objects;
import java.util.Optional;

/**
 * The sealed family of inbound replies to a
 * {@link SmaxWaffleStateExistsRequest}.
 *
 * @apiNote
 * Mirrors WA Web's three documented {@code StateExists} reply shapes:
 * a {@link Success} carrying the {@code wf_state} code and the optional
 * {@code <suspended_state/>} marker, a {@link ClientError} for
 * malformed or unauthorised requests, and a {@link ServerError} for
 * transient relay failures. {@code WAWebAccountLinkingAPI.stateExists}
 * casts the success {@code wf_state} into the
 * {@code AccountLinkingStateExists} enum ({@code UNLINKED=1},
 * {@code ACTIVE=2}, {@code PAUSED=3}) and treats every other variant as
 * "state unknown" by returning {@code null}.
 */
public sealed interface SmaxWaffleStateExistsResponse extends SmaxOperation.Response
        permits SmaxWaffleStateExistsResponse.Success, SmaxWaffleStateExistsResponse.ClientError, SmaxWaffleStateExistsResponse.ServerError {

    /**
     * Tries each {@link SmaxWaffleStateExistsResponse} variant in
     * priority order and returns the first that parses cleanly.
     *
     * @apiNote
     * Mirrors WA Web's {@code sendStateExistsRPC} dispatch: the
     * incoming stanza is offered to the {@link Success} parser first,
     * then the {@link ClientError} parser, then the {@link ServerError}
     * parser; the WA Web equivalent of an empty {@link Optional} is the
     * {@code SmaxParsingFailure} thrown by
     * {@code WASmaxParsingFailure.SmaxParsingFailure}.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or empty
     *         when none of the three parsers matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxWaffleStateExistsRPC",
            exports = "sendStateExistsRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxWaffleStateExistsResponse> of(Node node, Node request) {
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
     * The {@code Success} reply variant: the relay reported the
     * current Waffle state code and, optionally, the suspended-state
     * marker.
     *
     * @apiNote
     * Consumed by {@code WAWebAccountLinkingAPI.stateExists}, which
     * casts {@link #wfState()} into the {@code AccountLinkingStateExists}
     * enum ({@code UNLINKED=1}, {@code ACTIVE=2}, {@code PAUSED=3}). The
     * optional {@code <suspended_state/>} child surfaces only when the
     * account is currently suspended, and carries an inner {@code npr}
     * attribute (no-personal-recovery flag) when the suspension blocks
     * personal-recovery flows.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInWaffleStateExistsResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInWaffleIQResultResponseMixin")
    final class Success implements SmaxWaffleStateExistsResponse {
        /**
         * The {@code <wf_state/>} integer.
         */
        private final int wfState;

        /**
         * The {@code <suspended_state npr="..."/>} {@code npr} flag, or
         * {@code null} when the attribute is absent.
         */
        private final Boolean suspendedNpr;

        /**
         * {@code true} when the relay surfaced an explicit
         * {@code <suspended_state/>} child.
         */
        private final boolean suspended;

        /**
         * Constructs a new success projection.
         *
         * @apiNote
         * Called by {@link #of(Node, Node)} after the envelope and
         * payload have been validated; embedders typically do not
         * instantiate this directly.
         *
         * @param wfState      the {@code <wf_state/>} integer
         * @param suspended    {@code true} when a {@code <suspended_state/>}
         *                     child was present
         * @param suspendedNpr the inner {@code npr} flag, or {@code null}
         *                     when absent
         */
        public Success(int wfState, boolean suspended, Boolean suspendedNpr) {
            this.wfState = wfState;
            this.suspended = suspended;
            this.suspendedNpr = suspendedNpr;
        }

        /**
         * Returns the Waffle state code.
         *
         * @apiNote
         * Embedders typically cast the result through the
         * {@code AccountLinkingStateExists} enum: {@code UNLINKED=1},
         * {@code ACTIVE=2}, {@code PAUSED=3}. Any other value is treated
         * by WA Web as an unrecognised state and triggers an
         * {@code [WAFFLE] Failed to parse state exists response} log
         * line.
         *
         * @return the state code as supplied by the relay
         */
        public int wfState() {
            return wfState;
        }

        /**
         * Reports whether the relay surfaced an explicit
         * {@code <suspended_state/>} child.
         *
         * @return {@code true} when the account is reported as
         *         suspended
         */
        public boolean suspended() {
            return suspended;
        }

        /**
         * Returns the {@code npr} (no-personal-recovery) flag when the
         * {@code <suspended_state/>} child carried one.
         *
         * @apiNote
         * The attribute is parsed as a {@code FALSE_TRUE} enum on the
         * WA Web side; {@link Optional#empty()} represents the wire
         * absence rather than {@code false}, so callers can distinguish
         * "flag not surfaced" from "flag explicitly false".
         *
         * @return an {@link Optional} carrying the flag, or empty when
         *         absent
         */
        public Optional<Boolean> suspendedNpr() {
            return Optional.ofNullable(suspendedNpr);
        }

        /**
         * Tries to parse a {@link Success} variant from the inbound
         * stanza.
         *
         * @apiNote
         * Returns {@link Optional#empty()} when the envelope check
         * fails (wrong {@code id}/{@code from} echo or wrong
         * {@code type}), when the {@code <wf_state/>} child is missing
         * or non-numeric, or when the optional {@code <suspended_state/>}
         * child is malformed.
         *
         * @implNote
         * This implementation parses {@code wf_state} content as an
         * ASCII integer via {@link Integer#parseInt(String)}; WA Web
         * uses its typed {@code contentInt} parser which would reject
         * leading whitespace strictly, while Cobalt's {@link String#trim()}
         * pass is more permissive. The {@code npr} flag is matched
         * case-insensitively against the literal {@code "true"} to
         * mirror WA Web's {@code ENUM_FALSE_TRUE} parsing.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty on no-match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInWaffleStateExistsResponseSuccess",
                exports = "parseStateExistsResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var wfStateNode = node.getChild("wf_state").orElse(null);
            if (wfStateNode == null) {
                return Optional.empty();
            }
            var wfState = wfStateNode.toContentString()
                    .map(String::trim)
                    .map(s -> {
                        try {
                            return Integer.parseInt(s);
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    })
                    .orElse(null);
            if (wfState == null) {
                return Optional.empty();
            }
            var suspendedNode = node.getChild("suspended_state").orElse(null);
            Boolean npr = null;
            if (suspendedNode != null) {
                var nprValue = suspendedNode.getAttributeAsString("npr").orElse(null);
                if (nprValue != null) {
                    npr = "true".equalsIgnoreCase(nprValue);
                }
            }
            return Optional.of(new Success(wfState, suspendedNode != null, npr));
        }

        /**
         * Returns whether the given object is a {@link Success} with
         * equal payload fields.
         *
         * @param obj the candidate; may be {@code null}
         * @return {@code true} when state, suspension flag, and
         *         {@code npr} flag all match
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
            return this.wfState == that.wfState
                    && this.suspended == that.suspended
                    && Objects.equals(this.suspendedNpr, that.suspendedNpr);
        }

        /**
         * Returns a hash code derived from the three payload fields.
         *
         * @return a content-based hash consistent with
         *         {@link #equals(Object)}
         */
        @Override
        public int hashCode() {
            return Objects.hash(wfState, suspended, suspendedNpr);
        }

        /**
         * Returns a debug rendering of this success variant.
         *
         * @return a human-readable summary; never {@code null}
         */
        @Override
        public String toString() {
            return "SmaxWaffleStateExistsResponse.Success[wfState=" + wfState
                    + ", suspended=" + suspended
                    + ", suspendedNpr=" + suspendedNpr + ']';
        }
    }

    /**
     * The {@code ClientError} reply variant: the relay rejected the
     * request with a code below {@code 500}.
     *
     * @apiNote
     * {@code WAWebAccountLinkingAPI.stateExists} forwards the error
     * name to {@code WAWebWaffleIQErrorHandler.handleCommonWaffleIQError}
     * and logs {@code [WAFFLE] StateExists RPC failed}. The error name
     * is the only signal embedders typically consume; the integer code
     * is preserved for telemetry and tooling.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInWaffleStateExistsResponseError")
    final class ClientError implements SmaxWaffleStateExistsResponse {
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
         * has been validated by
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)};
         * embedders typically do not instantiate this directly.
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
         * Tries to parse a {@link ClientError} variant from the inbound
         * stanza.
         *
         * @apiNote
         * Delegates the envelope and code-range check to
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)},
         * which only matches codes below {@code 500}; codes at or above
         * that threshold fall through to {@link ServerError}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty on no-match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInWaffleStateExistsResponseError",
                exports = "parseStateExistsResponseError",
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
            return "SmaxWaffleStateExistsResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant: the relay rejected the
     * request with a code of {@code 500} or above.
     *
     * @apiNote
     * Indicates a transient relay-side failure. WA Web treats this the
     * same as {@link ClientError} at the API boundary (both are routed
     * through {@code WAWebWaffleIQErrorHandler}); the split exists so
     * that telemetry can distinguish client-side from server-side
     * regressions.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInWaffleStateExistsResponseError")
    final class ServerError implements SmaxWaffleStateExistsResponse {
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
         * has been validated by
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)};
         * embedders typically do not instantiate this directly.
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
        @WhatsAppWebExport(moduleName = "WASmaxInWaffleStateExistsResponseError",
                exports = "parseStateExistsResponseError",
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
            return "SmaxWaffleStateExistsResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
