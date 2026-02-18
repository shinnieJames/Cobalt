package com.github.auties00.cobalt.model.business.catalog;

import it.auties.protobuf.annotation.ProtobufEnum;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The compliance review status of a product or collection in a WhatsApp
 * Business catalog.
 *
 * <p>Products and collections submitted to the catalog undergo a
 * compliance review process before they become visible to customers. In
 * the WhatsApp Web client, product review statuses are validated by the
 * {@code asProductReviewType} function in {@code WAWebProductTypes.flow},
 * which accepts the canonical values {@code "APPROVED"},
 * {@code "PENDING"}, and {@code "REJECTED"}. For collections, the
 * {@code mapCollectionReviewStatusToWASchema} function maps prefixed
 * forms such as {@code "STATUS_APPROVED"} to their canonical
 * counterparts. Review statuses are parsed from the
 * {@code status_info.status} field in catalog query responses.
 *
 * <p>When a product is rejected, the business owner may be able to
 * appeal the decision. The WhatsApp Web client determines whether an
 * appeal is available through the {@code can_appeal} field in the
 * {@code status_info} object of catalog query responses, exposed as the
 * {@code whatsapp_product_can_appeal} property in the parsed product
 * model.
 */
@ProtobufEnum
public enum BusinessReviewStatus {
    /**
     * No compliance review has been performed on this item. This value
     * is used when the review process has not yet been initiated for the
     * product or collection.
     */
    NO_REVIEW,

    /**
     * The item has been submitted and is awaiting compliance review by
     * WhatsApp. This corresponds to the {@code "PENDING"} canonical
     * value recognized by the {@code asProductReviewType} function in
     * {@code WAWebProductTypes.flow} and to the
     * {@code "STATUS_PENDING"} prefixed form used for collections.
     */
    PENDING,

    /**
     * The item did not pass compliance review and is not visible to
     * customers. This corresponds to the {@code "REJECTED"} canonical
     * value recognized by the {@code asProductReviewType} function in
     * {@code WAWebProductTypes.flow} and to the
     * {@code "STATUS_REJECTED"} prefixed form used for collections.
     * Depending on the rejection reason, the business owner may be able
     * to appeal the decision, as indicated by the {@code can_appeal}
     * field in the {@code status_info} object of catalog query
     * responses.
     */
    REJECTED,

    /**
     * The item passed compliance review and is visible to customers.
     * This corresponds to the {@code "APPROVED"} canonical value
     * recognized by the {@code asProductReviewType} function in
     * {@code WAWebProductTypes.flow} and to the
     * {@code "STATUS_APPROVED"} prefixed form used for collections.
     * In the WhatsApp Web client, the most recently approved and
     * non-hidden product is selected by the
     * {@code getMostRecentlyApprovedProduct} method on the catalog
     * model.
     */
    APPROVED,

    /**
     * The item's review status is outdated and a new review cycle may
     * be required. This value indicates that the previous review
     * determination is no longer current.
     */
    OUTDATED;

    /**
     * A lookup map from lowercase status names to their corresponding
     * enum constants. Used by {@link #ofName(String)} for
     * case-insensitive name resolution.
     */
    private static final Map<String, BusinessReviewStatus> PRETTY_NAME_TO_REVIEW_STATUS = Arrays.stream(BusinessReviewStatus.values())
            .collect(Collectors.toMap(entry -> entry.name().toLowerCase(), Function.identity()));

    /**
     * Returns the review status corresponding to the given name.
     *
     * <p>The name is matched case-insensitively against the lowercase
     * form of each constant's name (e.g. {@code "approved"},
     * {@code "pending"}, {@code "rejected"}).
     *
     * @param reviewStatus the review status name to look up
     * @return an {@code Optional} describing the matching status, or an
     *         empty {@code Optional} if {@code reviewStatus} is
     *         {@code null} or no match is found
     */
    public static Optional<BusinessReviewStatus> ofName(String reviewStatus) {
        return reviewStatus == null
                ? Optional.empty()
                : Optional.ofNullable(PRETTY_NAME_TO_REVIEW_STATUS.get(reviewStatus.toLowerCase()));
    }
}
