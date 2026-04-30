package com.github.auties00.cobalt.node.mex.json.newsletter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.mex.MexOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Response variant for {@link FetchNewsletterIsDomainPreviewableMexRequest} carrying the parsed server reply.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterIsDomainPreviewableJob")
public final class FetchNewsletterIsDomainPreviewableMexResponse implements MexOperation.Response.Json {
    private final List<UrlPreviews> urlPreviews;

    /**
     * Creates a new response variant carrying the given list of url previews.
     *
     * @param urlPreviews the parsed list of url previews
     */
    private FetchNewsletterIsDomainPreviewableMexResponse(List<UrlPreviews> urlPreviews) {
        this.urlPreviews = urlPreviews;
    }

    /**
     * Parses a MEX response from the given IQ response node.
     *
     * @param node the IQ response node received from the relay
     * @return an {@link Optional} containing the parsed response, or
     *         empty if the node is missing a result payload
     */
    public static Optional<FetchNewsletterIsDomainPreviewableMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(FetchNewsletterIsDomainPreviewableMexResponse::of);
    }

    /**
     * Returns the {@code url_previews} field.
     *
     * @return the list of values, empty if absent
     */
    public List<UrlPreviews> urlPreviews() {
        return urlPreviews;
    }

    /**
     * A parsed {@code UrlPreviews} object.
     */
    public static final class UrlPreviews {
        private final String urlDomain;
        private final Boolean isPreviewable;

        /**
         * Creates a new UrlPreviews object.
         *
         * @param urlDomain the {@code url_domain} field
         * @param isPreviewable the {@code is_previewable} field, {@code null} if absent
         */
        private UrlPreviews(String urlDomain, Boolean isPreviewable) {
            this.urlDomain = urlDomain;
            this.isPreviewable = isPreviewable;
        }

        /**
         * Returns the {@code url_domain} field.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<String> urlDomain() {
            return Optional.ofNullable(urlDomain);
        }

        /**
         * Returns the {@code is_previewable} field.
         *
         * @return true if {@code isPreviawable} is {@code true}, false otherwise or if absent
         */
        public boolean isPreviewable() {
            return isPreviewable != null && isPreviewable;
        }

        /**
         * Returns the {@code is_previewable} field.
         *
         * @param obj the JSON object to parse
         * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
         */
        static Optional<UrlPreviews> of(JSONObject obj) {
            if (obj == null) {
                return Optional.empty();
            }

            var urlDomain = obj.getString("url_domain");
            var isPreviewable = obj.getBoolean("is_previewable");
            return Optional.of(new UrlPreviews(urlDomain, isPreviewable));
        }

        /**
         * Parses a list of {@code UrlPreviews} from the given JSON array.
         *
         * @param arr the JSON array to parse
         * @return the list of parsed results, empty if {@code arr} is {@code null}
         */
        static List<UrlPreviews> ofArray(JSONArray arr) {
            if (arr == null) {
                return List.of();
            }

            var result = new ArrayList<UrlPreviews>(arr.size());
            for (var i = 0; i < arr.size(); i++) {
                of(arr.getJSONObject(i)).ifPresent(result::add);
            }
            return result;
        }
    }

    /**
     * Parses a {@link FetchNewsletterIsDomainPreviewableMexResponse} from the raw JSON bytes of the
     * {@code <result>} child.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return an {@link Optional} containing the parsed response, or
     *         empty if the envelope is missing expected fields
     */
    private static Optional<FetchNewsletterIsDomainPreviewableMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_newsletter_message_integrity");
        if (root == null) {
            return Optional.empty();
        }

        var urlPreviews = UrlPreviews.ofArray(root.getJSONArray("url_previews"));

        return Optional.of(new FetchNewsletterIsDomainPreviewableMexResponse(urlPreviews));
    }
}
