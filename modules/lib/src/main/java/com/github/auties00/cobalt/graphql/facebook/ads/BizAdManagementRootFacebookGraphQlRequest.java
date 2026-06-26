package com.github.auties00.cobalt.graphql.facebook.ads;

import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.graphql.facebook.FacebookGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;

/**
 * Builds the Facebook GraphQL query that loads the WhatsApp Business ad management root screen.
 *
 * <p>The query reads the linked Facebook {@code page} for {@code page_id_1}, the latest CTWA draft
 * for {@code draft_page_id}, and a paginated connection of all the user's boosted ads filtered by
 * {@code page_id_1}, {@code page_id_2}, and {@code options} and windowed by {@code first} and
 * {@code after}. The reply is consumed through {@link BizAdManagementRootFacebookGraphQlResponse}.
 *
 * @implNote This implementation models {@code page_id_1}, {@code page_id_2}, and {@code draft_page_id}
 * as {@link String} Facebook page ids rather than {@link com.github.auties00.cobalt.model.jid.Jid}
 * values: they are numeric Facebook Graph object ids, not WhatsApp addresses. The {@code options}
 * variable is accepted as a caller-supplied, already JSON-encoded object literal and spliced in
 * verbatim because the compiled {@code WAWebBizAdManagementRootQuery} document of snapshot
 * {@code 1040120866} declares it as an opaque {@code LocalArgument} with no field-level type
 * information.
 *
 * @see BizAdManagementRootFacebookGraphQlResponse
 */
@WhatsAppWebModule(moduleName = "WAWebBizAdManagementRootQuery")
public final class BizAdManagementRootFacebookGraphQlRequest implements FacebookGraphQlOperation.Request {
    /**
     * The persisted document identifier the Meta graph endpoint maps to the server-side compiled
     * GraphQL document for this operation.
     *
     * <p>Emitted as the {@code doc_id} field of the Facebook GraphQL request body.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizAdManagementRootQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String DOC_ID = "26701127972903992";

    /**
     * The GraphQL operation name carried by this request.
     *
     * <p>Used as the persisted-query lookup key and as the perf-telemetry tag.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizAdManagementRootQuery.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "WAWebBizAdManagementRootQuery";

    /**
     * The {@code page_id_1} GraphQL variable: the Facebook page id whose {@code page} stanza is read and
     * which scopes the boosted-ads connection, or {@code null} to omit it.
     */
    private final String pageId1;

    /**
     * The {@code page_id_2} GraphQL variable: the secondary Facebook page id scoping the boosted-ads
     * connection, or {@code null} to omit it.
     */
    private final String pageId2;

    /**
     * The {@code draft_page_id} GraphQL variable: the Facebook page id whose latest CTWA draft is
     * read, or {@code null} to omit it.
     */
    private final String draftPageId;

    /**
     * The {@code options} GraphQL variable: the already-JSON-encoded boosted-ads connection options
     * object, or {@code null} to omit it.
     */
    private final String optionsJson;

    /**
     * The {@code first} GraphQL variable: the maximum number of boosted-ad edges to return in the
     * forward page, or {@code null} to omit it.
     */
    private final Integer first;

    /**
     * The {@code after} GraphQL variable: the opaque forward-pagination cursor, or {@code null} to
     * omit it.
     */
    private final String after;

    /**
     * Constructs an ad-management root query request.
     *
     * <p>Each value that is {@code null} omits its variable from the serialized object.
     *
     * @param pageId1     the Facebook page id read as the {@code page} stanza, or {@code null} to omit
     *                    the variable
     * @param pageId2     the secondary Facebook page id scoping the boosted-ads connection, or
     *                    {@code null} to omit the variable
     * @param draftPageId the Facebook page id whose latest CTWA draft is read, or {@code null} to omit
     *                    the variable
     * @param optionsJson the already-JSON-encoded connection options object, or {@code null} to omit
     *                    the variable
     * @param first       the maximum number of boosted-ad edges to return, or {@code null} to omit the
     *                    variable
     * @param after       the forward-pagination cursor, or {@code null} to omit the variable
     */
    public BizAdManagementRootFacebookGraphQlRequest(String pageId1, String pageId2, String draftPageId,
                                           String optionsJson, Integer first, String after) {
        this.pageId1 = pageId1;
        this.pageId2 = pageId2;
        this.draftPageId = draftPageId;
        this.optionsJson = optionsJson;
        this.first = first;
        this.after = after;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String docId() {
        return DOC_ID;
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
     * @implNote This implementation emits {@code {"page_id_1": ..., "page_id_2": ..., "draft_page_id":
     * ..., "options": ..., "first": ..., "after": ...}}, writing each variable only when its value is
     * non-null and emitting {@code "{}"} when all are {@code null}. The {@code options} value is
     * spliced in as a raw JSON value via {@link JSONWriter#writeRaw(String)} because it is supplied
     * already encoded.
     */
    @Override
    public String variables() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            if (pageId1 != null) {
                writer.writeName("page_id_1");
                writer.writeColon();
                writer.writeString(pageId1);
            }

            if (pageId2 != null) {
                writer.writeName("page_id_2");
                writer.writeColon();
                writer.writeString(pageId2);
            }

            if (draftPageId != null) {
                writer.writeName("draft_page_id");
                writer.writeColon();
                writer.writeString(draftPageId);
            }

            if (optionsJson != null) {
                writer.writeName("options");
                writer.writeColon();
                writer.writeRaw(optionsJson);
            }

            if (first != null) {
                writer.writeName("first");
                writer.writeColon();
                writer.writeInt32(first);
            }

            if (after != null) {
                writer.writeName("after");
                writer.writeColon();
                writer.writeString(after);
            }
            writer.endObject();
            try (var output = new StringWriter()) {
                writer.flushTo(output);
                return output.toString();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
