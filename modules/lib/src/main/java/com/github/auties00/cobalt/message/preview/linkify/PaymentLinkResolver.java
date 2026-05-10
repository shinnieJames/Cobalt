package com.github.auties00.cobalt.message.preview.linkify;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;
import com.github.auties00.cobalt.props.ABProp;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Resolves the payment-service-provider (PSP) attached to a payment
 * deep-link by matching the URL against the dynamic regex map shipped
 * server-side via {@code ABProp.SMB_PAYMENT_LINKS_URL_REGEX_LIST}.
 *
 * <p>The map is a JSON object whose keys are regular expressions and
 * whose values are PSP labels. The compiled patterns are cached per
 * AB-prop value so a single AB-prop refresh costs one parse, not one
 * per outgoing message.
 */
@WhatsAppWebModule(moduleName = "WAWebPaymentLinkUrlMetaData")
final class PaymentLinkResolver {
    /**
     * Cache of parsed regex maps keyed by the raw AB-prop JSON. A
     * concurrent map is used because outgoing messages run on virtual
     * threads, so multiple sends may race on the first parse.
     */
    private static final Map<String, Map<Pattern, String>> CACHE = new ConcurrentHashMap<>();

    /**
     * Hidden constructor for the utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private PaymentLinkResolver() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Returns the payment-link metadata associated with {@code url}, or
     * empty when none of the configured regexes matches.
     *
     * @param client the WhatsApp client used to read the AB-prop value
     *               and determine SMB status
     * @param url    the URL to inspect
     * @return the matched metadata, or empty
     */
    @WhatsAppWebExport(moduleName = "WAWebPaymentLinkUrlMetaData", exports = "getPaymentLinkUrlMetaData",
            adaptation = WhatsAppAdaptation.DIRECT)
    static Optional<Match> resolve(WhatsAppClient client, String url) {
        if (client == null || url == null) {
            return Optional.empty();
        }
        var raw = client.abPropsService().getString(ABProp.SMB_PAYMENT_LINKS_URL_REGEX_LIST);
        if (raw == null || raw.isEmpty()) {
            return Optional.empty();
        }
        var regexMap = CACHE.computeIfAbsent(raw, PaymentLinkResolver::parse);
        if (regexMap.isEmpty()) {
            return Optional.empty();
        }
        var smb = isSMB(client);
        for (var entry : regexMap.entrySet()) {
            if (entry.getKey().matcher(url).find()) {
                return Optional.of(new Match(entry.getValue(), smb));
            }
        }
        return Optional.empty();
    }

    /**
     * Parses the AB-prop JSON value into a {@link Map} of compiled
     * patterns to PSP labels. Malformed entries (broken regexes,
     * non-string values) are skipped silently to mirror the JS path,
     * which never throws on bad input.
     *
     * @param raw the AB-prop JSON value
     * @return the parsed map, possibly empty
     */
    private static Map<Pattern, String> parse(String raw) {
        try {
            var json = JSON.parseObject(raw);
            if (json == null || json.isEmpty()) {
                return Map.of();
            }
            var out = new LinkedHashMap<Pattern, String>();
            for (var entry : json.entrySet()) {
                if (!(entry.getValue() instanceof String psp)) {
                    continue;
                }
                try {
                    out.put(Pattern.compile(entry.getKey()), psp);
                } catch (PatternSyntaxException _) {
                }
            }
            return Map.copyOf(out);
        } catch (RuntimeException malformed) {
            return Map.of();
        }
    }

    /**
     * Returns whether the local client runs the SMB (WhatsApp Business)
     * variant.
     *
     * @param client the WhatsApp client
     * @return {@code true} when the device platform is one of the
     *         {@code _BUSINESS} variants
     */
    @WhatsAppWebExport(moduleName = "WAWebMobilePlatforms", exports = "isSMB",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static boolean isSMB(WhatsAppClient client) {
        var device = client.store().device();
        if (device == null) {
            return false;
        }
        var platform = device.platform();
        return platform == ClientPlatformType.ANDROID_BUSINESS
                || platform == ClientPlatformType.IOS_BUSINESS;
    }

    /**
     * Resolved payment-link metadata returned by
     * {@link #resolve(WhatsAppClient, String)}.
     *
     * @param psp                    the payment service provider label
     *                               associated with the matched regex
     * @param shouldDetectInComposer whether the composer should
     *                               materialise a payment-link card
     *                               (true only on SMB clients)
     */
    record Match(String psp, boolean shouldDetectInComposer) {
    }
}
