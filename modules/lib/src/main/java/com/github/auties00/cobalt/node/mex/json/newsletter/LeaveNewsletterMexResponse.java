package com.github.auties00.cobalt.node.mex.json.newsletter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.mex.MexOperation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses the MEX response of the leave-newsletter mutation built by
 * {@link LeaveNewsletterMexRequest}.
 *
 * @apiNote
 * Hands back the newsletter id echoed under
 * {@code xwa2_newsletter_leave_v2} together with a {@link State} marker;
 * WA Web's {@code WAWebMexLeaveNewsletterJob.mexLeaveNewsletter} consults
 * the {@code state.type} value to detect the {@code DELETED},
 * {@code NON_EXISTING} and {@code SUSPENDED} cases the relay reports as
 * soft failures and to throw the matching {@code ServerStatusCodeError}
 * (404 / 423). Cobalt surfaces both fields without throwing so callers can
 * decide their own recovery policy.
 */
@WhatsAppWebModule(moduleName = "WAWebMexLeaveNewsletterJob")
public final class LeaveNewsletterMexResponse implements MexOperation.Response.Json {
    /**
     * The newsletter Jid string echoed under {@code id}.
     */
    private final String id;

    /**
     * The lifecycle state marker echoed under {@code state}.
     */
    private final State state;

    /**
     * Constructs a response wrapping the echoed newsletter id and state.
     *
     * @apiNote
     * Reserved for the static parser; external callers obtain instances via
     * {@link #of(Node)}.
     *
     * @param id    the newsletter Jid string echoed by the relay
     * @param state the lifecycle state marker
     */
    private LeaveNewsletterMexResponse(String id, State state) {
        this.id = id;
        this.state = state;
    }

    /**
     * Parses the MEX response carried by the given IQ result node.
     *
     * @apiNote
     * Drains the {@code <result>} child's byte content into the JSON parser;
     * the returned {@link Optional} is empty when the result child is
     * missing or when the JSON envelope omits the expected
     * {@code data.xwa2_newsletter_leave_v2} root.
     *
     * @param node the IQ result node received from the relay
     * @return the parsed response, or empty when the node does not carry a
     *         well-formed result payload
     */
    public static Optional<LeaveNewsletterMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(LeaveNewsletterMexResponse::of);
    }

    /**
     * Returns the newsletter Jid string echoed by the relay.
     *
     * @apiNote
     * Empty when the GraphQL envelope omits {@code id}; otherwise carries
     * the same Jid string sent in {@link LeaveNewsletterMexRequest}.
     *
     * @return the echoed newsletter id, or empty when omitted
     */
    public Optional<String> id() {
        return Optional.ofNullable(id);
    }

    /**
     * Returns the lifecycle state marker the relay attached to the
     * mutation result.
     *
     * @apiNote
     * Empty when the GraphQL envelope omits {@code state}. A populated
     * {@link State} of type {@code DELETED} or {@code NON_EXISTING}
     * indicates the relay treated the leave as already complete because
     * the channel has been removed, while {@code SUSPENDED} indicates a
     * server-side suspension.
     *
     * @return the parsed {@link State}, or empty when omitted
     */
    public Optional<State> state() {
        return Optional.ofNullable(state);
    }

    /**
     * The lifecycle {@code state} marker echoed on the leave-mutation
     * result.
     *
     * @apiNote
     * Carries the relay-defined state-type string. WA Web's
     * {@code mexLeaveNewsletter} treats {@code DELETED} / {@code NON_EXISTING}
     * as a 404 soft failure and {@code SUSPENDED} as a 423 soft failure;
     * Cobalt surfaces the value verbatim and leaves recovery to the caller.
     */
    public static final class State {
        /**
         * The relay-defined state-type string.
         */
        private final String type;

        /**
         * Constructs a parsed {@code state} value.
         *
         * @apiNote
         * Reserved for {@link #of(JSONObject)}.
         *
         * @param type the state-type string
         */
        private State(String type) {
            this.type = type;
        }

        /**
         * Returns the state-type string.
         *
         * @apiNote
         * Empty when the GraphQL envelope omits {@code type}; otherwise
         * carries the relay-defined state-type identifier (for example
         * {@code "ACTIVE"}, {@code "DELETED"}, {@code "NON_EXISTING"},
         * {@code "SUSPENDED"}).
         *
         * @return the {@code type} value, or empty when omitted
         */
        public Optional<String> type() {
            return Optional.ofNullable(type);
        }

        /**
         * Parses a {@code state} fragment from the given JSON object.
         *
         * @apiNote
         * Reserved for the parent parser; returns {@link Optional#empty()}
         * when {@code obj} is {@code null} so an absent fragment cleanly
         * back-propagates to {@link #state()}.
         *
         * @param obj the JSON object to parse
         * @return the parsed value, or empty when {@code obj} is
         *         {@code null}
         */
        static Optional<State> of(JSONObject obj) {
            if (obj == null) {
                return Optional.empty();
            }

            var type = obj.getString("type");
            return Optional.of(new State(type));
        }

        /**
         * Parses every {@code state} fragment in the given JSON array.
         *
         * @apiNote
         * Reserved for callers that handle batched state arrays; returns
         * {@link List#of()} when {@code arr} is {@code null}.
         *
         * @param arr the JSON array to parse
         * @return the list of parsed values, empty when {@code arr} is
         *         {@code null}
         */
        static List<State> ofArray(JSONArray arr) {
            if (arr == null) {
                return List.of();
            }

            var result = new ArrayList<State>(arr.size());
            for (var i = 0; i < arr.size(); i++) {
                of(arr.getJSONObject(i)).ifPresent(result::add);
            }
            return result;
        }
    }

    /**
     * Parses the response from the raw UTF-8 JSON payload of the
     * {@code <result>} child.
     *
     * @apiNote
     * Reserved for the public {@link #of(Node)} overload; callers should not
     * hold raw JSON bytes.
     *
     * @implNote
     * This implementation guards every nested object lookup so a malformed
     * envelope produces {@link Optional#empty()} rather than a parser
     * exception, mirroring the defensive null-checks in WA Web's caller.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return the parsed response, or empty when the envelope lacks the
     *         expected {@code data.xwa2_newsletter_leave_v2} root
     */
    private static Optional<LeaveNewsletterMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_newsletter_leave_v2");
        if (root == null) {
            return Optional.empty();
        }

        var id = root.getString("id");
        var state = State.of(root.getJSONObject("state")).orElse(null);

        return Optional.of(new LeaveNewsletterMexResponse(id, state));
    }
}
