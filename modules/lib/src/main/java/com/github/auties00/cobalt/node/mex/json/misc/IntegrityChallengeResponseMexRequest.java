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
 * Outbound MEX mutation that submits a passkey-signed integrity challenge
 * response, returning the relay's verdict on whether the assertion was
 * accepted.
 *
 * @apiNote Issued by WA Web's
 * {@code WAWebIntegrityPasskeyCheckpointUtils} when the user completes the
 * passkey assertion in the integrity-challenge modal: the WebAuthn
 * assertion is forwarded here, and on a {@code success} verdict the WA Web
 * caller closes the modal and removes the cached challenge from user-prefs
 * IDB; on rejection it throws and surfaces a server-rejection log entry.
 * Cobalt callers consume the verdict through
 * {@link IntegrityChallengeResponseMexResponse#success()} and apply their
 * own modal/cache logic.
 *
 * @implNote This implementation base64-encodes the raw JSON-serialised
 * WebAuthn assertion using {@link Base64#getEncoder()}, mirroring WA Web's
 * {@code btoa(JSON.stringify(e))} call exactly. The
 * {@code prf_available} flag is taken from the caller because Cobalt's
 * codegen has no access to the original assertion object that WA Web
 * inspects through {@code e.prf_output != null}.
 */
@WhatsAppWebModule(moduleName = "WAWebMexIntegrityChallengeResponse")
public final class IntegrityChallengeResponseMexRequest implements MexOperation.Request.Json {
    /**
     * Compiled GraphQL query identifier for the
     * {@code WAWebMexIntegrityChallengeResponseMutation} document.
     *
     * @apiNote Mirrors the {@code params.id} value baked into
     * {@code WAWebMexIntegrityChallengeResponseMutation.graphql}. The relay
     * maps the id to a server-side persisted mutation and never sees the
     * GraphQL text on the wire.
     */
    public static final String QUERY_ID = "26230331493320650";

    /**
     * GraphQL operation name reported to
     * {@code MexPerfTracker.setOperationName} when this mutation is
     * dispatched.
     *
     * @apiNote Used by WA Web's MEX perf tracker to tag the query in
     * latency and error metrics; Cobalt keeps the name on the request for
     * embedders mirroring WA Web's telemetry surface.
     */
    public static final String OPERATION_NAME = "mexSubmitPasskeyChallengeResponse";

    /**
     * The {@code challenge_type} discriminator emitted on every request.
     *
     * @apiNote WA Web declares {@code var s = "PASSKEY"} as a
     * module-scoped constant and reuses it for every dispatch; no other
     * challenge type is currently supported.
     */
    String CHALLENGE_TYPE = "PASSKEY";

    /**
     * The raw bytes of the JSON-serialised WebAuthn assertion, base64-
     * encoded inline during dispatch.
     */
    private final byte[] signedChallenge;

    /**
     * Whether the assertion carries a {@code prf_output} field.
     *
     * @apiNote WA Web derives this flag inline via
     * {@code e.prf_output != null}; Cobalt callers compute the same
     * predicate against their own assertion object and pass the result.
     */
    private final boolean prfAvailable;

    /**
     * Constructs a new request submitting the given passkey-signed
     * challenge response to the relay.
     *
     * @apiNote {@code signedChallenge} must already be the JSON-serialised
     * form of the WebAuthn assertion (WA Web invokes
     * {@code JSON.stringify(e)} before {@code btoa}; Cobalt callers pass
     * the equivalent serialised bytes). {@code prfAvailable} must reflect
     * whether the assertion carries a non-{@code null} {@code prf_output}
     * field, matching the relay's per-assertion telemetry expectation.
     *
     * @param signedChallenge the raw bytes of the JSON-serialised WebAuthn
     *                        assertion, must not be {@code null}
     * @param prfAvailable    whether the assertion carries a {@code prf_output} field
     * @throws NullPointerException if {@code signedChallenge} is {@code null}
     */
    public IntegrityChallengeResponseMexRequest(byte[] signedChallenge, boolean prfAvailable) {
        this.signedChallenge = Objects.requireNonNull(signedChallenge, "signedChallenge cannot be null");
        this.prfAvailable = prfAvailable;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String id() {
        return QUERY_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String name() {
        return OPERATION_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation streams the GraphQL variables through
     * fastjson2's {@link JSONWriter}, nests the {@code passkey_response}
     * object under {@code input}, base64-encodes {@link #signedChallenge}
     * via {@link Base64#getEncoder()}, then wraps the payload via
     * {@link MexOperation.Request.Json#createMexNode(String, String)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexIntegrityChallengeResponse", exports = "mexSubmitPasskeyChallengeResponse",
            adaptation = WhatsAppAdaptation.DIRECT)
    @Override
    public NodeBuilder toNode() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("variables");
            writer.writeColon();
            writer.startObject();

            writer.writeName("input");
            writer.writeColon();
            writer.startObject();

            writer.writeName("challenge_type");
            writer.writeColon();
            writer.writeString(CHALLENGE_TYPE);

            writer.writeName("passkey_response");
            writer.writeColon();
            writer.startObject();
            writer.writeName("signed_challenge");
            writer.writeColon();
            writer.writeString(Base64.getEncoder().encodeToString(signedChallenge));
            writer.writeName("prf_available");
            writer.writeColon();
            writer.writeBool(prfAvailable);
            writer.endObject();

            writer.endObject();
            writer.endObject();
            writer.endObject();

            try (var output = new StringWriter()) {
                writer.flushTo(output);
                return Json.createMexNode(QUERY_ID, output.toString());
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
