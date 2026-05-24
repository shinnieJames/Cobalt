package com.github.auties00.cobalt.node.mex.json.misc;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.mex.MexOperation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * Inbound parsed response of the
 * {@link FetchNewChatMessageCappingInfoMexRequest} query, exposing the
 * messaging quota counters and per-policy status flags carried by the
 * {@code xwa2_message_capping_info} envelope.
 *
 * @apiNote Drives the individual new-chat messaging capping UI in
 * WA Web, including the per-message warning banner and the throttling
 * gate. WA Web's {@code WAWebGetNewChatMessageCappingInfoJob} persists the
 * snapshot in user-prefs IDB before notifying the frontend; Cobalt
 * callers project the same fields onto their own quota model.
 *
 * @implNote This implementation surfaces every scalar as an
 * {@link Optional} without coercing absent counters to {@code "0"} or
 * coercing the status flags through
 * {@code WAWebIndividualNewChatMessageCappingLimitUtils.getCappingStatusType}
 * and its sibling helpers; embedders perform that mapping themselves so
 * Cobalt does not bake WA Web's IDB shape into the response.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewChatMessageCappingInfoJob")
public final class FetchNewChatMessageCappingInfoMexResponse implements MexOperation.Response.Json {
    /**
     * The {@code total_quota} scalar projected from
     * {@code xwa2_message_capping_info.total_quota}.
     */
    private final String totalQuota;

    /**
     * The {@code used_quota} scalar projected from
     * {@code xwa2_message_capping_info.used_quota}.
     */
    private final String usedQuota;

    /**
     * The {@code cycle_start_timestamp} scalar projected from
     * {@code xwa2_message_capping_info.cycle_start_timestamp}.
     */
    private final String cycleStartTimestamp;

    /**
     * The {@code cycle_end_timestamp} scalar projected from
     * {@code xwa2_message_capping_info.cycle_end_timestamp}.
     */
    private final String cycleEndTimestamp;

    /**
     * The {@code server_sent_timestamp} scalar projected from
     * {@code xwa2_message_capping_info.server_sent_timestamp}.
     */
    private final String serverSentTimestamp;

    /**
     * The {@code ote_status} scalar (one-time-experience policy state)
     * projected from {@code xwa2_message_capping_info.ote_status}.
     */
    private final String oteStatus;

    /**
     * The {@code mv_status} scalar (message-verification policy state)
     * projected from {@code xwa2_message_capping_info.mv_status}.
     */
    private final String mvStatus;

    /**
     * The {@code capping_status} scalar reflecting the active throttle
     * verdict, projected from
     * {@code xwa2_message_capping_info.capping_status}.
     */
    private final String cappingStatus;

    /**
     * Constructs a new response wrapping the parsed scalar fields of the
     * {@code xwa2_message_capping_info} envelope.
     *
     * @apiNote Private; instances are produced by the {@link #of(Node)}
     * parser.
     *
     * @param totalQuota          the {@code total_quota} scalar, may be {@code null}
     * @param usedQuota           the {@code used_quota} scalar, may be {@code null}
     * @param cycleStartTimestamp the {@code cycle_start_timestamp} scalar, may be {@code null}
     * @param cycleEndTimestamp   the {@code cycle_end_timestamp} scalar, may be {@code null}
     * @param serverSentTimestamp the {@code server_sent_timestamp} scalar, may be {@code null}
     * @param oteStatus           the {@code ote_status} scalar, may be {@code null}
     * @param mvStatus            the {@code mv_status} scalar, may be {@code null}
     * @param cappingStatus       the {@code capping_status} scalar, may be {@code null}
     */
    private FetchNewChatMessageCappingInfoMexResponse(String totalQuota, String usedQuota, String cycleStartTimestamp, String cycleEndTimestamp, String serverSentTimestamp, String oteStatus, String mvStatus, String cappingStatus) {
        this.totalQuota = totalQuota;
        this.usedQuota = usedQuota;
        this.cycleStartTimestamp = cycleStartTimestamp;
        this.cycleEndTimestamp = cycleEndTimestamp;
        this.serverSentTimestamp = serverSentTimestamp;
        this.oteStatus = oteStatus;
        this.mvStatus = mvStatus;
        this.cappingStatus = cappingStatus;
    }

    /**
     * Parses the MEX response carried by an inbound IQ stanza.
     *
     * @apiNote Reads the {@code <result>} child's byte content and routes
     * it through the private byte-level parser. Returns
     * {@link Optional#empty()} when the stanza carries no result or when
     * the {@code data.xwa2_message_capping_info} envelope is absent; WA
     * Web's wrapper raises a {@code ServerStatusCodeError(500)} in the
     * same situation.
     *
     * @param node the inbound IQ stanza carrying the {@code <result>} child
     * @return an {@link Optional} wrapping the parsed response, or
     *         {@link Optional#empty()} if the expected JSON shape is absent
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewChatMessageCappingInfoJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<FetchNewChatMessageCappingInfoMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(FetchNewChatMessageCappingInfoMexResponse::of);
    }

    /**
     * Returns the {@code total_quota} scalar, the total number of new
     * chats the account may initiate in the current billing cycle.
     *
     * @return an {@link Optional} containing the total quota, or
     *         {@link Optional#empty()} if the relay omitted the scalar
     */
    public Optional<String> totalQuota() {
        return Optional.ofNullable(totalQuota);
    }

    /**
     * Returns the {@code used_quota} scalar, the number of new chats
     * already initiated in the current billing cycle.
     *
     * @return an {@link Optional} containing the used quota, or
     *         {@link Optional#empty()} if the relay omitted the scalar
     */
    public Optional<String> usedQuota() {
        return Optional.ofNullable(usedQuota);
    }

    /**
     * Returns the {@code cycle_start_timestamp} scalar marking when the
     * current billing cycle began.
     *
     * @return an {@link Optional} containing the cycle-start timestamp,
     *         or {@link Optional#empty()} if the relay omitted the scalar
     */
    public Optional<String> cycleStartTimestamp() {
        return Optional.ofNullable(cycleStartTimestamp);
    }

    /**
     * Returns the {@code cycle_end_timestamp} scalar marking when the
     * current billing cycle expires.
     *
     * @return an {@link Optional} containing the cycle-end timestamp, or
     *         {@link Optional#empty()} if the relay omitted the scalar
     */
    public Optional<String> cycleEndTimestamp() {
        return Optional.ofNullable(cycleEndTimestamp);
    }

    /**
     * Returns the {@code server_sent_timestamp} scalar marking when the
     * relay produced this snapshot.
     *
     * @return an {@link Optional} containing the server-sent timestamp,
     *         or {@link Optional#empty()} if the relay omitted the scalar
     */
    public Optional<String> serverSentTimestamp() {
        return Optional.ofNullable(serverSentTimestamp);
    }

    /**
     * Returns the {@code ote_status} scalar reflecting the one-time-
     * experience policy state.
     *
     * @return an {@link Optional} containing the OTE status, or
     *         {@link Optional#empty()} if the relay omitted the scalar
     */
    public Optional<String> oteStatus() {
        return Optional.ofNullable(oteStatus);
    }

    /**
     * Returns the {@code mv_status} scalar reflecting the message-
     * verification policy state.
     *
     * @return an {@link Optional} containing the MV status, or
     *         {@link Optional#empty()} if the relay omitted the scalar
     */
    public Optional<String> mvStatus() {
        return Optional.ofNullable(mvStatus);
    }

    /**
     * Returns the {@code capping_status} scalar reflecting the active
     * throttle verdict.
     *
     * @return an {@link Optional} containing the capping status, or
     *         {@link Optional#empty()} if the relay omitted the scalar
     */
    public Optional<String> cappingStatus() {
        return Optional.ofNullable(cappingStatus);
    }

    /**
     * Parses the JSON payload carried by the {@code <result>} child into a
     * {@link FetchNewChatMessageCappingInfoMexResponse}.
     *
     * @apiNote Private; routed through {@link #of(Node)} after the byte
     * content of the {@code <result>} child is extracted. Returns
     * {@link Optional#empty()} when the envelope, the {@code data} branch,
     * or the {@code xwa2_message_capping_info} child is absent.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return an {@link Optional} wrapping the parsed response, or
     *         {@link Optional#empty()} if the
     *         {@code data.xwa2_message_capping_info} envelope is absent
     */
    private static Optional<FetchNewChatMessageCappingInfoMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_message_capping_info");
        if (root == null) {
            return Optional.empty();
        }

        var totalQuota = root.getString("total_quota");
        var usedQuota = root.getString("used_quota");
        var cycleStartTimestamp = root.getString("cycle_start_timestamp");
        var cycleEndTimestamp = root.getString("cycle_end_timestamp");
        var serverSentTimestamp = root.getString("server_sent_timestamp");
        var oteStatus = root.getString("ote_status");
        var mvStatus = root.getString("mv_status");
        var cappingStatus = root.getString("capping_status");

        return Optional.of(new FetchNewChatMessageCappingInfoMexResponse(totalQuota, usedQuota, cycleStartTimestamp, cycleEndTimestamp, serverSentTimestamp, oteStatus, mvStatus, cappingStatus));
    }
}
