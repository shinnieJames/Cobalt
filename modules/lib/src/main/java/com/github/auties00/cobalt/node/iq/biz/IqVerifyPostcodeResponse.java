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
 * to an {@link IqVerifyPostcodeRequest}.
 *
 * @apiNote
 * Pattern-match the returned variant to drive the cart-postcode entry
 * surface: {@link Success} carries the verdict and the optional
 * encrypted location-name echo, {@link ClientError} surfaces a rejected
 * request and {@link ServerError} surfaces a transient internal
 * failure.
 */
@WhatsAppWebModule(moduleName = "WAWebVerifyPostcodeJob")
public sealed interface IqVerifyPostcodeResponse extends IqOperation.Response
        permits IqVerifyPostcodeResponse.Success, IqVerifyPostcodeResponse.ClientError, IqVerifyPostcodeResponse.ServerError {

    /**
     * Tries each variant in priority order until one matches.
     *
     * @apiNote
     * Use this entry point on every IQ stanza tagged with a
     * {@code <result_code/>} payload; the order is {@link Success},
     * then {@link ClientError}, then {@link ServerError}.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or
     *         empty when no documented variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    static Optional<? extends IqVerifyPostcodeResponse> of(Node node, Node request) {
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
     * The {@code Success} variant carrying the verdict and the optional
     * encrypted location-name echo.
     *
     * @apiNote
     * Use {@link #resultCode()} to drive the cart-postcode UI:
     * {@link ResultCode#SUCCESS} unlocks the catalog grid for the
     * resolved address, {@link ResultCode#INVALID_POSTCODE} prompts
     * the buyer to retry the entry and
     * {@link ResultCode#UNSERVICEABLE_LOCATION} blocks checkout with a
     * "not delivered to your area" message.
     */
    final class Success implements IqVerifyPostcodeResponse {
        /**
         * Closed set of verdicts the relay's reference parser
         * documents inside the {@code <result_code/>} child.
         *
         * @apiNote
         * Use this enum to dispatch on the verdict; the relay throws a
         * 500-status error for any value outside this set, so any
         * received string outside this enum is treated as an
         * unparseable success.
         */
        public enum ResultCode {
            /**
             * The postcode was successfully decrypted and resolves to
             * a serviceable address.
             *
             * @apiNote
             * Unlock the catalog grid for the resolved location.
             */
            SUCCESS("success"),

            /**
             * The postcode could not be decrypted or did not pass
             * validation.
             *
             * @apiNote
             * Prompt the buyer to retry the entry with a different
             * postcode.
             */
            INVALID_POSTCODE("invalid_postcode"),

            /**
             * The postcode resolved to an address outside the
             * merchant's serviceable area.
             *
             * @apiNote
             * Block checkout with a "not delivered to your area"
             * message.
             */
            UNSERVICEABLE_LOCATION("unserviceable_location");

            /**
             * The wire string carried by the
             * {@code <result_code/>} element.
             */
            private final String wireValue;

            /**
             * Constructs a constant from its wire string.
             *
             * @apiNote
             * Package-private enum constructor; the wire string is
             * the canonical string the relay echoes.
             *
             * @param wireValue the wire string; never {@code null}
             */
            ResultCode(String wireValue) {
                this.wireValue = wireValue;
            }

            /**
             * Returns the wire string for this constant.
             *
             * @apiNote
             * Use this getter when round-tripping the enum back to
             * the wire (e.g. for logging or fixture capture); the
             * relay never accepts the enum name itself.
             *
             * @return the wire string; never {@code null}
             */
            public String wireValue() {
                return wireValue;
            }

            /**
             * Looks up the constant for the given wire string.
             *
             * @apiNote
             * Use this entry point from the response parser; an empty
             * optional means the relay returned an unknown verdict
             * and the caller should treat the success as
             * unparseable.
             *
             * @param wireValue the wire string; may be {@code null}
             * @return an {@link Optional} carrying the matching
             *         constant, or empty when the value is unknown
             */
            public static Optional<ResultCode> of(String wireValue) {
                if (wireValue == null) {
                    return Optional.empty();
                }
                for (var value : values()) {
                    if (value.wireValue.equals(wireValue)) {
                        return Optional.of(value);
                    }
                }
                return Optional.empty();
            }
        }

        /**
         * The verdict returned by the relay.
         */
        private final ResultCode resultCode;

        /**
         * The opaque encrypted location-name echo returned for
         * {@link ResultCode#SUCCESS} only; absent for the other
         * verdicts.
         */
        private final String encryptedLocationName;

        /**
         * Constructs a successful reply.
         *
         * @apiNote
         * Use this constructor only from {@link #of(Node, Node)}.
         *
         * @param resultCode            the verdict; never {@code null}
         * @param encryptedLocationName the optional encrypted echo;
         *                              may be {@code null}
         * @throws NullPointerException if {@code resultCode} is
         *                              {@code null}
         */
        public Success(ResultCode resultCode, String encryptedLocationName) {
            this.resultCode = Objects.requireNonNull(resultCode, "resultCode cannot be null");
            this.encryptedLocationName = encryptedLocationName;
        }

        /**
         * Returns the verdict.
         *
         * @apiNote
         * Use this getter to dispatch on the verdict; see
         * {@link ResultCode} for the documented values.
         *
         * @return the verdict; never {@code null}
         */
        public ResultCode resultCode() {
            return resultCode;
        }

        /**
         * Returns the optional encrypted location-name echo.
         *
         * @apiNote
         * Use this getter to feed the encrypted echo into the
         * {@code WAWebDirectConnectionCypher.cypherStringToString}
         * decryption flow and display the resolved location-name
         * label in the cart UI; an empty optional means the verdict
         * is not {@link ResultCode#SUCCESS} or the relay omitted the
         * echo.
         *
         * @return an {@link Optional} carrying the echo
         */
        public Optional<String> encryptedLocationName() {
            return Optional.ofNullable(encryptedLocationName);
        }

        /**
         * Tries to parse a {@link Success} variant.
         *
         * @apiNote
         * Call this from {@link #of(Node, Node)}; the method validates
         * the {@code <iq type="result">} envelope, decodes the
         * {@code <result_code/>} body and reads the optional
         * {@code <encrypted_location_name/>} echo.
         *
         * @implNote
         * This implementation returns an empty optional when the
         * {@code <result_code/>} body is unknown; WA Web's reference
         * parser throws a 500-status error in that case, which Cobalt
         * defers to the caller's error handler.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty when the stanza does not match the success
         *         schema or the {@code result_code} is unknown
         */
        @WhatsAppWebExport(moduleName = "WAWebVerifyPostcodeJob",
                exports = "VerifyPostcode", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var resultCodeNode = node.getChild("result_code").orElse(null);
            if (resultCodeNode == null) {
                return Optional.empty();
            }
            var resultCodeValue = resultCodeNode.toContentString().orElse(null);
            var resultCode = ResultCode.of(resultCodeValue).orElse(null);
            if (resultCode == null) {
                return Optional.empty();
            }
            var encryptedLocationName = node.getChild("encrypted_location_name")
                    .flatMap(Node::toContentString)
                    .orElse(null);
            return Optional.of(new Success(resultCode, encryptedLocationName));
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
            return this.resultCode == that.resultCode
                    && Objects.equals(this.encryptedLocationName, that.encryptedLocationName);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(resultCode, encryptedLocationName);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "IqVerifyPostcodeResponse.Success[resultCode=" + resultCode
                    + ", encryptedLocationName=" + encryptedLocationName + ']';
        }
    }

    /**
     * The {@code ClientError} variant emitted when the relay rejects
     * the request as malformed or referencing an unknown merchant.
     *
     * @apiNote
     * Use this variant to surface a user-facing 4xx-class error to
     * the cart-postcode entry surface.
     */
    final class ClientError implements IqVerifyPostcodeResponse {
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
         * when surfacing a localised message to the cart-postcode
         * entry surface.
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
            return "IqVerifyPostcodeResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} variant emitted when the relay returns a
     * transient internal-failure status while processing the request.
     *
     * @apiNote
     * Use this variant to drive a backoff-and-retry path in the
     * cart-postcode entry surface; the relay returns this shape when
     * the catalog backend is temporarily unavailable.
     */
    final class ServerError implements IqVerifyPostcodeResponse {
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
            return "IqVerifyPostcodeResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
