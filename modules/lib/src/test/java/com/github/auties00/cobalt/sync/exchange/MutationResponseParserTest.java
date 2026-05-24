package com.github.auties00.cobalt.sync.exchange;

import com.github.auties00.cobalt.exception.WhatsAppWebAppStateSyncException;
import com.github.auties00.cobalt.model.media.ExternalBlobReferenceBuilder;
import com.github.auties00.cobalt.model.media.ExternalBlobReferenceSpec;
import com.github.auties00.cobalt.model.signal.KeyIdBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.data.SyncdPatchBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdPatchSpec;
import com.github.auties00.cobalt.model.sync.data.SyncdVersionBuilder;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins each branch of {@link MutationResponseParser}.
 *
 * @apiNote
 * Covers every observable branch of the parser:
 * <ul>
 * <li><b>Happy path</b>: snapshot-only, patches-only, version, has_more
 * <li><b>Collection-level errors</b>: 409 (Conflict / ConflictHasMore), 400/404 (fatal),
 *     anything else (retryable)
 * <li><b>IQ-level errors</b>: 400/404/405/406 fatal, anything else retryable with the
 *     {@code backoff} attribute preserved
 * <li><b>Malformed bytes</b>: non-protobuf {@code <patch>} or {@code <snapshot>} content
 *     deserialises to {@link WhatsAppWebAppStateSyncException.UnexpectedError}
 * <li><b>Structural failures</b>: missing {@code <sync>} or {@code <collection>}, unknown
 *     collection name
 * <li><b>Batched parse</b>: multiple collections under one {@code <sync>}, per-collection
 *     errors captured on the response
 * </ul>
 *
 * @implNote
 * This implementation is pure and stateless; every test builds the input
 * {@link Node} via the per-test helpers at the bottom of
 * the class so each test body stays focused on the assertion.
 */
@DisplayName("MutationResponseParser")
class MutationResponseParserTest {

    /**
     * The shared parser instance; the parser is stateless so a single instance is reused
     * across every test.
     */
    private static final MutationResponseParser PARSER = new MutationResponseParser();

    /**
     * Tests for the happy paths (snapshot, patches, version, has_more).
     */
    @Nested
    @DisplayName("happy path - patches and snapshot responses")
    class HappyPath {
        /**
         * Asserts that a patches-only collection produces the right type/version/has_more
         * and patch list.
         */
        @Test
        @DisplayName("collection with patches yields the right type/version/has_more and patch list")
        void patchesOnly() {
            var patchBytes = SyncdPatchSpec.encode(new SyncdPatchBuilder()
                    .version(new SyncdVersionBuilder().version(42L).build())
                    .keyId(new KeyIdBuilder().id(new byte[]{1, 2, 3}).build())
                    .build());

            var iq = iq("result", collection("regular", attrs -> attrs
                    .attribute("version", "42")
                    .attribute("has_more_patches", "true"),
                    new NodeBuilder().description("patches").content(List.of(
                            new NodeBuilder().description("patch").content(patchBytes).build()
                    )).build()
            ));

            var response = PARSER.parseSyncResponse(iq);
            assertEquals(SyncPatchType.REGULAR, response.collectionName());
            assertEquals(42L, response.version());
            assertTrue(response.hasMore(), "has_more_patches='true' must map to hasMore=true");
            assertEquals(1, response.patches().size());
            assertFalse(response.isSnapshot());
            assertTrue(response.snapshotReference().isEmpty());
            assertTrue(response.collectionError().isEmpty());
        }

        /**
         * Asserts that a snapshot-only collection produces the parsed external blob
         * reference.
         */
        @Test
        @DisplayName("collection with snapshot yields the parsed external blob reference")
        void snapshotOnly() {
            var blob = new ExternalBlobReferenceBuilder()
                    .mediaKey(new byte[]{(byte) 0xAA})
                    .mediaDirectPath("/path")
                    .build();
            var blobBytes = ExternalBlobReferenceSpec.encode(blob);

            var iq = iq("result", collection("regular_low", attrs -> attrs
                    .attribute("version", "1"),
                    new NodeBuilder().description("snapshot").content(blobBytes).build()
            ));

            var response = PARSER.parseSyncResponse(iq);
            assertEquals(SyncPatchType.REGULAR_LOW, response.collectionName());
            assertEquals(1L, response.version());
            assertFalse(response.hasMore());
            assertTrue(response.isSnapshot());
            assertNotNull(response.snapshotReference().orElseThrow());
            assertTrue(response.patches().isEmpty());
        }

        /**
         * Asserts that an empty collection (neither snapshot nor patches) parses without
         * error.
         */
        @Test
        @DisplayName("empty collection (no snapshot, no patches) is a valid no-op response")
        void emptyCollection() {
            var iq = iq("result", collection("regular_high", attrs -> attrs.attribute("version", "5")));
            var response = PARSER.parseSyncResponse(iq);
            assertEquals(SyncPatchType.REGULAR_HIGH, response.collectionName());
            assertEquals(5L, response.version());
            assertTrue(response.patches().isEmpty());
            assertFalse(response.isSnapshot());
        }

        /**
         * Asserts that a missing {@code version} attribute defaults to zero.
         */
        @Test
        @DisplayName("missing version attribute defaults to 0")
        void missingVersionDefaultsToZero() {
            var iq = iq("result", collection("regular", attrs -> {}));
            assertEquals(0L, PARSER.parseSyncResponse(iq).version());
        }
    }

    /**
     * Tests for the collection-level error routing in the throwing single-collection
     * parse mode.
     */
    @Nested
    @DisplayName("collection-level errors - codes route to specific exception subtypes")
    class CollectionErrors {
        /**
         * Asserts that a 409 collection error throws {@link WhatsAppWebAppStateSyncException.Conflict}.
         */
        @Test
        @DisplayName("409 throws Conflict in parseSyncResponse (single-collection mode)")
        void code409Throws() {
            var iq = iq("result", collection("regular", attrs -> attrs.attribute("type", "error"),
                    new NodeBuilder().description("error").attribute("code", "409").build()
            ));
            assertThrows(WhatsAppWebAppStateSyncException.Conflict.class,
                    () -> PARSER.parseSyncResponse(iq));
        }

        /**
         * Asserts that a 409 with {@code has_more_patches} sets {@code Conflict.hasMore}.
         */
        @Test
        @DisplayName("409 with has_more_patches sets Conflict.hasMore=true")
        void code409HasMoreSet() {
            var iq = iq("result", collection("regular", attrs -> attrs
                    .attribute("type", "error")
                    .attribute("has_more_patches", "true"),
                    new NodeBuilder().description("error").attribute("code", "409").build()
            ));
            var exception = assertThrows(WhatsAppWebAppStateSyncException.Conflict.class,
                    () -> PARSER.parseSyncResponse(iq));
            assertTrue(exception.hasMorePatches(), "Conflict.hasMore must reflect collection-level has_more_patches");
        }

        /**
         * Asserts that a 400 collection error throws
         * {@link WhatsAppWebAppStateSyncException.UnexpectedError}.
         */
        @Test
        @DisplayName("400 throws UnexpectedError (fatal)")
        void code400Throws() {
            var iq = iq("result", collection("regular", attrs -> attrs.attribute("type", "error"),
                    new NodeBuilder().description("error").attribute("code", "400").build()
            ));
            assertThrows(WhatsAppWebAppStateSyncException.UnexpectedError.class,
                    () -> PARSER.parseSyncResponse(iq));
        }

        /**
         * Asserts that a 404 collection error throws
         * {@link WhatsAppWebAppStateSyncException.UnexpectedError}.
         */
        @Test
        @DisplayName("404 throws UnexpectedError (fatal)")
        void code404Throws() {
            var iq = iq("result", collection("regular", attrs -> attrs.attribute("type", "error"),
                    new NodeBuilder().description("error").attribute("code", "404").build()
            ));
            assertThrows(WhatsAppWebAppStateSyncException.UnexpectedError.class,
                    () -> PARSER.parseSyncResponse(iq));
        }

        /**
         * Asserts that any other collection-level code throws
         * {@link WhatsAppWebAppStateSyncException.RetryableServerError}.
         */
        @Test
        @DisplayName("unmapped collection-level code throws RetryableServerError")
        void otherCodeIsRetryable() {
            var iq = iq("result", collection("regular", attrs -> attrs.attribute("type", "error"),
                    new NodeBuilder().description("error").attribute("code", "503").build()
            ));
            assertThrows(WhatsAppWebAppStateSyncException.RetryableServerError.class,
                    () -> PARSER.parseSyncResponse(iq));
        }
    }

    /**
     * Tests for the IQ-level error routing.
     */
    @Nested
    @DisplayName("IQ-level errors - fatal vs retryable")
    class IqLevelErrors {
        /**
         * Asserts that an IQ-level 400 throws
         * {@link WhatsAppWebAppStateSyncException.UnexpectedError}.
         */
        @Test
        @DisplayName("type=error code=400 is fatal (UnexpectedError)")
        void iq400IsFatal() {
            var iq = errorIq(400, null);
            assertThrows(WhatsAppWebAppStateSyncException.UnexpectedError.class,
                    () -> PARSER.parseSyncResponse(iq));
        }

        /**
         * Asserts that an IQ-level 404 throws
         * {@link WhatsAppWebAppStateSyncException.UnexpectedError}.
         */
        @Test
        @DisplayName("type=error code=404 is fatal")
        void iq404IsFatal() {
            assertThrows(WhatsAppWebAppStateSyncException.UnexpectedError.class,
                    () -> PARSER.parseSyncResponse(errorIq(404, null)));
        }

        /**
         * Asserts that an IQ-level 405 throws
         * {@link WhatsAppWebAppStateSyncException.UnexpectedError}.
         */
        @Test
        @DisplayName("type=error code=405 is fatal")
        void iq405IsFatal() {
            assertThrows(WhatsAppWebAppStateSyncException.UnexpectedError.class,
                    () -> PARSER.parseSyncResponse(errorIq(405, null)));
        }

        /**
         * Asserts that an IQ-level 406 throws
         * {@link WhatsAppWebAppStateSyncException.UnexpectedError}.
         */
        @Test
        @DisplayName("type=error code=406 is fatal")
        void iq406IsFatal() {
            assertThrows(WhatsAppWebAppStateSyncException.UnexpectedError.class,
                    () -> PARSER.parseSyncResponse(errorIq(406, null)));
        }

        /**
         * Asserts that an IQ-level 503 throws
         * {@link WhatsAppWebAppStateSyncException.RetryableServerError}.
         */
        @Test
        @DisplayName("type=error code=503 is retryable (RetryableServerError)")
        void iq503IsRetryable() {
            assertThrows(WhatsAppWebAppStateSyncException.RetryableServerError.class,
                    () -> PARSER.parseSyncResponse(errorIq(503, null)));
        }

        /**
         * Asserts that the {@code backoff} attribute on the IQ-level error is preserved
         * on the {@link WhatsAppWebAppStateSyncException.RetryableServerError}.
         */
        @Test
        @DisplayName("type=error with backoff attribute is preserved in RetryableServerError")
        void retryableServerCarriesBackoff() {
            var ex = assertThrows(WhatsAppWebAppStateSyncException.RetryableServerError.class,
                    () -> PARSER.parseSyncResponse(errorIq(429, 1500L)));
            assertNotNull(ex);
        }
    }

    /**
     * Tests for the structural-failure paths.
     */
    @Nested
    @DisplayName("structural failures - missing required nodes / unknown collection")
    class StructuralFailures {
        /**
         * Asserts that a response missing the {@code <sync>} child throws
         * {@link WhatsAppWebAppStateSyncException.UnexpectedError}.
         */
        @Test
        @DisplayName("response missing <sync> throws UnexpectedError")
        void missingSync() {
            var iq = new NodeBuilder().description("iq").attribute("type", "result").build();
            assertThrows(WhatsAppWebAppStateSyncException.UnexpectedError.class,
                    () -> PARSER.parseSyncResponse(iq));
        }

        /**
         * Asserts that a response missing the {@code <collection>} child throws
         * {@link WhatsAppWebAppStateSyncException.UnexpectedError}.
         */
        @Test
        @DisplayName("response missing <collection> throws UnexpectedError")
        void missingCollection() {
            var iq = new NodeBuilder().description("iq").attribute("type", "result")
                    .content(new NodeBuilder().description("sync").build()).build();
            assertThrows(WhatsAppWebAppStateSyncException.UnexpectedError.class,
                    () -> PARSER.parseSyncResponse(iq));
        }

        /**
         * Asserts that a collection without a {@code name} attribute throws
         * {@link WhatsAppWebAppStateSyncException.UnexpectedError}.
         */
        @Test
        @DisplayName("collection missing 'name' attribute throws UnexpectedError")
        void missingCollectionName() {
            var iq = iq("result", new NodeBuilder().description("collection")
                    .attribute("version", "1").build());
            assertThrows(WhatsAppWebAppStateSyncException.UnexpectedError.class,
                    () -> PARSER.parseSyncResponse(iq));
        }

        /**
         * Asserts that an unknown collection name throws
         * {@link WhatsAppWebAppStateSyncException.UnexpectedError}.
         */
        @Test
        @DisplayName("unknown collection name throws UnexpectedError")
        void unknownCollectionName() {
            var iq = iq("result", collection("bogus_collection", attrs -> attrs.attribute("version", "1")));
            assertThrows(WhatsAppWebAppStateSyncException.UnexpectedError.class,
                    () -> PARSER.parseSyncResponse(iq));
        }
    }

    /**
     * Tests for the malformed-bytes paths.
     */
    @Nested
    @DisplayName("malformed bytes - non-protobuf <patch>/<snapshot> content")
    class MalformedBytes {
        /**
         * Asserts that non-protobuf patch bytes throw
         * {@link WhatsAppWebAppStateSyncException.UnexpectedError}.
         */
        @Test
        @DisplayName("non-protobuf patch bytes throw UnexpectedError")
        void malformedPatch() {
            var iq = iq("result", collection("regular", attrs -> {},
                    new NodeBuilder().description("patches").content(List.of(
                            new NodeBuilder().description("patch")
                                    .content(new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF})
                                    .build()
                    )).build()
            ));
            assertThrows(WhatsAppWebAppStateSyncException.UnexpectedError.class,
                    () -> PARSER.parseSyncResponse(iq));
        }

        /**
         * Asserts that a content-less {@code <patch>} throws
         * {@link WhatsAppWebAppStateSyncException.UnexpectedError}.
         */
        @Test
        @DisplayName("patch node with no content throws UnexpectedError")
        void patchWithoutContent() {
            var iq = iq("result", collection("regular", attrs -> {},
                    new NodeBuilder().description("patches").content(List.of(
                            new NodeBuilder().description("patch").build()
                    )).build()
            ));
            assertThrows(WhatsAppWebAppStateSyncException.UnexpectedError.class,
                    () -> PARSER.parseSyncResponse(iq));
        }

        /**
         * Asserts that a content-less {@code <snapshot>} throws
         * {@link WhatsAppWebAppStateSyncException.UnexpectedError}.
         */
        @Test
        @DisplayName("snapshot node with no content throws UnexpectedError")
        void snapshotWithoutContent() {
            var iq = iq("result", collection("regular", attrs -> {},
                    new NodeBuilder().description("snapshot").build()
            ));
            assertThrows(WhatsAppWebAppStateSyncException.UnexpectedError.class,
                    () -> PARSER.parseSyncResponse(iq));
        }
    }

    /**
     * Tests for the multi-collection batched parse mode.
     */
    @Nested
    @DisplayName("parseBatchedSyncResponse - multiple collections, per-collection errors captured")
    class BatchedResponse {
        /**
         * Asserts that multiple collections under one {@code <sync>} produce one response
         * per collection in document order.
         */
        @Test
        @DisplayName("multiple collections produce one MutationSyncResponse per child")
        void multipleCollections() {
            var iq = new NodeBuilder().description("iq").attribute("type", "result")
                    .content(new NodeBuilder().description("sync").content(List.of(
                            collection("regular",    attrs -> attrs.attribute("version", "1")),
                            collection("regular_low", attrs -> attrs.attribute("version", "2")),
                            collection("critical_block", attrs -> attrs.attribute("version", "3"))
                    )).build())
                    .build();

            var responses = PARSER.parseBatchedSyncResponse(iq);
            assertEquals(3, responses.size());
            assertEquals(SyncPatchType.REGULAR,        responses.get(0).collectionName());
            assertEquals(SyncPatchType.REGULAR_LOW,    responses.get(1).collectionName());
            assertEquals(SyncPatchType.CRITICAL_BLOCK, responses.get(2).collectionName());
            assertEquals(1L, responses.get(0).version());
            assertEquals(2L, responses.get(1).version());
            assertEquals(3L, responses.get(2).version());
        }

        /**
         * Asserts that a collection-level error in a batched response is captured on the
         * response rather than thrown.
         */
        @Test
        @DisplayName("collection-level errors are captured on the response, not thrown")
        void errorCapturedNotThrown() {
            var iq = new NodeBuilder().description("iq").attribute("type", "result")
                    .content(new NodeBuilder().description("sync").content(List.of(
                            collection("regular",    attrs -> attrs.attribute("version", "1")),
                            collection("regular_low", attrs -> attrs.attribute("type", "error"),
                                    new NodeBuilder().description("error").attribute("code", "409").build())
                    )).build())
                    .build();

            var responses = PARSER.parseBatchedSyncResponse(iq);
            assertEquals(2, responses.size());
            assertTrue(responses.get(0).collectionError().isEmpty(),
                    "successful collection has no captured error");
            var error = responses.get(1).collectionError().orElseThrow();
            assertInstanceOf(WhatsAppWebAppStateSyncException.Conflict.class, error,
                    "409 must be captured as Conflict on the failing collection");
        }

        /**
         * Asserts that an empty {@code <sync>} produces an empty response list.
         */
        @Test
        @DisplayName("empty <sync> returns an empty list")
        void emptySync() {
            var iq = new NodeBuilder().description("iq").attribute("type", "result")
                    .content(new NodeBuilder().description("sync").build())
                    .build();
            assertTrue(PARSER.parseBatchedSyncResponse(iq).isEmpty());
        }

        /**
         * Asserts that an IQ-level error short-circuits the batched parse with the same
         * exception as the single-collection parse.
         */
        @Test
        @DisplayName("IQ-level error short-circuits the batched parse")
        void iqLevelErrorShortCircuits() {
            assertThrows(WhatsAppWebAppStateSyncException.UnexpectedError.class,
                    () -> PARSER.parseBatchedSyncResponse(errorIq(400, null)));
        }
    }

    /**
     * Builds a single-collection IQ wrapper with the given inner collection node.
     *
     * @apiNote
     * Helper that centralises the standard {@code <iq><sync><collection>} skeleton so
     * each test body stays focused on the collection-specific shape.
     *
     * @param iqType the {@code type} attribute on the {@code <iq>}
     * @param collectionNode the inner {@code <collection>} node
     * @return the built {@link Node}
     */
    private static Node iq(String iqType, Node collectionNode) {
        return new NodeBuilder().description("iq").attribute("type", iqType)
                .content(new NodeBuilder().description("sync")
                        .content(collectionNode).build())
                .build();
    }

    /**
     * Builds an IQ-level error node with the given code and optional backoff attribute.
     *
     * @apiNote
     * Helper for the IQ-level error tests; the {@code backoff} attribute is omitted when
     * {@code backoffMs} is {@code null}.
     *
     * @param code the IQ error code
     * @param backoffMs the backoff in milliseconds, or {@code null} to omit
     * @return the built {@link Node}
     */
    private static Node errorIq(int code, Long backoffMs) {
        var error = new NodeBuilder().description("error").attribute("code", String.valueOf(code));
        if (backoffMs != null) error.attribute("backoff", String.valueOf(backoffMs));
        return new NodeBuilder().description("iq").attribute("type", "error")
                .content(error.build()).build();
    }

    /**
     * Functional shim that lets tests configure attributes on a {@link NodeBuilder} via a
     * lambda.
     *
     * @apiNote
     * Used by {@link #collection(String, AttrConfig, Node...)} so each test can supply
     * its own attributes inline.
     */
    @FunctionalInterface
    private interface AttrConfig {
        /**
         * Applies attribute configuration to the supplied {@link NodeBuilder}.
         *
         * @param builder the builder to configure
         */
        void apply(NodeBuilder builder);
    }

    /**
     * Builds a {@code <collection>} node with the given name, attributes and child nodes.
     *
     * @apiNote
     * Helper used by every test in this class to build the collection child of an IQ
     * response without repeating the boilerplate.
     *
     * @param name the collection name
     * @param config the attribute configurer
     * @param children the optional child nodes
     * @return the built {@link Node}
     */
    private static Node collection(String name, AttrConfig config, Node... children) {
        var builder = new NodeBuilder().description("collection").attribute("name", name);
        config.apply(builder);
        if (children.length > 0) {
            builder.content(List.of(children));
        }
        return builder.build();
    }
}
