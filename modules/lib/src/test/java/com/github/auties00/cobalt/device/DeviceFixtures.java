package com.github.auties00.cobalt.device;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.client.WhatsAppClientType;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.store.WhatsAppStoreFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Loads device-package fixtures captured from a live WhatsApp Web session and
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
 * </ul>
 *
 * <p>Both fixture families live under
 * {@code src/test/resources/fixtures/device/} and are discovered through
 * the classpath, matching the pattern established by
 * {@code com.github.auties00.cobalt.call.transport.relay.Fixtures}.
 */
public final class DeviceFixtures {
    /**
     * The classpath prefix every device-fixture path lives under.
     */
    private static final String FIXTURE_ROOT = "fixtures/device";

    /**
     * Hidden constructor; this is a static-helper class.
     */
    private DeviceFixtures() {
        throw new AssertionError("DeviceFixtures is not instantiable");
    }

    /**
     * Returns every captured stanza event in the given JSONL fixture, in
     * capture order.
     *
     * @param topic the fixture topic (e.g. {@code "usync-self"}), without
     *              the {@code .jsonl} extension
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
     * <p>The node tree is the recursive plain-JSON shape emitted by
     * {@code script-sources.ts}: each level carries {@code tag},
     * {@code attrs}, and {@code content}. Binary leaves are objects with
     * {@code kind: "binary"} and a {@code base64} payload; nested children
     * are arrays of the same shape.
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
     * Returns whether the given fixture topic exists on the classpath.
     *
     * <p>Allows tests to skip cleanly when a corpus has not yet been
     * captured, instead of hard-failing on {@code getResourceAsStream}
     * returning {@code null}.
     *
     * @param topic the fixture topic
     * @return {@code true} when {@code <topic>.jsonl} is on the classpath
     */
    public static boolean isAvailable(String topic) {
        return DeviceFixtures.class.getResourceAsStream("/" + FIXTURE_ROOT + "/" + topic + ".jsonl") != null;
    }

    /**
     * Creates an in-memory temporary store pre-configured with the given
     * self-PN and self-LID. Use as the {@code store} dependency for any
     * device-package class that takes a {@link WhatsAppStore}.
     *
     * @param selfPn  the local user's PN-form bare JID
     * @param selfLid the local user's LID-form bare JID, or {@code null}
     *                when the test wants a pre-LID-migration store
     * @return the configured temporary store
     * @throws UncheckedIOException if the underlying factory cannot create
     *                              the store
     */
    public static WhatsAppStore temporaryStore(Jid selfPn, Jid selfLid) {
        Objects.requireNonNull(selfPn, "selfPn");
        try {
            var store = WhatsAppStoreFactory.temporary()
                    .create(WhatsAppClientType.WEB, Long.parseLong(selfPn.user()));
            store.setJid(selfPn);
            if (selfLid != null) {
                store.setLid(selfLid);
            }
            return store;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to create temporary store", e);
        }
    }

    /**
     * Opens the named classpath resource.
     *
     * @param resourcePath the resource path under {@code src/test/resources/}
     * @return an input stream over the resource bytes
     * @throws IOException if the resource is missing
     */
    private static InputStream open(String resourcePath) throws IOException {
        var stream = DeviceFixtures.class.getResourceAsStream("/" + resourcePath);
        if (stream == null) {
            throw new IOException("fixture not found on classpath: " + resourcePath);
        }
        return stream;
    }

    /**
     * Decodes a binary leaf object into raw bytes.
     *
     * @param binary the {@code {kind: "binary", base64: "..."}} object
     * @return the decoded bytes
     * @throws IllegalArgumentException if the object is not a binary leaf
     */
    /**
     * Flattens a captured attribute value into the string form the Cobalt
     * {@code NodeBuilder.attribute(String, String)} setter expects.
     *
     * <p>The MCP stanza logger captures WAWap's internal Jid wrappers as
     * {@code {"$1": {"type": <int>, "user": <string|null>, "server": <string>}}}.
     * Cobalt's {@link Node} carries those same JIDs as bare strings of the
     * form {@code user@server} (or just {@code @server} when {@code user}
     * is {@code null}, mirroring the WA Web {@code S_WHATSAPP_NET}-style
     * server JIDs). Any other shape is delegated to {@link String#valueOf(Object)}.
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
            // Single embedded child node (rare path: the logger sometimes emits a
            // single child object rather than a one-element array).
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
                    // Stringified leaf — treated as a text child node. This is rare
                    // for device-package fixtures.
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

    /**
     * Returns the parsed expected document for the given topic if available.
     *
     * @param topic the fixture topic
     * @return the document, or {@link Optional#empty()} when no expected
     *         file accompanies the fixture
     */
    public static Optional<JSONObject> findExpected(String topic) {
        var resource = FIXTURE_ROOT + "/" + topic + ".expected.json";
        try (var stream = DeviceFixtures.class.getResourceAsStream("/" + resource)) {
            if (stream == null) return Optional.empty();
            return Optional.of(JSON.parseObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read fixture: " + resource, e);
        }
    }
}
