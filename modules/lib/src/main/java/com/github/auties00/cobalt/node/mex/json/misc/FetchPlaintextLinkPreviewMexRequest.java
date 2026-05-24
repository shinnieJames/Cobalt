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
 * Outbound MEX query that fetches a rich link preview (title, description,
 * thumbnail handle) for a URL shared in a newsletter message, with the
 * relay acting as a trusted proxy so the preview does not leak reader
 * identity to the link target.
 *
 * @apiNote Issued by WA Web's
 * {@code WAWebNewsletterFetchLinkPreviewAction.fetchPlaintextLinkPreviewAction}
 * while composing or rendering a newsletter post. The relay performs the
 * URL unfurl server-side and returns an
 * {@code xwa2_newsletter_link_preview} envelope; if the relay omits the
 * envelope the WA Web caller falls back to
 * {@code WAWebGenMinimalLinkPreviewChatAction.genMinimalLinkPreview} with
 * preview type {@code NONE}. Cobalt callers may apply the same fallback.
 *
 * @implNote This implementation forwards the {@code input} variable as an
 * opaque caller-supplied JSON string. WA Web's call-site validates the
 * URL with {@code new URL(e)} and emits
 * {@snippet :
 * String input = "{\"url\":\"https://example.com/post\"}";
 * }
 * Cobalt leaves URL validation to the caller because the codegen pipeline
 * does not model {@code WAWebNewsletterFetchLinkPreviewAction}.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchPlaintextLinkPreviewJob")
public final class FetchPlaintextLinkPreviewMexRequest implements MexOperation.Request.Json {
    /**
     * Compiled GraphQL query identifier for the
     * {@code WAWebMexFetchPlaintextLinkPreviewJobQuery} document.
     *
     * @apiNote Mirrors the {@code params.id} value baked into
     * {@code WAWebMexFetchPlaintextLinkPreviewJobQuery.graphql}. The relay
     * maps the id to a server-side persisted operation and never sees the
     * GraphQL text on the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchPlaintextLinkPreviewJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "9101130456653613";

    /**
     * GraphQL operation name reported to
     * {@code MexPerfTracker.setOperationName} when this query is
     * dispatched.
     *
     * @apiNote Used by WA Web's MEX perf tracker to tag the query in
     * latency and error metrics; Cobalt keeps the name on the request for
     * embedders mirroring WA Web's telemetry surface.
     */
    public static final String OPERATION_NAME = "fetchPlaintextLinkPreview";

    /**
     * The serialised URL and optional preview options bound to the
     * {@code input} GraphQL variable.
     */
    private final String input;

    /**
     * Constructs a new request with the serialised {@code input} GraphQL
     * variable.
     *
     * @apiNote The caller is responsible for producing the JSON payload;
     * passing {@code null} omits the variable from the wire envelope.
     *
     * @param input the serialised {@code input} JSON payload, may be {@code null} to omit
     */
    public FetchPlaintextLinkPreviewMexRequest(String input) {
        this.input = input;
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
     * fastjson2's {@link JSONWriter}, emits the {@code input} string only
     * when the constructor argument is non-{@code null}, then wraps the
     * payload via
     * {@link MexOperation.Request.Json#createMexNode(String, String)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchPlaintextLinkPreviewJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public NodeBuilder toNode() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("variables");
            writer.writeColon();
            writer.startObject();
            if (input != null) {
                writer.writeName("input");
                writer.writeColon();
                writer.writeString(input);
            }
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
