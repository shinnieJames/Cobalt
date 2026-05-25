package com.github.auties00.cobalt.call;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;

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
 * Static test helper that loads call-package fixtures captured from live
 * WhatsApp Web sessions and reconstructs them as Cobalt {@link Node} trees
 * for the {@code com.github.auties00.cobalt.call} test suite.
 *
 * <p>Each captured flow is recorded from up to three roles: {@code caller}
 * (business session), {@code callee} (primary session), and {@code third}
 * (history-sync session, only for group flows), driven by
 * {@code src/test/resources/fixtures/call/generate.mjs}. Fixtures live under
 * {@code src/test/resources/fixtures/call/} as {@code <flow>.<role>.jsonl}
 * (captured stanza events in capture order) paired with
 * {@code <flow>.<role>.expected.json} (the eval-side result and auto-hook
 * state snapshot), plus companion {@code .ack.jsonl} and
 * {@code .terminate.jsonl} streams.
 *
 * <p>The tree-walking decoder mirrors
 * {@link com.github.auties00.cobalt.message.MessageFixtures#buildNodeFromEvent};
 * only the {@link #FIXTURE_ROOT} differs. JID-attribute normalisation
 * additionally handles {@code domainType=2}, the {@code @call} envelope
 * server used by group offers.
 */
public final class CallFixtures {
    /**
     * The classpath prefix every call-fixture path lives under.
     */
    private static final String FIXTURE_ROOT = "fixtures/call";

    /**
     * Hidden constructor; this is a static-helper class.
     */
    private CallFixtures() {
        throw new AssertionError("CallFixtures is not instantiable");
    }

    /**
     * Returns every captured stanza event in the given JSONL fixture, in
     * capture order.
     *
     * @param topic the fixture topic, e.g. {@code "1to1/audio-accept.caller"}
     *              (without the {@code .jsonl} extension)
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
     * Returns whether the given fixture topic exists on the classpath.
     *
     * <p>Allows tests to skip cleanly when a corpus has not yet been
     * captured (notably the inbound side of group calls and the
     * offline-delivered offer notice, which the server does not always
     * route to linked web sessions).
     *
     * @param topic the fixture topic
     * @return {@code true} when {@code <topic>.jsonl} is on the classpath
     */
    public static boolean isAvailable(String topic) {
        return CallFixtures.class.getResourceAsStream("/" + FIXTURE_ROOT + "/" + topic + ".jsonl") != null;
    }

    /**
     * Reconstructs a Cobalt {@link Node} from the {@code node} sub-tree of
     * a captured event.
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
     * Returns the first event in {@code topic} whose {@code tag} matches
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
     * Returns the first captured {@code <call><X .../></call>} event whose
     * inner-payload tag (offer, accept, reject, ...) matches {@code childTag}
     * and whose direction matches {@code direction} ({@code "in"},
     * {@code "out"}, or {@code null} for either).
     *
     * @param topic     the fixture topic
     * @param childTag  the inner-payload tag to find
     * @param direction the wire direction, or {@code null}
     * @return the matching event
     * @throws AssertionError if no event matches
     */
    public static JSONObject loadCallEventWithChild(String topic, String childTag, String direction) {
        Objects.requireNonNull(childTag, "childTag");
        for (var event : loadEvents(topic)) {
            if (!"call".equals(event.getString("tag"))) continue;
            if (direction != null && !direction.equals(event.getString("direction"))) continue;
            var node = event.getJSONObject("node");
            if (node == null) continue;
            var content = node.get("content");
            if (!(content instanceof JSONArray children)) continue;
            for (var entry : children) {
                if (entry instanceof JSONObject child && childTag.equals(child.getString("tag"))) {
                    return event;
                }
            }
        }
        throw new AssertionError("no <call><" + childTag + "> found in topic=" + topic + " direction=" + direction);
    }

    /**
     * Returns the expected-output JSON document paired with the given
     * fixture topic, loaded from {@code <topic>.expected.json}.
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
     * Returns the parsed expected document for the given topic if
     * available, or {@link Optional#empty()} otherwise.
     *
     * @param topic the fixture topic
     * @return the document, or {@link Optional#empty()}
     */
    public static Optional<JSONObject> findExpected(String topic) {
        var resource = FIXTURE_ROOT + "/" + topic + ".expected.json";
        try (var stream = CallFixtures.class.getResourceAsStream("/" + resource)) {
            if (stream == null) return Optional.empty();
            return Optional.of(JSON.parseObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read fixture: " + resource, e);
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
        var stream = CallFixtures.class.getResourceAsStream("/" + resourcePath);
        if (stream == null) {
            throw new IOException("fixture not found on classpath: " + resourcePath);
        }
        return stream;
    }

    /**
     * Flattens a captured attribute value into the string form
     * {@link NodeBuilder#attribute(String, String)} expects.
     *
     * <p>The MCP stanza logger captures WA Web's internal Jid wrappers in
     * two shapes:
     * <ul>
     *   <li><b>Server JID</b>: {@code {"$1": {"type", "user", "server"}}};</li>
     *   <li><b>Device JID</b>: {@code {"$1": {"type", "user", "device", "domainType"}}}.
     *       {@code domainType=0} maps to {@code @s.whatsapp.net},
     *       {@code domainType=1} to {@code @lid}, {@code domainType=2} to
     *       {@code @call} (the call-envelope server used by group offers).</li>
     * </ul>
     *
     * @param value the raw captured attribute value
     * @return the string-form value
     */
    private static String attrValueAsString(Object value) {
        if (value instanceof JSONObject obj && obj.containsKey("$1")) {
            var inner = obj.getJSONObject("$1");
            var user = inner.getString("user");
            var server = inner.getString("server");
            if (server != null) {
                return (user == null ? "" : user) + "@" + server;
            }
            var domainType = inner.getInteger("domainType");
            if (domainType != null) {
                var serverForDomain = switch (domainType) {
                    case 0 -> "s.whatsapp.net";
                    case 1 -> "lid";
                    case 2 -> "call";
                    default -> "domain" + domainType;
                };
                var deviceIndex = inner.getInteger("device");
                var deviceSuffix = (deviceIndex != null && deviceIndex != 0)
                        ? ":" + deviceIndex
                        : "";
                return (user == null ? "" : user) + deviceSuffix + "@" + serverForDomain;
            }
            return (user == null ? "" : user) + "@";
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
     * Applies a {@code content} value (binary leaf, child array, string,
     * or {@code null}) onto the builder.
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
