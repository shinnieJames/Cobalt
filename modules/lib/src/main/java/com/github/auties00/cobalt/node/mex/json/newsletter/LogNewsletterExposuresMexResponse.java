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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Parses the MEX response of the log-newsletter-exposures mutation built
 * by {@link LogNewsletterExposuresMexRequest}.
 *
 * @apiNote
 * Acts as a presence marker: WA Web's
 * {@code WAWebMexLogNewsletterExposuresJob.mexLogNewsletterExposures}
 * discards the returned envelope without reading any field, so Cobalt
 * exposes no getters and surfaces a marker instance only to signal that
 * the {@code data.xwa2_newsletter_log_exposures} root was present in the
 * relay's reply.
 */
@WhatsAppWebModule(moduleName = "WAWebMexLogNewsletterExposuresJob")
public final class LogNewsletterExposuresMexResponse implements MexOperation.Response.Json {

    /**
     * Constructs the marker response.
     *
     * @apiNote
     * Reserved for the static parser; external callers obtain instances via
     * {@link #of(Node)}. The marker carries no state because the relay's
     * reply has no fields of interest.
     */
    private LogNewsletterExposuresMexResponse() {
    }

    /**
     * Parses the MEX response carried by the given IQ result node.
     *
     * @apiNote
     * Drains the {@code <result>} child's byte content into the JSON parser;
     * the returned {@link Optional} is empty when the result child is
     * missing or when the JSON envelope omits the expected
     * {@code data.xwa2_newsletter_log_exposures} root.
     *
     * @param node the IQ result node received from the relay
     * @return the marker response, or empty when the node does not carry a
     *         well-formed result payload
     */
    public static Optional<LogNewsletterExposuresMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(LogNewsletterExposuresMexResponse::of);
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
     * @return the marker response, or empty when the envelope lacks the
     *         expected {@code data.xwa2_newsletter_log_exposures} root
     */
    private static Optional<LogNewsletterExposuresMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.get("xwa2_newsletter_log_exposures");
        if (root == null) {
            return Optional.empty();
        }

        return Optional.of(new LogNewsletterExposuresMexResponse());
    }
}
