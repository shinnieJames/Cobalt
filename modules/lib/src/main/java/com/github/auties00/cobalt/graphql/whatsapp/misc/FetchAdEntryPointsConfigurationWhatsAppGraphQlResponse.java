package com.github.auties00.cobalt.graphql.whatsapp.misc;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.graphql.WhatsAppGraphQlClient;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.business.ads.AdEntryPointEntitlement;
import com.github.auties00.cobalt.model.business.ads.AdEntryPointEntitlementBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses the WhatsApp Web GraphQL response of the ad-entry-points entitlement query built by
 * {@link FetchAdEntryPointsConfigurationWhatsAppGraphQlRequest} into a list of {@link AdEntryPointEntitlement}.
 *
 * <p>Reads the plural linked {@code ctwa_client_entry_point_entitlement} field and projects each entry
 * onto the Cobalt domain model: the entry-point or experience identifier and whether it should be
 * surfaced. This plain entitlement surface carries no localised copy, so the projected entitlements
 * leave their content empty.
 *
 * @see FetchAdEntryPointsConfigurationWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebFetchAdEntryPointsConfigurationQuery")
public final class FetchAdEntryPointsConfigurationWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the parsed entry-point entitlements.
     */
    private final List<AdEntryPointEntitlement> entitlements;

    /**
     * Constructs a response wrapping the parsed entitlement list.
     *
     * <p>Reserved for the static parser.
     *
     * @param entitlements the parsed entry-point entitlements
     */
    private FetchAdEntryPointsConfigurationWhatsAppGraphQlResponse(List<AdEntryPointEntitlement> entitlements) {
        this.entitlements = entitlements;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object.
     *
     * <p>Reads the plural linked root {@code ctwa_client_entry_point_entitlement} and projects each
     * entry onto an {@link AdEntryPointEntitlement}; the returned {@link Optional} is empty when
     * {@code data} is {@code null}.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} is {@code null}
     */
    public static Optional<FetchAdEntryPointsConfigurationWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }

        var entitlements = parseEntitlements(data.getJSONArray("ctwa_client_entry_point_entitlement"));
        return Optional.of(new FetchAdEntryPointsConfigurationWhatsAppGraphQlResponse(entitlements));
    }

    /**
     * Projects the {@code ctwa_client_entry_point_entitlement} array onto a list of
     * {@link AdEntryPointEntitlement}, leaving the localised copy empty.
     *
     * @param arr the JSON array to project
     * @return the projected list, empty when {@code arr} is {@code null}
     */
    private static List<AdEntryPointEntitlement> parseEntitlements(JSONArray arr) {
        if (arr == null) {
            return List.of();
        }

        var result = new ArrayList<AdEntryPointEntitlement>(arr.size());
        for (var i = 0; i < arr.size(); i++) {
            var obj = arr.getJSONObject(i);
            if (obj == null) {
                continue;
            }

            var shouldShow = obj.getBoolean("should_show");
            result.add(new AdEntryPointEntitlementBuilder()
                    .entryPointOrExperience(obj.getString("entry_point_or_experience"))
                    .shouldShow(shouldShow != null && shouldShow)
                    .build());
        }
        return result;
    }

    /**
     * Returns the parsed entry-point entitlements.
     *
     * @return the parsed entitlements, empty when the relay returned none
     */
    public List<AdEntryPointEntitlement> entitlements() {
        return entitlements;
    }
}
