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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses the MEX response of the fetch-newsletter-admin-capabilities query built by
 * {@link FetchNewsletterAdminCapabilitiesMexRequest}.
 *
 * <p>Exposes the raw capability tokens echoed under {@code xwa2_newsletter_admin.capabilities};
 * callers map each token to the corresponding newsletter-capability enum themselves.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterAdminCapabilitiesJob")
public final class FetchNewsletterAdminCapabilitiesMexResponse implements MexOperation.Response.Json {
    /**
     * Holds the unmodifiable list of capability tokens granted to the local user.
     */
    private final List<String> capabilities;

    /**
     * Constructs a response wrapping the parsed capability list.
     *
     * <p>Reserved for the static parser; external callers obtain instances via {@link #of(Node)}.
     *
     * @param capabilities the capability tokens echoed by the relay
     */
    private FetchNewsletterAdminCapabilitiesMexResponse(List<String> capabilities) {
        this.capabilities = capabilities;
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
    public static Optional<FetchNewsletterAdminCapabilitiesMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(FetchNewsletterAdminCapabilitiesMexResponse::of);
    }

    /**
     * Returns the raw capability tokens granted to the authenticated admin.
     *
     * <p>The list is unmodifiable; callers map each token to the corresponding newsletter-capability
     * enum before consuming the result.
     *
     * @return the unmodifiable list of capability tokens, never {@code null} but possibly empty
     */
    public List<String> capabilities() {
        return capabilities;
    }

    /**
     * Parses the response from the raw UTF-8 JSON payload of the {@code <result>} child.
     *
     * <p>Reserved for the public {@link #of(Node)} overload.
     *
     * @implNote This implementation drops {@code null} entries from the {@code capabilities} array
     * and returns an unmodifiable copy of the surviving tokens; the relay reports an empty array when
     * the local user holds no admin capabilities.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return the parsed response, or empty when the envelope lacks the expected
     *         {@code data.xwa2_newsletter_admin} root
     */
    private static Optional<FetchNewsletterAdminCapabilitiesMexResponse> of(byte[] json) {
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

        var capabilitiesArray = root.getJSONArray("capabilities");
        var capabilities = new ArrayList<String>();
        if (capabilitiesArray != null) {
            for (var i = 0; i < capabilitiesArray.size(); i++) {
                var value = capabilitiesArray.getString(i);
                if (value != null) {
                    capabilities.add(value);
                }
            }
        }

        return Optional.of(new FetchNewsletterAdminCapabilitiesMexResponse(List.copyOf(capabilities)));
    }
}
