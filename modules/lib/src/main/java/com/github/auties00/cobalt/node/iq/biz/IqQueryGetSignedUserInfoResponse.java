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
 * Sealed family of inbound reply variants the relay produces in response
 * to an {@link IqQueryGetSignedUserInfoRequest}.
 *
 * @apiNote
 * Pattern-match the returned variant to drive the buyer-side
 * direct-connection trust flow: {@link Success} carries the signed
 * phone-number bundle, {@link ClientError} surfaces a rejected request
 * and {@link ServerError} surfaces a transient internal failure.
 */
@WhatsAppWebModule(moduleName = "WAWebQueryGetSignedUserInfoJob")
public sealed interface IqQueryGetSignedUserInfoResponse extends IqOperation.Response
        permits IqQueryGetSignedUserInfoResponse.Success, IqQueryGetSignedUserInfoResponse.ClientError, IqQueryGetSignedUserInfoResponse.ServerError {

    /**
     * Tries each variant in priority order until one matches.
     *
     * @apiNote
     * Use this entry point on every IQ stanza tagged with the
     * {@code <signed_user_info/>} payload; the order is {@link Success},
     * then {@link ClientError}, then {@link ServerError}.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or empty
     *         when no documented variant matched
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
     * The {@code Success} variant carrying the merchant's signed
     * user-info bundle.
     *
     * @apiNote
     * Use the four optional fields to feed the buyer-side
     * direct-connection trust flow; the relay leaves each field unset
     * when the merchant has not registered the corresponding piece of
     * information yet.
     */
    final class Success implements IqQueryGetSignedUserInfoResponse {
        /**
         * The phone number echoed inside {@code <phone_number/>}.
         */
        private final String phoneNumber;

        /**
         * The signature TTL echoed inside {@code <ttl_timestamp/>}
         * (Unix-epoch seconds at which {@link #phoneNumberSignature}
         * expires).
         */
        private final String phoneNumberSignatureExpiration;

        /**
         * The opaque base64-encoded signature blob echoed inside
         * {@code <phone_number_signature/>}.
         */
        private final String phoneNumberSignature;

        /**
         * The business domain claim echoed inside
         * {@code <business_domain/>}.
         */
        private final String businessDomain;

        /**
         * Constructs a successful reply.
         *
         * @apiNote
         * Use this constructor only from {@link #of(Node, Node)}; each
         * field is independently optional on the wire.
         *
         * @param phoneNumber                    the phone number; may
         *                                       be {@code null}
         * @param phoneNumberSignatureExpiration the TTL timestamp; may
         *                                       be {@code null}
         * @param phoneNumberSignature           the signature blob; may
         *                                       be {@code null}
         * @param businessDomain                 the business domain;
         *                                       may be {@code null}
         */
        public Success(String phoneNumber, String phoneNumberSignatureExpiration,
                       String phoneNumberSignature, String businessDomain) {
            this.phoneNumber = phoneNumber;
            this.phoneNumberSignatureExpiration = phoneNumberSignatureExpiration;
            this.phoneNumberSignature = phoneNumberSignature;
            this.businessDomain = businessDomain;
        }

        /**
         * Returns the merchant's phone number, when supplied.
         *
         * @apiNote
         * Use this getter to seed the direct-connection cypher when
         * the cypher type is
         * {@code PhoneNumberAndPostcode}; an empty optional means the
         * relay did not echo the field.
         *
         * @return an {@link Optional} carrying the phone number
         */
        public Optional<String> phoneNumber() {
            return Optional.ofNullable(phoneNumber);
        }

        /**
         * Returns the signature TTL timestamp, when supplied.
         *
         * @apiNote
         * Use this getter to validate that the signature has not
         * expired before storing it in the
         * direct-connection-collection cache.
         *
         * @return an {@link Optional} carrying the timestamp
         */
        public Optional<String> phoneNumberSignatureExpiration() {
            return Optional.ofNullable(phoneNumberSignatureExpiration);
        }

        /**
         * Returns the opaque phone-number signature blob, when
         * supplied.
         *
         * @apiNote
         * Use this getter to attach the signature to the
         * direct-connection cypher; the blob is base64-encoded and
         * verified relay-side.
         *
         * @return an {@link Optional} carrying the signature
         */
        public Optional<String> phoneNumberSignature() {
            return Optional.ofNullable(phoneNumberSignature);
        }

        /**
         * Returns the merchant's claimed business domain, when set.
         *
         * @apiNote
         * Use this getter to display the merchant's declared business
         * domain in the cart UI; an empty optional means the merchant
         * has not declared one.
         *
         * @return an {@link Optional} carrying the business domain
         */
        public Optional<String> businessDomain() {
            return Optional.ofNullable(businessDomain);
        }

        /**
         * Tries to parse a {@link Success} variant.
         *
         * @apiNote
         * Call this from {@link #of(Node, Node)}; the method validates
         * the {@code <iq type="result">} envelope and reads each
         * optional grandchild of {@code <signed_user_info/>}.
         *
         * @implNote
         * This implementation reads every field as a content string;
         * the relay's reference parser shares the same shape.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty when the stanza does not match the success
         *         schema
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
            return Objects.equals(this.phoneNumber, that.phoneNumber)
                    && Objects.equals(this.phoneNumberSignatureExpiration, that.phoneNumberSignatureExpiration)
                    && Objects.equals(this.phoneNumberSignature, that.phoneNumberSignature)
                    && Objects.equals(this.businessDomain, that.businessDomain);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(phoneNumber, phoneNumberSignatureExpiration,
                    phoneNumberSignature, businessDomain);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "IqQueryGetSignedUserInfoResponse.Success[phoneNumber=" + phoneNumber
                    + ", phoneNumberSignatureExpiration=" + phoneNumberSignatureExpiration
                    + ", phoneNumberSignature=" + phoneNumberSignature
                    + ", businessDomain=" + businessDomain + ']';
        }
    }

    /**
     * The {@code ClientError} variant emitted when the relay rejects
     * the request as malformed or referencing an unknown merchant.
     *
     * @apiNote
     * Use this variant to surface a user-facing 4xx-class error to the
     * cart UI; the relay returns this shape when the merchant JID is
     * not a registered business.
     */
    final class ClientError implements IqQueryGetSignedUserInfoResponse {
        /**
         * The numeric error code echoed by the {@code <error/>} child.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text echoed by the
         * {@code <error/>} child.
         */
        private final String errorText;

        /**
         * Constructs a client-error reply.
         *
         * @apiNote
         * Use this constructor only from {@link #of(Node, Node)}; the
         * (code, text) pair comes from the relay's {@code <error/>}
         * envelope.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be
         *                  {@code null}
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @apiNote
         * Use this getter to dispatch on the relay-side error code
         * when surfacing a localised message to the cart UI.
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
         * Use this getter for logging; the text is server-localised
         * and not stable across snapshots.
         *
         * @return an {@link Optional} carrying the error text
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant.
         *
         * @apiNote
         * Call this from {@link #of(Node, Node)}; the method delegates
         * to {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}
         * to extract the (code, text) envelope.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty when the stanza does not match the client-error
         *         schema
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
            return "IqQueryGetSignedUserInfoResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} variant emitted when the relay returns a
     * transient internal-failure status while processing the request.
     *
     * @apiNote
     * Use this variant to drive a backoff-and-retry path in the cart
     * UI; the relay returns this shape when the catalog backend is
     * temporarily unavailable.
     */
    final class ServerError implements IqQueryGetSignedUserInfoResponse {
        /**
         * The numeric error code echoed by the {@code <error/>} child.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text echoed by the
         * {@code <error/>} child.
         */
        private final String errorText;

        /**
         * Constructs a server-error reply.
         *
         * @apiNote
         * Use this constructor only from {@link #of(Node, Node)}; the
         * (code, text) pair comes from the relay's {@code <error/>}
         * envelope.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be
         *                  {@code null}
         */
        public ServerError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @apiNote
         * Use this getter to log the relay-side error code; a 5xx-class
         * value is the canonical retry trigger.
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
         * Use this getter for logging only; the text is server-localised
         * and not stable across snapshots.
         *
         * @return an {@link Optional} carrying the error text
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant.
         *
         * @apiNote
         * Call this from {@link #of(Node, Node)}; the method delegates
         * to {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}
         * to extract the (code, text) envelope.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty when the stanza does not match the server-error
         *         schema
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
            return "IqQueryGetSignedUserInfoResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
