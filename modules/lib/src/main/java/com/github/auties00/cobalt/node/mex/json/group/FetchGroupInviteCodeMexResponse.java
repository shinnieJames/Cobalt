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
 * Inbound parsed response of the {@link FetchGroupInviteCodeMexRequest}
 * query, exposing the current invite code scalar and the echoed group
 * identifier.
 *
 * @apiNote Consumed by callers mirroring WA Web's
 * {@code WAWebGroupInviteAction} pipeline, which uses the code to compose
 * the shareable {@code chat.whatsapp.com/<code>} URL and cache it on the
 * in-memory group descriptor.
 *
 * @implNote This implementation surfaces a {@code null} invite code as an
 * empty {@link Optional} on {@link #inviteCode()} rather than throwing, in
 * contrast to WA Web's {@code mexFetchGroupInviteCode} which raises a MEX
 * error when the relay omits the field. The caller decides how to react to
 * the missing-code case.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchGroupInviteCodeJob")
public final class FetchGroupInviteCodeMexResponse implements MexOperation.Response.Json {
    /**
     * The current invite code scalar projected from
     * {@code data.xwa2_group_query_by_id.invite_code}.
     */
    private final String inviteCode;

    /**
     * The group identifier scalar projected from
     * {@code data.xwa2_group_query_by_id.id}, echoed back by the relay.
     */
    private final String id;

    /**
     * Constructs a new response wrapping the parsed scalar fields.
     *
     * @apiNote Package-private; instances are produced by the
     * {@link #of(Node)} parser.
     *
     * @param inviteCode the current invite code, or {@code null} if absent
     * @param id         the echoed group identifier, or {@code null} if absent
     */
    private FetchGroupInviteCodeMexResponse(String inviteCode, String id) {
        this.inviteCode = inviteCode;
        this.id = id;
    }

    /**
     * Parses the MEX response carried by an inbound IQ stanza.
     *
     * @apiNote Entry point for receivers handling the IQ reply of
     * {@link FetchGroupInviteCodeMexRequest}. The returned value is
     * {@link Optional#empty()} when the reply lacks a {@code <result>}
     * child or its JSON body cannot be parsed into the expected envelope.
     *
     * @param node the inbound IQ stanza carrying the {@code <result>} child
     * @return the parsed response, or {@link Optional#empty()} if the
     *         expected JSON shape is absent
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchGroupInviteCodeJob", exports = "fetchMexGroupInviteCode",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<FetchGroupInviteCodeMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(FetchGroupInviteCodeMexResponse::of);
    }

    /**
     * Returns the current invite code scalar.
     *
     * @apiNote The opaque suffix of the shareable
     * {@code chat.whatsapp.com/<code>} URL; absent when the relay omitted
     * the field (e.g. the group has not yet been issued an invite code).
     *
     * @return an {@link Optional} containing the value, or empty if absent
     */
    public Optional<String> inviteCode() {
        return Optional.ofNullable(inviteCode);
    }

    /**
     * Returns the echoed group identifier scalar.
     *
     * @apiNote Mirrors the {@code id} variable sent in
     * {@link FetchGroupInviteCodeMexRequest}; useful when correlating the
     * reply against a batched dispatch.
     *
     * @return an {@link Optional} containing the value, or empty if absent
     */
    public Optional<String> id() {
        return Optional.ofNullable(id);
    }

    /**
     * Parses the JSON payload carried by the {@code <result>} child into a
     * {@link FetchGroupInviteCodeMexResponse}.
     *
     * @implNote This implementation walks the
     * {@code data.xwa2_group_query_by_id} envelope and returns
     * {@link Optional#empty()} when any intermediate object is missing,
     * mirroring the WA Web optional-chain
     * {@code (t = n.xwa2_group_query_by_id) == null ? void 0 : t.invite_code}.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return an {@link Optional} containing the parsed response, or empty
     *         if the {@code data.xwa2_group_query_by_id} envelope is absent
     */
    private static Optional<FetchGroupInviteCodeMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_group_query_by_id");
        if (root == null) {
            return Optional.empty();
        }

        var inviteCode = root.getString("invite_code");
        var id = root.getString("id");

        return Optional.of(new FetchGroupInviteCodeMexResponse(inviteCode, id));
    }
}
