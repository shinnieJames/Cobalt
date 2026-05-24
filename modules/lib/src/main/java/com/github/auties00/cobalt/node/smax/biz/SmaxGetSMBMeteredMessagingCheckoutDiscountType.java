package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The {@code type} enum carried by the {@code <discount/>}
 * grandchildren of the {@code <discounts/>} child in the SMB
 * metered-messaging checkout success reply.
 *
 * @apiNote
 * Surfaced as the typed projection of each discount line on the
 * checkout quote returned by
 * {@code WAWebGetSMBMeteredMessagingCheckoutJob}; callers branch
 * on {@link #FREEMSG} to render a free-message count and on
 * {@link #PERCENTAGE} to render a percentage-off marker.
 *
 * @implNote
 * This implementation accepts only the case-insensitive literals
 * surfaced by
 * {@code WASmaxInSmbMeteredMessagingAccountEnums.ENUM_FREEMSG_PERCENTAGE}.
 */
@WhatsAppWebModule(moduleName = "WASmaxInSmbMeteredMessagingAccountEnums")
public enum SmaxGetSMBMeteredMessagingCheckoutDiscountType {
    /**
     * Free-message discount line.
     *
     * @apiNote
     * The discount is delivered as a number of free messages
     * granted to the campaign.
     */
    FREEMSG,
    /**
     * Percentage-off discount line.
     *
     * @apiNote
     * The discount is a percentage off the per-message rate
     * surfaced via the sibling {@code percentage} attribute.
     */
    PERCENTAGE;

    /**
     * Resolves a wire-form attribute string into the matching enum
     * constant.
     *
     * @apiNote
     * Invoked while parsing the {@code <discount type>} attribute
     * on each entry of the cost-discounts list in the metered
     * messaging checkout success reply.
     *
     * @implNote
     * This implementation matches the wire literals directly because the
     * {@code free_msg} value carries an underscore that {@link String#toUpperCase}
     * preserves, so a naive {@link #valueOf(String)} dispatch on the
     * upper-cased form would never match the {@link #FREEMSG} constant.
     *
     * @param value the attribute value; may be {@code null}
     * @return an {@link Optional} carrying the matching enum
     *         constant, or empty when the value is {@code null} or
     *         does not match a documented literal
     */
    public static Optional<SmaxGetSMBMeteredMessagingCheckoutDiscountType> of(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return switch (value) {
            case "free_msg" -> Optional.of(FREEMSG);
            case "percentage" -> Optional.of(PERCENTAGE);
            default -> Optional.empty();
        };
    }
}
