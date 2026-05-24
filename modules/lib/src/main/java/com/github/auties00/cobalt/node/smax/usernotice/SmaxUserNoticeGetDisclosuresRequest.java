package com.github.auties00.cobalt.node.smax.usernotice;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <iq xmlns="tos" type="get">} stanza that asks the
 * relay for the user-facing legal disclosures the account must
 * acknowledge.
 *
 * @apiNote
 * Built by Cobalt's TOS-prompt path, the counterpart of WA Web's
 * {@code WAWebGetUserDisclosuresQueryJob.queryAllUserDisclosures}. The
 * relay returns one {@code <notice>} per outstanding disclosure
 * (terms-of-service updates, regional privacy notices, biz-broadcast
 * opt-in prompts, etc.); embedders surface these to their UI so the user
 * can read and accept.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutUserNoticeGetDisclosuresRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutUserNoticeBaseIQGetRequestMixin")
public final class SmaxUserNoticeGetDisclosuresRequest implements SmaxOperation.Request {
    /**
     * The client-side fetch timestamp (seconds since the UNIX epoch) the
     * relay uses to decide which disclosures to return.
     */
    private final long getUserDisclosuresT;

    /**
     * Constructs a request.
     *
     * @apiNote
     * Pass the current wall-clock time in seconds (WA Web uses
     * {@code WATimeUtils.unixTime()}); the relay uses the timestamp to
     * decide which disclosures to surface.
     *
     * @param getUserDisclosuresT the fetch timestamp in seconds
     */
    public SmaxUserNoticeGetDisclosuresRequest(long getUserDisclosuresT) {
        this.getUserDisclosuresT = getUserDisclosuresT;
    }

    /**
     * Returns the client-side fetch timestamp.
     *
     * @apiNote
     * Used by {@link #toNode()} to populate the {@code t} attribute on
     * the {@code <get_user_disclosures>} child.
     *
     * @return the timestamp in seconds
     */
    public long getUserDisclosuresT() {
        return getUserDisclosuresT;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation hard-codes {@code xmlns="tos"},
     * {@code type="get"}, and {@code to=s.whatsapp.net} per the
     * {@code WASmaxOutUserNoticeGetDisclosuresRequest.makeGetDisclosuresRequest}
     * fixture, then nests a single {@code <get_user_disclosures t="..."/>}
     * child carrying the timestamp.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutUserNoticeGetDisclosuresRequest",
            exports = "makeGetDisclosuresRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var getUserDisclosuresNode = new NodeBuilder()
                .description("get_user_disclosures")
                .attribute("t", getUserDisclosuresT)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "tos")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(getUserDisclosuresNode);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation compares the fetch timestamp.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxUserNoticeGetDisclosuresRequest) obj;
        return this.getUserDisclosuresT == that.getUserDisclosuresT;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation hashes the fetch timestamp.
     */
    @Override
    public int hashCode() {
        return Objects.hash(getUserDisclosuresT);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation mirrors the record-like rendering used across
     * the {@code Smax*} stanza family.
     */
    @Override
    public String toString() {
        return "SmaxUserNoticeGetDisclosuresRequest[getUserDisclosuresT=" + getUserDisclosuresT + ']';
    }
}
