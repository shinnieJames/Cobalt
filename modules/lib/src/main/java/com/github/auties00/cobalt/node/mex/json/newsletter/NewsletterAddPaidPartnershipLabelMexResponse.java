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
 * Parses the MEX response of the add-paid-partnership-label mutation built by
 * {@link NewsletterAddPaidPartnershipLabelMexRequest}.
 *
 * <p>Carries the message id echoed under {@code xwa2_newsletter_label_paid_partnership.id}. An empty
 * {@link #id()} is the failure signal: it means the relay refused the label, so callers replicate
 * the failure check by inspecting whether {@link #id()} is present.
 */
@WhatsAppWebModule(moduleName = "WAWebMexNewsletterAddPaidPartnershipLabelJob")
public final class NewsletterAddPaidPartnershipLabelMexResponse implements MexOperation.Response.Json {
    /**
     * Holds the labelled message id echoed under
     * {@code xwa2_newsletter_label_paid_partnership.id}.
     */
    private final String id;

    /**
     * Constructs a response wrapping the echoed message id.
     *
     * <p>Invoked only by the static parser; external callers obtain instances via {@link #of(Node)}.
     *
     * @param id the message id echoed by the relay
     */
    private NewsletterAddPaidPartnershipLabelMexResponse(String id) {
        this.id = id;
    }

    /**
     * Parses the MEX response carried by the given IQ result node.
     *
     * <p>Drains the {@code <result>} child's byte content into the JSON parser. The returned
     * {@link Optional} is empty when the result child is missing or when the JSON envelope omits the
     * expected {@code data.xwa2_newsletter_label_paid_partnership} root.
     *
     * @param node the IQ result node received from the relay
     * @return the parsed response, or empty when the node does not carry a well-formed result
     *         payload
     */
    public static Optional<NewsletterAddPaidPartnershipLabelMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(NewsletterAddPaidPartnershipLabelMexResponse::of);
    }

    /**
     * Returns the labelled message id echoed by the relay.
     *
     * <p>Empty when the GraphQL envelope omits {@code id}, which is the signal that the relay
     * refused the label.
     *
     * @return the echoed message id, or empty when omitted
     */
    public Optional<String> id() {
        return Optional.ofNullable(id);
    }

    /**
     * Parses the response from the raw UTF-8 JSON payload of the {@code <result>} child.
     *
     * <p>Invoked only by the public {@link #of(Node)} overload.
     *
     * @implNote This implementation guards every nested object lookup so a malformed envelope
     * produces {@link Optional#empty()} rather than a parser exception.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return the parsed response, or empty when the envelope lacks the expected
     *         {@code data.xwa2_newsletter_label_paid_partnership} root
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
