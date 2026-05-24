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
 * The sealed reply family for a {@link SmaxGroupsGetLinkedGroupsParticipantsRequest}.
 *
 * @apiNote The three variants mirror the WA Web RPC dispatcher's {@code Success}/{@code ClientError}/{@code ServerError}
 * cases: {@link Success} carries the union of participants across the community's sub-groups projected through the
 * {@code ParticipantWithJidMixin} subtree, the two error variants surface the relay's reason codes. The
 * {@code WAWebGroupGetCommunityParticipantsJob.getCommunityParticipants} caller in WA Web uses the same dispatch shape.
 */
public sealed interface SmaxGroupsGetLinkedGroupsParticipantsResponse extends SmaxOperation.Response
        permits SmaxGroupsGetLinkedGroupsParticipantsResponse.Success, SmaxGroupsGetLinkedGroupsParticipantsResponse.ClientError, SmaxGroupsGetLinkedGroupsParticipantsResponse.ServerError {

    /**
     * Dispatches the inbound IQ across each {@link SmaxGroupsGetLinkedGroupsParticipantsResponse} variant in priority
     * order and returns the first that parses cleanly.
     *
     * @apiNote The priority order matches the WA Web RPC dispatcher in
     * {@code WASmaxGroupsGetLinkedGroupsParticipantsRPC}: {@link Success} first, then {@link ClientError}, then
     * {@link ServerError}.
     *
     * @implNote The empty {@link Optional} surfaces when the stanza shape matches none of the three documented
     * variants; WA Web throws {@code SmaxParsingFailure} on the same path, but Cobalt defers the decision to the
     * caller so it can apply its own error-handling policy.
     *
     * @param node    the inbound IQ stanza
     * @param request the original outbound request
     * @return an {@link Optional} carrying the parsed variant, or empty when no variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxGroupsGetLinkedGroupsParticipantsRPC",
            exports = "sendGetLinkedGroupsParticipantsRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxGroupsGetLinkedGroupsParticipantsResponse> of(Node node, Node request) {
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
     * The reply variant emitted when the relay returned the participant union for the addressed community.
     *
     * @apiNote Surfaces as the {@code GetLinkedGroupsParticipantsResponseSuccess} case in
     * {@code WAWebGroupGetCommunityParticipantsJob}; the community member-list UI binds the {@link Participant#jid()}
     * of each entry into a {@code WAWebWid} via {@code WAWebWidFactory.createWid}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetLinkedGroupsParticipantsResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsParticipantWithJidMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsPhoneNumberMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGroupAddressingModeMixin")
    final class Success implements SmaxGroupsGetLinkedGroupsParticipantsResponse {
        /**
         * The deduplicated participant list across the addressed community's sub-groups.
         */
        private final List<Participant> participants;

        /**
         * Constructs a {@link Success} reply.
         *
         * @param participants the participant list; never {@code null}
         * @throws NullPointerException if {@code participants} is {@code null}
         */
        public Success(List<Participant> participants) {
            Objects.requireNonNull(participants, "participants cannot be null");
            this.participants = List.copyOf(participants);
        }

        /**
         * Returns the participant list.
         *
         * @return an unmodifiable list of {@link Participant}; never {@code null}
         */
        public List<Participant> participants() {
            return participants;
        }

        /**
         * Tries to parse a {@link Success} variant from {@code node}.
         *
         * @apiNote Delegates to {@link SmaxIqResultResponseMixin#validate(Node, Node)} for envelope validation, then
         * matches the {@code <linked_groups_participants>} wrapper holding one or more {@code <participant/>} entries.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsGetLinkedGroupsParticipantsResponseSuccess",
                exports = "parseGetLinkedGroupsParticipantsResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var wrapper = node.getChild("linked_groups_participants").orElse(null);
            if (wrapper == null) {
                return Optional.empty();
            }
            var participantNodes = wrapper.getChildren("participant");
            var participants = new ArrayList<Participant>(participantNodes.size());
            for (var participantNode : participantNodes) {
                var participant = Participant.of(participantNode).orElse(null);
                if (participant == null) {
                    return Optional.empty();
                }
                participants.add(participant);
            }
            return Optional.of(new Success(participants));
        }

        /**
         * Compares this success to {@code obj} for value equality across every field.
         *
         * @param obj the other object
         * @return {@code true} when {@code obj} is a {@link Success} with identical fields
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
            return Objects.equals(this.participants, that.participants);
        }

        /**
         * Returns a hash composed of every field.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(participants);
        }

        /**
         * Returns a debug string carrying every field.
         *
         * @return the debug representation
         */
        @Override
        public String toString() {
            return "SmaxGroupsGetLinkedGroupsParticipantsResponse.Success[participants="
                    + participants + ']';
        }
    }

    /**
     * Per-participant projection carrying the addressing JID plus an optional resolved phone-number JID surfaced by
     * the relay's PN-LID mapping.
     *
     * @apiNote Mirrors the {@code ParticipantWithJidMixin} shape; the phone-number JID is non-null only when the
     * relay can map a LID participant to its PN counterpart.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsParticipantWithJidMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsPhoneNumberMixin")
    final class Participant {
        /**
         * The participant's primary addressing {@link Jid}.
         */
        private final Jid jid;

        /**
         * The optional resolved phone-number {@link Jid} surfaced when the relay supplied the PN-LID mapping.
         */
        private final Jid phoneNumber;

        /**
         * Constructs a {@link Participant} projection.
         *
         * @param jid         the addressing {@link Jid}; never {@code null}
         * @param phoneNumber the optional phone-number {@link Jid}; may be {@code null}
         * @throws NullPointerException if {@code jid} is {@code null}
         */
        public Participant(Jid jid, Jid phoneNumber) {
            this.jid = Objects.requireNonNull(jid, "jid cannot be null");
            this.phoneNumber = phoneNumber;
        }

        /**
         * Returns the participant's primary addressing {@link Jid}.
         *
         * @return the addressing {@link Jid}; never {@code null}
         */
        public Jid jid() {
            return jid;
        }

        /**
         * Returns the participant's resolved phone-number {@link Jid} when supplied by the relay.
         *
         * @return an {@link Optional} carrying the phone-number {@link Jid}, or empty when the relay omitted it
         */
        public Optional<Jid> phoneNumber() {
            return Optional.ofNullable(phoneNumber);
        }

        /**
         * Tries to parse a {@link Participant} from the given {@code <participant/>} child.
         *
         * @apiNote Matches when the child carries the {@code jid} attribute; the {@code phone_number} attribute is
         * optional.
         *
         * @param node the {@code <participant/>} child node
         * @return an {@link Optional} carrying the parsed participant, or empty when the child does not match
         */
        public static Optional<Participant> of(Node node) {
            Objects.requireNonNull(node, "node cannot be null");
            if (!node.hasDescription("participant")) {
                return Optional.empty();
            }
            var jid = node.getAttributeAsJid("jid").orElse(null);
            if (jid == null) {
                return Optional.empty();
            }
            var phoneNumber = node.getAttributeAsJid("phone_number").orElse(null);
            return Optional.of(new Participant(jid, phoneNumber));
        }

        /**
         * Compares this participant to {@code obj} for value equality across both fields.
         *
         * @param obj the other object
         * @return {@code true} when {@code obj} is a {@link Participant} with identical fields
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Participant) obj;
            return Objects.equals(this.jid, that.jid)
                    && Objects.equals(this.phoneNumber, that.phoneNumber);
        }

        /**
         * Returns a hash composed of both fields.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(jid, phoneNumber);
        }

        /**
         * Returns a debug string carrying both fields.
         *
         * @return the debug representation
         */
        @Override
        public String toString() {
            return "SmaxGroupsGetLinkedGroupsParticipantsResponse.Participant[jid=" + jid
                    + ", phoneNumber=" + phoneNumber + ']';
        }
    }

    /**
     * The reply variant emitted when the relay rejected the request as malformed, unauthorised, or referencing a
     * non-community group.
     *
     * @apiNote Surfaces as the {@code GetLinkedGroupsParticipantsResponseClientError} case in
     * {@code WAWebGroupGetCommunityParticipantsJob}, which logs the {@link #errorCode()} as the HTTP-style status
     * passed back to the community member-list UI.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetLinkedGroupsParticipantsResponseClientError")
    final class ClientError implements SmaxGroupsGetLinkedGroupsParticipantsResponse {
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
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsGetLinkedGroupsParticipantsResponseClientError",
                exports = "parseGetLinkedGroupsParticipantsResponseClientError",
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
            return "SmaxGroupsGetLinkedGroupsParticipantsResponse.ClientError[errorCode="
                    + errorCode + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The reply variant emitted on transient relay-side failure.
     *
     * @apiNote Surfaces as the {@code GetLinkedGroupsParticipantsResponseServerError} case in
     * {@code WAWebGroupGetCommunityParticipantsJob}, where it is logged at the same severity as {@link ClientError}
     * but typically signals retry-eligible relay outages rather than caller error.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetLinkedGroupsParticipantsResponseServerError")
    final class ServerError implements SmaxGroupsGetLinkedGroupsParticipantsResponse {
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
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsGetLinkedGroupsParticipantsResponseServerError",
                exports = "parseGetLinkedGroupsParticipantsResponseServerError",
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
            return "SmaxGroupsGetLinkedGroupsParticipantsResponse.ServerError[errorCode="
                    + errorCode + ", errorText=" + errorText + ']';
        }
    }
}
