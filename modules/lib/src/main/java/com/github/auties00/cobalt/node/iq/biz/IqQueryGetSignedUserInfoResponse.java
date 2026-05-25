package com.github.auties00.cobalt.node.iq.biz;

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
 * Models the sealed family of inbound reply variants the relay produces in response to an {@link IqQueryGetSignedUserInfoRequest}.
 *
 * <p>The matched variant drives the buyer-side direct-connection trust flow: {@link Success}
 * carries the signed phone-number bundle, {@link ClientError} surfaces a rejected request and
 * {@link ServerError} surfaces a transient internal failure.
 */
@WhatsAppWebModule(moduleName = "WAWebQueryGetSignedUserInfoJob")
public sealed interface IqQueryGetSignedUserInfoResponse extends IqOperation.Response
        permits IqQueryGetSignedUserInfoResponse.Success, IqQueryGetSignedUserInfoResponse.ClientError, IqQueryGetSignedUserInfoResponse.ServerError {

    /**
     * Tries each variant in priority order until one matches.
     *
     * <p>The order is {@link Success}, then {@link ClientError}, then {@link ServerError}.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or empty when no documented variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    static Optional<? extends IqQueryGetSignedUserInfoResponse> of(Node node, Node request) {
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
     * Carries the merchant's signed user-info bundle.
     *
     * <p>The four optional fields feed the buyer-side direct-connection trust flow; the relay
     * leaves each field unset when the merchant has not registered the corresponding piece of
     * information yet.
     */
    final class Success implements IqQueryGetSignedUserInfoResponse {
        /**
         * Holds the phone number echoed inside {@code <phone_number/>}.
         */
        private final String phoneNumber;

        /**
         * Holds the signature TTL echoed inside {@code <ttl_timestamp/>}, the Unix-epoch seconds at which {@link #phoneNumberSignature} expires.
         */
        private final String phoneNumberSignatureExpiration;

        /**
         * Holds the opaque base64-encoded signature blob echoed inside {@code <phone_number_signature/>}.
         */
        private final String phoneNumberSignature;

        /**
         * Holds the business domain claim echoed inside {@code <business_domain/>}.
         */
        private final String businessDomain;

        /**
         * Constructs a successful reply from the decoded {@code <signed_user_info/>} grandchildren.
         *
         * <p>Each field is independently optional on the wire.
         *
         * @param phoneNumber                    the phone number; may be {@code null}
         * @param phoneNumberSignatureExpiration the TTL timestamp; may be {@code null}
         * @param phoneNumberSignature           the signature blob; may be {@code null}
         * @param businessDomain                 the business domain; may be {@code null}
         */
        public Success(String phoneNumber, String phoneNumberSignatureExpiration,
                       String phoneNumberSignature, String businessDomain) {
            this.phoneNumber = phoneNumber;
            this.phoneNumberSignatureExpiration = phoneNumberSignatureExpiration;
            this.phoneNumberSignature = phoneNumberSignature;
            this.businessDomain = businessDomain;
        }

        /**
         * Returns the merchant's phone number that seeds the direct-connection cypher when the cypher type is {@code PhoneNumberAndPostcode}.
         *
         * <p>An empty optional means the relay did not echo the field.
         *
         * @return an {@link Optional} carrying the phone number
         */
        public Optional<String> phoneNumber() {
            return Optional.ofNullable(phoneNumber);
        }

        /**
         * Returns the signature TTL timestamp used to validate that the signature has not expired before storing it in the direct-connection-collection cache.
         *
         * @return an {@link Optional} carrying the timestamp
         */
        public Optional<String> phoneNumberSignatureExpiration() {
            return Optional.ofNullable(phoneNumberSignatureExpiration);
        }

        /**
         * Returns the opaque phone-number signature blob attached to the direct-connection cypher.
         *
         * <p>The blob is base64-encoded and verified relay-side.
         *
         * @return an {@link Optional} carrying the signature
         */
        public Optional<String> phoneNumberSignature() {
            return Optional.ofNullable(phoneNumberSignature);
        }

        /**
         * Returns the merchant's claimed business domain displayed in the cart UI.
         *
         * <p>An empty optional means the merchant has not declared one.
         *
         * @return an {@link Optional} carrying the business domain
         */
        public Optional<String> businessDomain() {
            return Optional.ofNullable(businessDomain);
        }

        /**
         * Tries to parse a {@link Success} variant from the inbound stanza.
         *
         * <p>The method validates the {@code <iq type="result">} envelope and reads each optional
         * grandchild of {@code <signed_user_info/>}.
         *
         * @implNote
         * This implementation reads every field as a content string; the relay's reference parser
         * shares the same shape.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match the success schema
         */
        @WhatsAppWebExport(moduleName = "WAWebQueryGetSignedUserInfoJob",
                exports = "QueryGetSignedUserInfo", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var signedUserInfo = node.getChild("signed_user_info").orElse(null);
            if (signedUserInfo == null) {
                return Optional.of(new Success(null, null, null, null));
            }
            var phoneNumber = signedUserInfo.getChild("phone_number")
                    .flatMap(Node::toContentString).orElse(null);
            var ttl = signedUserInfo.getChild("ttl_timestamp")
                    .flatMap(Node::toContentString).orElse(null);
            var signature = signedUserInfo.getChild("phone_number_signature")
                    .flatMap(Node::toContentString).orElse(null);
            var domain = signedUserInfo.getChild("business_domain")
                    .flatMap(Node::toContentString).orElse(null);
            return Optional.of(new Success(phoneNumber, ttl, signature, domain));
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
            return Objects.equals(this.phoneNumber, that.phoneNumber)
                    && Objects.equals(this.phoneNumberSignatureExpiration, that.phoneNumberSignatureExpiration)
                    && Objects.equals(this.phoneNumberSignature, that.phoneNumberSignature)
                    && Objects.equals(this.businessDomain, that.businessDomain);
        }

        @Override
        public int hashCode() {
            return Objects.hash(phoneNumber, phoneNumberSignatureExpiration,
                    phoneNumberSignature, businessDomain);
        }

        @Override
        public String toString() {
            return "IqQueryGetSignedUserInfoResponse.Success[phoneNumber=" + phoneNumber
                    + ", phoneNumberSignatureExpiration=" + phoneNumberSignatureExpiration
                    + ", phoneNumberSignature=" + phoneNumberSignature
                    + ", businessDomain=" + businessDomain + ']';
        }
    }

    /**
     * Surfaces a relay rejection of the request as malformed or referencing an unknown merchant.
     *
     * <p>This variant carries a user-facing 4xx-class error for the cart UI; the relay returns this
     * shape when the merchant JID is not a registered business.
     */
    final class ClientError implements IqQueryGetSignedUserInfoResponse {
        /**
         * Holds the numeric error code echoed by the {@code <error/>} child.
         */
        private final int errorCode;

        /**
         * Holds the optional human-readable error text echoed by the {@code <error/>} child.
         */
        private final String errorText;

        /**
         * Constructs a client-error reply from the relay's {@code <error/>} envelope.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be {@code null}
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code used to dispatch a localised message to the cart UI.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the human-readable error text, when supplied.
         *
         * <p>The text is server-localised and not stable across snapshots.
         *
         * @return an {@link Optional} carrying the error text
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant from the inbound stanza.
         *
         * <p>Delegates to {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)} to extract
         * the (code, text) envelope.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
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
            return "IqQueryGetSignedUserInfoResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * Surfaces a transient internal-failure status the relay returns while processing the request.
     *
     * <p>This variant drives a backoff-and-retry path in the cart UI; the relay returns this shape
     * when the catalog backend is temporarily unavailable.
     */
    final class ServerError implements IqQueryGetSignedUserInfoResponse {
        /**
         * Holds the numeric error code echoed by the {@code <error/>} child.
         */
        private final int errorCode;

        /**
         * Holds the optional human-readable error text echoed by the {@code <error/>} child.
         */
        private final String errorText;

        /**
         * Constructs a server-error reply from the relay's {@code <error/>} envelope.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be {@code null}
         */
        public ServerError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code; a 5xx-class value is the canonical retry trigger.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the human-readable error text, when supplied.
         *
         * <p>The text is server-localised and not stable across snapshots.
         *
         * @return an {@link Optional} carrying the error text
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant from the inbound stanza.
         *
         * <p>Delegates to {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)} to extract
         * the (code, text) envelope.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
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
            return "IqQueryGetSignedUserInfoResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
