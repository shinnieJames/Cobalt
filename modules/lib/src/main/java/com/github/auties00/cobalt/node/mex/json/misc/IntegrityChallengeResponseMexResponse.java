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
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * Inbound parsed response of the {@link IntegrityChallengeResponseMexRequest}
 * mutation, exposing the {@code success} and {@code error_message} scalars
 * carried by the {@code xwa2_submit_integrity_challenge_response} envelope.
 *
 * @apiNote Drives WA Web's integrity-passkey-checkpoint flow: on
 * {@link #success()} truthy the WA Web caller closes the checkpoint modal
 * and removes the cached challenge from user-prefs IDB; otherwise it
 * throws and logs {@code "server rejected challenge response"}. Cobalt
 * embedders may apply the same gating against their own modal/cache.
 *
 * @implNote This implementation surfaces both scalars as {@link Optional}
 * containers; WA Web reads {@code n.success} directly and treats a missing
 * value as falsy. Cobalt leaves absence observable so callers can
 * distinguish a relay outage from an explicit rejection.
 */
@WhatsAppWebModule(moduleName = "WAWebMexIntegrityChallengeResponse")
public final class IntegrityChallengeResponseMexResponse implements MexOperation.Response.Json {
    /**
     * The {@code success} scalar reflecting the relay's verdict on the
     * submitted challenge.
     */
    private final Boolean success;

    /**
     * The {@code error_message} scalar populated when the challenge was
     * rejected.
     */
    private final String errorMessage;

    /**
     * Constructs a new response wrapping the parsed scalar fields of the
     * {@code xwa2_submit_integrity_challenge_response} envelope.
     *
     * @apiNote Private; instances are produced by the {@link #of(Node)}
     * parser.
     *
     * @param success      the {@code success} scalar, may be {@code null}
     * @param errorMessage the {@code error_message} scalar, may be {@code null}
     */
    private IntegrityChallengeResponseMexResponse(Boolean success, String errorMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
    }

    /**
     * Parses the MEX response carried by an inbound IQ stanza.
     *
     * @apiNote Reads the {@code <result>} child's byte content and routes
     * it through the private byte-level parser. Returns
     * {@link Optional#empty()} when the stanza carries no result or when
     * the {@code data.xwa2_submit_integrity_challenge_response} envelope
     * is absent.
     *
     * @param node the inbound IQ stanza carrying the {@code <result>} child
     * @return an {@link Optional} wrapping the parsed response, or
     *         {@link Optional#empty()} if the expected JSON shape is absent
     */
    @WhatsAppWebExport(moduleName = "WAWebMexIntegrityChallengeResponse", exports = "mexSubmitPasskeyChallengeResponse",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static Optional<IntegrityChallengeResponseMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(IntegrityChallengeResponseMexResponse::of);
    }

    /**
     * Returns the relay's verdict on the submitted challenge.
     *
     * @return an {@link Optional} containing the success flag, or
     *         {@link Optional#empty()} if the relay omitted the scalar
     */
    public Optional<Boolean> success() {
        return Optional.ofNullable(success);
    }

    /**
     * Returns the error message reported by the relay when the challenge
     * was rejected.
     *
     * @return an {@link Optional} containing the error message, or
     *         {@link Optional#empty()} if the relay omitted the scalar
     */
    public Optional<String> errorMessage() {
        return Optional.ofNullable(errorMessage);
    }

    /**
     * Parses the JSON payload carried by the {@code <result>} child into a
     * {@link IntegrityChallengeResponseMexResponse}.
     *
     * @apiNote Private; routed through {@link #of(Node)} after the byte
     * content of the {@code <result>} child is extracted. Returns
     * {@link Optional#empty()} when the envelope, the {@code data} branch,
     * or the {@code xwa2_submit_integrity_challenge_response} child is
     * absent.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return an {@link Optional} wrapping the parsed response, or
     *         {@link Optional#empty()} if the
     *         {@code data.xwa2_submit_integrity_challenge_response}
     *         envelope is absent
     */
    private static Optional<IntegrityChallengeResponseMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_submit_integrity_challenge_response");
        if (root == null) {
            return Optional.empty();
        }

        var success = root.getBoolean("success");
        var errorMessage = root.getString("error_message");

        return Optional.of(new IntegrityChallengeResponseMexResponse(success, errorMessage));
    }
}
