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
 * The outbound {@code <iq xmlns="tos" type="get">} stanza that polls the
 * relay for the current acceptance stage of a specific set of
 * disclosures.
 *
 * @apiNote
 * Built by Cobalt's targeted TOS-poll path, the counterpart of WA Web's
 * use in {@code WAWebBizBroadcastTos} where the soft-opt-in sync hits the
 * relay with the single biz-broadcast disclosure id. Unlike
 * {@link SmaxUserNoticeGetDisclosuresRequest}, which returns every
 * outstanding disclosure, this RPC narrows the query to a known id set
 * and is used by background jobs that only care about one particular
 * disclosure's progression.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutUserNoticeGetDisclosureStageByIdsRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutUserNoticeBaseIQGetRequestMixin")
public final class SmaxUserNoticeGetDisclosureStageByIdsRequest implements SmaxOperation.Request {
    /**
     * The per-disclosure queries that will appear as
     * {@code <get_disclosure_stage_by_id>} children.
     */
    private final List<DisclosureStageQuery> queries;

    /**
     * Constructs a request.
     *
     * @apiNote
     * Pass one {@link DisclosureStageQuery} per disclosure id to poll;
     * the relay returns a parallel list of stage entries in the reply.
     *
     * @param queries the per-disclosure queries; defaults to an empty
     *                list when {@code null}
     * @throws NullPointerException if {@code queries} is {@code null}
     */
    public SmaxUserNoticeGetDisclosureStageByIdsRequest(List<DisclosureStageQuery> queries) {
        Objects.requireNonNull(queries, "queries cannot be null");
        this.queries = List.copyOf(queries);
    }

    /**
     * Returns the per-disclosure queries.
     *
     * @apiNote
     * Exposed for test and audit code; the list is unmodifiable.
     *
     * @return an unmodifiable {@link List} of {@link DisclosureStageQuery}
     */
    public List<DisclosureStageQuery> queries() {
        return queries;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation hard-codes {@code xmlns="tos"},
     * {@code type="get"}, and {@code to=s.whatsapp.net} per the
     * {@code WASmaxOutUserNoticeGetDisclosureStageByIdsRequest.makeGetDisclosureStageByIdsRequest}
     * fixture, then nests one
     * {@code <get_disclosure_stage_by_id id t/>} child per query.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutUserNoticeGetDisclosureStageByIdsRequest",
            exports = "makeGetDisclosureStageByIdsRequest",
            adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var iqBuilder = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "tos")
                .attribute("to", JidServer.user())
                .attribute("type", "get");
        for (var query : queries) {
            var child = new NodeBuilder()
                    .description("get_disclosure_stage_by_id")
                    .attribute("id", query.disclosureId())
                    .attribute("t", query.timestampSeconds())
                    .build();
            iqBuilder.content(child);
        }
        return iqBuilder;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation compares the queries list.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxUserNoticeGetDisclosureStageByIdsRequest) obj;
        return Objects.equals(this.queries, that.queries);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation hashes the queries list.
     */
    @Override
    public int hashCode() {
        return Objects.hash(queries);
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
        return "SmaxUserNoticeGetDisclosureStageByIdsRequest[queries=" + queries + ']';
    }

    /**
     * One {@code <get_disclosure_stage_by_id id t/>} child.
     *
     * @apiNote
     * Pairs a disclosure id (the value of
     * {@code WAWebBizBroadcastTos}'s {@code "20250915"}-style date code,
     * for example) with the client-side timestamp the relay uses to
     * decide whether the cached stage is still fresh.
     */
    public static final class DisclosureStageQuery {
        /**
         * The disclosure id to query.
         */
        private final long disclosureId;

        /**
         * The client-side timestamp in seconds since the UNIX epoch.
         */
        private final long timestampSeconds;

        /**
         * Constructs a per-disclosure query.
         *
         * @apiNote
         * Pass the current wall-clock time in seconds for
         * {@code timestampSeconds}; the relay uses it as a freshness
         * input.
         *
         * @param disclosureId     the disclosure id
         * @param timestampSeconds the client-side timestamp
         */
        public DisclosureStageQuery(long disclosureId, long timestampSeconds) {
            this.disclosureId = disclosureId;
            this.timestampSeconds = timestampSeconds;
        }

        /**
         * Returns the disclosure id.
         *
         * @apiNote
         * Used by
         * {@link SmaxUserNoticeGetDisclosureStageByIdsRequest#toNode()}
         * to populate the {@code id} attribute.
         *
         * @return the id
         */
        public long disclosureId() {
            return disclosureId;
        }

        /**
         * Returns the client-side timestamp.
         *
         * @apiNote
         * Used by
         * {@link SmaxUserNoticeGetDisclosureStageByIdsRequest#toNode()}
         * to populate the {@code t} attribute.
         *
         * @return the timestamp in seconds
         */
        public long timestampSeconds() {
            return timestampSeconds;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation compares both fields.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (DisclosureStageQuery) obj;
            return this.disclosureId == that.disclosureId
                    && this.timestampSeconds == that.timestampSeconds;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation hashes both fields via
         * {@link Objects#hash(Object...)}.
         */
        @Override
        public int hashCode() {
            return Objects.hash(disclosureId, timestampSeconds);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation mirrors the record-like rendering used
         * across the {@code Smax*} stanza family.
         */
        @Override
        public String toString() {
            return "SmaxUserNoticeGetDisclosureStageByIdsRequest.DisclosureStageQuery[disclosureId="
                    + disclosureId + ", timestampSeconds=" + timestampSeconds + ']';
        }
    }
}
