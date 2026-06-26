package com.github.auties00.cobalt.graphql.whatsapp.promotion;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.graphql.WhatsAppGraphQlClient;
import com.github.auties00.cobalt.graphql.whatsapp.WhatsAppGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.business.promotion.QuickPromotionLogAcknowledgement;
import com.github.auties00.cobalt.model.business.promotion.QuickPromotionLogAcknowledgementBuilder;

import java.util.Optional;

/**
 * Parses the WhatsApp Web GraphQL response of the WhatsApp Business quick-promotion log-event mutation built by
 * {@link QuickPromotionActionWhatsAppGraphQlRequest} into a {@link QuickPromotionLogAcknowledgement}.
 *
 * <p>Reads the linked {@code wa_quick_promotion_log_event} root and projects its
 * {@code client_mutation_id} scalar onto a {@link QuickPromotionLogAcknowledgement}.
 *
 * @see QuickPromotionActionWhatsAppGraphQlRequest
 */
@WhatsAppWebModule(moduleName = "WAWebQuickPromotionActionMutation")
public final class QuickPromotionActionWhatsAppGraphQlResponse implements WhatsAppGraphQlOperation.Response {
    /**
     * Holds the parsed acknowledgement.
     */
    private final QuickPromotionLogAcknowledgement acknowledgement;

    /**
     * Constructs a response wrapping the parsed acknowledgement.
     *
     * <p>Reserved for the static parser.
     *
     * @param acknowledgement the parsed acknowledgement
     */
    private QuickPromotionActionWhatsAppGraphQlResponse(QuickPromotionLogAcknowledgement acknowledgement) {
        this.acknowledgement = acknowledgement;
    }

    /**
     * Parses the WhatsApp Web GraphQL response from the unwrapped GraphQL {@code data} object.
     *
     * <p>Reads the linked {@code wa_quick_promotion_log_event} root and projects it onto a
     * {@link QuickPromotionLogAcknowledgement}; the returned {@link Optional} is empty when
     * {@code data} or the root is missing.
     *
     * @param data the unwrapped GraphQL {@code data} object returned by
     *             {@link WhatsAppGraphQlClient#send(WhatsAppGraphQlOperation.Request)}
     * @return the parsed response, or empty when {@code data} or the root is missing
     */
    public static Optional<QuickPromotionActionWhatsAppGraphQlResponse> of(JSONObject data) {
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("wa_quick_promotion_log_event");
        if (root == null) {
            return Optional.empty();
        }

        var acknowledgement = new QuickPromotionLogAcknowledgementBuilder()
                .clientMutationId(root.getString("client_mutation_id"))
                .build();
        return Optional.of(new QuickPromotionActionWhatsAppGraphQlResponse(acknowledgement));
    }

    /**
     * Returns the parsed acknowledgement.
     *
     * @return the parsed {@link QuickPromotionLogAcknowledgement}, never {@code null}
     */
    public QuickPromotionLogAcknowledgement acknowledgement() {
        return acknowledgement;
    }
}
