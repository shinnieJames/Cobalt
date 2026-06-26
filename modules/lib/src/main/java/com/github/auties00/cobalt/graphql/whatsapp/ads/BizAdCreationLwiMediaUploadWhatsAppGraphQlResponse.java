package com.github.auties00.cobalt.graphql.whatsapp.ads;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.graphql.WhatsAppGraphQlClient;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.business.ads.AdMediaUpload;
import com.github.auties00.cobalt.model.business.ads.AdMediaUploadBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses the WhatsApp Web GraphQL response of the ad-media upload mutation built by
 * {@link BizAdCreationLwiMediaUploadWhatsAppGraphQlRequest} into a list of {@link AdMediaUpload}.
 *
 * <p>Reads the linked root {@code wa_ad_creation_lwi_media_upload} and projects its
 * {@code uploaded_media_data} array onto the Cobalt domain model: one descriptor per uploaded
 * medium, carrying the resulting URL, content hash, media kind, and, for video uploads, the video
 * identifier.
 *
 * @see BizAdCreationLwiMediaUploadWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebBizAdCreationLWIMediaUploadMutation")
public final class BizAdCreationLwiMediaUploadWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the parsed uploaded-media descriptors.
     */
    private final List<AdMediaUpload> uploads;

    /**
     * Constructs a response wrapping the parsed uploaded-media descriptors.
     *
     * <p>Reserved for the static parser.
     *
     * @param uploads the parsed uploaded-media descriptors
     */
    private BizAdCreationLwiMediaUploadWhatsAppGraphQlResponse(List<AdMediaUpload> uploads) {
        this.uploads = uploads;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object.
     *
     * <p>Reads the linked root {@code wa_ad_creation_lwi_media_upload} and projects its
     * {@code uploaded_media_data} array onto a list of {@link AdMediaUpload}; the returned
     * {@link Optional} is empty when {@code data} or the upload object is missing.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} or the upload object is missing
     */
    public static Optional<BizAdCreationLwiMediaUploadWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }

        var node = data.getJSONObject("wa_ad_creation_lwi_media_upload");
        if (node == null) {
            return Optional.empty();
        }

        return Optional.of(new BizAdCreationLwiMediaUploadWhatsAppGraphQlResponse(parseUploads(node.getJSONArray("uploaded_media_data"))));
    }

    /**
     * Projects the {@code uploaded_media_data} array onto a list of {@link AdMediaUpload}.
     *
     * @param arr the JSON array to project
     * @return the projected list, empty when {@code arr} is {@code null}
     */
    private static List<AdMediaUpload> parseUploads(JSONArray arr) {
        if (arr == null) {
            return List.of();
        }

        var result = new ArrayList<AdMediaUpload>(arr.size());
        for (var i = 0; i < arr.size(); i++) {
            var obj = arr.getJSONObject(i);
            if (obj == null) {
                continue;
            }

            result.add(new AdMediaUploadBuilder()
                    .url(obj.getString("url"))
                    .contentHash(obj.getString("hash"))
                    .kind(obj.getString("type"))
                    .videoId(obj.getString("video_id"))
                    .build());
        }
        return result;
    }

    /**
     * Returns the parsed uploaded-media descriptors.
     *
     * @return the parsed descriptors, empty when the relay returned none
     */
    public List<AdMediaUpload> uploads() {
        return uploads;
    }
}
