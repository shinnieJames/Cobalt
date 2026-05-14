package com.github.auties00.cobalt.message.preview.linkify;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.props.ABPropsService;

import java.util.regex.Pattern;

/**
 * Recognises the WhatsApp deep-link patterns that the link-preview
 * pipeline needs to special-case: {@code chat.whatsapp.com} group
 * invites, {@code wa.me/c/} catalog links, {@code wa.me/p/} product
 * links, and {@code wa.me/pay/} payment links. The parser strips the
 * relevant identifiers out of the URL so the catalog, group-invite,
 * and payment branches can fetch the right data.
 *
 * <p>The four shapes covered here are the only ones that affect the
 * link-preview pipeline. WhatsApp Web's {@code parseAPICmd} also
 * recognises about twenty other deep-link shapes (open-chat,
 * call-link, message-yourself, registration campaigns, sticker pack,
 * etc.), all of which fall through the JS pipeline as
 * {@code APICmd.MSG_SEND}/{@code NEW_CHAT}/etc. and are routed by a
 * different module. They never produce a preview card, so the parser
 * surface here is intentionally narrower than WA's: any URL not
 * matched as one of the four supported types yields
 * {@link DeepLink.NotApplicable#INSTANCE} and proceeds to the regular
 * fetch path, mirroring WA's outcome on this code path.
 */
@WhatsAppWebModule(moduleName = "WAWebApiParse")
public final class DeepLinkParser {
    /**
     * Origin prefix accepted as the WhatsApp web origin. Mirrors
     * {@code WAWebApiParseUtils.ORIGIN}.
     */
    private static final String WEB_ORIGIN = "https?://(?:web\\.|chat\\.)?whatsapp\\.com";

    /**
     * Optional {@code /<lang>} segment that may follow the origin.
     */
    private static final String OPTIONAL_PATH_PART = "(?:/(?:[a-z]{2}|[a-z]{2}-[a-z]{2}))?";

    /**
     * {@code chat.whatsapp.com/...} accept-style group invite URLs.
     */
    private static final Pattern ACCEPT_GROUP_INVITE = Pattern.compile(
            "^" + WEB_ORIGIN + OPTIONAL_PATH_PART + "/accept/?\\?code=(\\w+)(?:&.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * {@code chat.whatsapp.com/invite/<code>} URLs.
     */
    private static final Pattern INVITE_GROUP_INVITE = Pattern.compile(
            "^https?://chat\\.whatsapp\\.com/invite/(\\w+)(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * {@code chat.whatsapp.com/<code>} URLs.
     */
    private static final Pattern SHORT_GROUP_INVITE = Pattern.compile(
            "^https?://chat\\.whatsapp\\.com/(\\w+)(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * {@code whatsapp://chat?code=<code>} URLs.
     */
    private static final Pattern SCHEME_GROUP_INVITE = Pattern.compile(
            "^whatsapp://chat/?\\?code=(\\w+)(?:&.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * {@code wa.me/c/<jid>} URLs.
     */
    private static final Pattern WAME_CATALOG = Pattern.compile(
            "^https?://wa\\.me/c/([0-9]{0,20})(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * {@code whatsapp://catalog/<jid>} URLs.
     */
    private static final Pattern SCHEME_CATALOG = Pattern.compile(
            "^whatsapp://catalog/([0-9]{0,20})(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * Catalog URLs hosted on the {@code whatsapp.com} origin: e.g.
     * {@code https://whatsapp.com/catalog/<jid>}.
     */
    private static final Pattern WEB_CATALOG = Pattern.compile(
            "^" + WEB_ORIGIN + OPTIONAL_PATH_PART + "/catalog/([0-9]{0,20})(/?.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * {@code wa.me/p/<productId>/<businessJid>} URLs (numeric ids).
     */
    private static final Pattern WAME_PRODUCT = Pattern.compile(
            "^https?://wa\\.me/p/([0-9]{0,20})/([0-9]{0,20})(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * {@code whatsapp://product/<productId>/<businessJid>} URLs.
     */
    private static final Pattern SCHEME_PRODUCT = Pattern.compile(
            "^whatsapp://product/([0-9]{0,20})/([0-9]{0,20})(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * Product URLs hosted on the {@code whatsapp.com} origin: e.g.
     * {@code https://whatsapp.com/product/<productId>/<businessJid>}.
     */
    private static final Pattern WEB_PRODUCT = Pattern.compile(
            "^" + WEB_ORIGIN + OPTIONAL_PATH_PART + "/product/([0-9]{0,20})/([0-9]{0,20})(/?.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * {@code wa.me/p/<retailerId>/<businessJid>} URLs where the
     * product identifier is a free-form retailer id (non-numeric).
     */
    private static final Pattern WAME_PRODUCT_RETAILER = Pattern.compile(
            "^https?://wa\\.me/p/([^/]{0,200})/([0-9]{0,20})(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * {@code whatsapp://product/<retailerId>/<businessJid>} URLs.
     */
    private static final Pattern SCHEME_PRODUCT_RETAILER = Pattern.compile(
            "^whatsapp://product/([^/]{0,200})/([0-9]{0,20})(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * Retailer-product URLs hosted on the {@code whatsapp.com} origin.
     */
    private static final Pattern WEB_PRODUCT_RETAILER = Pattern.compile(
            "^" + WEB_ORIGIN + OPTIONAL_PATH_PART + "/product/([^/]{0,200})/([0-9]{0,20})(/?.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * Hidden constructor for the utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private DeepLinkParser() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Recognises the deep-link shape of {@code url} and returns the
     * matching {@link DeepLink}, or {@link DeepLink.NotApplicable#INSTANCE}
     * when the URL is a regular web link.
     *
     * <p>The {@link DeepLink.PaymentLink} branch dispatches to
     * {@link PaymentLinkResolver} so the recognised PSP set tracks
     * the {@code SMB_PAYMENT_LINKS_URL_REGEX_LIST} AB-prop instead of
     * being hard-coded.
     *
     * @param client         the WhatsApp client used to resolve the SMB
     *                       flag from the local device platform
     * @param abPropsService the AB-props service used to read the
     *                       payment-link regex list AB-prop
     * @param url            the URL to inspect
     * @return the parsed deep-link, or {@code NotApplicable}
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
        var payment = PaymentLinkResolver.resolve(client, abPropsService, url).orElse(null);
        if (payment != null) {
            return new DeepLink.PaymentLink(payment.psp(), payment.shouldDetectInComposer());
        }
        return DeepLink.NotApplicable.INSTANCE;
    }

    /**
     * Tagged union of the deep-link shapes the preview pipeline cares
     * about.
     */
    public sealed interface DeepLink {
        /**
         * The URL is a {@code chat.whatsapp.com} group invite link.
         *
         * @param code the group invite code
         */
        record GroupInvite(String code) implements DeepLink {
        }

        /**
         * The URL is a business catalog link.
         *
         * @param catalogOwnerJid the JID of the catalog owner, in
         *                        {@code <number>@s.whatsapp.net} form
         */
        record Catalog(String catalogOwnerJid) implements DeepLink {
        }

        /**
         * The URL is a business product link.
         *
         * @param productId        the product identifier
         * @param businessOwnerJid the JID of the business owner, in
         *                         {@code <number>@s.whatsapp.net} form
         */
        record Product(String productId, String businessOwnerJid) implements DeepLink {
        }

        /**
         * The URL is a payment deep-link.
         *
         * @param psp                   the resolved payment service
         *                              provider matched out of
         *                              {@code ABProp.SMB_PAYMENT_LINKS_URL_REGEX_LIST}
         * @param shouldDetectInComposer whether the composer should
         *                              materialise a payment-link card
         *                              for this URL; mirrors
         *                              {@code WAWebPaymentLinkUrlMetaData.getPaymentLinkUrlMetaData}'s
         *                              SMB-only flag
         */
        record PaymentLink(String psp, boolean shouldDetectInComposer) implements DeepLink {
        }

        /**
         * The URL is a regular web link, not a WhatsApp deep-link.
         */
        final class NotApplicable implements DeepLink {
            /**
             * Shared singleton returned for any URL that is not a
             * recognised WhatsApp deep-link.
             */
            public static final NotApplicable INSTANCE = new NotApplicable();

            /**
             * Hidden constructor that enforces the singleton.
             */
            private NotApplicable() {
            }
        }
    }
}
