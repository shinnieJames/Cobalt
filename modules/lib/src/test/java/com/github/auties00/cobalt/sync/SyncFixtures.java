package com.github.auties00.cobalt.sync;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKey;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyData;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyDataBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyId;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyIdBuilder;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Loads sync-package fixtures captured from a live WhatsApp Web session and
 * exposes them to JUnit tests.
 *
 * <p>Fixture provenance:
 * <ul>
 *   <li>JSONL stanza captures are written by the MCP tool
 *       {@code web_live_stanza_dump_to_file} and re-hydrate into Cobalt
 *       {@link Node} instances through {@link #loadEvents(String)} and
 *       {@link #buildNode(JSONObject)}.</li>
 *   <li>{@code .expected.json} oracle outputs are written by
 *       {@code web_live_debug_eval_to_file} and exposed as raw
 *       {@link JSONObject} so individual tests can assert against the
 *       fields they care about.</li>
 *   <li>{@code .synckey.bin} binary fixtures carry the 32-byte sync key
 *       material that pairs with a given encrypted-mutation oracle. They
 *       are loaded byte-for-byte through {@link #loadSyncKey(String)}.</li>
 * </ul>
 *
 * <p>Fixtures live under {@code src/test/resources/fixtures/sync/} and are
 * discovered through the classpath. This class is a direct mirror of
 * {@code com.github.auties00.cobalt.device.DeviceFixtures} — the
 * JSON-tree-walking decoder is generic across packages; only the
 * {@link #FIXTURE_ROOT} differs.
 */
public final class SyncFixtures {
    /**
     * The classpath prefix every sync-fixture path lives under.
     */
    private static final String FIXTURE_ROOT = "fixtures/sync";

    /**
     * Hidden constructor; this is a static-helper class.
     */
    private SyncFixtures() {
        throw new AssertionError("SyncFixtures is not instantiable");
    }

    /**
     * Returns every captured stanza event in the given JSONL fixture, in
     * capture order.
     *
     * @param topic the fixture topic (e.g. {@code "exchange/regular-low/upload-archive"}),
     *              without the {@code .jsonl} extension
     * @return the list of {@code event} sub-objects, each shaped like the
     *         output of the MCP stanza logger
     * @throws UncheckedIOException if the fixture is missing or malformed
     */
    public static List<JSONObject> loadEvents(String topic) {
        Objects.requireNonNull(topic, "topic");
        var resource = FIXTURE_ROOT + "/" + topic + ".jsonl";
        try (var stream = open(resource);
             var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            var events = new ArrayList<JSONObject>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                var record = JSON.parseObject(line);
                var event = record.getJSONObject("event");
                if (event == null) {
                    throw new IllegalStateException("fixture line missing 'event': " + resource);
                }
                events.add(event);
            }
            return events;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read fixture: " + resource, e);
        }
    }

    /**
     * Returns the first event in the given fixture whose {@code tag} matches
     * and whose attributes contain every key/value pair in {@code attrs}.
     *
     * @param topic the fixture topic
     * @param tag   the required stanza tag, or {@code null} to match any
     * @param attrs the required attribute key/value pairs, or {@code null}
     *              to skip attribute filtering
     * @return the matching event
     * @throws AssertionError if no event matches
     */
    public static JSONObject loadEventWhere(String topic, String tag, JSONObject attrs) {
        for (var event : loadEvents(topic)) {
            if (tag != null && !tag.equals(event.getString("tag"))) continue;
            if (attrs != null) {
                var eventAttrs = event.getJSONObject("attrs");
                var allMatch = true;
                for (var key : attrs.keySet()) {
                    var expected = attrs.get(key);
                    var actual = eventAttrs == null ? null : eventAttrs.get(key);
                    if (!Objects.equals(String.valueOf(expected), String.valueOf(actual))) {
                        allMatch = false;
                        break;
                    }
                }
                if (!allMatch) continue;
            }
            return event;
        }
        throw new AssertionError("no event matched topic=" + topic + " tag=" + tag + " attrs=" + attrs);
    }

    /**
     * Reconstructs a Cobalt {@link Node} from the {@code node} sub-tree of a
     * captured event.
     *
     * <p>The node tree is the recursive plain-JSON shape emitted by the MCP
     * stanza logger: each level carries {@code tag}, {@code attrs}, and
     * {@code content}. Binary leaves are objects with {@code kind: "binary"}
     * and a {@code base64} payload; nested children are arrays of the same
     * shape.
     *
     * @param event the event object from {@link #loadEvents(String)}
     * @return the reconstructed {@link Node}
     * @throws IllegalArgumentException if the tree is malformed
     */
    public static Node buildNodeFromEvent(JSONObject event) {
        Objects.requireNonNull(event, "event");
        var nodeTree = event.getJSONObject("node");
        if (nodeTree == null) {
            throw new IllegalArgumentException("event missing 'node' subtree");
        }
        return buildNode(nodeTree);
    }

    /**
     * Recursively reconstructs a {@link Node} from a captured plain-JSON
     * tree.
     *
     * @param tree the {@code {tag, attrs, content}} object
     * @return the reconstructed node
     * @throws IllegalArgumentException if {@code tree} has no tag
     */
    public static Node buildNodeFromTree(JSONObject tree) {
        return buildNode(tree);
    }

    /**
     * Returns the expected-output JSON document paired with the given
     * fixture topic, loaded from {@code <topic>.expected.json} alongside the
     * stanza capture.
     *
     * @param topic the fixture topic
     * @return the parsed expected document
     * @throws UncheckedIOException if the fixture is missing or malformed
     */
    public static JSONObject loadExpected(String topic) {
        Objects.requireNonNull(topic, "topic");
        var resource = FIXTURE_ROOT + "/" + topic + ".expected.json";
        try (var stream = open(resource)) {
            return JSON.parseObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read fixture: " + resource, e);
        }
    }

    /**
     * Returns the live-runtime result payload for an eval-style oracle
     * fixture, unwrapping the {@code result.value} field and re-parsing it
     * as JSON.
     *
     * <p>{@code web_live_debug_eval_to_file} captures wrap the evaluation
     * outcome as
     * {@code {schema, expression, result: {resultType: "string", value: "<json-string>"}}}.
     * The vast majority of oracle invocations stringify their result before
     * returning so the live runtime can deliver it through CDP without
     * structured-clone hazards; this helper undoes that stringification.
     *
     * @param topic the fixture topic
     * @return the parsed inner result document
     * @throws IllegalStateException if the fixture is malformed or the
     *                               result is not a {@code string} payload
     */
    public static JSONObject loadOracle(String topic) {
        var outer = loadExpected(topic);
        var result = outer.getJSONObject("result");
        if (result == null) {
            throw new IllegalStateException("fixture " + topic + " missing 'result' wrapper");
        }
        var resultType = result.getString("resultType");
        var value = result.getString("value");
        if (!"string".equals(resultType) || value == null) {
            throw new IllegalStateException("fixture " + topic + " result is not a string payload: type=" + resultType);
        }
        return JSON.parseObject(value);
    }

    /**
     * Decodes a base64-encoded byte field out of an oracle document.
     *
     * <p>Live oracle expressions cannot transport raw {@code Uint8Array}
     * payloads through CDP, so byte fields are wrapped as
     * {@code {"base64": "..."}} objects on the WA Web side. This helper
     * walks a dotted field path inside an oracle JSON document and pulls
     * the decoded bytes back out.
     *
     * @param oracle    the oracle JSON document (from {@link #loadOracle(String)}
     *                  or an inner sub-object)
     * @param fieldPath the dotted path of the wrapping object, e.g.
     *                  {@code "patch.snapshotMac"} or {@code "indexKey"}
     * @return the decoded byte array
     * @throws IllegalArgumentException if the path does not resolve to a
     *                                  base64 wrapper object
     */
    public static byte[] decodeOracleBytes(JSONObject oracle, String fieldPath) {
        Objects.requireNonNull(oracle, "oracle");
        Objects.requireNonNull(fieldPath, "fieldPath");
        var segments = fieldPath.split("\\.");
        Object cursor = oracle;
        for (var segment : segments) {
            if (!(cursor instanceof JSONObject obj)) {
                throw new IllegalArgumentException("path " + fieldPath + " traverses non-object at " + segment);
            }
            cursor = obj.get(segment);
            if (cursor == null) {
                throw new IllegalArgumentException("path " + fieldPath + " missing segment " + segment);
            }
        }
        if (cursor instanceof String s) {
            return Base64.getDecoder().decode(s);
        }
        if (cursor instanceof JSONObject wrapper) {
            var b64 = wrapper.getString("base64");
            if (b64 == null) {
                throw new IllegalArgumentException("path " + fieldPath + " resolved to object without 'base64' field: " + wrapper);
            }
            return Base64.getDecoder().decode(b64);
        }
        throw new IllegalArgumentException("path " + fieldPath + " resolved to non-bytes value: " + cursor);
    }

    /**
     * Returns the raw sync-key bytes captured alongside a fixture, loaded
     * from {@code <topic>.synckey.bin}.
     *
     * <p>Encrypted-mutation oracles need both the encrypted payload and the
     * 32-byte sync key that produced it; the key is stored as a binary
     * sibling to the JSONL/expected.json files so it never gets stringified
     * through JSON. Tests that drive {@code MutationKeys.ofSyncKey(...)}
     * read the key through this helper.
     *
     * @param topic the fixture topic
     * @return the raw sync-key bytes (32 bytes for a valid app-state sync key)
     * @throws UncheckedIOException if the resource is missing or unreadable
     */
    public static byte[] loadSyncKey(String topic) {
        Objects.requireNonNull(topic, "topic");
        var resource = FIXTURE_ROOT + "/" + topic + ".synckey.bin";
        try (var stream = open(resource)) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read fixture: " + resource, e);
        }
    }

    /**
     * Returns whether the given fixture topic exists on the classpath.
     *
     * <p>Looks for the JSONL stanza capture; oracle-only fixtures should be
     * probed through {@link #findExpected(String)} instead.
     *
     * @param topic the fixture topic
     * @return {@code true} when {@code <topic>.jsonl} is on the classpath
     */
    public static boolean isAvailable(String topic) {
        return SyncFixtures.class.getResourceAsStream("/" + FIXTURE_ROOT + "/" + topic + ".jsonl") != null;
    }

    /**
     * Returns whether the given fixture topic has an accompanying oracle
     * document on the classpath.
     *
     * @param topic the fixture topic
     * @return {@code true} when {@code <topic>.expected.json} is on the
     *         classpath
     */
    public static boolean isOracleAvailable(String topic) {
        return SyncFixtures.class.getResourceAsStream("/" + FIXTURE_ROOT + "/" + topic + ".expected.json") != null;
    }

    /**
     * Returns the parsed expected document for the given topic if available.
     *
     * @param topic the fixture topic
     * @return the document, or {@link Optional#empty()} when no expected
     *         file accompanies the fixture
     */
    public static Optional<JSONObject> findExpected(String topic) {
        var resource = FIXTURE_ROOT + "/" + topic + ".expected.json";
        try (var stream = SyncFixtures.class.getResourceAsStream("/" + resource)) {
            if (stream == null) return Optional.empty();
            return Optional.of(JSON.parseObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read fixture: " + resource, e);
        }
    }

    /**
     * Returns whether the per-syncType history-sync fixture triplet
     * ({@code notification.json}, {@code chunk.b64}, {@code expected.json})
     * exists on the classpath for the given chunk-type slug.
     *
     * <p>History-sync fixtures live under
     * {@code fixtures/sync/history/<slug>/} rather than the flat
     * {@code .jsonl} layout of stanza fixtures, so they cannot use
     * {@link #isAvailable(String)}. Only the {@code chunk.b64} file is
     * probed because the three siblings are always written together by
     * {@code tools/web/mcp-server/scripts/split-history-fixtures.mjs}.
     *
     * @param typeSlug the chunk-type slug, e.g. {@code "initial-bootstrap"}
     *                 or {@code "on-demand"}; the values come from the
     *                 {@code TYPE_SLUG} map in the splitter script
     * @return {@code true} when the captured fixture is present on the
     *         classpath
     */
    public static boolean isHistoryChunkAvailable(String typeSlug) {
        Objects.requireNonNull(typeSlug, "typeSlug");
        return SyncFixtures.class.getResourceAsStream("/" + FIXTURE_ROOT + "/history/" + typeSlug + "/chunk.b64") != null;
    }

    /**
     * Returns the inflated {@code HistorySync} protobuf bytes captured for
     * the given chunk-type slug.
     *
     * <p>The bytes are the plaintext payload that WhatsApp Web's
     * {@code WAWebHandleHistorySyncChunk.handleHistorySyncChunk} feeds into
     * {@code decodeProtobuf(WAWebProtobufsHistorySync.pb.HistorySyncSpec, ...)}
     * after the encrypted CDN blob has been decrypted and gzip-inflated, or
     * after the inline bootstrap payload has been inflated. They round-trip
     * to the value carried by the sibling {@code expected.json} document.
     *
     * @param typeSlug the chunk-type slug
     * @return the raw protobuf bytes
     * @throws UncheckedIOException if the fixture is missing or malformed
     */
    public static byte[] loadHistoryChunkBytes(String typeSlug) {
        Objects.requireNonNull(typeSlug, "typeSlug");
        var resource = FIXTURE_ROOT + "/history/" + typeSlug + "/chunk.b64";
        try (var stream = open(resource)) {
            var b64 = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
            return Base64.getDecoder().decode(b64);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read fixture: " + resource, e);
        }
    }

    /**
     * Returns the {@code HistorySync} oracle JSON captured for the given
     * chunk-type slug.
     *
     * <p>This is the parsed protobuf value WhatsApp Web's
     * {@code WAWebHandleHistorySyncChunk.handleHistorySyncChunk} obtains
     * from {@code decodeProtobuf(HistorySyncSpec, inflatedBytes)}, captured
     * verbatim through the in-page {@code __hs_capture} hook installed by
     * {@code tools/web/mcp-server/scripts/split-history-fixtures.mjs}.
     *
     * @param typeSlug the chunk-type slug
     * @return the oracle document
     * @throws UncheckedIOException if the fixture is missing or malformed
     */
    public static JSONObject loadHistoryExpected(String typeSlug) {
        Objects.requireNonNull(typeSlug, "typeSlug");
        var resource = FIXTURE_ROOT + "/history/" + typeSlug + "/expected.json";
        try (var stream = open(resource)) {
            return JSON.parseObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read fixture: " + resource, e);
        }
    }

    /**
     * Returns the captured {@code HistorySyncNotification} payload (in its
     * pre-decode protobuf-JSON form) paired with the given chunk-type slug.
     *
     * <p>The notification carries the {@code syncType},
     * {@code chunkOrder}, optional {@code initialHistBootstrapInlinePayload}
     * (gzip-compressed inline chunk), and the {@code directPath} /
     * {@code mediaKey} download options. The outer wrapper additionally
     * exposes {@code msgKey} and {@code progress} pulled out of the WA Web
     * dispatcher context.
     *
     * @param typeSlug the chunk-type slug
     * @return the notification document
     * @throws UncheckedIOException if the fixture is missing or malformed
     */
    public static JSONObject loadHistoryNotification(String typeSlug) {
        Objects.requireNonNull(typeSlug, "typeSlug");
        var resource = FIXTURE_ROOT + "/history/" + typeSlug + "/notification.json";
        try (var stream = open(resource)) {
            return JSON.parseObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read fixture: " + resource, e);
        }
    }

    /**
     * Creates an in-memory temporary store seeded with the given self-PN
     * and self-LID and pre-populated with one app-state sync key.
     *
     * <p>Encrypted-mutation tests need a store that already knows the sync
     * key the captured payload was encrypted under; otherwise the
     * decryption path takes the missing-key branch. This helper wraps
     * {@link DeviceFixtures#temporaryStore(Jid, Jid)} and inserts an
     * {@link AppStateSyncKey} carrying the given id and key material.
     *
     * @param selfPn      the local user's PN-form bare JID
     * @param selfLid     the local user's LID-form bare JID, or
     *                    {@code null} for a pre-LID-migration store
     * @param syncKeyId   the sync key's raw id bytes (typically a SHA-256
     *                    prefix; WA Web's
     *                    {@code WAWebSyncdMutationsCryptoUtils} uses the
     *                    raw id verbatim)
     * @param syncKeyData the 32-byte symmetric key material
     * @return the configured temporary store with the key planted
     */
    public static WhatsAppStore temporaryStoreWithSyncKey(Jid selfPn, Jid selfLid, byte[] syncKeyId, byte[] syncKeyData) {
        Objects.requireNonNull(syncKeyId, "syncKeyId");
        Objects.requireNonNull(syncKeyData, "syncKeyData");
        var store = DeviceFixtures.temporaryStore(selfPn, selfLid);
        var keyId = new AppStateSyncKeyIdBuilder()
                .keyId(syncKeyId)
                .build();
        var keyData = new AppStateSyncKeyDataBuilder()
                .keyData(syncKeyData)
                .timestamp(Instant.now())
                .build();
        var key = new AppStateSyncKeyBuilder()
                .keyId(keyId)
                .keyData(keyData)
                .build();
        store.addWebAppStateKeys(List.of(key));
        return store;
    }

    /**
     * Opens the named classpath resource.
     *
     * @param resourcePath the resource path under {@code src/test/resources/}
     * @return an input stream over the resource bytes
     * @throws IOException if the resource is missing
     */
    private static InputStream open(String resourcePath) throws IOException {
        var stream = SyncFixtures.class.getResourceAsStream("/" + resourcePath);
        if (stream == null) {
            throw new IOException("fixture not found on classpath: " + resourcePath);
        }
        return stream;
    }

    /**
     * Flattens a captured attribute value into the string form the Cobalt
     * {@code NodeBuilder.attribute(String, String)} setter expects.
     *
     * <p>The MCP stanza logger captures WA Web's internal Jid wrappers as
     * {@code {"$1": {"type": <int>, "user": <string|null>, "server": <string>}}}.
     * Cobalt's {@link Node} carries those same JIDs as bare strings of the
     * form {@code user@server} (or just {@code @server} when {@code user}
     * is {@code null}, mirroring the WA Web {@code S_WHATSAPP_NET}-style
     * server JIDs). Any other shape is delegated to
     * {@link String#valueOf(Object)}.
     *
     * @param value the raw captured attribute value
     * @return the string-form value
     */
    private static String attrValueAsString(Object value) {
        if (value instanceof JSONObject obj && obj.containsKey("$1")) {
            var inner = obj.getJSONObject("$1");
            var user = inner.getString("user");
            var server = inner.getString("server");
            return (user == null ? "" : user) + "@" + (server == null ? "" : server);
        }
        return String.valueOf(value);
    }

    /**
     * Decodes a binary leaf object into raw bytes.
     *
     * @param binary the {@code {kind: "binary", base64: "..."}} object
     * @return the decoded bytes
     * @throws IllegalArgumentException if the object is not a binary leaf
     */
    private static byte[] decodeBinary(JSONObject binary) {
        var base64 = binary.getString("base64");
        if (base64 == null) {
            throw new IllegalArgumentException("binary leaf missing 'base64' field: " + binary);
        }
        return Base64.getDecoder().decode(base64);
    }

    /**
     * Builds a {@link Node} from a plain-JSON tree.
     *
     * @param tree the {@code {tag, attrs, content}} object
     * @return the reconstructed node
     */
    private static Node buildNode(JSONObject tree) {
        var tag = tree.getString("tag");
        if (tag == null || tag.isEmpty()) {
            throw new IllegalArgumentException("node tree missing 'tag': " + tree);
        }

        var builder = new NodeBuilder().description(tag);

        var attrs = tree.getJSONObject("attrs");
        if (attrs != null) {
            for (var key : attrs.keySet()) {
                var value = attrs.get(key);
                if (value == null) continue;
                builder.attribute(key, attrValueAsString(value));
            }
        }

        var content = tree.get("content");
        applyContent(builder, content);
        return builder.build();
    }

    /**
     * Applies a {@code content} value (binary leaf, child array, string, or
     * {@code null}) onto the builder.
     *
     * @param builder the target builder
     * @param content the JSON-shaped content value
     */
    private static void applyContent(NodeBuilder builder, Object content) {
        if (content == null) return;

        if (content instanceof JSONObject leaf) {
            if ("binary".equals(leaf.getString("kind"))) {
                builder.content(decodeBinary(leaf));
                return;
            }
            builder.content(buildNode(leaf));
            return;
        }

        if (content instanceof JSONArray children) {
            var built = new ArrayList<Node>(children.size());
            byte[] inlineBytes = null;
            for (var entry : children) {
                if (entry instanceof JSONObject obj) {
                    if ("binary".equals(obj.getString("kind"))) {
                        inlineBytes = decodeBinary(obj);
                        continue;
                    }
                    built.add(buildNode(obj));
                } else if (entry != null) {
                    built.add(new NodeBuilder().description("__text").content(String.valueOf(entry)).build());
                }
            }
            if (!built.isEmpty()) {
                builder.content(built);
            } else if (inlineBytes != null) {
                builder.content(inlineBytes);
            }
            return;
        }

        builder.content(String.valueOf(content));
    }
}
