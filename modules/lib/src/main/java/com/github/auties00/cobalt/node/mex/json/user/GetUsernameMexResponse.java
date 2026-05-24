package com.github.auties00.cobalt.node.mex.json.user;

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
 * Decoded reply to the get-username query.
 *
 * @apiNote Consume after dispatching {@link GetUsernameMexRequest}. The
 * relay either returns the {@code username_info} sub-object (current
 * username, registration state, recovery PIN hash) or, when the account
 * has none registered, replies with HTTP 404 which surfaces as an empty
 * {@link #usernameInfo()}.
 *
 * @see GetUsernameMexRequest
 */
@WhatsAppWebModule(moduleName = "WAWebMexGetUsernameJob")
public final class GetUsernameMexResponse implements MexOperation.Response.Json {
    /**
     * The decoded {@code username_info} sub-object, possibly {@code null}.
     */
    private final UsernameInfo usernameInfo;

    /**
     * Wraps the decoded username record.
     *
     * @param usernameInfo the decoded {@code username_info} sub-object
     */
    private GetUsernameMexResponse(UsernameInfo usernameInfo) {
        this.usernameInfo = usernameInfo;
    }

    /**
     * Decodes the {@code <result>} child of an inbound MEX IQ.
     *
     * @apiNote Pass the IQ node received in reply to a stanza dispatched
     * with {@link GetUsernameMexRequest#toNode()}.
     *
     * @param node the IQ reply stanza
     * @return the decoded reply, or {@link Optional#empty()} when the
     *         payload is missing or malformed
     */
    @WhatsAppWebExport(moduleName = "WAWebMexGetUsernameJob", exports = "mexGetUsernameQueryJob",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<GetUsernameMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(GetUsernameMexResponse::of);
    }

    /**
     * Returns the decoded username record.
     *
     * @apiNote {@link Optional#empty()} indicates the account has no
     * registered username, which the relay signals by omitting the
     * {@code username_info} sub-object from the reply.
     *
     * @return the record wrapped in an {@link Optional}, or
     *         {@link Optional#empty()} when absent
     */
    public Optional<UsernameInfo> usernameInfo() {
        return Optional.ofNullable(usernameInfo);
    }

    /**
     * Decoded {@code username_info} sub-object of the get-username reply.
     *
     * @apiNote Carries the assigned username, its registration state
     * (typically {@code RESERVED} or {@code SET}) and the recovery PIN
     * hash used for account recovery via the username PIN flow.
     */
    public static final class UsernameInfo {
        /**
         * The {@code username} field carrying the assigned identifier.
         */
        private final String username;

        /**
         * The {@code state} field carrying the registration state token.
         */
        private final String state;

        /**
         * The {@code pin} field carrying the recovery PIN hash.
         */
        private final String pin;

        /**
         * Wraps the decoded fields of one username record.
         *
         * @param username the {@code username} field
         * @param state the {@code state} field
         * @param pin the {@code pin} field
         */
        private UsernameInfo(String username, String state, String pin) {
            this.username = username;
            this.state = state;
            this.pin = pin;
        }

        /**
         * Returns the assigned username.
         *
         * @return the username wrapped in an {@link Optional}, or
         *         {@link Optional#empty()} when the relay omitted the
         *         field
         */
        public Optional<String> username() {
            return Optional.ofNullable(username);
        }

        /**
         * Returns the registration state token.
         *
         * @apiNote Typical values are {@code RESERVED} (the username is
         * held during onboarding but not active) and {@code SET} (the
         * username is the account's active identifier).
         *
         * @return the state wrapped in an {@link Optional}, or
         *         {@link Optional#empty()} when the relay omitted the
         *         field
         */
        public Optional<String> state() {
            return Optional.ofNullable(state);
        }

        /**
         * Returns the recovery PIN hash bound to the username.
         *
         * @apiNote The relay never returns the cleartext PIN; the hashed
         * value is suitable only for surfacing the "PIN is set" state in
         * the UI.
         *
         * @return the hash wrapped in an {@link Optional}, or
         *         {@link Optional#empty()} when the relay omitted the
         *         field
         */
        public Optional<String> pin() {
            return Optional.ofNullable(pin);
        }

        /**
         * Decodes a single username record from a {@link JSONObject}.
         *
         * @apiNote Used by {@link GetUsernameMexResponse#of(byte[])} when
         * projecting the {@code username_info} sub-object; not part of
         * the public API.
         *
         * @param obj the JSON object to decode, possibly {@code null}
         * @return the decoded record, or {@link Optional#empty()} when
         *         {@code obj} is {@code null}
         */
        static Optional<UsernameInfo> of(JSONObject obj) {
            if (obj == null) {
                return Optional.empty();
            }

            var username = obj.getString("username");
            var state = obj.getString("state");
            var pin = obj.getString("pin");
            return Optional.of(new UsernameInfo(username, state, pin));
        }

        /**
         * Decodes a list of username records from a {@link JSONArray}.
         *
         * @apiNote Provided for parity with other {@code ofArray}
         * helpers; not invoked by the response decoder because the wire
         * schema carries {@code username_info} as a single sub-object,
         * not an array.
         *
         * @param arr the JSON array to decode, possibly {@code null}
         * @return the decoded records in source order; empty when
         *         {@code arr} is {@code null}
         */
        static List<UsernameInfo> ofArray(JSONArray arr) {
            if (arr == null) {
                return List.of();
            }

            var result = new ArrayList<UsernameInfo>(arr.size());
            for (var i = 0; i < arr.size(); i++) {
                of(arr.getJSONObject(i)).ifPresent(result::add);
            }
            return result;
        }
    }

    /**
     * Decodes the {@code <result>} payload bytes into a {@link GetUsernameMexResponse}.
     *
     * @implNote This implementation projects
     * {@code data.xwa2_username_get.username_info}; missing
     * {@code data}, {@code xwa2_username_get}, or {@code username_info}
     * sub-objects yield a response whose {@link #usernameInfo()} is empty
     * (top-level {@link Optional#empty()} only when the bytes are not a
     * JSON object or lack {@code data}).
     *
     * @param json the raw {@code <result>} payload bytes
     * @return the decoded reply, or {@link Optional#empty()} when the
     *         payload does not parse or lacks the {@code data} envelope
     */
    private static Optional<GetUsernameMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_username_get");
        if (root == null) {
            return Optional.empty();
        }

        var usernameInfo = UsernameInfo.of(root.getJSONObject("username_info")).orElse(null);

        return Optional.of(new GetUsernameMexResponse(usernameInfo));
    }
}
