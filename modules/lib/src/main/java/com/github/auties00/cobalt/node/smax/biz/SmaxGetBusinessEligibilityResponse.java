package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The sealed family of inbound reply variants produced by the relay
 * in response to a {@link SmaxGetBusinessEligibilityRequest}.
 *
 * @apiNote
 * Each variant projects a distinct outcome of the SMB
 * marketing-messages and Meta-Verified gating bridge:
 * {@link Success} carries 0..3 typed eligibility projections,
 * {@link ClientError} carries a documented {@code 4xx} rejection
 * via the shared {@code IQError*MixinGroup}, and {@link ServerError}
 * carries a transient {@code 5xx} relay failure.
 *
 * @implNote
 * This implementation mirrors WA Web's
 * {@code WASmaxBizMarketingMessageGetBusinessEligibilityRPC.sendGetBusinessEligibilityRPC}
 * by trying each variant in priority order via {@link #of} and
 * returning the first successful parse.
 */
public sealed interface SmaxGetBusinessEligibilityResponse extends SmaxOperation.Response
        permits SmaxGetBusinessEligibilityResponse.Success, SmaxGetBusinessEligibilityResponse.ClientError, SmaxGetBusinessEligibilityResponse.ServerError {

    /**
     * Tries each {@link SmaxGetBusinessEligibilityResponse} variant
     * in priority order and returns the first that parses cleanly.
     *
     * @apiNote
     * Invoked by the smax reply pump after dispatching a
     * {@link SmaxGetBusinessEligibilityRequest}; the priority order
     * matches WA Web's {@code parsing} dispatch table so that a
     * malformed {@code Success} stanza falls through to
     * {@link ClientError} rather than masking an error.
     *
     * @implNote
     * This implementation invokes {@link Success#of(Node, Node)}
     * first, then {@link ClientError#of(Node, Node)}, then
     * {@link ServerError#of(Node, Node)}; an unrecognised stanza
     * shape returns {@link Optional#empty()}.
     *
     * @param node    the inbound IQ stanza received from the relay;
     *                never {@code null}
     * @param request the original outbound stanza, used to validate
     *                echoed identifiers; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or
     *         {@link Optional#empty()} when no documented variant
     *         matched the stanza shape
     * @throws NullPointerException if either argument is
     *                              {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxBizMarketingMessageGetBusinessEligibilityRPC",
            exports = "sendGetBusinessEligibilityRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxGetBusinessEligibilityResponse> of(Node node, Node request) {
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
     * The {@code Success} reply variant carrying 0..3 optional
     * feature-eligibility projections.
     *
     * @apiNote
     * Projected by {@link SmaxGetBusinessEligibilityResponse#of(Node, Node)}
     * when the relay returns the documented success envelope; each
     * sub-projection is present only when the corresponding
     * feature toggle was set on the outbound
     * {@link SmaxGetBusinessEligibilityRequest}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBizMarketingMessageGetBusinessEligibilityResponseSuccess")
    final class Success implements SmaxGetBusinessEligibilityResponse {
        /**
         * The optional {@code <meta_verified/>} projection.
         */
        private final MetaVerified metaVerified;

        /**
         * The optional {@code <marketing_messages/>} projection.
         */
        private final MarketingMessages marketingMessages;

        /**
         * The optional {@code <genai/>} projection.
         */
        private final Genai genai;

        /**
         * Constructs a new successful reply.
         *
         * @apiNote
         * Invoked by {@link #of(Node, Node)} after the relay's
         * three optional projection children have been parsed
         * individually.
         *
         * @param metaVerified      the optional Meta-Verified
         *                          projection; may be {@code null}
         * @param marketingMessages the optional marketing-messages
         *                          projection; may be {@code null}
         * @param genai             the optional GenAI projection;
         *                          may be {@code null}
         */
        public Success(MetaVerified metaVerified,
                       MarketingMessages marketingMessages,
                       Genai genai) {
            this.metaVerified = metaVerified;
            this.marketingMessages = marketingMessages;
            this.genai = genai;
        }

        /**
         * Returns the optional Meta-Verified projection.
         *
         * @return an {@link Optional} carrying the projection, or
         *         empty when the relay omitted the
         *         {@code <meta_verified/>} child
         */
        public Optional<MetaVerified> metaVerified() {
            return Optional.ofNullable(metaVerified);
        }

        /**
         * Returns the optional marketing-messages projection.
         *
         * @return an {@link Optional} carrying the projection, or
         *         empty when the relay omitted the
         *         {@code <marketing_messages/>} child
         */
        public Optional<MarketingMessages> marketingMessages() {
            return Optional.ofNullable(marketingMessages);
        }

        /**
         * Returns the optional GenAI projection.
         *
         * @return an {@link Optional} carrying the projection, or
         *         empty when the relay omitted the
         *         {@code <genai/>} child
         */
        public Optional<Genai> genai() {
            return Optional.ofNullable(genai);
        }

        /**
         * Tries to parse a {@link Success} variant from the given
         * inbound stanza.
         *
         * @implNote
         * This implementation enforces the
         * {@code SmaxIqResultResponseMixin} envelope check first,
         * then dispatches the three optional projection children
         * to {@link MetaVerified#of(Node)},
         * {@link MarketingMessages#of(Node)} and
         * {@link Genai#of(Node)}; if any of those sub-parses fails
         * the success parse fails as a whole (unlike the
         * {@code GetLinkedAccounts} mixins which silently swallow
         * sub-parse failures).
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant,
         *         or empty when the stanza does not match the
         *         success schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBizMarketingMessageGetBusinessEligibilityResponseSuccess",
                exports = "parseGetBusinessEligibilityResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            Objects.requireNonNull(node, "node cannot be null");
            Objects.requireNonNull(request, "request cannot be null");
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            MetaVerified metaVerified = null;
            var metaVerifiedNode = node.getChild("meta_verified").orElse(null);
            if (metaVerifiedNode != null) {
                var parsed = MetaVerified.of(metaVerifiedNode);
                if (parsed.isEmpty()) {
                    return Optional.empty();
                }
                metaVerified = parsed.get();
            }
            MarketingMessages marketingMessages = null;
            var marketingNode = node.getChild("marketing_messages").orElse(null);
            if (marketingNode != null) {
                var parsed = MarketingMessages.of(marketingNode);
                if (parsed.isEmpty()) {
                    return Optional.empty();
                }
                marketingMessages = parsed.get();
            }
            Genai genai = null;
            var genaiNode = node.getChild("genai").orElse(null);
            if (genaiNode != null) {
                var parsed = Genai.of(genaiNode);
                if (parsed.isEmpty()) {
                    return Optional.empty();
                }
                genai = parsed.get();
            }
            return Optional.of(new Success(metaVerified, marketingMessages, genai));
        }

        /**
         * {@inheritDoc}
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
            return Objects.equals(this.metaVerified, that.metaVerified)
                    && Objects.equals(this.marketingMessages, that.marketingMessages)
                    && Objects.equals(this.genai, that.genai);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(metaVerified, marketingMessages, genai);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "SmaxGetBusinessEligibilityResponse.Success[metaVerified=" + metaVerified
                    + ", marketingMessages=" + marketingMessages
                    + ", genai=" + genai + ']';
        }

        /**
         * The {@code <meta_verified/>} child projection carrying
         * the Meta-Verified eligibility status and optional
         * onboarding-flow toggles.
         *
         * @apiNote
         * Drives the WA Web Meta-Verified compose-banner and
         * privacy-interstitial onboarding flows; the
         * {@link #additionalParams()} string is an opaque relay
         * payload propagated to Meta's onboarding endpoint.
         */
        @WhatsAppWebModule(moduleName = "WASmaxInBizMarketingMessageGetBusinessEligibilityResponseSuccess")
        public static final class MetaVerified {
            /**
             * The mandatory {@code status} attribute on the
             * {@code <meta_verified/>} child.
             */
            private final SmaxGetBusinessEligibilityFailSuccessStatus status;

            /**
             * The optional
             * {@code should_show_privacy_interstitial_to_new_users}
             * attribute.
             */
            private final SmaxGetBusinessEligibilityFalseTrueFlag shouldShowPrivacyInterstitialToNewUsers;

            /**
             * The optional {@code additional_params} attribute; an
             * opaque relay-side payload that the WA Web
             * Meta-Verified onboarding flow forwards verbatim to
             * Meta's endpoint.
             */
            private final String additionalParams;

            /**
             * Constructs a new projection.
             *
             * @apiNote
             * Invoked by {@link #of(Node)} after the
             * {@code <meta_verified/>} child has been validated.
             *
             * @param status                                  the
             *                                                eligibility
             *                                                status;
             *                                                never
             *                                                {@code null}
             * @param shouldShowPrivacyInterstitialToNewUsers the
             *                                                optional
             *                                                privacy-interstitial
             *                                                toggle; may
             *                                                be
             *                                                {@code null}
             * @param additionalParams                        the
             *                                                optional
             *                                                opaque
             *                                                params; may
             *                                                be
             *                                                {@code null}
             * @throws NullPointerException if {@code status} is
             *                              {@code null}
             */
            public MetaVerified(SmaxGetBusinessEligibilityFailSuccessStatus status,
                                SmaxGetBusinessEligibilityFalseTrueFlag shouldShowPrivacyInterstitialToNewUsers,
                                String additionalParams) {
                this.status = Objects.requireNonNull(status, "status cannot be null");
                this.shouldShowPrivacyInterstitialToNewUsers = shouldShowPrivacyInterstitialToNewUsers;
                this.additionalParams = additionalParams;
            }

            /**
             * Returns the eligibility status.
             *
             * @return the status; never {@code null}
             */
            public SmaxGetBusinessEligibilityFailSuccessStatus status() {
                return status;
            }

            /**
             * Returns the optional privacy-interstitial toggle.
             *
             * @return an {@link Optional} carrying the toggle, or
             *         empty when the relay omitted the attribute
             */
            public Optional<SmaxGetBusinessEligibilityFalseTrueFlag> shouldShowPrivacyInterstitialToNewUsers() {
                return Optional.ofNullable(shouldShowPrivacyInterstitialToNewUsers);
            }

            /**
             * Returns the optional opaque onboarding params.
             *
             * @return an {@link Optional} carrying the params, or
             *         empty when the relay omitted the attribute
             */
            public Optional<String> additionalParams() {
                return Optional.ofNullable(additionalParams);
            }

            /**
             * Tries to parse the projection from the given node.
             *
             * @implNote
             * This implementation asserts the {@code meta_verified}
             * tag, projects the mandatory {@code status} attribute
             * through {@link SmaxGetBusinessEligibilityFailSuccessStatus#of(String)},
             * and only then optionally projects the
             * privacy-interstitial flag through
             * {@link SmaxGetBusinessEligibilityFalseTrueFlag#of(String)};
             * a present-but-malformed flag attribute is a
             * parse failure rather than a silent ignore.
             *
             * @param node the {@code <meta_verified/>} node
             * @return an {@link Optional} carrying the projection,
             *         or empty when the node does not match the
             *         documented schema
             */
            @WhatsAppWebExport(moduleName = "WASmaxInBizMarketingMessageGetBusinessEligibilityResponseSuccess",
                    exports = "parseGetBusinessEligibilityResponseSuccessMetaVerified",
                    adaptation = WhatsAppAdaptation.ADAPTED)
            public static Optional<MetaVerified> of(Node node) {
                Objects.requireNonNull(node, "node cannot be null");
                if (!node.hasDescription("meta_verified")) {
                    return Optional.empty();
                }
                var statusStr = node.getAttributeAsString("status").orElse(null);
                var status = SmaxGetBusinessEligibilityFailSuccessStatus.of(statusStr).orElse(null);
                if (status == null) {
                    return Optional.empty();
                }
                SmaxGetBusinessEligibilityFalseTrueFlag flag = null;
                var flagStr = node.getAttributeAsString("should_show_privacy_interstitial_to_new_users").orElse(null);
                if (flagStr != null) {
                    flag = SmaxGetBusinessEligibilityFalseTrueFlag.of(flagStr).orElse(null);
                    if (flag == null) {
                        return Optional.empty();
                    }
                }
                var additional = node.getAttributeAsString("additional_params").orElse(null);
                return Optional.of(new MetaVerified(status, flag, additional));
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (MetaVerified) obj;
                return this.status == that.status
                        && this.shouldShowPrivacyInterstitialToNewUsers
                                == that.shouldShowPrivacyInterstitialToNewUsers
                        && Objects.equals(this.additionalParams, that.additionalParams);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int hashCode() {
                return Objects.hash(status, shouldShowPrivacyInterstitialToNewUsers, additionalParams);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "SmaxGetBusinessEligibilityResponse.Success.MetaVerified[status=" + status
                        + ", shouldShowPrivacyInterstitialToNewUsers="
                        + shouldShowPrivacyInterstitialToNewUsers
                        + ", additionalParams=" + additionalParams + ']';
            }
        }

        /**
         * The {@code <marketing_messages/>} child projection
         * carrying the marketing-messages eligibility plus the
         * optional expiration timestamp.
         *
         * @apiNote
         * Drives the SMB marketing-messages broadcast-compose
         * gating; the {@link #expiration()} timestamp is the
         * epoch-seconds deadline at which the eligibility window
         * closes and the caller should re-issue
         * {@link SmaxGetBusinessEligibilityRequest}.
         */
        @WhatsAppWebModule(moduleName = "WASmaxInBizMarketingMessageGetBusinessEligibilityResponseSuccess")
        public static final class MarketingMessages {
            /**
             * The mandatory {@code status} attribute on the
             * {@code <marketing_messages/>} child.
             */
            private final SmaxGetBusinessEligibilityMarketingMessagesStatus status;

            /**
             * The optional {@code expiration} attribute (epoch
             * seconds).
             */
            private final Integer expiration;

            /**
             * Constructs a new projection.
             *
             * @apiNote
             * Invoked by {@link #of(Node)} after the
             * {@code <marketing_messages/>} child has been
             * validated.
             *
             * @param status     the marketing-messages status;
             *                   never {@code null}
             * @param expiration the optional expiration timestamp;
             *                   may be {@code null}
             * @throws NullPointerException if {@code status} is
             *                              {@code null}
             */
            public MarketingMessages(SmaxGetBusinessEligibilityMarketingMessagesStatus status, Integer expiration) {
                this.status = Objects.requireNonNull(status, "status cannot be null");
                this.expiration = expiration;
            }

            /**
             * Returns the marketing-messages status.
             *
             * @return the status; never {@code null}
             */
            public SmaxGetBusinessEligibilityMarketingMessagesStatus status() {
                return status;
            }

            /**
             * Returns the optional expiration timestamp.
             *
             * @apiNote
             * Empty when the relay omitted the {@code expiration}
             * attribute; the value is epoch seconds.
             *
             * @return an {@link OptionalInt} carrying the
             *         timestamp, or empty when the relay omitted
             *         the attribute
             */
            public OptionalInt expiration() {
                if (expiration == null) {
                    return OptionalInt.empty();
                }
                return OptionalInt.of(expiration);
            }

            /**
             * Tries to parse the projection from the given node.
             *
             * @implNote
             * This implementation asserts the
             * {@code marketing_messages} tag, projects the
             * mandatory {@code status} through
             * {@link SmaxGetBusinessEligibilityMarketingMessagesStatus#of(String)},
             * and then optionally parses the {@code expiration}
             * attribute as a non-negative {@code int}; a malformed
             * or negative value causes a parse failure.
             *
             * @param node the {@code <marketing_messages/>} node
             * @return an {@link Optional} carrying the projection,
             *         or empty when the node does not match the
             *         documented schema
             */
            @WhatsAppWebExport(moduleName = "WASmaxInBizMarketingMessageGetBusinessEligibilityResponseSuccess",
                    exports = "parseGetBusinessEligibilityResponseSuccessMarketingMessages",
                    adaptation = WhatsAppAdaptation.ADAPTED)
            public static Optional<MarketingMessages> of(Node node) {
                Objects.requireNonNull(node, "node cannot be null");
                if (!node.hasDescription("marketing_messages")) {
                    return Optional.empty();
                }
                var statusStr = node.getAttributeAsString("status").orElse(null);
                var status = SmaxGetBusinessEligibilityMarketingMessagesStatus.of(statusStr).orElse(null);
                if (status == null) {
                    return Optional.empty();
                }
                Integer expiration = null;
                var expirationStr = node.getAttributeAsString("expiration").orElse(null);
                if (expirationStr != null) {
                    try {
                        var parsed = Integer.parseInt(expirationStr);
                        if (parsed < 0) {
                            return Optional.empty();
                        }
                        expiration = parsed;
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                }
                return Optional.of(new MarketingMessages(status, expiration));
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (MarketingMessages) obj;
                return this.status == that.status && Objects.equals(this.expiration, that.expiration);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int hashCode() {
                return Objects.hash(status, expiration);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "SmaxGetBusinessEligibilityResponse.Success.MarketingMessages[status=" + status
                        + ", expiration=" + expiration + ']';
            }
        }

        /**
         * The {@code <genai/>} child projection carrying the GenAI
         * per-broadcast eligibility status.
         *
         * @apiNote
         * Drives the WA Web SMB GenAI-text broadcast-compose
         * gating consulted from
         * {@code WAWebBizBroadcastGenAIGating.isGenAITextEnabled}.
         */
        @WhatsAppWebModule(moduleName = "WASmaxInBizMarketingMessageGetBusinessEligibilityResponseSuccess")
        public static final class Genai {
            /**
             * The mandatory {@code status} attribute on the
             * {@code <genai/>} child.
             */
            private final SmaxGetBusinessEligibilityFailSuccessStatus status;

            /**
             * Constructs a new projection.
             *
             * @apiNote
             * Invoked by {@link #of(Node)} after the
             * {@code <genai/>} child has been validated.
             *
             * @param status the GenAI status; never {@code null}
             * @throws NullPointerException if {@code status} is
             *                              {@code null}
             */
            public Genai(SmaxGetBusinessEligibilityFailSuccessStatus status) {
                this.status = Objects.requireNonNull(status, "status cannot be null");
            }

            /**
             * Returns the GenAI status.
             *
             * @return the status; never {@code null}
             */
            public SmaxGetBusinessEligibilityFailSuccessStatus status() {
                return status;
            }

            /**
             * Tries to parse the projection from the given node.
             *
             * @implNote
             * This implementation asserts the {@code genai} tag
             * and projects the mandatory {@code status} through
             * {@link SmaxGetBusinessEligibilityFailSuccessStatus#of(String)};
             * a missing or malformed status is a parse failure.
             *
             * @param node the {@code <genai/>} node
             * @return an {@link Optional} carrying the projection,
             *         or empty when the node does not match the
             *         documented schema
             */
            @WhatsAppWebExport(moduleName = "WASmaxInBizMarketingMessageGetBusinessEligibilityResponseSuccess",
                    exports = "parseGetBusinessEligibilityResponseSuccessGenai",
                    adaptation = WhatsAppAdaptation.ADAPTED)
            public static Optional<Genai> of(Node node) {
                Objects.requireNonNull(node, "node cannot be null");
                if (!node.hasDescription("genai")) {
                    return Optional.empty();
                }
                var statusStr = node.getAttributeAsString("status").orElse(null);
                var status = SmaxGetBusinessEligibilityFailSuccessStatus.of(statusStr).orElse(null);
                if (status == null) {
                    return Optional.empty();
                }
                return Optional.of(new Genai(status));
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (Genai) obj;
                return this.status == that.status;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int hashCode() {
                return Objects.hash(status);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "SmaxGetBusinessEligibilityResponse.Success.Genai[status=" + status + ']';
            }
        }
    }

    /**
     * The {@code ClientError} reply variant carrying a documented
     * {@code 4xx} rejection.
     *
     * @apiNote
     * Surfaced when the relay rejected the
     * {@code GetBusinessEligibility} request via one of the
     * BadRequest / Forbidden / NotAllowed mixin arms; the WA Web
     * {@code WAWebRefreshBusinessEligibility} loop typically halts
     * the backoff on a {@code 4xx}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBizMarketingMessageGetBusinessEligibilityResponseError")
    @WhatsAppWebModule(moduleName = "WASmaxInBizMarketingMessageIQErrorBadRequestOrForbiddenOrInternalServerErrorOrServiceUnavailableMixinGroup")
    final class ClientError implements SmaxGetBusinessEligibilityResponse {
        /**
         * The numeric server-side error code in the {@code 4xx}
         * range.
         */
        private final int errorCode;

        /**
         * The human-readable error text, when the relay supplied
         * one.
         */
        private final String errorText;

        /**
         * Constructs a new client-error reply.
         *
         * @apiNote
         * Invoked by {@link #of(Node, Node)} after the
         * {@code 4xx} envelope has been validated.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may
         *                  be {@code null}
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
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the error text, or
         *         empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant from the
         * given inbound stanza.
         *
         * @implNote
         * This implementation routes the {@code <iq>}/{@code <error>}
         * extraction through
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)};
         * unlike
         * {@link SmaxGetAccountNonceResponse.ClientError} this
         * variant does not enforce a literal-pair disjunction
         * because the WA Web
         * {@code IQErrorBadRequestOrForbiddenOrInternalServerErrorOrServiceUnavailableMixinGroup}
         * admits the entire {@code 4xx} range as a catch-all.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant,
         *         or empty when the stanza does not match the
         *         client-error schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBizMarketingMessageGetBusinessEligibilityResponseError",
                exports = "parseGetBusinessEligibilityResponseError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBizMarketingMessageIQErrorBadRequestOrForbiddenOrInternalServerErrorOrServiceUnavailableMixinGroup",
                exports = "parseIQErrorBadRequestOrForbiddenOrInternalServerErrorOrServiceUnavailableMixinGroup",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ClientError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseClientError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ClientError(envelope.code(), envelope.text()));
        }

        /**
         * {@inheritDoc}
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
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "SmaxGetBusinessEligibilityResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant carrying a transient
     * {@code 5xx} relay failure.
     *
     * @apiNote
     * Surfaced when the relay returned a transient internal
     * failure; the WA Web
     * {@code WAWebRefreshBusinessEligibility} loop re-invokes the
     * request with exponential backoff for this case.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBizMarketingMessageGetBusinessEligibilityResponseError")
    @WhatsAppWebModule(moduleName = "WASmaxInBizMarketingMessageIQErrorBadRequestOrForbiddenOrInternalServerErrorOrServiceUnavailableMixinGroup")
    final class ServerError implements SmaxGetBusinessEligibilityResponse {
        /**
         * The numeric server-side error code in the {@code 5xx}
         * range.
         */
        private final int errorCode;

        /**
         * The human-readable error text, when the relay supplied
         * one.
         */
        private final String errorText;

        /**
         * Constructs a new server-error reply.
         *
         * @apiNote
         * Invoked by {@link #of(Node, Node)} after the
         * {@code 5xx} envelope has been validated.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may
         *                  be {@code null}
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
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the error text, or
         *         empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant from the
         * given inbound stanza.
         *
         * @implNote
         * This implementation delegates the {@code 5xx} range check
         * to
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)};
         * a stanza outside the {@code 5xx} range yields
         * {@link Optional#empty()}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant,
         *         or empty when the stanza does not match the
         *         server-error schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBizMarketingMessageGetBusinessEligibilityResponseError",
                exports = "parseGetBusinessEligibilityResponseError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBizMarketingMessageIQErrorBadRequestOrForbiddenOrInternalServerErrorOrServiceUnavailableMixinGroup",
                exports = "parseIQErrorBadRequestOrForbiddenOrInternalServerErrorOrServiceUnavailableMixinGroup",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ServerError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseServerError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ServerError(envelope.code(), envelope.text()));
        }

        /**
         * {@inheritDoc}
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
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "SmaxGetBusinessEligibilityResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
