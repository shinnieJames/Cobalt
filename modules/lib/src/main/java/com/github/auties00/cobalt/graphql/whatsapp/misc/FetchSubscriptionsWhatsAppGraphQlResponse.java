package com.github.auties00.cobalt.graphql.whatsapp.misc;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.graphql.WhatsAppGraphQlClient;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.business.subscription.BusinessSubscription;
import com.github.auties00.cobalt.model.business.subscription.BusinessSubscriptionBuilder;
import com.github.auties00.cobalt.model.business.subscription.BusinessSubscriptions;
import com.github.auties00.cobalt.model.business.subscription.BusinessSubscriptionsBuilder;
import com.github.auties00.cobalt.model.business.subscription.SubscriptionFeatureFlag;
import com.github.auties00.cobalt.model.business.subscription.SubscriptionFeatureFlagBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses the WhatsApp Web GraphQL response of the subscriptions query built by
 * {@link FetchSubscriptionsWhatsAppGraphQlRequest} into a {@link BusinessSubscriptions}.
 *
 * <p>Reads the linked root {@code xwa_get_subscriptions} and projects its {@code subscriptions} and
 * {@code feature_flags} lists onto the Cobalt domain model: the account's held subscriptions together
 * with the per-feature flags they unlock, with their limits and expiry.
 *
 * @see FetchSubscriptionsWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebFetchSubscriptionsQuery")
public final class FetchSubscriptionsWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the parsed subscriptions result.
     */
    private final BusinessSubscriptions subscriptions;

    /**
     * Constructs a response wrapping the parsed subscriptions result.
     *
     * <p>Reserved for the static parser.
     *
     * @param subscriptions the parsed result, or {@code null} when the relay omitted the field
     */
    private FetchSubscriptionsWhatsAppGraphQlResponse(BusinessSubscriptions subscriptions) {
        this.subscriptions = subscriptions;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object.
     *
     * <p>Reads the linked root {@code xwa_get_subscriptions} and projects it onto a
     * {@link BusinessSubscriptions}; the returned {@link Optional} is empty when {@code data} or the
     * subscriptions object is missing.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} or the subscriptions object is missing
     */
    public static Optional<FetchSubscriptionsWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }

        var node = data.getJSONObject("xwa_get_subscriptions");
        if (node == null) {
            return Optional.empty();
        }

        var subscriptions = new BusinessSubscriptionsBuilder()
                .subscriptions(parseSubscriptions(node.getJSONArray("subscriptions")))
                .featureFlags(parseFeatureFlags(node.getJSONArray("feature_flags")))
                .build();
        return Optional.of(new FetchSubscriptionsWhatsAppGraphQlResponse(subscriptions));
    }

    /**
     * Projects the {@code subscriptions} array onto a list of {@link BusinessSubscription}.
     *
     * @param arr the JSON array to project
     * @return the projected list, empty when {@code arr} is {@code null}
     */
    private static List<BusinessSubscription> parseSubscriptions(JSONArray arr) {
        if (arr == null) {
            return List.of();
        }

        var result = new ArrayList<BusinessSubscription>(arr.size());
        for (var i = 0; i < arr.size(); i++) {
            var obj = arr.getJSONObject(i);
            if (obj == null) {
                continue;
            }

            var platformChanged = obj.getBoolean("is_platform_changed");
            result.add(new BusinessSubscriptionBuilder()
                    .id(obj.getString("id"))
                    .status(obj.getString("status"))
                    .endTime(instantOfEpochSeconds(obj.getLong("end_time")))
                    .creationTime(instantOfEpochSeconds(obj.getLong("creation_time")))
                    .tier(obj.getString("tier"))
                    .source(obj.getString("source"))
                    .platformChanged(platformChanged != null && platformChanged)
                    .startTime(instantOfEpochSeconds(obj.getLong("start_time")))
                    .build());
        }
        return result;
    }

    /**
     * Projects the {@code feature_flags} array onto a list of {@link SubscriptionFeatureFlag}.
     *
     * @param arr the JSON array to project
     * @return the projected list, empty when {@code arr} is {@code null}
     */
    private static List<SubscriptionFeatureFlag> parseFeatureFlags(JSONArray arr) {
        if (arr == null) {
            return List.of();
        }

        var result = new ArrayList<SubscriptionFeatureFlag>(arr.size());
        for (var i = 0; i < arr.size(); i++) {
            var obj = arr.getJSONObject(i);
            if (obj == null) {
                continue;
            }

            var enabled = obj.getBoolean("enabled");
            result.add(new SubscriptionFeatureFlagBuilder()
                    .name(obj.getString("name"))
                    .enabled(enabled != null && enabled)
                    .expirationTime(instantOfEpochSeconds(obj.getLong("expiration_time")))
                    .limit(obj.getLong("limit"))
                    .build());
        }
        return result;
    }

    /**
     * Converts an epoch-second value to an {@link Instant}.
     *
     * @param epochSeconds the epoch-second value, or {@code null}
     * @return the converted {@link Instant}, or {@code null} when {@code epochSeconds} is {@code null}
     */
    private static Instant instantOfEpochSeconds(Long epochSeconds) {
        return epochSeconds == null ? null : Instant.ofEpochSecond(epochSeconds);
    }

    /**
     * Returns the parsed subscriptions result.
     *
     * @return the parsed {@link BusinessSubscriptions}, never {@code null}
     */
    public BusinessSubscriptions subscriptions() {
        return subscriptions;
    }
}
