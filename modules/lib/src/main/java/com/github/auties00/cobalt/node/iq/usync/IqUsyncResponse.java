package com.github.auties00.cobalt.node.iq.usync;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.iq.IqOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed family of inbound reply variants produced by the relay in response to an
 * {@link IqUsyncRequest}.
 *
 * @apiNote
 * Switch on the returned variant to discriminate the relay outcome: a {@link Success}
 * carries the per-user attribute projections plus the per-protocol envelopes (which
 * carry per-protocol errors and refresh-TTL hints), a {@link ClientError} surfaces a
 * full-query rejection (malformed envelope or unauthorised caller), and a
 * {@link ServerError} surfaces a transient relay failure.
 *
 * @implNote
 * This implementation mirrors WA Web's {@code usyncParser} verbatim. The per-protocol
 * envelope grouping (one {@link ProtocolEnvelope} per requested protocol tag,
 * carrying either an error or a refresh hint) plus the per-user list grouping (one
 * {@link UserResult} per resolved user) is identical to the WA Web split, except
 * Cobalt exposes it as typed records rather than a free-form object map.
 */
public sealed interface IqUsyncResponse extends IqOperation.Response
        permits IqUsyncResponse.Success, IqUsyncResponse.ClientError, IqUsyncResponse.ServerError {

    /**
     * Parses the inbound stanza into the first matching {@link IqUsyncResponse}
     * variant.
     *
     * @apiNote
     * Try this once per inbound reply; the priority ordering (success, then
     * client-error, then server-error) matches the wire shape and never returns
     * ambiguous matches.
     *
     * @implNote
     * This implementation calls each variant's {@code of(node, request)} in turn
     * and returns the first present result.
     *
     * @param node    the inbound IQ stanza received from the relay; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or empty when no
     *         documented variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends IqUsyncResponse> of(Node node, Node request) {
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
     * Success variant. The relay returned the per-protocol envelopes plus the per-user
     * attribute projections.
     *
     * @apiNote
     * Inspect {@link #protocolEnvelopes()} for per-protocol errors and refresh hints
     * (WA Web stores the backoff hints in its {@code WAWebUsyncBackoff} module) and
     * {@link #userResults()} for the per-user projection rows.
     */
    @WhatsAppWebModule(moduleName = "WAWebUsync")
    final class Success implements IqUsyncResponse {
        /**
         * Holds the per-protocol envelopes returned in the {@code <result>}
         * grandchild, one entry per protocol tag the request asked for.
         */
        private final List<ProtocolEnvelope> protocolEnvelopes;

        /**
         * Holds the per-user results returned in the {@code <list>} grandchild,
         * one entry per user the relay resolved.
         */
        private final List<UserResult> userResults;

        /**
         * Constructs a successful reply bound to the given per-protocol envelopes
         * and per-user results.
         *
         * @param protocolEnvelopes the per-protocol envelopes; never {@code null}
         * @param userResults       the per-user results; never {@code null}
         * @throws NullPointerException if any reference argument is {@code null}
         */
        public Success(List<ProtocolEnvelope> protocolEnvelopes, List<UserResult> userResults) {
            Objects.requireNonNull(protocolEnvelopes, "protocolEnvelopes cannot be null");
            Objects.requireNonNull(userResults, "userResults cannot be null");
            this.protocolEnvelopes = List.copyOf(protocolEnvelopes);
            this.userResults = List.copyOf(userResults);
        }

        /**
         * Returns the unmodifiable list of per-protocol envelopes.
         *
         * @return the envelopes; never {@code null}
         */
        public List<ProtocolEnvelope> protocolEnvelopes() {
            return protocolEnvelopes;
        }

        /**
         * Returns the unmodifiable list of per-user results.
         *
         * @return the results; never {@code null}
         */
        public List<UserResult> userResults() {
            return userResults;
        }

        /**
         * Parses the inbound stanza into a {@link Success} variant when it matches
         * the success schema.
         *
         * @apiNote
         * Returns empty when the SMAX result-envelope check fails or when the
         * {@code <usync>} child is absent.
         *
         * @implNote
         * This implementation accepts a missing {@code <result>} or {@code <list>}
         * grandchild by returning an empty list in the corresponding slot, matching
         * WA Web's {@code usyncParser} which treats both as zero-iteration loops.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when
         *         the stanza does not match the success schema
         */
        @WhatsAppWebExport(moduleName = "WAWebUsync",
                exports = "USyncQuery", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var usyncChild = node.getChild("usync").orElse(null);
            if (usyncChild == null) {
                return Optional.empty();
            }
            var resultChild = usyncChild.getChild("result").orElse(null);
            var listChild = usyncChild.getChild("list").orElse(null);
            var envelopes = new ArrayList<ProtocolEnvelope>();
            if (resultChild != null) {
                for (var protocolNode : resultChild.children()) {
                    envelopes.add(ProtocolEnvelope.of(protocolNode));
                }
            }
            var users = new ArrayList<UserResult>();
            if (listChild != null) {
                for (var userNode : listChild.getChildren("user")) {
                    var userJid = userNode.getAttributeAsJid("jid").orElse(null);
                    var pnJid = userNode.getAttributeAsJid("pn_jid").orElse(null);
                    var payloads = new ArrayList<Node>(userNode.children());
                    users.add(new UserResult(userJid, pnJid, payloads));
                }
            }
            return Optional.of(new Success(envelopes, users));
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
            return Objects.equals(this.protocolEnvelopes, that.protocolEnvelopes)
                    && Objects.equals(this.userResults, that.userResults);
        }

        @Override
        public int hashCode() {
            return Objects.hash(protocolEnvelopes, userResults);
        }

        @Override
        public String toString() {
            return "IqUsyncResponse.Success[protocolEnvelopes=" + protocolEnvelopes
                    + ", userResults=" + userResults + ']';
        }
    }

    /**
     * Per-protocol envelope projected from one grandchild of the inbound
     * {@code <result>} child.
     *
     * @apiNote
     * Each envelope carries either a per-protocol error code (with optional text and
     * backoff hint) or a per-protocol refresh hint, never both. WA Web feeds the
     * backoff hints into its {@code WAWebUsyncBackoff} module so subsequent queries
     * skip the failing protocol until the backoff elapses; the refresh hint sets
     * the per-protocol cache TTL.
     */
    @WhatsAppWebModule(moduleName = "WAWebUsync")
    final class ProtocolEnvelope {
        /**
         * Holds the protocol tag this envelope corresponds to (e.g.
         * {@code "devices"}, {@code "contact"}), routed from the grandchild's tag
         * name.
         */
        private final String protocol;

        /**
         * Holds the numeric per-protocol error code, or {@code null} when no
         * error.
         */
        private final Integer errorCode;

        /**
         * Holds the optional human-readable error text echoed by the relay.
         */
        private final String errorText;

        /**
         * Holds the optional per-protocol error backoff hint in seconds.
         */
        private final Integer errorBackoff;

        /**
         * Holds the optional per-protocol cache TTL hint in seconds.
         */
        private final Integer refresh;

        /**
         * Constructs a new per-protocol envelope.
         *
         * @apiNote
         * Construct with all error fields {@code null} and a present
         * {@code refresh} for a clean refresh-hint envelope; construct with a
         * present {@code errorCode} and {@code refresh = null} for an error
         * envelope.
         *
         * @param protocol     the protocol tag; never {@code null}
         * @param errorCode    the optional error code
         * @param errorText    the optional error text
         * @param errorBackoff the optional backoff hint in seconds
         * @param refresh      the optional refresh hint in seconds
         * @throws NullPointerException if {@code protocol} is {@code null}
         */
        public ProtocolEnvelope(String protocol, Integer errorCode, String errorText,
                                Integer errorBackoff, Integer refresh) {
            this.protocol = Objects.requireNonNull(protocol, "protocol cannot be null");
            this.errorCode = errorCode;
            this.errorText = errorText;
            this.errorBackoff = errorBackoff;
            this.refresh = refresh;
        }

        /**
         * Returns the protocol tag.
         *
         * @return the tag; never {@code null}
         */
        public String protocol() {
            return protocol;
        }

        /**
         * Returns the optional per-protocol error code.
         *
         * @return an {@link Optional} carrying the code, or empty when the
         *         envelope has no error
         */
        public Optional<Integer> errorCode() {
            return Optional.ofNullable(errorCode);
        }

        /**
         * Returns the optional per-protocol error text.
         *
         * @return an {@link Optional} carrying the text, or empty
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Returns the optional per-protocol backoff hint in seconds.
         *
         * @apiNote
         * Multiply by {@code 1000} to convert to milliseconds before feeding into
         * an exponential-backoff scheduler, matching WA Web's
         * {@code setProtocolBackoffMs(protocol, errorBackoff * 1e3)} call.
         *
         * @return an {@link Optional} carrying the seconds, or empty
         */
        public Optional<Integer> errorBackoff() {
            return Optional.ofNullable(errorBackoff);
        }

        /**
         * Returns the optional per-protocol refresh hint in seconds.
         *
         * @return an {@link Optional} carrying the seconds, or empty
         */
        public Optional<Integer> refresh() {
            return Optional.ofNullable(refresh);
        }

        /**
         * Parses an envelope from the given protocol grandchild.
         *
         * @apiNote
         * Drives {@link Success#of(Node, Node)}; never returns empty because the
         * minimum schema (a tag name) is always present.
         *
         * @implNote
         * This implementation prefers the {@code <error>} sub-envelope over the
         * {@code refresh} attribute, matching WA Web's branch ordering inside
         * {@code usyncParser}.
         *
         * @param protocolNode the protocol grandchild; never {@code null}
         * @return the parsed envelope
         */
        @WhatsAppWebExport(moduleName = "WAWebUsync",
                exports = "usyncParser", adaptation = WhatsAppAdaptation.ADAPTED)
        public static ProtocolEnvelope of(Node protocolNode) {
            Objects.requireNonNull(protocolNode, "protocolNode cannot be null");
            var protocol = protocolNode.description();
            var errorChild = protocolNode.getChild("error").orElse(null);
            Integer errorCode = null;
            String errorText = null;
            Integer errorBackoff = null;
            Integer refresh = null;
            if (errorChild != null) {
                var codeOpt = errorChild.getAttributeAsInt("code");
                errorCode = codeOpt.isPresent() ? codeOpt.getAsInt() : null;
                errorText = errorChild.getAttributeAsString("text").orElse(null);
                var backoffOpt = errorChild.getAttributeAsInt("backoff");
                errorBackoff = backoffOpt.isPresent() ? backoffOpt.getAsInt() : null;
            } else if (protocolNode.hasAttribute("refresh")) {
                refresh = protocolNode.getAttributeAsInt("refresh", 0);
            }
            return new ProtocolEnvelope(protocol, errorCode, errorText, errorBackoff, refresh);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (ProtocolEnvelope) obj;
            return Objects.equals(this.protocol, that.protocol)
                    && Objects.equals(this.errorCode, that.errorCode)
                    && Objects.equals(this.errorText, that.errorText)
                    && Objects.equals(this.errorBackoff, that.errorBackoff)
                    && Objects.equals(this.refresh, that.refresh);
        }

        @Override
        public int hashCode() {
            return Objects.hash(protocol, errorCode, errorText, errorBackoff, refresh);
        }

        @Override
        public String toString() {
            return "IqUsyncResponse.ProtocolEnvelope[protocol=" + protocol
                    + ", errorCode=" + errorCode
                    + ", errorText=" + errorText
                    + ", errorBackoff=" + errorBackoff
                    + ", refresh=" + refresh + ']';
        }
    }

    /**
     * Per-user result projected from one {@code <user>} grandchild of the inbound
     * {@code <list>} child.
     *
     * @apiNote
     * Carries the relay-resolved JIDs plus the raw per-protocol payload nodes
     * ({@code <devices/>}, {@code <contact/>}, {@code <picture/>}, etc.); the
     * caller routes each payload through the matching per-protocol parser based on
     * which protocols the original request asked for.
     */
    @WhatsAppWebModule(moduleName = "WAWebUsync")
    final class UserResult {
        /**
         * Holds the optional relay-resolved primary user JID.
         */
        private final Jid userJid;

        /**
         * Holds the optional relay-resolved phone-number JID.
         */
        private final Jid pnJid;

        /**
         * Holds the per-protocol payload nodes returned for this user.
         */
        private final List<Node> protocolPayloads;

        /**
         * Constructs a user result bound to the given JIDs and per-protocol
         * payloads.
         *
         * @param userJid          the optional primary JID
         * @param pnJid            the optional phone JID
         * @param protocolPayloads the per-protocol payload nodes; never
         *                         {@code null}
         * @throws NullPointerException if {@code protocolPayloads} is {@code null}
         */
        public UserResult(Jid userJid, Jid pnJid, List<Node> protocolPayloads) {
            this.userJid = userJid;
            this.pnJid = pnJid;
            Objects.requireNonNull(protocolPayloads, "protocolPayloads cannot be null");
            this.protocolPayloads = List.copyOf(protocolPayloads);
        }

        /**
         * Returns the optional primary user JID.
         *
         * @return an {@link Optional} carrying the JID, or empty
         */
        public Optional<Jid> userJid() {
            return Optional.ofNullable(userJid);
        }

        /**
         * Returns the optional phone-number JID.
         *
         * @return an {@link Optional} carrying the JID, or empty
         */
        public Optional<Jid> pnJid() {
            return Optional.ofNullable(pnJid);
        }

        /**
         * Returns the unmodifiable list of per-protocol payload nodes.
         *
         * @return the payloads; never {@code null}
         */
        public List<Node> protocolPayloads() {
            return protocolPayloads;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (UserResult) obj;
            return Objects.equals(this.userJid, that.userJid)
                    && Objects.equals(this.pnJid, that.pnJid)
                    && Objects.equals(this.protocolPayloads, that.protocolPayloads);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userJid, pnJid, protocolPayloads);
        }

        @Override
        public String toString() {
            return "IqUsyncResponse.UserResult[userJid=" + userJid
                    + ", pnJid=" + pnJid
                    + ", protocolPayloads=" + protocolPayloads + ']';
        }
    }

    /**
     * Client-error variant. The relay rejected the entire usync as malformed or
     * unauthorised.
     *
     * @apiNote
     * A full-query rejection is rare; per-protocol failures land in
     * {@link ProtocolEnvelope} instead. Look here for malformed envelopes
     * (e.g. missing {@code sid}, missing {@code mode}) or auth issues that affect
     * the entire query.
     */
    @WhatsAppWebModule(moduleName = "WAWebUsync")
    final class ClientError implements IqUsyncResponse {
        /**
         * Holds the numeric server-side error code.
         */
        private final int errorCode;

        /**
         * Holds the optional human-readable error text.
         */
        private final String errorText;

        /**
         * Constructs a client-error reply carrying the relay-echoed envelope.
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
         * @return an {@link Optional} carrying the error text, or empty when the
         *         relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Parses the inbound stanza into a {@link ClientError} variant when it
         * matches the standard SMAX client-error envelope.
         *
         * @apiNote
         * Returns empty when the envelope check fails; delegates entirely to
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty
         *         when the stanza does not match the client-error schema
         */
        @WhatsAppWebExport(moduleName = "WAWebUsync",
                exports = "USyncQuery", adaptation = WhatsAppAdaptation.ADAPTED)
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
            return "IqUsyncResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * Server-error variant. The relay encountered a transient internal failure while
     * processing the usync.
     *
     * @apiNote
     * Typically retryable after a short backoff; WA Web routes this through its
     * standard query-failure path that surfaces an empty result.
     */
    @WhatsAppWebModule(moduleName = "WAWebUsync")
    final class ServerError implements IqUsyncResponse {
        /**
         * Holds the numeric server-side error code.
         */
        private final int errorCode;

        /**
         * Holds the optional human-readable error text.
         */
        private final String errorText;

        /**
         * Constructs a server-error reply carrying the relay-echoed envelope.
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
         * @return an {@link Optional} carrying the error text, or empty when the
         *         relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Parses the inbound stanza into a {@link ServerError} variant when it
         * matches the standard SMAX server-error envelope.
         *
         * @apiNote
         * Returns empty when the envelope check fails; delegates entirely to
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty
         *         when the stanza does not match the server-error schema
         */
        @WhatsAppWebExport(moduleName = "WAWebUsync",
                exports = "USyncQuery", adaptation = WhatsAppAdaptation.ADAPTED)
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
            return "IqUsyncResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
