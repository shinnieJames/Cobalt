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
 * The {@code is_eligible} boolean enum carried by the
 * {@code <integrity/>} child of the SMB metered-messaging
 * checkout success reply.
 *
 * @apiNote
 * Surfaces the integrity-review verdict on the checkout quote
 * returned by
 * {@code WAWebGetSMBMeteredMessagingCheckoutJob}; the WA Web
 * compose surface blocks the broadcast send when this value is
 * {@link #FALSE} and admits it when {@link #TRUE}.
 *
 * @implNote
 * This implementation accepts only the case-insensitive literals
 * {@code "TRUE"} and {@code "FALSE"} surfaced by
 * {@code WASmaxInSmbMeteredMessagingAccountEnums.ENUM_FALSE_TRUE}.
 */
@WhatsAppWebModule(moduleName = "WASmaxInSmbMeteredMessagingAccountEnums")
public enum SmaxGetSMBMeteredMessagingCheckoutIntegrityEligibility {
    /**
     * Wire form {@code "false"}.
     *
     * @apiNote
     * The campaign is held back by integrity review; the compose
     * surface disables the send action.
     */
    FALSE,
    /**
     * Wire form {@code "true"}.
     *
     * @apiNote
     * The campaign has cleared integrity review.
     */
    TRUE;

    /**
     * Resolves a wire-form attribute string into the matching enum
     * constant.
     *
     * @apiNote
     * Invoked while parsing the {@code <integrity is_eligible>}
     * attribute on the metered messaging checkout success reply.
     *
     * @implNote
     * This implementation upper-cases the input under
     * {@link Locale#ROOT} before delegating to
     * {@link #valueOf(String)} and swallows the resulting
     * {@link IllegalArgumentException} as an empty result.
     *
     * @param value the attribute value; may be {@code null}
     * @return an {@link Optional} carrying the matching enum
     *         constant, or empty when the value is {@code null} or
     *         does not match a documented literal
     */
    public static Optional<SmaxGetSMBMeteredMessagingCheckoutIntegrityEligibility> of(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(SmaxGetSMBMeteredMessagingCheckoutIntegrityEligibility.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
