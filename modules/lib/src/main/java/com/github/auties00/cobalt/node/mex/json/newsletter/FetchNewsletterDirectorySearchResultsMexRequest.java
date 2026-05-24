package com.github.auties00.cobalt.node.mex.json.newsletter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Builds the MEX request that searches the newsletter discovery directory
 * by free-text query.
 *
 * @apiNote
 * Drives the directory search box; the {@code searchText} argument carries
 * the user-typed query (matched against newsletter name, handle and
 * description on the server side), the {@code categories} list narrows the
 * search to specific topic categories, and {@code limit} plus
 * {@code cursorToken} drive forward pagination.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterDirectorySearchResultsJob")
public final class FetchNewsletterDirectorySearchResultsMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled persisted-query identifier of
     * {@code WAWebMexFetchNewsletterDirectorySearchResultsJobQuery.graphql} on
     * the WhatsApp relay.
     *
     * @apiNote
     * Sent as the {@code id} attribute of the outgoing {@code <query>} child.
     */
    public static final String QUERY_ID = "9699865846759651";

    /**
     * The GraphQL operation name reported by WA Web's {@code MexPerfTracker}
     * for this query.
     */
    public static final String OPERATION_NAME = "mexFetchNewsletterDirectorySearchResults";

    /**
     * The user-typed free-text query.
     */
    private final String searchText;

    /**
     * The list of category enum strings to filter by, or {@code null} when no
     * category filter is applied.
     */
    private final List<String> categories;

    /**
     * The page size, or {@code null} for the server default.
     */
    private final Long limit;

    /**
     * The forward pagination cursor returned by the previous page, or
     * {@code null} for the first page.
     */
    private final String cursorToken;

    /**
     * Whether to populate the {@code status_metadata} fragment in the
     * response.
     */
    private final boolean fetchStatusMetadata;

    /**
     * Constructs a request for one page of search results.
     *
     * @apiNote
     * The {@code categories} list must already carry the on-wire enum names
     * produced by
     * {@code WAWebNewsletterDirectoryCategoryUtils.getCategoryValueFromEnum}
     * in WA Web. The {@code fetchStatusMetadata} flag mirrors the result of
     * {@code WAWebNewsletterGatingUtils.isNewsletterStatusReceiverEnabled()}.
     *
     * @param searchText          the free-text search query
     * @param categories          the category enum-string filter, may be
     *                            {@code null}
     * @param limit               the page size, may be {@code null}
     * @param cursorToken         the forward pagination cursor, may be
     *                            {@code null}
     * @param fetchStatusMetadata whether to request the optional
     *                            {@code status_metadata} fragment
     */
    public FetchNewsletterDirectorySearchResultsMexRequest(String searchText,
                   List<String> categories,
                   Long limit,
                   String cursorToken,
                   boolean fetchStatusMetadata) {
        this.searchText = searchText;
        this.categories = categories;
        this.limit = limit;
        this.cursorToken = cursorToken;
        this.fetchStatusMetadata = fetchStatusMetadata;
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
     * Produces the
     * {@code {variables: {input: {search_text, categories, limit, start_cursor}, fetch_status_metadata}}}
     * payload.
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
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterDirectorySearchResultsJob", exports = "mexFetchNewsletterDirectorySearchResults",
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

            writer.writeName("search_text");
            writer.writeColon();
            writer.writeString(searchText);

            writer.writeName("categories");
            writer.writeColon();
            writer.startArray();
            if (categories != null) {
                for (var i = 0; i < categories.size(); i++) {
                    if (i > 0) {
                        writer.writeComma();
                    }
                    writer.writeString(categories.get(i));
                }
            }
            writer.endArray();

            if (limit != null) {
                writer.writeName("limit");
                writer.writeColon();
                writer.writeInt64(limit);
            }
            if (cursorToken != null) {
                writer.writeName("start_cursor");
                writer.writeColon();
                writer.writeString(cursorToken);
            }

            writer.endObject();

            writer.writeName("fetch_status_metadata");
            writer.writeColon();
            writer.writeBool(fetchStatusMetadata);

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
