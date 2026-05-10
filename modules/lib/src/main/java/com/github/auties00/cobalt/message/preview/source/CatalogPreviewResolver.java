package com.github.auties00.cobalt.message.preview.source;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.BusinessVerifiedName;
import com.github.auties00.cobalt.model.business.catalog.BusinessCatalogEntry;
import com.github.auties00.cobalt.model.business.catalog.BusinessReviewStatus;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessage;
import com.github.auties00.cobalt.message.preview.PreviewThumbnailFetcher;
import com.github.auties00.cobalt.message.preview.model.LinkDetails;
import com.github.auties00.cobalt.message.preview.model.LinkThumbnail;
import com.github.auties00.cobalt.message.preview.model.ResolvedPreview;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Builds preview details for {@code wa.me/c/...} catalog and
 * {@code wa.me/p/.../...} product links by querying the server-side
 * catalog through {@link WhatsAppClient#queryBusinessCatalog(Jid)}.
 */
@WhatsAppWebModule(moduleName = "WAWebBizLinkPreviewCatalogUtils")
public final class CatalogPreviewResolver {
    /**
     * Hidden constructor for the utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private CatalogPreviewResolver() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Resolves the preview for a catalog or product URL.
     *
     * @param client     the WhatsApp client used to query the server
     * @param ownerJid   the catalog owner JID (in
     *                   {@code <number>@s.whatsapp.net} form)
     * @param productId  the optional product identifier; when
     *                   {@code null} the first approved-and-visible
     *                   product is selected
     * @param httpClient the HTTP client used to download the product
     *                   image
     * @param timeout    the per-download timeout
     * @return the preview details and inline thumbnail, or empty when
     *         the lookup failed or no eligible product was found
     */
    @WhatsAppWebExport(moduleName = "WAWebBizLinkPreviewCatalogUtils", exports = "getProductOrCatalogLinkPreview",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<ResolvedPreview> resolve(WhatsAppClient client, String ownerJid, String productId,
                                                    HttpClient httpClient, Duration timeout) {
        if (client == null || ownerJid == null) {
            return Optional.empty();
        }
        Jid wid;
        try {
            wid = Jid.of(ownerJid);
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
        var catalog = safeQueryCatalog(client, wid);
        if (catalog.isEmpty()) {
            return Optional.empty();
        }
        var product = pickProduct(catalog, productId);
        if (product == null) {
            return Optional.empty();
        }
        var ownerName = ownerDisplayName(client, wid);
        var hasProductFocus = productId != null;
        var title = hasProductFocus
                ? "%s from %s on WhatsApp.".formatted(product.name().orElse(""), ownerName)
                : "View %s's catalog on WhatsApp".formatted(ownerName);
        var description = hasProductFocus
                ? buildProductDescription(product)
                : "Learn more about their products & services";
        var details = new LinkDetails(
                title,
                description,
                ExtendedTextMessage.PreviewType.NONE,
                true);
        var imageUri = product.encryptedImage().orElse(null);
        var thumbnailBytes = imageUri == null ? null
                : PreviewThumbnailFetcher.download(httpClient, imageUri, timeout);
        var thumbnail = thumbnailBytes == null ? null
                : new LinkThumbnail(thumbnailBytes, null, null, null, null, null, null, null);
        return Optional.of(new ResolvedPreview(details, thumbnail));
    }

    /**
     * Issues the catalog query, swallowing any transport-level error so
     * the link preview pipeline can fall back to the minimal preview.
     *
     * @param client the WhatsApp client used to query the server
     * @param wid    the catalog owner JID
     * @return the catalog entries, or empty when the query failed
     */
    private static List<BusinessCatalogEntry> safeQueryCatalog(WhatsAppClient client, Jid wid) {
        try {
            return client.queryBusinessCatalog(wid);
        } catch (RuntimeException _) {
            return List.of();
        }
    }

    /**
     * Picks the product to render in the preview: when
     * {@code productId} is supplied the matching entry, otherwise the
     * first approved and visible entry.
     *
     * @param catalog   the catalog entries
     * @param productId the product identifier, or {@code null}
     * @return the picked entry, or {@code null} when none is eligible
     */
    private static BusinessCatalogEntry pickProduct(List<BusinessCatalogEntry> catalog, String productId) {
        for (var entry : catalog) {
            if (productId != null && !productId.equals(entry.id())) {
                continue;
            }
            if (entry.reviewStatus().orElse(null) != BusinessReviewStatus.APPROVED) {
                continue;
            }
            if (entry.hidden()) {
                continue;
            }
            return entry;
        }
        return null;
    }

    /**
     * Resolves the display name of the catalog owner.
     *
     * <p>Mirrors WA Web's branching: when the catalog owner is the
     * current user, the local contact's display name is used; for any
     * other JID, the cached verified-business name has priority,
     * followed by the cached contact's pushname, and finally a fresh
     * usync round-trip via
     * {@link WhatsAppClient#queryBusinessProfile} which surfaces the
     * server-supplied {@code BusinessVerifiedName}.
     *
     * @param client the WhatsApp client whose stores and usync are
     *               consulted
     * @param wid    the catalog owner JID
     * @return the display name, falling back to the JID's user portion
     *         when nothing else resolves
     */
    @WhatsAppWebExport(moduleName = "WAWebGetOrQueryUsyncInfoContactAction", exports = "getOrQueryUsyncInfo",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static String ownerDisplayName(WhatsAppClient client, Jid wid) {
        var contactName = client.store().findContactByJid(wid)
                .flatMap(contact -> contact.chosenName().or(contact::shortName))
                .orElse(null);
        if (contactName != null && !contactName.isEmpty()) {
            return contactName;
        }
        var verified = client.store().findVerifiedBusinessName(wid)
                .flatMap(BusinessVerifiedName::name)
                .orElse(null);
        if (verified != null && !verified.isEmpty()) {
            return verified;
        }
        try {
            client.queryBusinessProfile(wid);
            var refreshed = client.store().findVerifiedBusinessName(wid)
                    .flatMap(BusinessVerifiedName::name)
                    .orElse(null);
            if (refreshed != null && !refreshed.isEmpty()) {
                return refreshed;
            }
        } catch (RuntimeException _) {
        }
        return wid.user();
    }

    /**
     * Builds the description rendered for a single-product preview.
     *
     * @param product the catalog entry
     * @return the description string
     */
    private static String buildProductDescription(BusinessCatalogEntry product) {
        var description = product.description().orElse(null);
        var currency = product.currency().orElse(null);
        var price = product.price();
        if (description != null && currency != null && price > 0) {
            return description + " · " + formatAmount(currency, price);
        }
        if (description != null) {
            return description;
        }
        if (currency != null && price > 0) {
            return formatAmount(currency, price);
        }
        return "";
    }

    /**
     * Formats a price expressed in thousandths of the major currency
     * unit, mirroring {@code WAWebCurrencyUtils.formatAmount1000}.
     *
     * @param currency the ISO 4217 currency code
     * @param amount   the amount in thousandths
     * @return the formatted amount
     */
    @WhatsAppWebExport(moduleName = "WAWebCurrencyUtils", exports = "formatAmount1000",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static String formatAmount(String currency, long amount) {
        var major = amount / 1000d;
        return String.format(Locale.US, "%.2f %s", major, currency);
    }
}
