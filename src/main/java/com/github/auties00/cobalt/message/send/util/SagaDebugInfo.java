package com.github.auties00.cobalt.message.send.util;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Generates SAGA V1 debug info for messages sent to CAPI support accounts.
 *
 * @apiNote WAWebE2EProtoGenerator.addDebugInfoSupportPayload
 */
public final class SagaDebugInfo {
    private static final System.Logger LOGGER = System.getLogger("SagaDebugInfo");
    private static final Set<String> CAPI_SUPPORT_NUMBERS = Set.of();

    private final WhatsAppStore store;
    private final ABPropsService abPropsService;

    public SagaDebugInfo(WhatsAppStore store, ABPropsService abPropsService) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService cannot be null");
    }

    /**
     * Checks if debug info should be added for the recipient.
     */
    public boolean shouldAdd(Jid recipientJid) {
        if (!isCAPISupportAccount(recipientJid)) {
            return false;
        }

        var sagaV1Enabled = abPropsService.getBool(ABProp.SAGA_V1_ENABLED_AB_PROP_CODE).orElse(false);
        var sagaV1ReengagementEnabled = abPropsService.getBool(ABProp.SAGA_V1_REENGAGEMENT_ENABLED_AB_PROP_CODE).orElse(false);

        return sagaV1Enabled && sagaV1ReengagementEnabled;
    }

    /**
     * Checks if a JID is a CAPI support account.
     */
    public boolean isCAPISupportAccount(Jid jid) {
        if (jid == null) {
            return false;
        }
        return CAPI_SUPPORT_NUMBERS.contains(jid.user());
    }

    /**
     * Creates the debug info support payload.
     */
    public String createPayload() {
        var sagaCarouselEnabled = abPropsService.getBool(ABProp.SAGA_V1_CAROUSEL_ENABLED_AB_PROP_CODE).orElse(false);

        Map<String, Object> debugInfo = new LinkedHashMap<>();
        debugInfo.put("locale", store.locale().orElse("en"));
        debugInfo.put("platform", "cobalt");
        debugInfo.put("sagaKey", "saga_v1_enabled");

        debugInfo.put("clientVersion", store.clientVersion().toString());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", 1);
        payload.put("debug_information", debugInfo);

        if (sagaCarouselEnabled) {
            payload.put("citations_carousel", true);
        }

        return toJson(payload);
    }

    /**
     * Gets the support payload if applicable for the recipient.
     */
    public String getPayloadIfApplicable(Jid recipientJid) {
        if (!shouldAdd(recipientJid)) {
            return null;
        }

        LOGGER.log(System.Logger.Level.DEBUG,
                "Creating SAGA V1 debug info for CAPI support account {0}", recipientJid);

        return createPayload();
    }

    private static String toJson(Map<String, Object> map) {
        var sb = new StringBuilder("{");
        var first = true;
        for (var entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            appendValue(sb, entry.getValue());
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            sb.append("\"").append(escapeJson(s)).append("\"");
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> m) {
            sb.append(toJson((Map<String, Object>) m));
        } else {
            sb.append("\"").append(escapeJson(value.toString())).append("\"");
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
