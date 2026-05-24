package com.github.auties00.cobalt.node.mex.json.newsletter;

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
 * Parses the MEX response of the demote-newsletter-admin mutation built by
 * {@link DemoteNewsletterAdminMexRequest}.
 *
 * @apiNote
 * Hands back the newsletter id echoed under
 * {@code xwa2_newsletter_admin_demote}; consumers use it to confirm the
 * mutation applied to the expected channel before refreshing local
 * membership state to follower for the demoted user.
 */
@WhatsAppWebModule(moduleName = "WAWebMexDemoteNewsletterAdminJob")
public final class DemoteNewsletterAdminMexResponse implements MexOperation.Response.Json {
    /**
     * The newsletter Jid string echoed under the
     * {@code xwa2_newsletter_admin_demote.id} response field.
     */
    private final String id;

    /**
     * Constructs a response wrapping the echoed newsletter id.
     *
     * @apiNote
     * Reserved for the static parser; external callers obtain instances via
     * {@link #of(Node)}.
     *
     * @param id the newsletter Jid echoed by the relay
     */
    private DemoteNewsletterAdminMexResponse(String id) {
        this.id = id;
    }

    /**
     * Parses the MEX response carried by the given IQ result node.
     *
     * @apiNote
     * Drains the {@code <result>} child's byte content into the JSON parser;
     * the returned {@link Optional} is empty when the result child is
     * missing or when the JSON envelope omits the expected
     * {@code data.xwa2_newsletter_admin_demote} root.
     *
     * @param node the IQ result node received from the relay
     * @return the parsed response, or empty when the node does not carry a
     *         well-formed result payload
     */
    public static Optional<DemoteNewsletterAdminMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(DemoteNewsletterAdminMexResponse::of);
    }

    /**
     * Returns the newsletter Jid string echoed by the relay.
     *
     * @return the echoed newsletter id, or empty when the relay omitted it
     */
    public Optional<String> id() {
        return Optional.ofNullable(id);
    }

    /**
     * Parses the response from the raw UTF-8 JSON payload of the
     * {@code <result>} child.
     *
     * @apiNote
     * Reserved for the public {@link #of(Node)} overload.
     *
     * @implNote
     * This implementation guards every nested object lookup so a malformed
     * envelope produces {@link Optional#empty()} rather than a parser
     * exception.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return the parsed response, or empty when the envelope lacks the
     *         expected {@code data.xwa2_newsletter_admin_demote} root
     */
    private static Optional<DemoteNewsletterAdminMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_newsletter_admin_demote");
        if (root == null) {
            return Optional.empty();
        }

        var id = root.getString("id");

        return Optional.of(new DemoteNewsletterAdminMexResponse(id));
    }
}
