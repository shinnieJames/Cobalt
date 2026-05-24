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
 * Parses the MEX response of the add-paid-partnership-label mutation
 * built by {@link NewsletterAddPaidPartnershipLabelMexRequest}.
 *
 * @apiNote
 * Hands back the message id echoed under
 * {@code xwa2_newsletter_label_paid_partnership.id}; WA Web's
 * {@code WAWebMexNewsletterAddPaidPartnershipLabelJob.mexNewsletterAddPaidPartnershipLabelJob}
 * treats an empty {@link #id()} as the failure signal that triggers the
 * {@code newsletter-add-paid-partnership-label-mex-failed} error log.
 * Cobalt callers can replicate the check by inspecting whether
 * {@link #id()} is present.
 */
@WhatsAppWebModule(moduleName = "WAWebMexNewsletterAddPaidPartnershipLabelJob")
public final class NewsletterAddPaidPartnershipLabelMexResponse implements MexOperation.Response.Json {
    /**
     * The labelled message id echoed under
     * {@code xwa2_newsletter_label_paid_partnership.id}.
     */
    private final String id;

    /**
     * Constructs a response wrapping the echoed message id.
     *
     * @apiNote
     * Reserved for the static parser; external callers obtain instances via
     * {@link #of(Node)}.
     *
     * @param id the message id echoed by the relay
     */
    private NewsletterAddPaidPartnershipLabelMexResponse(String id) {
        this.id = id;
    }

    /**
     * Parses the MEX response carried by the given IQ result node.
     *
     * @apiNote
     * Drains the {@code <result>} child's byte content into the JSON parser;
     * the returned {@link Optional} is empty when the result child is
     * missing or when the JSON envelope omits the expected
     * {@code data.xwa2_newsletter_label_paid_partnership} root.
     *
     * @param node the IQ result node received from the relay
     * @return the parsed response, or empty when the node does not carry a
     *         well-formed result payload
     */
    public static Optional<NewsletterAddPaidPartnershipLabelMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(NewsletterAddPaidPartnershipLabelMexResponse::of);
    }

    /**
     * Returns the labelled message id echoed by the relay.
     *
     * @apiNote
     * Empty when the GraphQL envelope omits {@code id}; mirrors WA Web's
     * "no id means the relay refused the label" failure signal.
     *
     * @return the echoed message id, or empty when omitted
     */
    public Optional<String> id() {
        return Optional.ofNullable(id);
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
     *         expected {@code data.xwa2_newsletter_label_paid_partnership}
     *         root
     */
    private static Optional<NewsletterAddPaidPartnershipLabelMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_newsletter_label_paid_partnership");
        if (root == null) {
            return Optional.empty();
        }

        var id = root.getString("id");

        return Optional.of(new NewsletterAddPaidPartnershipLabelMexResponse(id));
    }
}
