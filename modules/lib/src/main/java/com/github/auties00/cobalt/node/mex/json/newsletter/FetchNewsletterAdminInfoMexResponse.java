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
import java.util.OptionalLong;

/**
 * Parses the MEX response of the fetch-newsletter-admin-info query built by
 * {@link FetchNewsletterAdminInfoMexRequest}.
 *
 * <p>Exposes the admin headcount scalar echoed under {@code xwa2_newsletter_admin.admin_count}
 * together with the newsletter id.
 *
 * @implNote WhatsApp Web's response also carries {@code admin_profile} and {@code admin_settings}
 * sub-objects; this implementation surfaces only the count and id scalars.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterAdminInfoJob")
public final class FetchNewsletterAdminInfoMexResponse implements MexOperation.Response.Json {
    /**
     * Holds the admin headcount echoed under {@code xwa2_newsletter_admin.admin_count}.
     */
    private final Long adminCount;

    /**
     * Holds the newsletter Jid string echoed under {@code xwa2_newsletter_admin.id}.
     */
    private final String id;

    /**
     * Constructs a response wrapping the parsed scalar fields.
     *
     * <p>Reserved for the static parser; external callers obtain instances via {@link #of(Node)}.
     *
     * @param adminCount the admin headcount
     * @param id         the newsletter Jid echoed by the relay
     */
    private FetchNewsletterAdminInfoMexResponse(Long adminCount, String id) {
        this.adminCount = adminCount;
        this.id = id;
    }

    /**
     * Parses the MEX response carried by the given IQ result node.
     *
     * <p>Drains the {@code <result>} child's byte content into the JSON parser; the returned
     * {@link Optional} is empty when the result child is missing or when the JSON envelope omits the
     * expected {@code data.xwa2_newsletter_admin} root.
     *
     * @param node the IQ result node received from the relay
     * @return the parsed response, or empty when the node does not carry a well-formed result
     *         payload
     */
    public static Optional<FetchNewsletterAdminInfoMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(FetchNewsletterAdminInfoMexResponse::of);
    }

    /**
     * Returns the admin headcount.
     *
     * <p>WhatsApp Web falls back to a default admin count when this scalar is omitted; callers must
     * apply their own fallback if they need a numeric value.
     *
     * @return the admin headcount, or empty when the relay omitted the field
     */
    public OptionalLong adminCount() {
        return adminCount != null ? OptionalLong.of(adminCount) : OptionalLong.empty();
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
     * Parses the response from the raw UTF-8 JSON payload of the {@code <result>} child.
     *
     * <p>Reserved for the public {@link #of(Node)} overload.
     *
     * @implNote This implementation guards every nested object lookup so a malformed envelope
     * produces {@link Optional#empty()} rather than a parser exception.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return the parsed response, or empty when the envelope lacks the expected
     *         {@code data.xwa2_newsletter_admin} root
     */
    private static Optional<FetchNewsletterAdminInfoMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_newsletter_admin");
        if (root == null) {
            return Optional.empty();
        }

        var adminCount = root.getLong("admin_count");
        var id = root.getString("id");

        return Optional.of(new FetchNewsletterAdminInfoMexResponse(adminCount, id));
    }
}
