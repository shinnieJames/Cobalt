package com.github.auties00.cobalt.node.iq.group;

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
 * The sealed family of inbound reply variants the relay produces in
 * response to an {@link IqResetGroupInviteCodeRequest}.
 *
 * @apiNote
 * After dispatching the rotate request, pattern-match the returned
 * variant: {@link Success} carries the freshly-issued invite code,
 * {@link ClientError} surfaces caller-side rejections (typically
 * {@code 403} when the caller is not a group admin or {@code 404}
 * when the group no longer exists), and {@link ServerError} surfaces
 * transient {@code 5xx} relay failures that may be retried.
 *
 * @implNote
 * This implementation collapses WA Web's
 * {@code WAWebGroupInviteJob.resetGroupInviteCode} promise-rejection
 * surface into a sealed sum: the JS path either resolves with the
 * parser's {@code {code}} object or rejects with a
 * {@code ServerStatusCodeError}; Cobalt routes both into one typed
 * sealed family so callers can match against a closed set.
 */
@WhatsAppWebModule(moduleName = "WAWebGroupInviteJob")
public sealed interface IqResetGroupInviteCodeResponse extends IqOperation.Response
        permits IqResetGroupInviteCodeResponse.Success, IqResetGroupInviteCodeResponse.ClientError, IqResetGroupInviteCodeResponse.ServerError {

    /**
     * Tries each {@link IqResetGroupInviteCodeResponse} variant in
     * priority order and returns the first that parses cleanly.
     *
     * @apiNote
     * Use this when dispatching the request through the typed
     * {@link IqOperation} pipeline; the dispatcher hands the inbound
     * {@link Node} together with the original outbound request so that
     * each variant can correlate echoed identifiers before claiming a
     * match. Returns {@link Optional#empty()} when none of the
     * documented shapes match, which the caller should treat as an
     * unknown server reply and surface up.
     *
     * @implNote
     * This implementation tries {@link Success} first, then
     * {@link ClientError}, then {@link ServerError}; the order matches
     * WA Web's parser fall-through where the success path is asserted
     * before any error envelope is inspected.
     *
     * @param node    the inbound IQ stanza received from the relay; never {@code null}
     * @param request the original outbound stanza used to validate echoed identifiers; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or {@link Optional#empty()} when no documented variant matched the stanza shape
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupInviteJob",
            exports = "resetGroupInviteCode", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends IqResetGroupInviteCodeResponse> of(Node node, Node request) {
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
     * The {@code Success} reply variant.
     *
     * @apiNote
     * Carries the freshly-rotated invite code echoed by the relay
     * inside {@code <invite code="..."/>}; callers should overwrite
     * any cached {@code chat.whatsapp.com/<code>} URL with this new
     * value before surfacing the result to the user.
     *
     * @implNote
     * This implementation projects WA Web's
     * {@code queryGroupInviteCodeParser} return shape
     * ({@code {code: <invite-code-string>}}) into a single
     * {@link #code()} accessor; the parser reads the {@code code}
     * attribute on the {@code <invite>} child of the result envelope.
     */
    @WhatsAppWebModule(moduleName = "WAWebGroupInviteJob")
    final class Success implements IqResetGroupInviteCodeResponse {
        /**
         * The freshly-rotated invite code.
         */
        private final String code;

        /**
         * Constructs a {@link Success} reply carrying the given
         * invite code.
         *
         * @param code the new invite code; never {@code null}
         * @throws NullPointerException if {@code code} is {@code null}
         */
        public Success(String code) {
            this.code = Objects.requireNonNull(code, "code cannot be null");
        }

        /**
         * Returns the freshly-rotated invite code.
         *
         * @return the new invite code; never {@code null}
         */
        public String code() {
            return code;
        }

        /**
         * Tries to parse a {@link Success} variant from the given
         * inbound stanza.
         *
         * @apiNote
         * The caller normally goes through
         * {@link IqResetGroupInviteCodeResponse#of(Node, Node)}; this
         * factory is exposed so callers can short-circuit when they
         * already know the wire shape is a success.
         *
         * @implNote
         * This implementation first runs
         * {@link SmaxIqResultResponseMixin#validate(Node, Node)} to
         * confirm the envelope is an IQ {@code result} matching the
         * request id, then mirrors WA Web's
         * {@code queryGroupInviteCodeParser}: reads the {@code <invite>}
         * child and extracts its {@code code} attribute. Missing
         * either yields {@link Optional#empty()}, matching the
         * fall-through behaviour the caller expects when the stanza
         * is actually an error envelope.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match the success schema
         */
        @WhatsAppWebExport(moduleName = "WAWebGroupInviteJob",
                exports = "resetGroupInviteCode",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var inviteChild = node.getChild("invite").orElse(null);
            if (inviteChild == null) {
                return Optional.empty();
            }
            var code = inviteChild.getAttributeAsString("code").orElse(null);
            if (code == null) {
                return Optional.empty();
            }
            return Optional.of(new Success(code));
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
            return Objects.equals(this.code, that.code);
        }

        @Override
        public int hashCode() {
            return Objects.hash(code);
        }

        @Override
        public String toString() {
            return "IqResetGroupInviteCodeResponse.Success[code=" + code + ']';
        }
    }

    /**
     * The {@code ClientError} reply variant.
     *
     * @apiNote
     * Surfaces caller-side rejections of the rotate request:
     * typically {@code 403} when the caller is not an admin of the
     * target group, {@code 404} when the group does not exist, or
     * {@code 400} on a malformed stanza. Retrying the same request
     * is pointless; the caller must surface the failure to the user.
     *
     * @implNote
     * This implementation corresponds to the {@code 4xx} branch of
     * WA Web's {@code ServerStatusCodeError} promise rejection inside
     * {@code resetGroupInviteCode}; the {@code <error>} envelope's
     * {@code code} and {@code text} attributes feed
     * {@link #errorCode()} and {@link #errorText()}.
     */
    @WhatsAppWebModule(moduleName = "WAWebGroupInviteJob")
    final class ClientError implements IqResetGroupInviteCodeResponse {
        /**
         * The numeric server-side error code.
         */
        private final int errorCode;

        /**
         * The human-readable error text when the relay supplied one,
         * otherwise {@code null}.
         */
        private final String errorText;

        /**
         * Constructs a {@link ClientError} reply.
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
         * @return an {@link Optional} carrying the error text, or empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant from the given
         * inbound stanza.
         *
         * @apiNote
         * The caller normally goes through
         * {@link IqResetGroupInviteCodeResponse#of(Node, Node)}; this
         * factory is exposed so callers can short-circuit when they
         * already know the wire shape is a client error.
         *
         * @implNote
         * This implementation delegates to
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)},
         * which validates the IQ envelope's {@code type="error"}
         * attribute and the {@code <error>} child's {@code code} in
         * the {@code 4xx} range before extracting code/text. WA Web
         * raises {@code ServerStatusCodeError} from the same wire
         * shape; Cobalt yields a typed variant instead.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match the client-error schema
         */
        @WhatsAppWebExport(moduleName = "WAWebGroupInviteJob",
                exports = "resetGroupInviteCode", adaptation = WhatsAppAdaptation.ADAPTED)
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
            return "IqResetGroupInviteCodeResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant.
     *
     * @apiNote
     * Surfaces transient {@code 5xx} relay failures while rotating
     * the invite code; the request may be retried after a backoff.
     *
     * @implNote
     * This implementation corresponds to the {@code 5xx} branch of
     * WA Web's {@code ServerStatusCodeError} promise rejection inside
     * {@code resetGroupInviteCode}; the {@code <error>} envelope's
     * {@code code} and {@code text} attributes feed
     * {@link #errorCode()} and {@link #errorText()}.
     */
    @WhatsAppWebModule(moduleName = "WAWebGroupInviteJob")
    final class ServerError implements IqResetGroupInviteCodeResponse {
        /**
         * The numeric server-side error code.
         */
        private final int errorCode;

        /**
         * The human-readable error text when the relay supplied one,
         * otherwise {@code null}.
         */
        private final String errorText;

        /**
         * Constructs a {@link ServerError} reply.
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
         * @return an {@link Optional} carrying the error text, or empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant from the given
         * inbound stanza.
         *
         * @apiNote
         * The caller normally goes through
         * {@link IqResetGroupInviteCodeResponse#of(Node, Node)}; this
         * factory is exposed so callers can short-circuit when they
         * already know the wire shape is a server error.
         *
         * @implNote
         * This implementation delegates to
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)},
         * which validates the IQ envelope's {@code type="error"}
         * attribute and the {@code <error>} child's {@code code} in
         * the {@code 5xx} range before extracting code/text.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match the server-error schema
         */
        @WhatsAppWebExport(moduleName = "WAWebGroupInviteJob",
                exports = "resetGroupInviteCode", adaptation = WhatsAppAdaptation.ADAPTED)
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
            return "IqResetGroupInviteCodeResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
