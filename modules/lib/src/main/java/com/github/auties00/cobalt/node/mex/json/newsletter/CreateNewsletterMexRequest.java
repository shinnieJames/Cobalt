package com.github.auties00.cobalt.node.mex.json.newsletter;

import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.mex.MexOperation;
import com.github.auties00.cobalt.node.NodeBuilder;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;

/**
 * Builds the MEX request that creates a new newsletter owned by the
 * authenticated user.
 *
 * @apiNote
 * Drives the newsletter-creation form: the caller supplies the display name,
 * description and uploaded picture payload, and the relay assigns the new
 * newsletter Jid plus initial thread and viewer metadata. The response is
 * parsed by {@link CreateNewsletterMexResponse} and used to seed the local
 * newsletter store entry.
 *
 * @implNote
 * This implementation always emits the {@code name}, {@code description} and
 * {@code picture} fields even when their backing strings are {@code null},
 * mirroring WA Web's {@code mexCreateNewsletter} which passes the literal
 * arguments without null-skipping.
 */
@WhatsAppWebModule(moduleName = "WAWebMexCreateNewsletterJob")
public final class CreateNewsletterMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled persisted-query identifier of
     * {@code WAWebMexCreateNewsletterJobMutation.graphql} on the WhatsApp
     * relay.
     *
     * @apiNote
     * Sent as the {@code id} attribute of the outgoing {@code <query>} child.
     */
    public static final String QUERY_ID = "25149874324715067";

    /**
     * The GraphQL operation name reported by WA Web's {@code MexPerfTracker}
     * for this mutation.
     */
    public static final String OPERATION_NAME = "mexCreateNewsletter";

    /**
     * The newsletter display name.
     */
    private final String name;

    /**
     * The newsletter description text.
     */
    private final String description;

    /**
     * The picture payload (base64 thumbnail or direct-path string) chosen
     * for the newsletter avatar.
     */
    private final String picture;

    /**
     * Constructs a new request with the given mutation variables.
     *
     * @apiNote
     * Any of the three string fields may be {@code null}; the resulting
     * GraphQL request always emits them, so {@code null} arrives as a JSON
     * {@code null} literal at the relay.
     *
     * @param name        the newsletter display name
     * @param description the newsletter description
     * @param picture     the base64 thumbnail or direct-path picture string
     */
    public CreateNewsletterMexRequest(String name, String description, String picture) {
        this.name = name;
        this.description = description;
        this.picture = picture;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Returns {@link #QUERY_ID}.
     */
    @Override
    public String id() {
        return QUERY_ID;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Returns {@link #OPERATION_NAME}.
     */
    @Override
    public String name() {
        return OPERATION_NAME;
    }

    /**
     * Serialises this request into a MEX IQ {@link NodeBuilder}.
     *
     * @apiNote
     * Produces the {@code {variables: {input: {name, description, picture}}}}
     * payload consumed by the persisted-query identified by
     * {@link #QUERY_ID}; the three scalars are wrapped under a single
     * {@code input} object that mirrors the GraphQL input type schema.
     *
     * @implNote
     * This implementation writes the GraphQL variables directly through
     * {@link JSONWriter} and wraps any {@link IOException} from the
     * in-memory writer in an {@link UncheckedIOException}.
     *
     * @return the {@link NodeBuilder} carrying the IQ envelope and serialised
     *         GraphQL variables
     * @throws UncheckedIOException if the underlying writer fails
     */
    @WhatsAppWebExport(moduleName = "WAWebMexCreateNewsletterJob", exports = "mexCreateNewsletter",
            adaptation = WhatsAppAdaptation.ADAPTED)
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
            writer.writeName("name");
            writer.writeColon();
            writer.writeString(name);
            writer.writeName("description");
            writer.writeColon();
            writer.writeString(description);
            writer.writeName("picture");
            writer.writeColon();
            writer.writeString(picture);
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
