package com.github.auties00.cobalt.node.smax.groups;

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
 * The sealed reply family for a {@link SmaxGroupsPromoteDemoteRequest}.
 *
 * @apiNote The four variants mirror the WA Web RPC dispatcher in {@code WASmaxGroupsPromoteDemoteRPC}.
 * Promote-side outcomes and demote-side outcomes arrive in separate envelopes ({@link SuccessPromote} carries the
 * {@code <promote>} child, {@link SuccessDemote} carries the {@code <demote>} child), even when the original
 * request batched both sub-actions; the relay emits whichever envelope corresponds to the sub-action it processed.
 */
public sealed interface SmaxGroupsPromoteDemoteResponse extends SmaxOperation.Response
        permits SmaxGroupsPromoteDemoteResponse.SuccessPromote, SmaxGroupsPromoteDemoteResponse.SuccessDemote,
        SmaxGroupsPromoteDemoteResponse.ClientError, SmaxGroupsPromoteDemoteResponse.ServerError {

    /**
     * Dispatches the inbound IQ across each {@link SmaxGroupsPromoteDemoteResponse} variant in priority order and
     * returns the first that parses cleanly.
     *
     * @apiNote The priority order matches the WA Web RPC dispatcher in {@code WASmaxGroupsPromoteDemoteRPC}:
     * {@link SuccessPromote} is tried before {@link SuccessDemote}.
     *
     * @implNote The empty {@link Optional} surfaces when the stanza shape matches none of the documented variants;
     * WA Web throws {@code SmaxParsingFailure} on the same path, but Cobalt defers the decision to the caller so
     * it can apply its own error-handling policy.
     *
     * @param node    the inbound IQ stanza
     * @param request the original outbound {@link SmaxGroupsPromoteDemoteRequest} stanza, used to validate echoed
     *                identifiers
     * @return an {@link Optional} carrying the parsed variant, or empty when no variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxGroupsPromoteDemoteRPC",
            exports = "sendPromoteDemoteRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxGroupsPromoteDemoteResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var promote = SuccessPromote.of(node, request);
        if (promote.isPresent()) {
            return promote;
        }
        var demote = SuccessDemote.of(node, request);
        if (demote.isPresent()) {
            return demote;
        }
        var clientError = ClientError.of(node, request);
        if (clientError.isPresent()) {
            return clientError;
        }
        return ServerError.of(node, request);
    }

    /**
     * The reply variant carrying the per-participant outcomes for the {@code <promote>} sub-action.
     *
     * @apiNote The IQ envelope succeeds even when every candidate is rejected at the participant-policy level,
     * so callers must walk {@link #participants()} to detect partial or total rejection. Each row exposes an
     * optional {@code type="admin"} marker confirming the promotion plus an optional rejection code lifted from
     * the WA Web {@code ENUM_404_419} projection.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsPromoteDemoteResponseSuccessPromote")
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGroupAddressingModeMixin")
    final class SuccessPromote implements SmaxGroupsPromoteDemoteResponse {
        /**
         * The optional {@code addressing_mode} attribute echoed on the IQ envelope.
         */
        private final String addressingMode;

        /**
         * The per-participant promotion outcome rows projected from the {@code <promote>} child.
         */
        private final List<PromoteParticipantResult> participants;

        /**
         * Constructs a {@link SuccessPromote}.
         *
         * @param addressingMode the optional addressing-mode echo ({@code "lid"} or {@code "pn"}); may be
         *                       {@code null}
         * @param participants   the per-participant outcomes; defensively copied, {@code null} treated as empty
         */
        public SuccessPromote(String addressingMode, List<PromoteParticipantResult> participants) {
            this.addressingMode = addressingMode;
            this.participants = List.copyOf(Objects.requireNonNullElse(participants, List.of()));
        }

        /**
         * Returns the optional {@code addressing_mode} echo.
         *
         * @apiNote The relay flips between {@code "lid"} and {@code "pn"} according to the group's addressing
         * mode; the field is omitted on legacy groups.
         *
         * @return an {@link Optional} carrying the mode, or empty when the relay omitted it
         */
        public Optional<String> addressingMode() {
            return Optional.ofNullable(addressingMode);
        }

        /**
         * Returns the per-participant promotion outcomes.
         *
         * @return an unmodifiable list of outcome rows; never {@code null}
         */
        public List<PromoteParticipantResult> participants() {
            return participants;
        }

        /**
         * Tries to parse a {@link SuccessPromote} variant from {@code node}.
         *
         * @apiNote Matches the WA Web parser {@code parsePromoteDemoteResponseSuccessPromote}: the IQ must be a
         * valid {@code type="result"} echo of the request, must carry a {@code <promote>} child, and every
         * {@code <participant>} grand-child must satisfy {@link PromoteParticipantResult#of(Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsPromoteDemoteResponseSuccessPromote",
                exports = "parsePromoteDemoteResponseSuccessPromote",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<SuccessPromote> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var promote = node.getChild("promote").orElse(null);
            if (promote == null) {
                return Optional.empty();
            }
            var addressingMode = node.getAttributeAsString("addressing_mode").orElse(null);
            var participants = new ArrayList<PromoteParticipantResult>();
            for (var participantNode : promote.getChildren("participant")) {
                var participant = PromoteParticipantResult.of(participantNode).orElse(null);
                if (participant == null) {
                    return Optional.empty();
                }
                participants.add(participant);
            }
            return Optional.of(new SuccessPromote(addressingMode, participants));
        }

        /**
         * Compares this success to {@code obj} for value equality across both fields.
         *
         * @param obj the other object
         * @return {@code true} when {@code obj} is a {@link SuccessPromote} with identical fields
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (SuccessPromote) obj;
            return Objects.equals(this.addressingMode, that.addressingMode)
                    && Objects.equals(this.participants, that.participants);
        }

        /**
         * Returns a hash composed of both fields.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(addressingMode, participants);
        }

        /**
         * Returns a debug string carrying both fields.
         *
         * @return the debug representation
         */
        @Override
        public String toString() {
            return "SmaxGroupsPromoteDemoteResponse.SuccessPromote[addressingMode=" + addressingMode
                    + ", participants=" + participants + ']';
        }

        /**
         * The per-participant outcome row produced by the relay for a single promotion candidate.
         *
         * @apiNote Each row exposes {@link #jid()} (always present), {@link #type()} (set to {@code "admin"} on
         * confirmed promotions), {@link #errorCode()} (lifted from the WA Web {@code ENUM_404_419} projection on
         * rejections), and the optional {@link #phoneNumber()} / {@link #username()} echoes.
         */
        public static final class PromoteParticipantResult {
            /**
             * The participant {@link Jid}, always present on every outcome row.
             */
            private final Jid jid;

            /**
             * The optional {@code type} marker echoed by the relay.
             */
            private final String type;

            /**
             * The optional rejection-error code lifted from the {@code error} attribute.
             */
            private final Integer errorCode;

            /**
             * The optional {@code phone_number} attribute echoed by the relay.
             */
            private final String phoneNumber;

            /**
             * The optional {@code username} attribute echoed by the relay.
             */
            private final String username;

            /**
             * Constructs a {@link PromoteParticipantResult} row.
             *
             * @param jid         the participant {@link Jid}
             * @param type        the optional {@code type} marker; may be {@code null}
             * @param errorCode   the optional rejection-error code; may be {@code null}
             * @param phoneNumber the optional {@code phone_number} echo; may be {@code null}
             * @param username    the optional {@code username} echo; may be {@code null}
             * @throws NullPointerException if {@code jid} is {@code null}
             */
            public PromoteParticipantResult(Jid jid, String type, Integer errorCode,
                                            String phoneNumber, String username) {
                this.jid = Objects.requireNonNull(jid, "jid cannot be null");
                this.type = type;
                this.errorCode = errorCode;
                this.phoneNumber = phoneNumber;
                this.username = username;
            }

            /**
             * Returns the participant {@link Jid}.
             *
             * @return the {@link Jid}; never {@code null}
             */
            public Jid jid() {
                return jid;
            }

            /**
             * Returns the optional {@code type} marker.
             *
             * @apiNote Present and set to {@code "admin"} on confirmed promotions; empty on rejected entries.
             *
             * @return an {@link Optional} carrying the marker, or empty
             */
            public Optional<String> type() {
                return Optional.ofNullable(type);
            }

            /**
             * Returns the optional rejection-error code.
             *
             * @apiNote The code is one of the values projected by WA Web's {@code ENUM_404_419}; empty when the
             * promotion succeeded.
             *
             * @return an {@link Optional} carrying the error code, or empty
             */
            public Optional<Integer> errorCode() {
                return Optional.ofNullable(errorCode);
            }

            /**
             * Returns the optional {@code phone_number} echo.
             *
             * @return an {@link Optional} carrying the phone number, or empty when the relay omitted it
             */
            public Optional<String> phoneNumber() {
                return Optional.ofNullable(phoneNumber);
            }

            /**
             * Returns the optional {@code username} echo.
             *
             * @return an {@link Optional} carrying the username, or empty when the relay omitted it
             */
            public Optional<String> username() {
                return Optional.ofNullable(username);
            }

            /**
             * Tries to parse a promotion outcome row from a single {@code <participant>} child.
             *
             * @apiNote Matches the WA Web parser
             * {@code parsePromoteDemoteResponseSuccessPromotePromoteParticipant}: the node must be a
             * {@code <participant>} carrying a {@code jid} attribute, with optional {@code type="admin"} and
             * {@code error} attributes lifted from the {@code ENUM_404_419} projection.
             *
             * @param node the {@code <participant>} child
             * @return an {@link Optional} carrying the parsed row, or empty when the node does not match
             */
            @WhatsAppWebExport(moduleName = "WASmaxInGroupsPromoteDemoteResponseSuccessPromote",
                    exports = "parsePromoteDemoteResponseSuccessPromotePromoteParticipant",
                    adaptation = WhatsAppAdaptation.ADAPTED)
            public static Optional<PromoteParticipantResult> of(Node node) {
                Objects.requireNonNull(node, "node cannot be null");
                if (!node.hasDescription("participant")) {
                    return Optional.empty();
                }
                var jid = node.getAttributeAsJid("jid").orElse(null);
                if (jid == null) {
                    return Optional.empty();
                }
                var type = node.getAttributeAsString("type").orElse(null);
                var error = node.getAttributeAsInt("error").orElse(-1);
                var errorCode = error < 0 ? null : Integer.valueOf(error);
                var phoneNumber = node.getAttributeAsString("phone_number").orElse(null);
                var username = node.getAttributeAsString("username").orElse(null);
                return Optional.of(new PromoteParticipantResult(jid, type, errorCode,
                        phoneNumber, username));
            }

            /**
             * Compares this row to {@code obj} for value equality across every field.
             *
             * @param obj the other object
             * @return {@code true} when {@code obj} is a {@link PromoteParticipantResult} with identical fields
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (PromoteParticipantResult) obj;
                return Objects.equals(this.jid, that.jid)
                        && Objects.equals(this.type, that.type)
                        && Objects.equals(this.errorCode, that.errorCode)
                        && Objects.equals(this.phoneNumber, that.phoneNumber)
                        && Objects.equals(this.username, that.username);
            }

            /**
             * Returns a hash composed of every field.
             *
             * @return the hash code
             */
            @Override
            public int hashCode() {
                return Objects.hash(jid, type, errorCode, phoneNumber, username);
            }

            /**
             * Returns a debug string carrying every field.
             *
             * @return the debug representation
             */
            @Override
            public String toString() {
                return "SmaxGroupsPromoteDemoteResponse.SuccessPromote.PromoteParticipantResult[jid=" + jid
                        + ", type=" + type
                        + ", errorCode=" + errorCode
                        + ", phoneNumber=" + phoneNumber
                        + ", username=" + username + ']';
            }
        }
    }

    /**
     * The reply variant carrying the per-participant outcomes for the {@code <demote>} sub-action.
     *
     * @apiNote The IQ envelope succeeds even when every candidate is rejected at the participant-policy level,
     * so callers must walk {@link #participants()} to detect partial or total rejection. Each row exposes an
     * optional rejection code lifted from the WA Web {@code ENUM_404_406} projection.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsPromoteDemoteResponseSuccessDemote")
    final class SuccessDemote implements SmaxGroupsPromoteDemoteResponse {
        /**
         * The optional {@code addressing_mode} attribute echoed on the IQ envelope.
         */
        private final String addressingMode;

        /**
         * The per-participant demotion outcome rows projected from the {@code <demote>} child.
         */
        private final List<DemoteParticipantResult> participants;

        /**
         * Constructs a {@link SuccessDemote}.
         *
         * @param addressingMode the optional addressing-mode echo ({@code "lid"} or {@code "pn"}); may be
         *                       {@code null}
         * @param participants   the per-participant outcomes; defensively copied, {@code null} treated as empty
         */
        public SuccessDemote(String addressingMode, List<DemoteParticipantResult> participants) {
            this.addressingMode = addressingMode;
            this.participants = List.copyOf(Objects.requireNonNullElse(participants, List.of()));
        }

        /**
         * Returns the optional {@code addressing_mode} echo.
         *
         * @apiNote The relay flips between {@code "lid"} and {@code "pn"} according to the group's addressing
         * mode; the field is omitted on legacy groups.
         *
         * @return an {@link Optional} carrying the mode, or empty when the relay omitted it
         */
        public Optional<String> addressingMode() {
            return Optional.ofNullable(addressingMode);
        }

        /**
         * Returns the per-participant demotion outcomes.
         *
         * @return an unmodifiable list of outcome rows; never {@code null}
         */
        public List<DemoteParticipantResult> participants() {
            return participants;
        }

        /**
         * Tries to parse a {@link SuccessDemote} variant from {@code node}.
         *
         * @apiNote Matches the WA Web parser {@code parsePromoteDemoteResponseSuccessDemote}: the IQ must be a
         * valid {@code type="result"} echo of the request, must carry a {@code <demote>} child, and every
         * {@code <participant>} grand-child must satisfy {@link DemoteParticipantResult#of(Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsPromoteDemoteResponseSuccessDemote",
                exports = "parsePromoteDemoteResponseSuccessDemote",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<SuccessDemote> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var demote = node.getChild("demote").orElse(null);
            if (demote == null) {
                return Optional.empty();
            }
            var addressingMode = node.getAttributeAsString("addressing_mode").orElse(null);
            var participants = new ArrayList<DemoteParticipantResult>();
            for (var participantNode : demote.getChildren("participant")) {
                var participant = DemoteParticipantResult.of(participantNode).orElse(null);
                if (participant == null) {
                    return Optional.empty();
                }
                participants.add(participant);
            }
            return Optional.of(new SuccessDemote(addressingMode, participants));
        }

        /**
         * Compares this success to {@code obj} for value equality across both fields.
         *
         * @param obj the other object
         * @return {@code true} when {@code obj} is a {@link SuccessDemote} with identical fields
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (SuccessDemote) obj;
            return Objects.equals(this.addressingMode, that.addressingMode)
                    && Objects.equals(this.participants, that.participants);
        }

        /**
         * Returns a hash composed of both fields.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(addressingMode, participants);
        }

        /**
         * Returns a debug string carrying both fields.
         *
         * @return the debug representation
         */
        @Override
        public String toString() {
            return "SmaxGroupsPromoteDemoteResponse.SuccessDemote[addressingMode=" + addressingMode
                    + ", participants=" + participants + ']';
        }

        /**
         * The per-participant outcome row produced by the relay for a single demotion candidate.
         *
         * @apiNote Each row exposes {@link #jid()} (always present), {@link #errorCode()} (lifted from the WA Web
         * {@code ENUM_404_406} projection on rejections), and the optional {@link #phoneNumber()} /
         * {@link #username()} echoes. Unlike the promotion row there is no {@code type} marker since a successful
         * demotion carries no positive label.
         */
        public static final class DemoteParticipantResult {
            /**
             * The participant {@link Jid}, always present on every outcome row.
             */
            private final Jid jid;

            /**
             * The optional rejection-error code lifted from the {@code error} attribute.
             */
            private final Integer errorCode;

            /**
             * The optional {@code phone_number} attribute echoed by the relay.
             */
            private final String phoneNumber;

            /**
             * The optional {@code username} attribute echoed by the relay.
             */
            private final String username;

            /**
             * Constructs a {@link DemoteParticipantResult} row.
             *
             * @param jid         the participant {@link Jid}
             * @param errorCode   the optional rejection-error code; may be {@code null}
             * @param phoneNumber the optional {@code phone_number} echo; may be {@code null}
             * @param username    the optional {@code username} echo; may be {@code null}
             * @throws NullPointerException if {@code jid} is {@code null}
             */
            public DemoteParticipantResult(Jid jid, Integer errorCode,
                                           String phoneNumber, String username) {
                this.jid = Objects.requireNonNull(jid, "jid cannot be null");
                this.errorCode = errorCode;
                this.phoneNumber = phoneNumber;
                this.username = username;
            }

            /**
             * Returns the participant {@link Jid}.
             *
             * @return the {@link Jid}; never {@code null}
             */
            public Jid jid() {
                return jid;
            }

            /**
             * Returns the optional rejection-error code.
             *
             * @apiNote The code is one of the values projected by WA Web's {@code ENUM_404_406}; empty when the
             * demotion succeeded.
             *
             * @return an {@link Optional} carrying the error code, or empty
             */
            public Optional<Integer> errorCode() {
                return Optional.ofNullable(errorCode);
            }

            /**
             * Returns the optional {@code phone_number} echo.
             *
             * @return an {@link Optional} carrying the phone number, or empty when the relay omitted it
             */
            public Optional<String> phoneNumber() {
                return Optional.ofNullable(phoneNumber);
            }

            /**
             * Returns the optional {@code username} echo.
             *
             * @return an {@link Optional} carrying the username, or empty when the relay omitted it
             */
            public Optional<String> username() {
                return Optional.ofNullable(username);
            }

            /**
             * Tries to parse a demotion outcome row from a single {@code <participant>} child.
             *
             * @apiNote Matches the WA Web parser
             * {@code parsePromoteDemoteResponseSuccessDemoteDemoteParticipant}: the node must be a
             * {@code <participant>} carrying a {@code jid} attribute, with an optional {@code error} attribute
             * lifted from the {@code ENUM_404_406} projection.
             *
             * @param node the {@code <participant>} child
             * @return an {@link Optional} carrying the parsed row, or empty when the node does not match
             */
            @WhatsAppWebExport(moduleName = "WASmaxInGroupsPromoteDemoteResponseSuccessDemote",
                    exports = "parsePromoteDemoteResponseSuccessDemoteDemoteParticipant",
                    adaptation = WhatsAppAdaptation.ADAPTED)
            public static Optional<DemoteParticipantResult> of(Node node) {
                Objects.requireNonNull(node, "node cannot be null");
                if (!node.hasDescription("participant")) {
                    return Optional.empty();
                }
                var jid = node.getAttributeAsJid("jid").orElse(null);
                if (jid == null) {
                    return Optional.empty();
                }
                var error = node.getAttributeAsInt("error").orElse(-1);
                var errorCode = error < 0 ? null : Integer.valueOf(error);
                var phoneNumber = node.getAttributeAsString("phone_number").orElse(null);
                var username = node.getAttributeAsString("username").orElse(null);
                return Optional.of(new DemoteParticipantResult(jid, errorCode, phoneNumber, username));
            }

            /**
             * Compares this row to {@code obj} for value equality across every field.
             *
             * @param obj the other object
             * @return {@code true} when {@code obj} is a {@link DemoteParticipantResult} with identical fields
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (DemoteParticipantResult) obj;
                return Objects.equals(this.jid, that.jid)
                        && Objects.equals(this.errorCode, that.errorCode)
                        && Objects.equals(this.phoneNumber, that.phoneNumber)
                        && Objects.equals(this.username, that.username);
            }

            /**
             * Returns a hash composed of every field.
             *
             * @return the hash code
             */
            @Override
            public int hashCode() {
                return Objects.hash(jid, errorCode, phoneNumber, username);
            }

            /**
             * Returns a debug string carrying every field.
             *
             * @return the debug representation
             */
            @Override
            public String toString() {
                return "SmaxGroupsPromoteDemoteResponse.SuccessDemote.DemoteParticipantResult[jid=" + jid
                        + ", errorCode=" + errorCode
                        + ", phoneNumber=" + phoneNumber
                        + ", username=" + username + ']';
            }
        }
    }

    /**
     * The reply variant emitted when the relay rejected the request envelope as malformed, unauthorised, or
     * referencing a non-existent group.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsPromoteDemoteResponseClientError")
    final class ClientError implements SmaxGroupsPromoteDemoteResponse {
        /**
         * The numeric error code echoed by the relay.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text echoed by the relay.
         */
        private final String errorText;

        /**
         * Constructs a {@link ClientError} from raw error attributes.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional error text; may be {@code null}
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code echoed by the relay.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text echoed by the relay.
         *
         * @return an {@link Optional} carrying the error text, or empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant from {@code node}.
         *
         * @apiNote Delegates to {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)} which validates the
         * shared {@code <iq type="error"><error code="..." text="..."/></iq>} envelope.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsPromoteDemoteResponseClientError",
                exports = "parsePromoteDemoteResponseClientError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ClientError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseClientError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ClientError(envelope.code(), envelope.text()));
        }

        /**
         * Compares this error to {@code obj} for value equality across both fields.
         *
         * @param obj the other object
         * @return {@code true} when {@code obj} is a {@link ClientError} with identical fields
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
         * Returns a hash composed of both fields.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * Returns a debug string carrying both fields.
         *
         * @return the debug representation
         */
        @Override
        public String toString() {
            return "SmaxGroupsPromoteDemoteResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The reply variant emitted on transient relay-side failure.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsPromoteDemoteResponseServerError")
    final class ServerError implements SmaxGroupsPromoteDemoteResponse {
        /**
         * The numeric error code echoed by the relay.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text echoed by the relay.
         */
        private final String errorText;

        /**
         * Constructs a {@link ServerError} from raw error attributes.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional error text; may be {@code null}
         */
        public ServerError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code echoed by the relay.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text echoed by the relay.
         *
         * @return an {@link Optional} carrying the error text, or empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant from {@code node}.
         *
         * @apiNote Delegates to {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)} which validates the
         * shared {@code <iq type="error"><error code="..." text="..."/></iq>} envelope.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsPromoteDemoteResponseServerError",
                exports = "parsePromoteDemoteResponseServerError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ServerError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseServerError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ServerError(envelope.code(), envelope.text()));
        }

        /**
         * Compares this error to {@code obj} for value equality across both fields.
         *
         * @param obj the other object
         * @return {@code true} when {@code obj} is a {@link ServerError} with identical fields
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
         * Returns a hash composed of both fields.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * Returns a debug string carrying both fields.
         *
         * @return the debug representation
         */
        @Override
        public String toString() {
            return "SmaxGroupsPromoteDemoteResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
