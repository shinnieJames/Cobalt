package com.github.auties00.cobalt.node.mex.json.group;

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
import java.util.Optional;

/**
 * Inbound parsed response of the {@link UpdateGroupPropertyMexRequest}
 * mutation, exposing the affected group id and the resulting group state
 * echoed back by the relay.
 *
 * @apiNote The {@code state} scalar is the post-mutation lifecycle status.
 * WA Web's {@code WAWebMexUpdateGroupPropertyJob.mexUpdateGroupPropertyJob}
 * treats any state other than {@code "ACTIVE"} as a hard failure and
 * raises a {@code ServerStatusCodeError(405)}; Cobalt instead surfaces the
 * raw scalar so the caller decides how to react.
 *
 * @implNote This implementation does not reject non-{@code ACTIVE} states
 * inside the parser, in contrast to WA Web's inline throw; the choice is
 * deliberate per the Cobalt configurable-error-handler model. Callers may
 * compare {@link #state()} against {@code "ACTIVE"} explicitly.
 */
@WhatsAppWebModule(moduleName = "WAWebMexUpdateGroupPropertyJob")
public final class UpdateGroupPropertyMexResponse implements MexOperation.Response.Json {
    /**
     * The group id scalar projected from
     * {@code data.xwa2_group_update_property.id}, echoed back by the relay.
     */
    private final String id;

    /**
     * The post-mutation group state scalar projected from
     * {@code data.xwa2_group_update_property.state}; expected to be
     * {@code "ACTIVE"} on success.
     */
    private final String state;

    /**
     * Constructs a new response wrapping the parsed scalar fields.
     *
     * @apiNote Package-private; instances are produced by the
     * {@link #of(Node)} parser.
     *
     * @param id    the echoed group id, or {@code null} if absent
     * @param state the post-mutation group state, or {@code null} if absent
     */
    private UpdateGroupPropertyMexResponse(String id, String state) {
        this.id = id;
        this.state = state;
    }

    /**
     * Parses the MEX response carried by an inbound IQ stanza.
     *
     * @apiNote Entry point for receivers handling the IQ reply of
     * {@link UpdateGroupPropertyMexRequest}. The returned value is
     * {@link Optional#empty()} when the reply lacks a {@code <result>}
     * child or its JSON body cannot be parsed into the expected envelope.
     *
     * @param node the inbound IQ stanza carrying the {@code <result>} child
     * @return the parsed response, or {@link Optional#empty()} if the
     *         expected JSON shape is absent
     */
    @WhatsAppWebExport(moduleName = "WAWebMexUpdateGroupPropertyJob", exports = "mexUpdateGroupPropertyJob",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<UpdateGroupPropertyMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(UpdateGroupPropertyMexResponse::of);
    }

    /**
     * Returns the group id scalar echoed back by the relay.
     *
     * @apiNote Mirrors the {@code group_id} variable sent in
     * {@link UpdateGroupPropertyMexRequest}; useful when correlating the
     * reply against a batched dispatch.
     *
     * @return an {@link Optional} containing the value, or empty if absent
     */
    public Optional<String> id() {
        return Optional.ofNullable(id);
    }

    /**
     * Returns the post-mutation group state scalar.
     *
     * @apiNote Expected to be {@code "ACTIVE"} on success; values such as
     * {@code "NON_EXISTENT"} or {@code "SUSPENDED"} indicate the relay
     * rejected the mutation because the group is no longer in a writable
     * state.
     *
     * @return an {@link Optional} containing the value, or empty if absent
     */
    public Optional<String> state() {
        return Optional.ofNullable(state);
    }

    /**
     * Parses the JSON payload carried by the {@code <result>} child into a
     * {@link UpdateGroupPropertyMexResponse}.
     *
     * @implNote This implementation walks the
     * {@code data.xwa2_group_update_property} envelope and returns
     * {@link Optional#empty()} when any intermediate object is missing,
     * mirroring the WA Web destructuring
     * {@code (n = a.xwa2_group_update_property) != null ? n : {}}.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return an {@link Optional} containing the parsed response, or empty
     *         if the {@code data.xwa2_group_update_property} envelope is absent
     */
    private static Optional<UpdateGroupPropertyMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_group_update_property");
        if (root == null) {
            return Optional.empty();
        }

        var id = root.getString("id");
        var state = root.getString("state");

        return Optional.of(new UpdateGroupPropertyMexResponse(id, state));
    }
}
