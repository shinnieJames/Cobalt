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
 * A single {@code <campaign/>} grandchild of the outbound
 * {@code <pending_campaigns>} block on the SMB metered-messaging
 * checkout request.
 *
 * @apiNote
 * Used by Cobalt clients composing a
 * {@link SmaxGetSMBMeteredMessagingCheckoutRequest} to declare a
 * previously-reserved send whose free-message impact must be
 * accounted for in the new quote; the relay subtracts the declared
 * reservations from the campaign's free-message allowance before
 * computing the cost.
 *
 * @implNote
 * This implementation mirrors WA Web's
 * {@code makeGetSMBMeteredMessagingCheckoutRequestPendingCampaignsCampaign}
 * by emitting a {@code <campaign/>} child with the mandatory
 * {@code free_reserved_msgs} attribute and the optional
 * {@code send_timestamp} attribute; the parent
 * {@code <pending_campaigns/>} block accepts a repeated list of up
 * to 200 entries on the WA Web side.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutSmbMeteredMessagingAccountGetSMBMeteredMessagingCheckoutRequest")
public final class SmaxGetSMBMeteredMessagingCheckoutPendingCampaign {
    /**
     * The number of free reserved messages on the previously-issued
     * campaign; mirrors the WA Web
     * {@code campaignFreeReservedMsgs} field.
     */
    private final int freeReservedMsgs;

    /**
     * The optional send-timestamp of the previously-issued campaign
     * (epoch seconds); mirrors the WA Web
     * {@code campaignSendTimestamp} optional field.
     */
    private final Integer sendTimestamp;

    /**
     * Constructs a new entry.
     *
     * @apiNote
     * Invoked by callers assembling the {@code pending_campaigns}
     * list before issuing
     * {@link SmaxGetSMBMeteredMessagingCheckoutRequest}; one entry
     * per previously-reserved send to be accounted for.
     *
     * @param freeReservedMsgs the number of reserved messages
     * @param sendTimestamp    the optional send timestamp; may be
     *                         {@code null}
     */
    public SmaxGetSMBMeteredMessagingCheckoutPendingCampaign(int freeReservedMsgs, Integer sendTimestamp) {
        this.freeReservedMsgs = freeReservedMsgs;
        this.sendTimestamp = sendTimestamp;
    }

    /**
     * Returns the reserved-message count.
     *
     * @return the count
     */
    public int freeReservedMsgs() {
        return freeReservedMsgs;
    }

    /**
     * Returns the optional send timestamp.
     *
     * @apiNote
     * Empty when the entry was constructed without an associated
     * timestamp; the value is epoch seconds.
     *
     * @return an {@link OptionalInt} carrying the timestamp, or
     *         empty when the entry omitted it
     */
    public OptionalInt sendTimestamp() {
        if (sendTimestamp == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(sendTimestamp);
    }

    /**
     * Builds the {@code <campaign/>} child node.
     *
     * @implNote
     * This implementation stamps the mandatory
     * {@code free_reserved_msgs} attribute and emits
     * {@code send_timestamp} only when supplied, matching WA Web's
     * {@code OPTIONAL(INT, ...)} guard on the
     * {@code campaignSendTimestamp} field.
     *
     * @return the materialised {@link Node}
     */
    public Node toNode() {
        var builder = new NodeBuilder()
                .description("campaign")
                .attribute("free_reserved_msgs", freeReservedMsgs);
        if (sendTimestamp != null) {
            builder.attribute("send_timestamp", sendTimestamp.intValue());
        }
        return builder.build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxGetSMBMeteredMessagingCheckoutPendingCampaign) obj;
        return this.freeReservedMsgs == that.freeReservedMsgs
                && Objects.equals(this.sendTimestamp, that.sendTimestamp);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(freeReservedMsgs, sendTimestamp);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "SmaxGetSMBMeteredMessagingCheckoutPendingCampaign[freeReservedMsgs=" + freeReservedMsgs
                + ", sendTimestamp=" + sendTimestamp + ']';
    }
}
