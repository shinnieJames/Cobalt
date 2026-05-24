package com.github.auties00.cobalt.wam;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Shared loader for WAM-package fixtures captured from a live
 * WhatsApp Web session.
 *
 * @apiNote
 * Consumed by every KAT and oracle test in this package; centralises
 * the three on-disk fixture shapes Cobalt uses so individual tests
 * declare only what they need:
 * <ul>
 *   <li>{@code .json} vector fixtures whose header pins a captured
 *       snapshot revision, loaded via {@link #loadJson(String)} or
 *       mapped via {@link #loadVectors(String, Function)};</li>
 *   <li>{@code .expected.json} oracle outputs captured by
 *       {@code mcp__whatsapp__web_live_debug_eval_to_file}, exposed
 *       raw through {@link #loadExpected(String)} and unwrapped
 *       through {@link #loadOracle(String)};</li>
 *   <li>{@code .jsonl} stanza captures emitted by
 *       {@code mcp__whatsapp__web_live_stanza_dump_to_file}, loaded
 *       through {@link #loadEvents(String)}.</li>
 * </ul>
 *
 * @implNote
 * All WAM fixtures live under
 * {@code modules/lib/src/test/resources/fixtures/wam/}. Tests are
 * expected to call {@link #requireSnapshotRevision(JSONObject, long)}
 * on load so revision drift against the live WA Web bundle fails
 * loudly rather than silently passing against stale captures.
 */
public final class WamFixtures {
    /**
     * The classpath prefix every WAM fixture sits under, relative to
     * {@code src/test/resources/}.
     */
    private static final String FIXTURE_ROOT = "fixtures/wam";

    /**
     * Hidden constructor; this is a static-helper class.
     */
    private WamFixtures() {
        throw new AssertionError("WamFixtures is not instantiable");
    }

    /**
     * Loads and parses a JSON object from the WAM fixture corpus.
     *
     * @apiNote
     * Vector-style fixtures typically carry a header
     * ({@code snapshotRevision}, {@code liveRuntimeRevision},
     * {@code capturedAt}, {@code capturedVia}) alongside a
     * {@code vectors} array; the caller should immediately call
     * {@link #requireSnapshotRevision(JSONObject, long)} on the
     * returned object.
     *
     * @param resource the resource path relative to
     *                 {@link #FIXTURE_ROOT}
     * @return the parsed JSON object
     * @throws UncheckedIOException if the fixture is missing or
     *                              malformed
     */
    public static JSONObject loadJson(String resource) {
        Objects.requireNonNull(resource, "resource");
        var path = FIXTURE_ROOT + "/" + resource;
        try (var stream = open(path)) {
            return JSON.parseObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new UncheckedIOException("failed to read fixture: " + path, error);
        }
    }

    /**
     * Loads a vector-style fixture and maps each entry of its
     * {@code vectors} array through {@code mapper} into a typed
     * record.
     *
     * @apiNote
     * The shared loader for KAT tests; same shape as the
     * {@code Ed25519LiveBundleKatTest.loadVectors} pattern.
     *
     * @param <T>      the vector record type
     * @param resource the resource path relative to
     *                 {@link #FIXTURE_ROOT}
     * @param mapper   the per-vector mapper
     * @return the list of mapped vectors, in capture order
     * @throws UncheckedIOException  if the fixture is missing or
     *                               malformed
     * @throws IllegalStateException if the fixture has no
     *                               {@code vectors} array or an entry
     *                               is not a JSON object
     */
    public static <T> List<T> loadVectors(String resource, Function<JSONObject, T> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        var fixture = loadJson(resource);
        var vectors = fixture.getJSONArray("vectors");
        if (vectors == null) {
            throw new IllegalStateException("fixture " + resource + " has no 'vectors' array");
        }
        var out = new ArrayList<T>(vectors.size());
        for (var entry : vectors) {
            if (!(entry instanceof JSONObject obj)) {
                throw new IllegalStateException("non-object entry in 'vectors' of " + resource + ": " + entry);
            }
            out.add(mapper.apply(obj));
        }
        return out;
    }

    /**
     * Returns the expected-output JSON document paired with
     * {@code topic}, loaded from {@code <topic>.expected.json}.
     *
     * @param topic the fixture topic
     * @return the parsed expected document
     * @throws UncheckedIOException if the fixture is missing or
     *                              malformed
     */
    public static JSONObject loadExpected(String topic) {
        Objects.requireNonNull(topic, "topic");
        return loadJson(topic + ".expected.json");
    }

    /**
     * Returns the live-runtime result payload for an eval-style
     * oracle fixture, unwrapping the {@code result.value} stringified
     * JSON produced by
     * {@code mcp__whatsapp__web_live_debug_eval_to_file}.
     *
     * @apiNote
     * The captures wrap the evaluation outcome as
     * {@code {schema, expression, result: {resultType: "string", value: "<json-string>"}}};
     * most oracle invocations stringify their result before returning
     * so the live runtime can ship it through CDP without
     * structured-clone hazards. This helper undoes the
     * stringification and returns the inner document directly.
     *
     * @param topic the fixture topic
     * @return the parsed inner result document
     * @throws IllegalStateException if the fixture is malformed or the
     *                               result is not a {@code string}
     *                               payload
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
     * Returns every event in the given JSONL fixture, in capture
     * order.
     *
     * @apiNote
     * Used by stanza-style captures of WAM upload IQs emitted by
     * {@code mcp__whatsapp__web_live_stanza_dump_to_file}; each
     * captured line is expected to carry an {@code event} sub-object
     * shaped like the rest of the MCP stanza logger output.
     *
     * @param topic the fixture topic (without the {@code .jsonl}
     *              extension)
     * @return the list of {@code event} sub-objects
     * @throws UncheckedIOException  if the fixture is missing or
     *                               malformed
     * @throws IllegalStateException if a captured line has no
     *                               {@code event} sub-object
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
        } catch (IOException error) {
            throw new UncheckedIOException("failed to read fixture: " + resource, error);
        }
    }

    /**
     * Returns whether the given JSON fixture is present on the test
     * classpath.
     *
     * @apiNote
     * Lets a test skip cleanly when a corpus has not yet been
     * captured rather than failing with a missing-resource error.
     *
     * @param resource the resource path relative to
     *                 {@link #FIXTURE_ROOT}
     * @return {@code true} when the resource is present
     */
    public static boolean isAvailable(String resource) {
        Objects.requireNonNull(resource, "resource");
        return WamFixtures.class.getResourceAsStream("/" + FIXTURE_ROOT + "/" + resource) != null;
    }

    /**
     * Asserts that the given fixture header pins {@code expected} as
     * its captured snapshot revision.
     *
     * @apiNote
     * Every KAT test in this package calls this immediately after
     * loading a fixture so a captured-vs-live revision drift fails
     * the test loudly rather than passing silently against stale
     * captures.
     *
     * @param fixtureHeader the JSON object carrying a
     *                      {@code snapshotRevision} numeric field
     * @param expected      the snapshot revision the test was written
     *                      against
     * @throws AssertionError if the header has no
     *                        {@code snapshotRevision} or it does not
     *                        match {@code expected}
     */
    public static void requireSnapshotRevision(JSONObject fixtureHeader, long expected) {
        Objects.requireNonNull(fixtureHeader, "fixtureHeader");
        var actual = fixtureHeader.getLong("snapshotRevision");
        if (actual == null) {
            throw new AssertionError("fixture has no 'snapshotRevision' field; expected " + expected);
        }
        if (actual != expected) {
            throw new AssertionError("fixture snapshotRevision drift: expected " + expected + " but was " + actual);
        }
    }

    /**
     * Returns the {@code vectors} array of a vector-style fixture as
     * a raw {@link JSONArray}.
     *
     * @apiNote
     * For callers that prefer to iterate without a per-row mapper;
     * use {@link #loadVectors(String, Function)} when the mapping is
     * stable.
     *
     * @param resource the resource path relative to
     *                 {@link #FIXTURE_ROOT}
     * @return the {@code vectors} array
     * @throws IllegalStateException if the fixture has no
     *                               {@code vectors} array
     */
    public static JSONArray rawVectors(String resource) {
        var fixture = loadJson(resource);
        var vectors = fixture.getJSONArray("vectors");
        if (vectors == null) {
            throw new IllegalStateException("fixture " + resource + " has no 'vectors' array");
        }
        return vectors;
    }

    /**
     * Opens the named classpath resource for reading.
     *
     * @param resourcePath the resource path relative to
     *                     {@code src/test/resources/}
     * @return an input stream over the resource bytes
     * @throws IOException if the resource is missing
     */
    private static InputStream open(String resourcePath) throws IOException {
        var stream = WamFixtures.class.getResourceAsStream("/" + resourcePath);
        if (stream == null) {
            throw new IOException("fixture not found on classpath: " + resourcePath);
        }
        return stream;
    }
}
