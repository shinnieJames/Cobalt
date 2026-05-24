package com.github.auties00.cobalt.node.iq.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.iq.IqOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The typed sealed family of inbound reply variants produced by the relay in response to an {@link IqGetMerchantComplianceRequest}.
 *
 * @apiNote
 * Use this type to switch over the three documented outcomes of a merchant-compliance fetch: {@link Success} carries the decoded compliance bundles per merchant, {@link ClientError} surfaces a relay validation rejection, and {@link ServerError} reports a transport or backend failure. The dispatcher invokes {@link #of(Node, Node)} to project the raw {@link Node} into the right variant before handing it to the caller.
 */
@WhatsAppWebModule(moduleName = "WAWebMerchantComplianceJob")
public sealed interface IqGetMerchantComplianceResponse extends IqOperation.Response
        permits IqGetMerchantComplianceResponse.Success, IqGetMerchantComplianceResponse.ClientError, IqGetMerchantComplianceResponse.ServerError {

    /**
     * Tries each {@link IqGetMerchantComplianceResponse} variant in priority order.
     *
     * @apiNote
     * Call this entry from the dispatcher to fan the inbound stanza into the matching sealed variant; the success path is tried first, then the client-error envelope, then the server-error envelope. Returns empty only when none of the three documented shapes apply.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant
     * @throws NullPointerException if either argument is {@code null}
     */
    static Optional<? extends IqGetMerchantComplianceResponse> of(Node node, Node request) {
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
     * The {@code Success} reply variant carrying the typed merchant-compliance bundles.
     *
     * @apiNote
     * Use this variant to read the per-merchant compliance bundles; the India e-commerce compliance surfaces consume the {@link MerchantInfo} entries to render the entity-name row, the entity-type row, the registered flag, the customer-care contact triple and the grievance-officer block.
     */
    final class Success implements IqGetMerchantComplianceResponse {
        /**
         * One typed merchant-info entry decoded from a {@code <merchant_info/>} child of the inbound IQ.
         *
         * @apiNote
         * Use this class to model one merchant's compliance bundle; the entity-name and entity-type fields drive the headline rows, the registered flag drives the registered-merchant badge, and the customer-care and grievance-officer blocks drive the contact tables.
         */
        public static final class MerchantInfo {
            /**
             * The legal entity name lifted from {@code <merchant_info><entity_name/></merchant_info>}; empty when the relay returned no entity name.
             */
            private final String entityName;

            /**
             * The entity-type identifier (for example {@code "LIMITED_LIABILITY_PARTNERSHIP"}, {@code "PRIVATE_COMPANY"}); empty when the relay returned no entity type.
             */
            private final String entityType;

            /**
             * The registered-merchant flag lifted from the {@code is_registered} attribute.
             */
            private final boolean registered;

            /**
             * The optional custom entity-type free-text label rendered when {@code entityType} is {@code "OTHER"}.
             */
            private final String entityTypeCustom;

            /**
             * The customer-care contact triple decoded from {@code <merchant_info><customer_care_details/></merchant_info>}.
             */
            private final BusinessContactDetails customerCareDetails;

            /**
             * The grievance-officer block decoded from {@code <merchant_info><grievance_officer_details/></merchant_info>}.
             */
            private final BusinessGrievanceOfficerDetails grievanceOfficerDetails;

            /**
             * Constructs a typed merchant-info entry.
             *
             * @apiNote
             * Call this constructor when projecting a {@code <merchant_info/>} child into the typed model; pass {@code null} for {@code entityTypeCustom} when the wire shape omitted the custom-type label.
             *
             * @param entityName              the legal entity name; never {@code null}
             * @param entityType              the entity-type identifier; never {@code null}
             * @param registered              the registered flag
             * @param entityTypeCustom        the custom-type label; may be {@code null}
             * @param customerCareDetails     the customer-care contact triple; never {@code null}
             * @param grievanceOfficerDetails the grievance-officer block; never {@code null}
             * @throws NullPointerException if any non-optional argument is {@code null}
             */
            public MerchantInfo(String entityName,
                                String entityType,
                                boolean registered,
                                String entityTypeCustom,
                                BusinessContactDetails customerCareDetails,
                                BusinessGrievanceOfficerDetails grievanceOfficerDetails) {
                this.entityName = Objects.requireNonNull(entityName, "entityName cannot be null");
                this.entityType = Objects.requireNonNull(entityType, "entityType cannot be null");
                this.registered = registered;
                this.entityTypeCustom = entityTypeCustom;
                this.customerCareDetails = Objects.requireNonNull(
                        customerCareDetails, "customerCareDetails cannot be null");
                this.grievanceOfficerDetails = Objects.requireNonNull(
                        grievanceOfficerDetails, "grievanceOfficerDetails cannot be null");
            }

            /**
             * Returns the legal entity name.
             *
             * @apiNote
             * Use this getter to render the headline entity-name row of the compliance surface.
             *
             * @return the name; never {@code null}
             */
            public String entityName() {
                return entityName;
            }

            /**
             * Returns the entity-type identifier.
             *
             * @apiNote
             * Use this getter to read back the entity-type identifier; consumers map the value through a localised lookup to render the entity-type row.
             *
             * @return the identifier; never {@code null}
             */
            public String entityType() {
                return entityType;
            }

            /**
             * Returns the registered-merchant flag.
             *
             * @apiNote
             * Use this getter to drive the registered-merchant badge in the compliance surface.
             *
             * @return {@code true} when the merchant is registered
             */
            public boolean registered() {
                return registered;
            }

            /**
             * Returns the custom entity-type label.
             *
             * @apiNote
             * Use this getter to render the merchant-supplied custom entity-type label when {@link #entityType()} is the catch-all {@code "OTHER"} bucket.
             *
             * @return an {@link Optional} carrying the label
             */
            public Optional<String> entityTypeCustom() {
                return Optional.ofNullable(entityTypeCustom);
            }

            /**
             * Returns the customer-care contact triple.
             *
             * @apiNote
             * Use this getter to render the customer-care row of the compliance surface.
             *
             * @return the contact triple; never {@code null}
             */
            public BusinessContactDetails customerCareDetails() {
                return customerCareDetails;
            }

            /**
             * Returns the grievance-officer block.
             *
             * @apiNote
             * Use this getter to render the grievance-officer row of the compliance surface.
             *
             * @return the block; never {@code null}
             */
            public BusinessGrievanceOfficerDetails grievanceOfficerDetails() {
                return grievanceOfficerDetails;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (MerchantInfo) obj;
                return this.registered == that.registered
                        && Objects.equals(this.entityName, that.entityName)
                        && Objects.equals(this.entityType, that.entityType)
                        && Objects.equals(this.entityTypeCustom, that.entityTypeCustom)
                        && Objects.equals(this.customerCareDetails, that.customerCareDetails)
                        && Objects.equals(this.grievanceOfficerDetails, that.grievanceOfficerDetails);
            }

            @Override
            public int hashCode() {
                return Objects.hash(entityName, entityType, registered, entityTypeCustom,
                        customerCareDetails, grievanceOfficerDetails);
            }

            @Override
            public String toString() {
                return "IqGetMerchantComplianceResponse.Success.MerchantInfo[entityName="
                        + entityName + ", entityType=" + entityType
                        + ", registered=" + registered + ']';
            }
        }

        /**
         * The customer-care contact triple decoded from the {@code <customer_care_details/>} block.
         *
         * @apiNote
         * Use this class to render the customer-care row in the compliance surface; each field defaults to the empty string when the wire shape omitted the corresponding child, matching the WA Web parser's default.
         */
        public static final class BusinessContactDetails {
            /**
             * The contact email lifted from {@code <customer_care_details><email/></customer_care_details>}.
             */
            private final String email;

            /**
             * The landline phone number lifted from {@code <customer_care_details><landline_number/></customer_care_details>}.
             */
            private final String landlineNumber;

            /**
             * The mobile phone number lifted from {@code <customer_care_details><mobile_number/></customer_care_details>}.
             */
            private final String mobileNumber;

            /**
             * Constructs a typed contact triple.
             *
             * @apiNote
             * Call this constructor when projecting a customer-care or grievance-officer contact block into the typed model; pass an empty string for any field that the wire shape omitted to keep the WA Web parser's defaults.
             *
             * @param email          the contact email; never {@code null}
             * @param landlineNumber the landline number; never {@code null}
             * @param mobileNumber   the mobile number; never {@code null}
             * @throws NullPointerException if any argument is {@code null}
             */
            public BusinessContactDetails(String email, String landlineNumber, String mobileNumber) {
                this.email = Objects.requireNonNull(email, "email cannot be null");
                this.landlineNumber = Objects.requireNonNull(landlineNumber, "landlineNumber cannot be null");
                this.mobileNumber = Objects.requireNonNull(mobileNumber, "mobileNumber cannot be null");
            }

            /**
             * Returns the contact email.
             *
             * @apiNote
             * Use this getter to render the contact-email cell of the customer-care row.
             *
             * @return the email; never {@code null}
             */
            public String email() {
                return email;
            }

            /**
             * Returns the landline phone number.
             *
             * @apiNote
             * Use this getter to render the landline-number cell of the customer-care row.
             *
             * @return the number; never {@code null}
             */
            public String landlineNumber() {
                return landlineNumber;
            }

            /**
             * Returns the mobile phone number.
             *
             * @apiNote
             * Use this getter to render the mobile-number cell of the customer-care row.
             *
             * @return the number; never {@code null}
             */
            public String mobileNumber() {
                return mobileNumber;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (BusinessContactDetails) obj;
                return Objects.equals(this.email, that.email)
                        && Objects.equals(this.landlineNumber, that.landlineNumber)
                        && Objects.equals(this.mobileNumber, that.mobileNumber);
            }

            @Override
            public int hashCode() {
                return Objects.hash(email, landlineNumber, mobileNumber);
            }

            @Override
            public String toString() {
                return "IqGetMerchantComplianceResponse.Success.BusinessContactDetails[email="
                        + email + ", landlineNumber=" + landlineNumber
                        + ", mobileNumber=" + mobileNumber + ']';
            }
        }

        /**
         * The grievance-officer block decoded from the {@code <grievance_officer_details/>} child; extends the customer-care triple with an officer name.
         *
         * @apiNote
         * Use this class to render the grievance-officer row in the compliance surface; each field defaults to the empty string when the wire shape omitted the corresponding child, matching the WA Web parser's default.
         */
        public static final class BusinessGrievanceOfficerDetails {
            /**
             * The officer's full name lifted from {@code <grievance_officer_details><name/></grievance_officer_details>}.
             */
            private final String name;

            /**
             * The officer's email lifted from {@code <grievance_officer_details><email/></grievance_officer_details>}.
             */
            private final String email;

            /**
             * The officer's landline phone number lifted from {@code <grievance_officer_details><landline_number/></grievance_officer_details>}.
             */
            private final String landlineNumber;

            /**
             * The officer's mobile phone number lifted from {@code <grievance_officer_details><mobile_number/></grievance_officer_details>}.
             */
            private final String mobileNumber;

            /**
             * Constructs a typed block.
             *
             * @apiNote
             * Call this constructor when projecting a {@code <grievance_officer_details/>} child into the typed model; pass an empty string for any field that the wire shape omitted to keep the WA Web parser's defaults.
             *
             * @param name           the officer's full name; never {@code null}
             * @param email          the officer's email; never {@code null}
             * @param landlineNumber the officer's landline number; never {@code null}
             * @param mobileNumber   the officer's mobile number; never {@code null}
             * @throws NullPointerException if any argument is {@code null}
             */
            public BusinessGrievanceOfficerDetails(String name, String email,
                                           String landlineNumber, String mobileNumber) {
                this.name = Objects.requireNonNull(name, "name cannot be null");
                this.email = Objects.requireNonNull(email, "email cannot be null");
                this.landlineNumber = Objects.requireNonNull(landlineNumber, "landlineNumber cannot be null");
                this.mobileNumber = Objects.requireNonNull(mobileNumber, "mobileNumber cannot be null");
            }

            /**
             * Returns the officer's full name.
             *
             * @apiNote
             * Use this getter to render the name cell of the grievance-officer row.
             *
             * @return the name; never {@code null}
             */
            public String name() {
                return name;
            }

            /**
             * Returns the officer's email.
             *
             * @apiNote
             * Use this getter to render the email cell of the grievance-officer row.
             *
             * @return the email; never {@code null}
             */
            public String email() {
                return email;
            }

            /**
             * Returns the officer's landline phone number.
             *
             * @apiNote
             * Use this getter to render the landline-number cell of the grievance-officer row.
             *
             * @return the number; never {@code null}
             */
            public String landlineNumber() {
                return landlineNumber;
            }

            /**
             * Returns the officer's mobile phone number.
             *
             * @apiNote
             * Use this getter to render the mobile-number cell of the grievance-officer row.
             *
             * @return the number; never {@code null}
             */
            public String mobileNumber() {
                return mobileNumber;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (BusinessGrievanceOfficerDetails) obj;
                return Objects.equals(this.name, that.name)
                        && Objects.equals(this.email, that.email)
                        && Objects.equals(this.landlineNumber, that.landlineNumber)
                        && Objects.equals(this.mobileNumber, that.mobileNumber);
            }

            @Override
            public int hashCode() {
                return Objects.hash(name, email, landlineNumber, mobileNumber);
            }

            @Override
            public String toString() {
                return "IqGetMerchantComplianceResponse.Success.BusinessGrievanceOfficerDetails[name="
                        + name + ", email=" + email + ']';
            }
        }

        /**
         * The decoded merchant-compliance entries in wire order.
         */
        private final List<MerchantInfo> entries;

        /**
         * Constructs a typed success reply.
         *
         * @apiNote
         * Call this constructor when projecting the inbound {@code result} envelope into the typed model; pass an empty list when the relay returned no {@code <merchant_info/>} children.
         *
         * @param entries the compliance entries; never {@code null}
         * @throws NullPointerException if {@code entries} is {@code null}
         */
        public Success(List<MerchantInfo> entries) {
            Objects.requireNonNull(entries, "entries cannot be null");
            this.entries = List.copyOf(entries);
        }

        /**
         * Returns the merchant-compliance entries.
         *
         * @apiNote
         * Use this getter to iterate the per-merchant compliance bundles when rendering the compliance surface; the list preserves the wire order, which mirrors the order of the merchant JIDs supplied to the request.
         *
         * @return an unmodifiable list; never {@code null}
         */
        public List<MerchantInfo> entries() {
            return entries;
        }

        /**
         * Tries to parse a {@link Success} variant from the inbound stanza.
         *
         * @apiNote
         * Call this entry from {@link IqGetMerchantComplianceResponse#of(Node, Node)} or directly when only the success branch is interesting; returns empty when the stanza does not carry a {@code result} envelope matching the original request.
         *
         * @implNote
         * This implementation mirrors the deprecated WAP parser inside {@code WAWebMerchantComplianceJob.merchantComplianceResponse}: each {@code <merchant_info/>} child contributes a {@link MerchantInfo} where the entity-name, entity-type and entity-type-custom fields default to the empty string when omitted, the registered flag is decoded from the {@code is_registered="true"} attribute string, and the customer-care and grievance-officer blocks default to all-empty-string when the carrier child is absent.
         *
         * @param node    the inbound IQ stanza; never {@code null}
         * @param request the original outbound request; never {@code null}
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match the success schema
         */
        @WhatsAppWebExport(moduleName = "WAWebMerchantComplianceJob",
                exports = "merchantComplianceResponse", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var entries = new ArrayList<MerchantInfo>();
            for (var miNode : node.getChildren("merchant_info")) {
                var entityName = miNode.getChild("entity_name")
                        .flatMap(Node::toContentString).orElse("");
                var entityType = miNode.getChild("entity_type")
                        .flatMap(Node::toContentString).orElse("");
                var entityTypeCustom = miNode.getChild("entity_type_custom")
                        .flatMap(Node::toContentString).orElse(null);
                var registered = miNode.getAttributeAsString("is_registered")
                        .map("true"::equals).orElse(false);
                var ccdNode = miNode.getChild("customer_care_details").orElse(null);
                var ccd = parseContactDetails(ccdNode);
                var godNode = miNode.getChild("grievance_officer_details").orElse(null);
                var godName = godNode == null
                        ? ""
                        : godNode.getChild("name").flatMap(Node::toContentString).orElse("");
                var godEmail = godNode == null
                        ? ""
                        : godNode.getChild("email").flatMap(Node::toContentString).orElse("");
                var godLandline = godNode == null
                        ? ""
                        : godNode.getChild("landline_number").flatMap(Node::toContentString).orElse("");
                var godMobile = godNode == null
                        ? ""
                        : godNode.getChild("mobile_number").flatMap(Node::toContentString).orElse("");
                var god = new BusinessGrievanceOfficerDetails(godName, godEmail, godLandline, godMobile);
                entries.add(new MerchantInfo(entityName, entityType, registered,
                        entityTypeCustom, ccd, god));
            }
            if (entries.isEmpty()) {
                return Optional.of(new Success(Collections.emptyList()));
            }
            return Optional.of(new Success(entries));
        }

        /**
         * Decodes a contact-details carrier node into a {@link BusinessContactDetails} triple.
         *
         * @apiNote
         * Used internally by {@link #of(Node, Node)} to fold a {@code <customer_care_details/>} or any other carrier of {@code <email>}, {@code <landline_number>} and {@code <mobile_number>} children into the typed model.
         *
         * @implNote
         * This implementation defaults each missing field to the empty string per the WA Web parser; a {@code null} carrier produces an all-empty-string triple rather than a {@code null} result, so the merchant-info row always has a non-{@code null} contact block.
         *
         * @param contactNode the carrier node; may be {@code null}
         * @return the typed triple; never {@code null}
         */
        private static BusinessContactDetails parseContactDetails(Node contactNode) {
            var email = contactNode == null
                    ? ""
                    : contactNode.getChild("email").flatMap(Node::toContentString).orElse("");
            var landline = contactNode == null
                    ? ""
                    : contactNode.getChild("landline_number").flatMap(Node::toContentString).orElse("");
            var mobile = contactNode == null
                    ? ""
                    : contactNode.getChild("mobile_number").flatMap(Node::toContentString).orElse("");
            return new BusinessContactDetails(email, landline, mobile);
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
            return Objects.equals(this.entries, that.entries);
        }

        @Override
        public int hashCode() {
            return Objects.hash(entries);
        }

        @Override
        public String toString() {
            return "IqGetMerchantComplianceResponse.Success[entries=" + entries + ']';
        }
    }

    /**
     * The {@code ClientError} reply variant surfacing a client-side rejection.
     *
     * @apiNote
     * Use this variant to react to a refused merchant-compliance fetch; typical examples include the relay returning a code-451 sanctions block or a validation rejection on a malformed merchant JID.
     */
    final class ClientError implements IqGetMerchantComplianceResponse {
        /**
         * The numeric error code lifted from the SMAX error envelope.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text lifted from the SMAX error envelope.
         */
        private final String errorText;

        /**
         * Constructs a typed client-error reply.
         *
         * @apiNote
         * Call this constructor when projecting a client-error envelope into the typed model; pass {@code null} for {@code errorText} when the wire shape omitted the text field.
         *
         * @param errorCode the numeric error code
         * @param errorText the human-readable error text; may be {@code null}
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @apiNote
         * Use this getter to read back the SMAX error code that the relay used to classify the failure.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the human-readable error text, when supplied.
         *
         * @apiNote
         * Use this getter to surface the relay-supplied error explanation in the UI when present.
         *
         * @return an {@link Optional} carrying the error text
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant from the inbound stanza.
         *
         * @apiNote
         * Call this entry from {@link IqGetMerchantComplianceResponse#of(Node, Node)} or directly when only the client-error branch is interesting; returns empty when the stanza does not carry a client-error envelope matching the original request.
         *
         * @param node    the inbound IQ stanza; never {@code null}
         * @param request the original outbound request; never {@code null}
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match the client-error schema
         */
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
            return this.errorCode == that.errorCode && Objects.equals(this.errorText, that.errorText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        @Override
        public String toString() {
            return "IqGetMerchantComplianceResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant surfacing a server-side failure.
     *
     * @apiNote
     * Use this variant to react to a backend failure that did not produce typed compliance bundles; WA Web's {@code WAWebMerchantComplianceJob.getMerchantCompliance} surfaces this as a {@code ServerStatusCodeError} carrying the relay-supplied status.
     */
    final class ServerError implements IqGetMerchantComplianceResponse {
        /**
         * The numeric error code lifted from the SMAX error envelope.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text lifted from the SMAX error envelope.
         */
        private final String errorText;

        /**
         * Constructs a typed server-error reply.
         *
         * @apiNote
         * Call this constructor when projecting a server-error envelope into the typed model; pass {@code null} for {@code errorText} when the wire shape omitted the text field.
         *
         * @param errorCode the numeric error code
         * @param errorText the human-readable error text; may be {@code null}
         */
        public ServerError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @apiNote
         * Use this getter to read back the SMAX error code that the relay used to classify the failure.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the human-readable error text, when supplied.
         *
         * @apiNote
         * Use this getter to surface the relay-supplied error explanation in the UI when present.
         *
         * @return an {@link Optional} carrying the error text
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant from the inbound stanza.
         *
         * @apiNote
         * Call this entry from {@link IqGetMerchantComplianceResponse#of(Node, Node)} or directly when only the server-error branch is interesting; returns empty when the stanza does not carry a server-error envelope matching the original request.
         *
         * @param node    the inbound IQ stanza; never {@code null}
         * @param request the original outbound request; never {@code null}
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match the server-error schema
         */
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
            return this.errorCode == that.errorCode && Objects.equals(this.errorText, that.errorText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        @Override
        public String toString() {
            return "IqGetMerchantComplianceResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
