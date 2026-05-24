package com.github.auties00.cobalt.node.mex.json.user;

import com.alibaba.fastjson2.JSON;
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
import java.util.Objects;
import java.util.Optional;

/**
 * Decoded reply to the set-username mutation.
 *
 * @apiNote Consume after dispatching {@link SetUsernameMexRequest}. Wraps
 * the {@code xwa2_username_set.result} status token; WA Web's
 * {@code WAWebMexSetUsernameJob.mexSetUsernameQueryJob} returns only the
 * boolean derived from {@code result === "SUCCESS"}, which Cobalt
 * surfaces via {@link #isSuccess()}.
 *
 * @see SetUsernameMexRequest
 */
@WhatsAppWebModule(moduleName = "WAWebMexSetUsernameJob")
public final class SetUsernameMexResponse implements MexOperation.Response.Json {
    /**
     * The {@code result} field carrying the relay's status token.
     */
    private final String result;

    /**
     * Wraps the decoded relay status token.
     *
     * @param result the {@code result} field
     */
    private SetUsernameMexResponse(String result) {
        this.result = result;
    }

    /**
     * Decodes the {@code <result>} child of an inbound MEX IQ.
     *
     * @apiNote Pass the IQ node received in reply to a stanza dispatched
     * with {@link SetUsernameMexRequest#toNode()}.
     *
     * @param node the IQ reply stanza
     * @return the decoded reply, or {@link Optional#empty()} when the
     *         payload is missing or malformed
     */
    @WhatsAppWebExport(moduleName = "WAWebMexSetUsernameJob", exports = "mexSetUsernameQueryJob",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<SetUsernameMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(SetUsernameMexResponse::of);
    }

    /**
     * Returns the raw status token.
     *
     * @apiNote Use {@link #isSuccess()} for the boolean WA Web exposes;
     * the raw token is preserved so callers may distinguish among the
     * relay's error tokens (such as the various "username taken" or
     * "username invalid" failures).
     *
     * @return the token wrapped in an {@link Optional}, or
     *         {@link Optional#empty()} when the relay omitted the field
     */
    public Optional<String> result() {
        return Optional.ofNullable(result);
    }

    /**
     * Returns whether the mutation succeeded.
     *
     * @apiNote Mirrors WA Web's
     * {@code result?.xwa2_username_set?.result === "SUCCESS"} check,
     * which is the only signal the JS implementation surfaces to callers.
     *
     * @return {@code true} when {@link #result()} equals
     *         {@code "SUCCESS"}, {@code false} otherwise
     */
    @WhatsAppWebExport(moduleName = "WAWebMexSetUsernameJob", exports = "mexSetUsernameQueryJob",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean isSuccess() {
        return Objects.equals(result, "SUCCESS");
    }

    /**
     * Decodes the {@code <result>} payload bytes into a {@link SetUsernameMexResponse}.
     *
     * @implNote This implementation projects
     * {@code data.xwa2_username_set.result}; missing intermediate
     * envelopes yield {@link Optional#empty()}.
     *
     * @param json the raw {@code <result>} payload bytes
     * @return the decoded reply, or {@link Optional#empty()} when the
     *         payload does not parse or lacks the required envelope
     */
    private static Optional<SetUsernameMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_username_set");
        if (root == null) {
            return Optional.empty();
        }

        var result = root.getString("result");

        return Optional.of(new SetUsernameMexResponse(result));
    }
}
