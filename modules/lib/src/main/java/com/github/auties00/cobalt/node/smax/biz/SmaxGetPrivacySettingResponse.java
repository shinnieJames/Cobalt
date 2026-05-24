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

/**
 * The sealed family of inbound reply variants produced by the relay
 * in response to a {@link SmaxGetPrivacySettingRequest}.
 *
 * @apiNote
 * Each variant projects a distinct outcome of the SMB
 * data-sharing-with-Meta consent bridge: {@link Success} carries
 * the current consent value, {@link ClientError} carries a
 * documented {@code 4xx} rejection, and {@link ServerError} carries
 * a transient {@code 5xx} relay failure; the WA Web
 * {@code WAWebCommonCTWADataSharing} pipeline writes the parsed
 * consent into {@code CTWADataSharingModel}.
 *
 * @implNote
 * This implementation mirrors WA Web's
 * {@code WASmaxBizSettingsGetPrivacySettingRPC.sendGetPrivacySettingRPC}
 * by trying each variant in priority order via {@link #of} and
 * returning the first successful parse.
 */
public sealed interface SmaxGetPrivacySettingResponse extends SmaxOperation.Response
        permits SmaxGetPrivacySettingResponse.Success, SmaxGetPrivacySettingResponse.ClientError, SmaxGetPrivacySettingResponse.ServerError {

    /**
     * Tries each {@link SmaxGetPrivacySettingResponse} variant in
     * priority order and returns the first that parses cleanly.
     *
     * @apiNote
     * Invoked by the smax reply pump after dispatching a
     * {@link SmaxGetPrivacySettingRequest}; the priority order
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
    @WhatsAppWebExport(moduleName = "WASmaxBizSettingsGetPrivacySettingRPC",
            exports = "sendGetPrivacySettingRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInBizSettingsPrivacySettingErrors",
            exports = "parsePrivacySettingErrors", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxGetPrivacySettingResponse> of(Node node, Node request) {
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
     * The {@code Success} reply variant carrying the current SMB
     * data-sharing-with-Meta consent value.
     *
     * @apiNote
     * Projected by {@link SmaxGetPrivacySettingResponse#of(Node, Node)}
     * when the relay returns the documented
     * {@code <privacy><smb_data_sharing_with_meta_consent>} tree;
     * the wire value is one of the {@code "true"}/{@code "false"}/
     * {@code "notset"} dictionary literals enforced by
     * {@code WASmaxInBizSettingsEnums.ENUM_FALSE_NOTSET_TRUE}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBizSettingsGetPrivacySettingResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInBizSettingsSmbDataSharingSettingMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInBizSettingsSmbDataSharingSettingValueMixin")
    final class Success implements SmaxGetPrivacySettingResponse {
        /**
         * The {@code value} attribute of the
         * {@code <smb_data_sharing_with_meta_consent>} child; one
         * of the {@code "true"}/{@code "false"}/{@code "notset"}
         * dictionary literals.
         */
        private final String dataSharingConsent;

        /**
         * Constructs a new successful reply.
         *
         * @apiNote
         * Invoked by {@link #of(Node, Node)} after the
         * {@code value} attribute has been validated against the
         * {@code ENUM_FALSE_NOTSET_TRUE} dictionary.
         *
         * @param dataSharingConsent the consent enum value; never
         *                           {@code null}
         * @throws NullPointerException if
         *                              {@code dataSharingConsent}
         *                              is {@code null}
         */
        public Success(String dataSharingConsent) {
            this.dataSharingConsent = Objects.requireNonNull(dataSharingConsent,
                    "dataSharingConsent cannot be null");
        }

        /**
         * Returns the consent enum value.
         *
         * @apiNote
         * Use as the input to
         * {@code CTWADataSharingModel.setValue} when mirroring the
         * WA Web {@code WAWebCommonCTWADataSharing} cache.
         *
         * @return the consent value; never {@code null}
         */
        public String dataSharingConsent() {
            return dataSharingConsent;
        }

        /**
         * Tries to parse a {@link Success} variant from the given
         * inbound stanza.
         *
         * @implNote
         * This implementation enforces the
         * {@code SmaxIqResultResponseMixin} envelope check, walks
         * the {@code <privacy><smb_data_sharing_with_meta_consent>}
         * tree, and validates the {@code value} attribute against
         * {@link SmaxBizSettingsFalseNotsetTrueFlag#of(String)};
         * any other value yields {@link Optional#empty()} rather
         * than an exception, matching the strict dictionary
         * semantics of
         * {@code WASmaxInBizSettingsSmbDataSharingSettingValueMixin.parseSmbDataSharingSettingValueMixin}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant,
         *         or empty when the stanza does not match the
         *         success schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBizSettingsGetPrivacySettingResponseSuccess",
                exports = "parseGetPrivacySettingResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            Objects.requireNonNull(node, "node cannot be null");
            Objects.requireNonNull(request, "request cannot be null");
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var privacy = node.getChild("privacy").orElse(null);
            if (privacy == null) {
                return Optional.empty();
            }
            var consent = privacy.getChild("smb_data_sharing_with_meta_consent").orElse(null);
            if (consent == null) {
                return Optional.empty();
            }
            var value = consent.getAttributeAsString("value").orElse(null);
            if (SmaxBizSettingsFalseNotsetTrueFlag.of(value).isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Success(value));
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
            return Objects.equals(this.dataSharingConsent, that.dataSharingConsent);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(dataSharingConsent);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "SmaxGetPrivacySettingResponse.Success[dataSharingConsent="
                    + dataSharingConsent + ']';
        }
    }

    /**
     * The {@code ClientError} reply variant carrying a documented
     * {@code 4xx} privacy-setting error code.
     *
     * @apiNote
     * Surfaced when the relay rejected the consent fetch via one
     * of the documented {@code parsePrivacySettingErrors} mixin
     * arms; the WA Web CTWA settings surface treats the consent
     * value as indeterminate on this branch.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBizSettingsGetPrivacySettingResponseError")
    @WhatsAppWebModule(moduleName = "WASmaxInBizSettingsPrivacySettingErrors")
    final class ClientError implements SmaxGetPrivacySettingResponse {
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
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}
         * and admits the full {@code 4xx} range as a catch-all,
         * matching WA Web's
         * {@code parsePrivacySettingErrors} dispatch.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant,
         *         or empty when the stanza does not match the
         *         client-error schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBizSettingsGetPrivacySettingResponseError",
                exports = "parseGetPrivacySettingResponseError",
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
            return "SmaxGetPrivacySettingResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant carrying a transient
     * {@code 5xx} relay failure.
     *
     * @apiNote
     * Surfaced when the relay returned a transient internal
     * failure while reading the consent; the caller can re-issue
     * the request with backoff.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBizSettingsGetPrivacySettingResponseError")
    final class ServerError implements SmaxGetPrivacySettingResponse {
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
         * This implementation delegates the {@code 5xx} range
         * check to
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)};
         * any stanza outside the {@code 5xx} range yields
         * {@link Optional#empty()}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant,
         *         or empty when the stanza does not match the
         *         server-error schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBizSettingsGetPrivacySettingResponseError",
                exports = "parseGetPrivacySettingResponseError",
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
            return "SmaxGetPrivacySettingResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
