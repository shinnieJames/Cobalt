package com.github.auties00.cobalt.node.smax.account;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The reply produced by the relay for a
 * {@link SmaxBrPaymentCreateCustomPaymentMethodRequest}; either a
 * {@link Success} carrying the persisted method or an {@link IqError}
 * with the rejection code-text pair.
 *
 * @apiNote
 * Returned by the smax send pipeline that
 * {@code WASmaxBrPaymentCreateCustomPaymentMethodRPC.sendCreateCustomPaymentMethodRPC}
 * drives. The {@link Success} arm hands the caller the relay-assigned
 * credential id so subsequent payment flows can reference the newly
 * registered method.
 */
public sealed interface SmaxBrPaymentCreateCustomPaymentMethodResponse extends SmaxOperation.Response
        permits SmaxBrPaymentCreateCustomPaymentMethodResponse.Success, SmaxBrPaymentCreateCustomPaymentMethodResponse.IqError {

    /**
     * Resolves an inbound IQ reply into the first matching response
     * variant.
     *
     * @apiNote
     * Called by the smax send pipeline after dispatching a
     * {@link SmaxBrPaymentCreateCustomPaymentMethodRequest};
     * {@link Success} is tried first and falls through to
     * {@link IqError} on schema mismatch.
     *
     * @implNote
     * This implementation mirrors the WA Web
     * {@code sendCreateCustomPaymentMethodRPC} disjunction.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the originating outbound IQ stanza; never
     *                {@code null}
     * @return an {@link Optional} carrying the parsed variant, or
     *         {@link Optional#empty()} when no documented variant
     *         matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxBrPaymentCreateCustomPaymentMethodRPC",
            exports = "sendCreateCustomPaymentMethodRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxBrPaymentCreateCustomPaymentMethodResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var success = Success.of(node, request);
        if (success.isPresent()) {
            return success;
        }
        return IqError.of(node, request);
    }

    /**
     * Validates the {@code <iq type="result">} envelope on an inbound
     * reply by cross-checking description, type, id and from against
     * the originating request.
     *
     * @apiNote
     * Used internally by {@link Success#of(Node, Node)} to gate
     * further parsing; a failed envelope short-circuits before any
     * payload inspection.
     *
     * @implNote
     * This implementation allows the {@code from} echo check to
     * succeed when the request lacked a {@code to} attribute, which
     * is the WA Web parser's behaviour for IQ result mixins.
     *
     * @param reply   the inbound IQ stanza
     * @param request the originating outbound IQ stanza
     * @return {@code true} when the envelope passes every echo check
     */
    @WhatsAppWebExport(moduleName = "WASmaxInBrPaymentIQResultResponseMixin",
            exports = "parseIQResultResponseMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    private static boolean validateIqResultEnvelope(Node reply, Node request) {
        if (!reply.hasDescription("iq")) {
            return false;
        }
        if (!reply.hasAttribute("type", "result")) {
            return false;
        }
        var requestId = request.getAttributeAsString("id").orElse(null);
        if (requestId == null) {
            return false;
        }
        if (!reply.hasAttribute("id", requestId)) {
            return false;
        }
        var requestTo = request.getAttributeAsString("to").orElse(null);
        return requestTo == null || reply.hasAttribute("from", requestTo);
    }

    /**
     * The positive reply variant carrying the relay-persisted method
     * projection.
     *
     * @apiNote
     * Surfaced to callers when the relay registered the custom
     * payment method; hands back the assigned credential id plus the
     * echoed type, flow, eligibility flags, and any persisted
     * metadata for subsequent payment flows.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBrPaymentCreateCustomPaymentMethodResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInBrPaymentCustomPaymentMethodMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInBrPaymentMethodBaseMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInBrPaymentCustomPaymentMethodMetaDataInfoMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInBrPaymentCustomPaymentMethodMetaDataMixin")
    final class Success implements SmaxBrPaymentCreateCustomPaymentMethodResponse {
        /**
         * The echoed method-type literal; one of
         * {@code "pay_on_delivery"} or {@code "pix_key"}.
         *
         * @apiNote
         * Mirrors the value the caller originally submitted via
         * {@link SmaxBrPaymentCreateCustomPaymentMethodRequest#customPaymentMethodType()}.
         */
        private final String customPaymentMethodType;

        /**
         * The optional echoed {@code country} attribute; always
         * {@code "BR"} when present.
         *
         * @apiNote
         * The Brazilian-payments wire schema only emits this when the
         * relay's serialiser feels like echoing it; absence is normal.
         */
        private final String country;

        /**
         * The optional creation-timestamp string echoed by the relay.
         *
         * @apiNote
         * Format mirrors the relay's
         * {@code custom_payment_method created="..."} attribute (a
         * raw string; not normalised).
         */
        private final String created;

        /**
         * The optional flow enum literal; one of {@code "p2p"} or
         * {@code "p2m"}.
         *
         * @apiNote
         * Validated against
         * {@code WASmaxInBrPaymentEnums.ENUM_P2M_P2P} during parsing.
         */
        private final String flow;

        /**
         * The credential-id assigned by the relay.
         *
         * @apiNote
         * Use this as the stable key when referencing this method in
         * subsequent payment flows.
         */
        private final String credentialId;

        /**
         * The optional {@code p2p-eligible} flag; one of {@code "0"}
         * or {@code "1"}.
         */
        private final String p2pEligible;

        /**
         * The optional {@code p2m-eligible} flag; one of {@code "0"}
         * or {@code "1"}.
         */
        private final String p2mEligible;

        /**
         * The echoed metadata key-value pairs, preserving the relay's
         * emit order.
         *
         * @apiNote
         * Empty when the relay omitted the {@code <metadata_info>}
         * child or when the metadata sub-mixin failed to parse; the
         * outer mixin's WA Web behaviour swallows nested-parse
         * failures.
         */
        private final Map<String, String> metadata;

        /**
         * Constructs a successful reply carrying the echoed method
         * projection.
         *
         * @apiNote
         * Called by {@link #of(Node, Node)} after a successful parse;
         * not intended for direct caller use.
         *
         * @implNote
         * This implementation defensively copies the metadata map
         * into a {@link LinkedHashMap} wrapped via
         * {@link Collections#unmodifiableMap(Map)};
         * {@code null} input becomes the empty map so the accessor
         * never returns {@code null}.
         *
         * @param customPaymentMethodType the method-type literal;
         *                                never {@code null}
         * @param country                 the optional country; may
         *                                be {@code null}
         * @param created                 the optional creation
         *                                timestamp; may be
         *                                {@code null}
         * @param flow                    the optional flow literal;
         *                                may be {@code null}
         * @param credentialId            the credential-id; never
         *                                {@code null}
         * @param p2pEligible             the optional p2p flag; may
         *                                be {@code null}
         * @param p2mEligible             the optional p2m flag; may
         *                                be {@code null}
         * @param metadata                the optional metadata map;
         *                                {@code null} treated as
         *                                empty
         * @throws NullPointerException if {@code customPaymentMethodType}
         *                              or {@code credentialId} is
         *                              {@code null}
         */
        public Success(String customPaymentMethodType,
                       String country,
                       String created,
                       String flow,
                       String credentialId,
                       String p2pEligible,
                       String p2mEligible,
                       Map<String, String> metadata) {
            this.customPaymentMethodType = Objects.requireNonNull(customPaymentMethodType, "customPaymentMethodType cannot be null");
            this.country = country;
            this.created = created;
            this.flow = flow;
            this.credentialId = Objects.requireNonNull(credentialId, "credentialId cannot be null");
            this.p2pEligible = p2pEligible;
            this.p2mEligible = p2mEligible;
            this.metadata = metadata == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        }

        /**
         * Returns the echoed method-type literal.
         *
         * @apiNote
         * Lets callers branch UI on {@code "pay_on_delivery"} vs
         * {@code "pix_key"}.
         *
         * @return the literal; never {@code null}
         */
        public String customPaymentMethodType() {
            return customPaymentMethodType;
        }

        /**
         * Returns the optional echoed country.
         *
         * @apiNote
         * Always {@code "BR"} when present.
         *
         * @return an {@link Optional} carrying the country, or
         *         {@link Optional#empty()} when omitted
         */
        public Optional<String> country() {
            return Optional.ofNullable(country);
        }

        /**
         * Returns the optional relay-supplied creation timestamp.
         *
         * @apiNote
         * Raw string from the relay; not normalised.
         *
         * @return an {@link Optional} carrying the timestamp, or
         *         {@link Optional#empty()} when omitted
         */
        public Optional<String> created() {
            return Optional.ofNullable(created);
        }

        /**
         * Returns the optional flow literal.
         *
         * @apiNote
         * Lets callers tell apart {@code "p2p"} (person-to-person)
         * and {@code "p2m"} (person-to-merchant) registrations.
         *
         * @return an {@link Optional} carrying the literal, or
         *         {@link Optional#empty()} when omitted
         */
        public Optional<String> flow() {
            return Optional.ofNullable(flow);
        }

        /**
         * Returns the relay-assigned credential-id.
         *
         * @apiNote
         * Use this as the stable key for subsequent payment flows
         * referencing this method.
         *
         * @return the id; never {@code null}
         */
        public String credentialId() {
            return credentialId;
        }

        /**
         * Returns the optional p2p-eligibility flag.
         *
         * @apiNote
         * Value is the wire literal {@code "0"} or {@code "1"}.
         *
         * @return an {@link Optional} carrying the flag, or
         *         {@link Optional#empty()} when omitted
         */
        public Optional<String> p2pEligible() {
            return Optional.ofNullable(p2pEligible);
        }

        /**
         * Returns the optional p2m-eligibility flag.
         *
         * @apiNote
         * Value is the wire literal {@code "0"} or {@code "1"}.
         *
         * @return an {@link Optional} carrying the flag, or
         *         {@link Optional#empty()} when omitted
         */
        public Optional<String> p2mEligible() {
            return Optional.ofNullable(p2mEligible);
        }

        /**
         * Returns the echoed metadata key-value pairs.
         *
         * @apiNote
         * Empty when the relay omitted {@code <metadata_info>} or
         * when the sub-mixin failed to parse.
         *
         * @return an unmodifiable map; never {@code null}
         */
        public Map<String, String> metadata() {
            return metadata;
        }

        /**
         * Parses a {@code Success} reply from the given inbound
         * stanza cross-checked against the originating request.
         *
         * @apiNote
         * Returns {@link Optional#empty()} for any deviation from
         * the documented success schema (missing
         * {@code <custom_payment_method>}, unknown type literal,
         * mismatched action attribute, missing credential-id, unknown
         * flow or eligibility literal).
         *
         * @implNote
         * This implementation diverges from WA Web's
         * {@code optionalLiteral(country, "BR")} step: WA Web parses
         * country both through the outer mixin (literal-pin to
         * {@code "BR"}) and through {@code parseMethodBaseMixin} as a
         * free string; here a single check accepts either {@code "BR"}
         * or absence. The metadata sub-mixin failure swallow is
         * adapted from WA Web: any failure inside
         * {@code <metadata_info>} collapses to the empty map without
         * failing the parent parse, matching the WA Web
         * {@code spread} on {@code s.success ? s.value : null}.
         *
         * @param node    the inbound IQ stanza
         * @param request the originating outbound IQ stanza
         * @return an {@link Optional} carrying the parsed variant, or
         *         {@link Optional#empty()} when the stanza does not
         *         match the success schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBrPaymentCreateCustomPaymentMethodResponseSuccess",
                exports = "parseCreateCustomPaymentMethodResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBrPaymentCustomPaymentMethodMixin",
                exports = "parseCustomPaymentMethodMixin",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBrPaymentMethodBaseMixin",
                exports = "parseMethodBaseMixin",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBrPaymentCustomPaymentMethodMetaDataInfoMixin",
                exports = "parseCustomPaymentMethodMetaDataInfoMixin",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBrPaymentCustomPaymentMethodMetaDataMixin",
                exports = "parseCustomPaymentMethodMetaDataMixin",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBrPaymentCustomPaymentMethodMetaDataMixin",
                exports = "parseCustomPaymentMethodMetaDataMetadata",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBrPaymentEnums",
                exports = "ENUM_PAYONDELIVERY_PIXKEY",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBrPaymentEnums",
                exports = "ENUM_P2M_P2P",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBrPaymentEnums",
                exports = "ENUM_0_1",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!validateIqResultEnvelope(node, request)) {
                return Optional.empty();
            }
            var account = node.getChild("account").orElse(null);
            if (account == null) {
                return Optional.empty();
            }
            var requestAccount = request.getChild("account").orElse(null);
            if (requestAccount == null) {
                return Optional.empty();
            }
            var requestAction = requestAccount.getAttributeAsString("action").orElse(null);
            if (requestAction == null || !account.hasAttribute("action", requestAction)) {
                return Optional.empty();
            }
            var customPaymentMethod = account.getChild("custom_payment_method").orElse(null);
            if (customPaymentMethod == null) {
                return Optional.empty();
            }
            var type = customPaymentMethod.getAttributeAsString("type").orElse(null);
            if (type == null || (!type.equals("pay_on_delivery") && !type.equals("pix_key"))) {
                return Optional.empty();
            }
            var country = customPaymentMethod.getAttributeAsString("country").orElse(null);
            if (country != null && !"BR".equals(country)) {
                return Optional.empty();
            }
            var created = customPaymentMethod.getAttributeAsString("created").orElse(null);
            var flow = customPaymentMethod.getAttributeAsString("flow").orElse(null);
            if (flow != null && !flow.equals("p2m") && !flow.equals("p2p")) {
                return Optional.empty();
            }
            var credentialId = customPaymentMethod.getAttributeAsString("credential-id").orElse(null);
            if (credentialId == null) {
                return Optional.empty();
            }
            var p2pEligible = customPaymentMethod.getAttributeAsString("p2p-eligible").orElse(null);
            if (p2pEligible != null && !p2pEligible.equals("0") && !p2pEligible.equals("1")) {
                return Optional.empty();
            }
            var p2mEligible = customPaymentMethod.getAttributeAsString("p2m-eligible").orElse(null);
            if (p2mEligible != null && !p2mEligible.equals("0") && !p2mEligible.equals("1")) {
                return Optional.empty();
            }
            var metadataMap = new LinkedHashMap<String, String>();
            var metadataInfo = customPaymentMethod.getChild("metadata_info").orElse(null);
            if (metadataInfo != null) {
                var metadataNodes = metadataInfo.getChildren("metadata");
                if (metadataNodes.size() >= 1 && metadataNodes.size() <= 5) {
                    var partial = new LinkedHashMap<String, String>();
                    var ok = true;
                    for (var entry : metadataNodes) {
                        var key = entry.getAttributeAsString("key").orElse(null);
                        var value = entry.getAttributeAsString("value").orElse(null);
                        if (key == null || value == null) {
                            ok = false;
                            break;
                        }
                        partial.put(key, value);
                    }
                    if (ok) {
                        metadataMap.putAll(partial);
                    }
                }
            }
            return Optional.of(new Success(type, country, created, flow, credentialId,
                    p2pEligible, p2mEligible, metadataMap));
        }

        /**
         * Compares this success reply to another for value equality
         * on every echoed field.
         *
         * @param obj the object to compare against
         * @return {@code true} when {@code obj} is a {@link Success}
         *         with identical fields
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
            return Objects.equals(this.customPaymentMethodType, that.customPaymentMethodType)
                    && Objects.equals(this.country, that.country)
                    && Objects.equals(this.created, that.created)
                    && Objects.equals(this.flow, that.flow)
                    && Objects.equals(this.credentialId, that.credentialId)
                    && Objects.equals(this.p2pEligible, that.p2pEligible)
                    && Objects.equals(this.p2mEligible, that.p2mEligible)
                    && Objects.equals(this.metadata, that.metadata);
        }

        /**
         * Returns a hash code consistent with {@link #equals(Object)}.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(customPaymentMethodType, country, created, flow, credentialId,
                    p2pEligible, p2mEligible, metadata);
        }

        /**
         * Returns a debug-friendly representation of this success
         * reply.
         *
         * @apiNote
         * Intended for logging; the format is not part of the public
         * contract.
         *
         * @return the string form
         */
        @Override
        public String toString() {
            return "SmaxBrPaymentCreateCustomPaymentMethodResponse.Success[customPaymentMethodType="
                    + customPaymentMethodType
                    + ", country=" + country
                    + ", created=" + created
                    + ", flow=" + flow
                    + ", credentialId=" + credentialId
                    + ", p2pEligible=" + p2pEligible
                    + ", p2mEligible=" + p2mEligible
                    + ", metadata=" + metadata + ']';
        }
    }

    /**
     * The negative reply variant carrying the generic IQ-error
     * code-text pair.
     *
     * @apiNote
     * Surfaced to callers when the relay rejected the method-create
     * with an {@code <iq type="error">} envelope; the code-text pair
     * disambiguates the rejection reason.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBrPaymentCreateCustomPaymentMethodResponseIQErrorWithCodeAndReason")
    @WhatsAppWebModule(moduleName = "WASmaxInBrPaymentIQErrorGenericResponseMixin")
    final class IqError implements SmaxBrPaymentCreateCustomPaymentMethodResponse {
        /**
         * The numeric error code; always {@code >= 1}.
         *
         * @apiNote
         * Mirrors the WA Web {@code attrIntRange(code, 1, undefined)}
         * range check.
         */
        private final int errorCode;

        /**
         * The human-readable error text echoed by the relay.
         */
        private final String errorText;

        /**
         * Constructs an error reply.
         *
         * @apiNote
         * Called by {@link #of(Node, Node)} after a successful parse;
         * not intended for direct caller use.
         *
         * @param errorCode the numeric error code
         * @param errorText the error text; never {@code null}
         * @throws NullPointerException if {@code errorText} is
         *                              {@code null}
         */
        public IqError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = Objects.requireNonNull(errorText, "errorText cannot be null");
        }

        /**
         * Returns the numeric error code.
         *
         * @apiNote
         * Mirrors the relay's HTTP-style code, always {@code >= 1}.
         *
         * @return the code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the human-readable error text.
         *
         * @apiNote
         * Wrapped in {@link Optional} for parity with sibling Error
         * variants in the smax module; the text is in practice
         * always present.
         *
         * @return an {@link Optional} carrying the text; never empty
         */
        public Optional<String> errorText() {
            return Optional.of(errorText);
        }

        /**
         * Parses an {@code IqError} reply from the given inbound
         * stanza.
         *
         * @apiNote
         * Returns {@link Optional#empty()} for any deviation from the
         * generic-IQ-error schema (missing {@code <error/>} child,
         * missing or out-of-range {@code code}, missing
         * {@code text}, mismatched id/from echoes).
         *
         * @implNote
         * This implementation reads the first {@code <error/>} child
         * via {@code getChild}; WA Web's
         * {@code flattenedChildWithTag} fails when more than one
         * matches, but the relay only ever emits a single error in
         * practice so the observable behaviour is identical for
         * documented payloads.
         *
         * @param node    the inbound IQ stanza
         * @param request the originating outbound IQ stanza
         * @return an {@link Optional} carrying the parsed variant, or
         *         {@link Optional#empty()} when the stanza does not
         *         match the generic IQ-error schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBrPaymentCreateCustomPaymentMethodResponseIQErrorWithCodeAndReason",
                exports = "parseCreateCustomPaymentMethodResponseIQErrorWithCodeAndReason",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBrPaymentIQErrorGenericResponseMixin",
                exports = "parseIQErrorGenericResponseMixin",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<IqError> of(Node node, Node request) {
            if (!node.hasDescription("iq")) {
                return Optional.empty();
            }
            if (!node.hasAttribute("type", "error")) {
                return Optional.empty();
            }
            var requestId = request.getAttributeAsString("id").orElse(null);
            if (requestId == null) {
                return Optional.empty();
            }
            if (!node.hasAttribute("id", requestId)) {
                return Optional.empty();
            }
            var requestTo = request.getAttributeAsString("to").orElse(null);
            if (requestTo == null || !node.hasAttribute("from", requestTo)) {
                return Optional.empty();
            }
            var errorChild = node.getChild("error").orElse(null);
            if (errorChild == null) {
                return Optional.empty();
            }
            var text = errorChild.getAttributeAsString("text").orElse(null);
            if (text == null) {
                return Optional.empty();
            }
            var codeOpt = errorChild.getAttributeAsInt("code");
            if (codeOpt.isEmpty()) {
                return Optional.empty();
            }
            var code = codeOpt.getAsInt();
            if (code < 1) {
                return Optional.empty();
            }
            return Optional.of(new IqError(code, text));
        }

        /**
         * Compares this error reply to another for value equality on
         * the code-text pair.
         *
         * @param obj the object to compare against
         * @return {@code true} when {@code obj} is an {@link IqError}
         *         with identical code and text
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (IqError) obj;
            return this.errorCode == that.errorCode
                    && Objects.equals(this.errorText, that.errorText);
        }

        /**
         * Returns a hash code consistent with {@link #equals(Object)}.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * Returns a debug-friendly representation of this error reply.
         *
         * @apiNote
         * Intended for logging; the format is not part of the public
         * contract.
         *
         * @return the string form
         */
        @Override
        public String toString() {
            return "SmaxBrPaymentCreateCustomPaymentMethodResponse.IqError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
