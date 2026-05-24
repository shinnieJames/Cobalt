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
 * Inbound parsed response of the {@link FetchReachoutTimelockMexRequest}
 * query, exposing the reachout-timelock enforcement state carried by the
 * {@code xwa2_fetch_account_reachout_timelock} envelope.
 *
 * @apiNote Drives WA Web's reach-out gating UI; the matching call-site
 * forwards the three scalars to
 * {@code WAWebMexReachoutTimelockNotificationHandler.handleReachoutTimelockUpdate}
 * which produces the user-visible enforcement notification. Cobalt
 * embedders may project the same scalars onto their own enforcement
 * model.
 *
 * @implNote This implementation surfaces {@link #isActive()} as a
 * primitive {@code boolean} that defaults to {@code false} when the relay
 * omits the field (matching WA Web's downstream cast), while the two
 * string scalars stay {@link Optional} so absence is observable.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchReachoutTimelockJob")
public final class FetchReachoutTimelockMexResponse implements MexOperation.Response.Json {
    /**
     * The {@code is_active} scalar reflecting whether the timelock is
     * currently being enforced.
     */
    private final Boolean isActive;

    /**
     * The {@code time_enforcement_ends} scalar marking when the
     * enforcement window expires.
     */
    private final String timeEnforcementEnds;

    /**
     * The {@code enforcement_type} scalar classifying the enforcement
     * severity (for example soft warning, hard block).
     */
    private final String enforcementType;

    /**
     * Constructs a new response wrapping the parsed scalar fields of the
     * {@code xwa2_fetch_account_reachout_timelock} envelope.
     *
     * @apiNote Private; instances are produced by the {@link #of(Node)}
     * parser.
     *
     * @param isActive             the {@code is_active} scalar, may be {@code null}
     * @param timeEnforcementEnds  the {@code time_enforcement_ends} scalar, may be {@code null}
     * @param enforcementType      the {@code enforcement_type} scalar, may be {@code null}
     */
    private FetchReachoutTimelockMexResponse(Boolean isActive, String timeEnforcementEnds, String enforcementType) {
        this.isActive = isActive;
        this.timeEnforcementEnds = timeEnforcementEnds;
        this.enforcementType = enforcementType;
    }

    /**
     * Parses the MEX response carried by an inbound IQ stanza.
     *
     * @apiNote Reads the {@code <result>} child's byte content and routes
     * it through the private byte-level parser. Returns
     * {@link Optional#empty()} when the stanza carries no result or when
     * the {@code data.xwa2_fetch_account_reachout_timelock} envelope is
     * absent; WA Web's wrapper raises a {@code ServerStatusCodeError(500)}
     * in the same situation.
     *
     * @param node the inbound IQ stanza carrying the {@code <result>} child
     * @return an {@link Optional} wrapping the parsed response, or
     *         {@link Optional#empty()} if the expected JSON shape is absent
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchReachoutTimelockJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<FetchReachoutTimelockMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(FetchReachoutTimelockMexResponse::of);
    }

    /**
     * Returns whether the reachout timelock is currently being enforced.
     *
     * @apiNote A {@code null} scalar on the wire collapses to
     * {@code false} so the gate is open by default; callers needing to
     * distinguish "absent" from "explicitly false" must parse the raw JSON
     * themselves.
     *
     * @return {@code true} when the relay set {@code is_active} to true,
     *         {@code false} otherwise
     */
    public boolean isActive() {
        return isActive != null && isActive;
    }

    /**
     * Returns the {@code time_enforcement_ends} scalar marking when the
     * enforcement window expires.
     *
     * @return an {@link Optional} containing the enforcement-end
     *         timestamp, or {@link Optional#empty()} if the relay omitted
     *         the scalar
     */
    public Optional<String> timeEnforcementEnds() {
        return Optional.ofNullable(timeEnforcementEnds);
    }

    /**
     * Returns the {@code enforcement_type} scalar classifying the
     * enforcement severity.
     *
     * @return an {@link Optional} containing the enforcement type, or
     *         {@link Optional#empty()} if the relay omitted the scalar
     */
    public Optional<String> enforcementType() {
        return Optional.ofNullable(enforcementType);
    }

    /**
     * Parses the JSON payload carried by the {@code <result>} child into a
     * {@link FetchReachoutTimelockMexResponse}.
     *
     * @apiNote Private; routed through {@link #of(Node)} after the byte
     * content of the {@code <result>} child is extracted. Returns
     * {@link Optional#empty()} when the envelope, the {@code data} branch,
     * or the {@code xwa2_fetch_account_reachout_timelock} child is absent.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return an {@link Optional} wrapping the parsed response, or
     *         {@link Optional#empty()} if the
     *         {@code data.xwa2_fetch_account_reachout_timelock} envelope
     *         is absent
     */
    private static Optional<FetchReachoutTimelockMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_fetch_account_reachout_timelock");
        if (root == null) {
            return Optional.empty();
        }

        var isActive = root.getBoolean("is_active");
        var timeEnforcementEnds = root.getString("time_enforcement_ends");
        var enforcementType = root.getString("enforcement_type");

        return Optional.of(new FetchReachoutTimelockMexResponse(isActive, timeEnforcementEnds, enforcementType));
    }
}
