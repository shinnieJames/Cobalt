package com.github.auties00.cobalt.graphql.whatsapp.ads;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.graphql.WhatsAppGraphQlClient;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.business.ads.NativeAdsEligibility;
import com.github.auties00.cobalt.model.business.ads.NativeAdsEligibilityBuilder;

import java.util.Optional;

/**
 * Parses the WhatsApp Web GraphQL response of the native-ads eligibility query built by
 * {@link FetchNativeAdsMvpEligibilityWhatsAppGraphQlRequest} into a {@link NativeAdsEligibility}.
 *
 * <p>Reads the linked root {@code wa_smb_native_ads_web_info} and projects its four boolean
 * eligibility scalars onto the Cobalt domain model. Each missing flag defaults to {@code false}.
 *
 * @see FetchNativeAdsMvpEligibilityWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebFetchNativeAdsMvpEligibilityQuery")
public final class FetchNativeAdsMvpEligibilityWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the parsed native-ads eligibility view.
     */
    private final NativeAdsEligibility eligibility;

    /**
     * Constructs a response wrapping the parsed eligibility view.
     *
     * <p>Reserved for the static parser.
     *
     * @param eligibility the parsed eligibility view, or {@code null} when the relay omitted the field
     */
    private FetchNativeAdsMvpEligibilityWhatsAppGraphQlResponse(NativeAdsEligibility eligibility) {
        this.eligibility = eligibility;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object.
     *
     * <p>Reads the linked root {@code wa_smb_native_ads_web_info} and projects it onto a
     * {@link NativeAdsEligibility}; the returned {@link Optional} is empty when {@code data} or the
     * eligibility object is missing.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} or the eligibility object is missing
     */
    public static Optional<FetchNativeAdsMvpEligibilityWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }

        var node = data.getJSONObject("wa_smb_native_ads_web_info");
        if (node == null) {
            return Optional.empty();
        }

        var lifetime = node.getBoolean("lifetime_native_ctwa_advertiser");
        var recentWeb = node.getBoolean("webclient_l90_ad_creator");
        var pageLinked = node.getBoolean("is_page_asset_linked");
        var pagelessLinked = node.getBoolean("is_pageless_asset_linked");
        var eligibility = new NativeAdsEligibilityBuilder()
                .lifetimeAdvertiser(lifetime != null && lifetime)
                .recentWebAdvertiser(recentWeb != null && recentWeb)
                .pageAssetLinked(pageLinked != null && pageLinked)
                .pagelessAssetLinked(pagelessLinked != null && pagelessLinked)
                .build();
        return Optional.of(new FetchNativeAdsMvpEligibilityWhatsAppGraphQlResponse(eligibility));
    }

    /**
     * Returns the parsed native-ads eligibility view.
     *
     * @return the parsed {@link NativeAdsEligibility}, never {@code null}
     */
    public NativeAdsEligibility eligibility() {
        return eligibility;
    }
}
