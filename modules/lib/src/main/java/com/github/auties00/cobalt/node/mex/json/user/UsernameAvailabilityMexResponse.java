package com.github.auties00.cobalt.node.mex.json.user;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Decoded reply to the username-availability check.
 *
 * @apiNote Consume after dispatching {@link UsernameAvailabilityMexRequest}.
 * Mirrors the {@code XWA2UsernameCheckResponse} GraphQL type: WA Web's
 * {@code WAWebMexUsernameAvailability.mexCheckUsernameAvailabilityQueryJob}
 * projects {@code (isUsernameAvailable, suggestedUsernames)}, and Cobalt
 * exposes the same shape via {@link #isUsernameAvailable()} and
 * {@link #suggestedUsernames()}.
 *
 * @see UsernameAvailabilityMexRequest
 */
@WhatsAppWebModule(moduleName = "WAWebMexUsernameAvailability")
public final class UsernameAvailabilityMexResponse implements MexOperation.Response.Json {
    /**
     * The status token the relay returns when the candidate is available.
     *
     * @apiNote WA Web's
     * {@code WAWebMexUsernameAvailability.mexCheckUsernameAvailabilityQueryJob}
     * projects the boolean availability flag from
     * {@code result === "SUCCESS"}; this constant captures the literal so
     * callers comparing against {@link #result()} need not repeat it.
     */
    public static final String RESULT_SUCCESS = "SUCCESS";

    /**
     * The {@code result} field carrying the relay's status token.
     */
    private final String result;

    /**
     * The decoded {@code suggestions} array, never {@code null}.
     */
    private final List<String> suggestedUsernames;

    /**
     * Wraps the decoded result token and suggestion list.
     *
     * @param result the {@code result} field
     * @param suggestedUsernames the decoded {@code suggestions} array
     */
    private UsernameAvailabilityMexResponse(String result, List<String> suggestedUsernames) {
        this.result = result;
        this.suggestedUsernames = suggestedUsernames;
    }

    /**
     * Decodes the {@code <result>} child of an inbound MEX IQ.
     *
     * @apiNote Pass the IQ node received in reply to a stanza dispatched
     * with {@link UsernameAvailabilityMexRequest#toNode()}.
     *
     * @param node the IQ reply stanza
     * @return the decoded reply, or {@link Optional#empty()} when the
     *         payload is missing or malformed
     */
    @WhatsAppWebExport(moduleName = "WAWebMexUsernameAvailability", exports = "mexCheckUsernameAvailabilityQueryJob",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<UsernameAvailabilityMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(UsernameAvailabilityMexResponse::of);
    }

    /**
     * Returns the raw status token.
     *
     * @apiNote Use {@link #isUsernameAvailable()} for the boolean WA Web
     * exposes; the raw token is preserved for callers that want to
     * distinguish among the relay's error tokens.
     *
     * @return the token wrapped in an {@link Optional}, or
     *         {@link Optional#empty()} when the relay omitted the field
     */
    public Optional<String> result() {
        return Optional.ofNullable(result);
    }

    /**
     * Returns the list of alternative usernames suggested by the relay.
     *
     * @apiNote WA Web exposes the suggestions as the
     * {@code suggestedUsernames} field of the response object the
     * username picker consumes to render the "try these instead" chips.
     * The returned list is unmodifiable.
     *
     * @return the suggestions; may be empty, never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebMexUsernameAvailability", exports = "mexCheckUsernameAvailabilityQueryJob",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<String> suggestedUsernames() {
        return suggestedUsernames;
    }

    /**
     * Returns whether the queried username is available.
     *
     * @apiNote Mirrors WA Web's
     * {@code isUsernameAvailable: result === "SUCCESS"} projection.
     *
     * @return {@code true} when {@link #result()} equals
     *         {@link #RESULT_SUCCESS}, {@code false} otherwise
     */
    @WhatsAppWebExport(moduleName = "WAWebMexUsernameAvailability", exports = "mexCheckUsernameAvailabilityQueryJob",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean isUsernameAvailable() {
        return RESULT_SUCCESS.equals(result);
    }

    /**
     * Decodes the {@code <result>} payload bytes into a {@link UsernameAvailabilityMexResponse}.
     *
     * @implNote This implementation projects
     * {@code data.xwa2_username_check.{result, suggestions}}; the
     * {@code suggestions} array is wrapped in an unmodifiable list to
     * preserve the public-method contract. Missing intermediate envelopes
     * yield {@link Optional#empty()}.
     *
     * @param json the raw {@code <result>} payload bytes
     * @return the decoded reply, or {@link Optional#empty()} when the
     *         payload does not parse or lacks the required envelope
     */
    private static Optional<UsernameAvailabilityMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_username_check");
        if (root == null) {
            return Optional.empty();
        }

        var result = root.getString("result");

        var suggestionsArray = root.getJSONArray("suggestions");
        List<String> suggestedUsernames;
        if (suggestionsArray == null) {
            suggestedUsernames = List.of();
        } else {
            var collected = new ArrayList<String>(suggestionsArray.size());
            for (var i = 0; i < suggestionsArray.size(); i++) {
                var entry = suggestionsArray.getString(i);
                if (entry != null) {
                    collected.add(entry);
                }
            }
            suggestedUsernames = Collections.unmodifiableList(collected);
        }

        return Optional.of(new UsernameAvailabilityMexResponse(result, suggestedUsernames));
    }
}
