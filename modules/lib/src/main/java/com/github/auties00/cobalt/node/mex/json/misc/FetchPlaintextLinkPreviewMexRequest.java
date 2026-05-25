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
 * Fetches a rich link preview for a URL shared in a newsletter message, with the relay acting as a
 * trusted unfurl proxy.
 *
 * <p>The relay performs the URL unfurl server-side so the preview does not leak reader identity to
 * the link target, and returns a {@code xwa2_newsletter_link_preview} envelope carrying the title,
 * description and thumbnail handle. The {@link #input} variable is forwarded as an opaque
 * caller-supplied JSON string; passing {@code null} omits it from the wire payload.
 *
 * @implNote This implementation leaves URL validation to the caller (WhatsApp Web validates the URL
 * before sending {@code {"url":"..."}}) because the codegen pipeline does not model the newsletter
 * link-preview action.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchPlaintextLinkPreviewJob")
public final class FetchPlaintextLinkPreviewMexRequest implements MexOperation.Request.Json {
    /**
     * Holds the compiled GraphQL query identifier for the plaintext link-preview query document.
     *
     * <p>The relay maps this identifier to a server-side persisted operation and never sees the
     * GraphQL text on the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchPlaintextLinkPreviewJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "9101130456653613";

    /**
     * Holds the GraphQL operation name reported to the MEX perf tracker when this query is
     * dispatched.
     *
     * <p>The name tags the query in latency and error metrics; it is kept on the request so
     * embedders mirroring that telemetry surface can emit the same tag.
     */
    public static final String OPERATION_NAME = "fetchPlaintextLinkPreview";

    /**
     * Holds the serialised URL and optional preview options bound to the {@code input} GraphQL
     * variable.
     */
    private final String input;

    /**
     * Constructs a new request with the serialised {@code input} GraphQL variable.
     *
     * <p>The caller produces the JSON payload; passing {@code null} omits the variable from the
     * wire envelope.
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
     * @implNote This implementation streams the GraphQL variables through fastjson2's
     * {@link JSONWriter}, emits the {@code input} string only when the constructor argument is
     * non-{@code null}, then wraps the payload via
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
