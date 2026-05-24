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
 * Decoded reply to the update-text-status mutation.
 *
 * @apiNote Consume after dispatching {@link UpdateTextStatusMexRequest}.
 * Wraps the {@code xwa2_update_text_status.result} status token; WA Web's
 * {@code WAWebUpdateTextStatusJob} inspects this token to drive the
 * post-submit UI and emits a structured log entry tagged with the result.
 *
 * @see UpdateTextStatusMexRequest
 */
@WhatsAppWebModule(moduleName = "WAWebMexUpdateTextStatusJob")
public final class UpdateTextStatusMexResponse implements MexOperation.Response.Json {
    /**
     * The {@code result} field carrying the relay's status token.
     */
    private final String result;

    /**
     * Wraps the decoded relay status token.
     *
     * @param result the {@code result} field
     */
    private UpdateTextStatusMexResponse(String result) {
        this.result = result;
    }

    /**
     * Decodes the {@code <result>} child of an inbound MEX IQ.
     *
     * @apiNote Pass the IQ node received in reply to a stanza dispatched
     * with {@link UpdateTextStatusMexRequest#toNode()}.
     *
     * @param node the IQ reply stanza
     * @return the decoded reply, or {@link Optional#empty()} when the
     *         payload is missing or malformed
     */
    @WhatsAppWebExport(moduleName = "WAWebMexUpdateTextStatusJob", exports = "mexUpdateTextStatus",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<UpdateTextStatusMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(UpdateTextStatusMexResponse::of);
    }

    /**
     * Returns the raw status token.
     *
     * @apiNote Use {@link #isSuccess()} for the boolean WA Web exposes;
     * the raw token is preserved for callers that want to distinguish
     * among the relay's error tokens.
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
     * {@code result?.xwa2_update_text_status?.result === "SUCCESS"} check
     * inside {@code WAWebUpdateTextStatusJob}.
     *
     * @return {@code true} when {@link #result()} equals
     *         {@code "SUCCESS"}, {@code false} otherwise
     */
    @WhatsAppWebExport(moduleName = "WAWebMexUpdateTextStatusJob", exports = "mexUpdateTextStatus",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean isSuccess() {
        return Objects.equals(result, "SUCCESS");
    }

    /**
     * Decodes the {@code <result>} payload bytes into a {@link UpdateTextStatusMexResponse}.
     *
     * @implNote This implementation projects
     * {@code data.xwa2_update_text_status.result}; missing intermediate
     * envelopes yield {@link Optional#empty()}.
     *
     * @param json the raw {@code <result>} payload bytes
     * @return the decoded reply, or {@link Optional#empty()} when the
     *         payload does not parse or lacks the required envelope
     */
    private static Optional<UpdateTextStatusMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_update_text_status");
        if (root == null) {
            return Optional.empty();
        }

        var result = root.getString("result");

        return Optional.of(new UpdateTextStatusMexResponse(result));
    }
}
