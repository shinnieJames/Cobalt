package com.github.auties00.cobalt.media.transcode.text.link;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The deep-link recogniser consumed by the link-preview pipeline.
 *
 * @apiNote
 * Mirrors the link-preview-relevant subset of
 * {@code WAWebApiParse.parseAPICmd}: group-invite
 * ({@code chat.whatsapp.com/...}), business catalog
 * ({@code wa.me/c/...}), business product ({@code wa.me/p/.../...}),
 * and payment links ({@code wa.me/pay/...}). The parser strips the
 * relevant identifiers out of the URL so the per-source resolvers
 * can fetch the right data.
 *
 * @implNote
 * This implementation is intentionally narrower than
 * {@code parseAPICmd}: WA Web's parser also recognises around twenty
 * other deep-link shapes (open-chat, call-link, message-yourself,
 * registration campaigns, sticker pack, etc.) all of which fall
 * through the JS pipeline as {@code APICmd.MSG_SEND} /
 * {@code NEW_CHAT} / etc. and are routed by a different module.
 * Those shapes never produce a preview card, so Cobalt collapses
 * everything outside the four supported shapes to
 * {@link DeepLink.NotApplicable#INSTANCE}, matching WA's outcome on
 * this code path.
 */
@WhatsAppWebModule(moduleName = "WAWebApiParse")
@WhatsAppWebModule(moduleName = "WAWebPaymentLinkUrlMetaData")
@WhatsAppWebModule(moduleName = "WAWebMobilePlatforms")
public final class DeepLinkParser {
    /**
     * The cache of parsed payment-link regex maps keyed by the raw
     * AB-prop value.
     *
     * @apiNote
     * Backs {@link #paymentLink(WhatsAppClient, ABPropsService, String)};
     * a single AB-prop refresh costs one parse, not one per outgoing
     * message.
     *
     * @implNote
     * A {@link ConcurrentHashMap} is used because outgoing messages
     * run on virtual threads and several sends may race on the first
     * parse after an AB-prop refresh.
     */
    private static final Map<String, Map<Pattern, String>> PAYMENT_REGEX_CACHE = new ConcurrentHashMap<>();

    /**
     * The web-origin prefix accepted by the catalog / product / group
     * patterns.
     *
     * @apiNote
     * Mirrors {@code WAWebApiParseUtils.ORIGIN}; covers the
     * {@code whatsapp.com}, {@code web.whatsapp.com}, and
     * {@code chat.whatsapp.com} hosts under either scheme.
     */
    private static final String WEB_ORIGIN = "https?://(?:web\\.|chat\\.)?whatsapp\\.com";

    /**
     * The optional locale-tag path segment that may follow the
     * origin.
     *
     * @apiNote
     * Mirrors {@code WAWebApiParseUtils.OPTIONAL_PATH_PART}; matches
     * {@code /xx} or {@code /xx-yy} where {@code xx} and {@code yy}
     * are two-letter language tags.
     */
    private static final String OPTIONAL_PATH_PART = "(?:/(?:[a-z]{2}|[a-z]{2}-[a-z]{2}))?";

    /**
     * The pattern matching {@code chat.whatsapp.com/...?code=...}
     * accept-style group invites.
     *
     * @apiNote
     * Mirrors the {@code _} regex in {@code WAWebApiParse}; the
     * second capture group carries the invite code.
     */
    private static final Pattern ACCEPT_GROUP_INVITE = Pattern.compile(
            "^" + WEB_ORIGIN + OPTIONAL_PATH_PART + "/accept/?\\?code=(\\w+)(?:&.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * The pattern matching {@code chat.whatsapp.com/invite/<code>}
     * URLs.
     *
     * @apiNote
     * Mirrors the {@code f} regex in {@code WAWebApiParse}; capture
     * group 1 carries the invite code.
     */
    private static final Pattern INVITE_GROUP_INVITE = Pattern.compile(
            "^https?://chat\\.whatsapp\\.com/invite/(\\w+)(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * The pattern matching {@code chat.whatsapp.com/<code>} short
     * invite URLs.
     *
     * @apiNote
     * Mirrors the {@code g} regex in {@code WAWebApiParse}; capture
     * group 1 carries the invite code.
     */
    private static final Pattern SHORT_GROUP_INVITE = Pattern.compile(
            "^https?://chat\\.whatsapp\\.com/(\\w+)(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * The pattern matching {@code whatsapp://chat?code=<code>} URLs.
     *
     * @apiNote
     * Mirrors the {@code h} regex in {@code WAWebApiParse}; capture
     * group 1 carries the invite code.
     */
    private static final Pattern SCHEME_GROUP_INVITE = Pattern.compile(
            "^whatsapp://chat/?\\?code=(\\w+)(?:&.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * The pattern matching {@code wa.me/c/<jid>} catalog URLs.
     *
     * @apiNote
     * Mirrors the {@code xe} regex in {@code WAWebApiParse}; capture
     * group 1 carries the catalog owner's phone number.
     */
    private static final Pattern WAME_CATALOG = Pattern.compile(
            "^https?://wa\\.me/c/([0-9]{0,20})(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * The pattern matching {@code whatsapp://catalog/<jid>} URLs.
     *
     * @apiNote
     * Mirrors the {@code $e} regex in {@code WAWebApiParse}.
     */
    private static final Pattern SCHEME_CATALOG = Pattern.compile(
            "^whatsapp://catalog/([0-9]{0,20})(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * The pattern matching catalog URLs hosted on the
     * {@code whatsapp.com} origin
     * ({@code https://whatsapp.com/catalog/<jid>}).
     *
     * @apiNote
     * Mirrors the {@code Pe} / {@code Ne} regexes in
     * {@code WAWebApiParse}; collapsed here because both shapes
     * resolve to the same {@link DeepLink.Catalog}.
     */
    private static final Pattern WEB_CATALOG = Pattern.compile(
            "^" + WEB_ORIGIN + OPTIONAL_PATH_PART + "/catalog/([0-9]{0,20})(/?.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * The pattern matching {@code wa.me/p/<productId>/<businessJid>}
     * URLs with numeric product ids.
     *
     * @apiNote
     * Mirrors the {@code j} regex in {@code WAWebApiParse}; capture
     * group 1 carries the product id, group 2 the business owner's
     * phone number.
     */
    private static final Pattern WAME_PRODUCT = Pattern.compile(
            "^https?://wa\\.me/p/([0-9]{0,20})/([0-9]{0,20})(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * The pattern matching
     * {@code whatsapp://product/<productId>/<businessJid>} URLs.
     *
     * @apiNote
     * Mirrors the {@code K} regex in {@code WAWebApiParse}.
     */
    private static final Pattern SCHEME_PRODUCT = Pattern.compile(
            "^whatsapp://product/([0-9]{0,20})/([0-9]{0,20})(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * The pattern matching product URLs hosted on the
     * {@code whatsapp.com} origin
     * ({@code https://whatsapp.com/product/<productId>/<businessJid>}).
     *
     * @apiNote
     * Mirrors the {@code Q} regex in {@code WAWebApiParse}.
     */
    private static final Pattern WEB_PRODUCT = Pattern.compile(
            "^" + WEB_ORIGIN + OPTIONAL_PATH_PART + "/product/([0-9]{0,20})/([0-9]{0,20})(/?.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * The pattern matching
     * {@code wa.me/p/<retailerId>/<businessJid>} URLs where the
     * product identifier is a free-form retailer id.
     *
     * @apiNote
     * Mirrors the {@code Z} regex in {@code WAWebApiParse}; permits
     * any non-slash characters in capture group 1 so non-numeric
     * retailer SKUs match.
     */
    private static final Pattern WAME_PRODUCT_RETAILER = Pattern.compile(
            "^https?://wa\\.me/p/([^/]{0,200})/([0-9]{0,20})(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * The pattern matching
     * {@code whatsapp://product/<retailerId>/<businessJid>} URLs.
     *
     * @apiNote
     * Mirrors the {@code ee} regex in {@code WAWebApiParse}.
     */
    private static final Pattern SCHEME_PRODUCT_RETAILER = Pattern.compile(
            "^whatsapp://product/([^/]{0,200})/([0-9]{0,20})(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * The pattern matching retailer-product URLs hosted on the
     * {@code whatsapp.com} origin.
     *
     * @apiNote
     * Mirrors the {@code te} regex in {@code WAWebApiParse}.
     */
    private static final Pattern WEB_PRODUCT_RETAILER = Pattern.compile(
            "^" + WEB_ORIGIN + OPTIONAL_PATH_PART + "/product/([^/]{0,200})/([0-9]{0,20})(/?.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * The hidden constructor of the utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private DeepLinkParser() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Recognises the deep-link shape of {@code url}.
     *
     * @apiNote
     * Called by the link-preview pipeline before issuing the rich
     * fetch so the catalog, group-invite, and payment branches can
     * short-circuit the og-tag scrape; returns
     * {@link DeepLink.NotApplicable#INSTANCE} for any URL the four
     * supported shapes do not match, leaving the regular fetch path
     * to run.
     *
     * @implNote
     * This implementation tries the group-invite, catalog, and
     * product shapes in JS source order and finally falls back to
     * {@link #paymentLink} so the recognised PSP set tracks the
     * {@link ABProp#SMB_PAYMENT_LINKS_URL_REGEX_LIST} AB-prop rather
     * than being hard-coded.
     *
     * @param client         the WhatsApp client used to derive SMB
     *                       status for the payment-link branch
     * @param abPropsService the AB-props service consulted by the
     *                       payment-link branch
     * @param url            the URL to inspect
     * @return the parsed deep-link, or
     *         {@link DeepLink.NotApplicable#INSTANCE}
     */
    @WhatsAppWebExport(moduleName = "WAWebApiParse", exports = "parseAPICmd",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static DeepLink parse(WhatsAppClient client, ABPropsService abPropsService, String url) {
        if (url == null) {
            return DeepLink.NotApplicable.INSTANCE;
        }
        var inviteAccept = ACCEPT_GROUP_INVITE.matcher(url);
        if (inviteAccept.matches()) {
            return new DeepLink.GroupInvite(inviteAccept.group(2));
        }
        var inviteFull = INVITE_GROUP_INVITE.matcher(url);
        if (inviteFull.matches()) {
            return new DeepLink.GroupInvite(inviteFull.group(1));
        }
        var inviteShort = SHORT_GROUP_INVITE.matcher(url);
        if (inviteShort.matches()) {
            return new DeepLink.GroupInvite(inviteShort.group(1));
        }
        var inviteScheme = SCHEME_GROUP_INVITE.matcher(url);
        if (inviteScheme.matches()) {
            return new DeepLink.GroupInvite(inviteScheme.group(1));
        }
        for (var catalogPattern : new Pattern[]{WAME_CATALOG, SCHEME_CATALOG, WEB_CATALOG}) {
            var matcher = catalogPattern.matcher(url);
            if (matcher.matches()) {
                return new DeepLink.Catalog(matcher.group(1) + "@s.whatsapp.net");
            }
        }
        for (var productPattern : new Pattern[]{WAME_PRODUCT, SCHEME_PRODUCT, WEB_PRODUCT,
                WAME_PRODUCT_RETAILER, SCHEME_PRODUCT_RETAILER, WEB_PRODUCT_RETAILER}) {
            var matcher = productPattern.matcher(url);
            if (matcher.matches()) {
                return new DeepLink.Product(matcher.group(1), matcher.group(2) + "@s.whatsapp.net");
            }
        }
        var payment = paymentLink(client, abPropsService, url);
        if (payment != null) {
            return payment;
        }
        return DeepLink.NotApplicable.INSTANCE;
    }

    /**
     * Returns the {@link DeepLink.PaymentLink} matching {@code url}
     * against the AB-prop-driven PSP regex map, or {@code null} when
     * no configured regex matches.
     *
     * @apiNote
     * Mirrors {@code WAWebPaymentLinkUrlMetaData.getPaymentLinkUrlMetaData};
     * iterated in JSON-declaration order so the first matching entry
     * wins. The {@code shouldDetectInComposer} flag on the returned
     * variant is {@code true} only on SMB clients
     * (Android-Business or iOS-Business), matching the JS
     * {@code o("WAWebMobilePlatforms").isSMB()} call.
     *
     * @param client         the WhatsApp client used to determine SMB
     *                       status from the local device platform
     * @param abPropsService the AB-props service consulted for
     *                       {@link ABProp#SMB_PAYMENT_LINKS_URL_REGEX_LIST}
     * @param url            the URL to inspect
     * @return the matched payment-link deep-link, or {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebPaymentLinkUrlMetaData", exports = "getPaymentLinkUrlMetaData",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static DeepLink.PaymentLink paymentLink(WhatsAppClient client, ABPropsService abPropsService, String url) {
        if (client == null || abPropsService == null || url == null) {
            return null;
        }
        var raw = abPropsService.getString(ABProp.SMB_PAYMENT_LINKS_URL_REGEX_LIST);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        var regexMap = PAYMENT_REGEX_CACHE.computeIfAbsent(raw, DeepLinkParser::parsePaymentRegex);
        if (regexMap.isEmpty()) {
            return null;
        }
        var smb = isSmb(client);
        for (var entry : regexMap.entrySet()) {
            if (entry.getKey().matcher(url).find()) {
                return new DeepLink.PaymentLink(entry.getValue(), smb);
            }
        }
        return null;
    }

    /**
     * Parses the AB-prop JSON value into a regex-to-PSP-label map.
     *
     * @apiNote
     * Called from {@link #paymentLink} via
     * {@link Map#computeIfAbsent(Object, java.util.function.Function)}
     * so the parse happens at most once per distinct AB-prop value.
     *
     * @implNote
     * This implementation silently skips malformed entries (broken
     * regexes, non-string values) to mirror the JS, which iterates
     * the parsed object with a {@code for ... in} loop that ignores
     * any entry whose value is not a string and propagates the
     * {@code SyntaxError} a bad regex would raise at match time, not
     * parse time.
     *
     * @param raw the AB-prop JSON value
     * @return the parsed map, possibly empty
     */
    private static Map<Pattern, String> parsePaymentRegex(String raw) {
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
     * Returns whether the local client runs the SMB (WhatsApp
     * Business) variant.
     *
     * @apiNote
     * Mirrors {@code WAWebMobilePlatforms.isSMB()} which returns
     * {@code true} for {@code SMBA} (Android Business) and
     * {@code SMBI} (iOS Business). The flag decides whether the
     * payment-link card materialises in the composer; non-SMB
     * clients still see the URL but no preview card.
     *
     * @param client the WhatsApp client
     * @return {@code true} when the device platform is one of the
     *         {@code _BUSINESS} variants
     */
    @WhatsAppWebExport(moduleName = "WAWebMobilePlatforms", exports = "isSMB",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static boolean isSmb(WhatsAppClient client) {
        var device = client.store().device();
        if (device == null) {
            return false;
        }
        var platform = device.platform();
        return platform == ClientPlatformType.ANDROID_BUSINESS
                || platform == ClientPlatformType.IOS_BUSINESS;
    }

    /**
     * The tagged union of deep-link shapes the link-preview pipeline
     * dispatches on.
     *
     * @apiNote
     * Switched on by
     * {@link com.github.auties00.cobalt.media.transcode.text.TextPipeline#run};
     * the four concrete variants map to the four
     * link-preview-relevant {@code APICmd} result types from
     * {@code WAWebApi}.
     */
    public sealed interface DeepLink {
        /**
         * The URL is a {@code chat.whatsapp.com} group invite link.
         *
         * @apiNote
         * Dispatched to
         * {@link com.github.auties00.cobalt.media.transcode.text.preview.GroupInvitePreviewResolver}
         * which queries the group metadata via
         * {@code WAWebQueryGroupInfoJob} and downloads the group's
         * profile picture as the inline thumbnail.
         *
         * @param code the group invite code
         */
        record GroupInvite(String code) implements DeepLink {
        }

        /**
         * The URL is a business catalog link without a specific
         * product.
         *
         * @apiNote
         * Dispatched to
         * {@link com.github.auties00.cobalt.media.transcode.text.preview.CatalogPreviewResolver}
         * with a {@code null} product id, which renders the
         * "View {owner}'s catalog on WhatsApp" card.
         *
         * @param catalogOwnerJid the JID of the catalog owner, in
         *                        {@code <number>@s.whatsapp.net} form
         */
        record Catalog(String catalogOwnerJid) implements DeepLink {
        }

        /**
         * The URL is a business product link pointing at a specific
         * product.
         *
         * @apiNote
         * Dispatched to
         * {@link com.github.auties00.cobalt.media.transcode.text.preview.CatalogPreviewResolver}
         * which renders the product name, description, and price as
         * the card.
         *
         * @param productId        the product identifier (may be a
         *                         numeric id or a free-form retailer
         *                         SKU)
         * @param businessOwnerJid the JID of the business owner, in
         *                         {@code <number>@s.whatsapp.net}
         *                         form
         */
        record Product(String productId, String businessOwnerJid) implements DeepLink {
        }

        /**
         * The URL is a payment-link deep link.
         *
         * @apiNote
         * Materialised onto the wire as a
         * {@code PaymentLinkMetadata} carrying the PSP label; the
         * preview card is only rendered when
         * {@link #shouldDetectInComposer()} is {@code true} (SMB
         * clients only), matching WA Web's
         * {@code WAWebPaymentLinkUrlMetaData.isSMB()} check.
         *
         * @param psp                    the payment service provider
         *                               matched from
         *                               {@link ABProp#SMB_PAYMENT_LINKS_URL_REGEX_LIST}
         * @param shouldDetectInComposer whether the composer should
         *                               materialise a payment-link
         *                               card
         */
        record PaymentLink(String psp, boolean shouldDetectInComposer) implements DeepLink {
        }

        /**
         * The URL is a regular web link, not a recognised WhatsApp
         * deep-link.
         *
         * @apiNote
         * Returned for every URL outside the four
         * preview-pipeline-relevant deep-link shapes; the rich-fetch
         * branch of
         * {@link com.github.auties00.cobalt.media.transcode.text.TextPipeline}
         * runs in this case.
         */
        final class NotApplicable implements DeepLink {
            /**
             * The shared singleton.
             *
             * @apiNote
             * Compared against by identity in the switch inside
             * {@link com.github.auties00.cobalt.media.transcode.text.TextPipeline#run};
             * Cobalt never instantiates a second
             * {@code NotApplicable}.
             */
            public static final NotApplicable INSTANCE = new NotApplicable();

            /**
             * The hidden constructor that enforces the singleton.
             */
            private NotApplicable() {
            }
        }
    }
}
