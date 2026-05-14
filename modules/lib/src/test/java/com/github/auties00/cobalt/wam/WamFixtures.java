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
 * Loads WAM-package fixtures captured from a live WhatsApp Web session
 * and exposes them to JUnit tests.
 *
 * <p>Fixture provenance follows the same model as
 * {@code DeviceFixtures} and {@code MessageFixtures}:
 *
 * <ul>
 *   <li>{@code .json} vector fixtures (keyed by a captured snapshot
 *       revision header) are loaded via {@link #loadVectors(String, Function)}
 *       and {@link #loadJson(String)}.</li>
 *   <li>{@code .expected.json} oracle outputs captured by
 *       {@code mcp__whatsapp__web_live_debug_eval_to_file} are exposed
 *       through {@link #loadExpected(String)} (raw) and
 *       {@link #loadOracle(String)} (unwrapped from the
 *       {@code result.value} stringified payload).</li>
 *   <li>{@code .jsonl} stanza captures emitted by
 *       {@code mcp__whatsapp__web_live_stanza_dump_to_file} (for example
 *       upload IQs) are loaded by {@link #loadEvents(String)}.</li>
 * </ul>
 *
 * <p>All WAM fixtures live under
 * {@code modules/lib/src/test/resources/fixtures/wam/}. Each fixture is
 * pinned to a snapshot revision documented in its header; tests are
 * expected to call {@link #requireSnapshotRevision(JSONObject, long)} on
 * load to fail loudly when the live WA Web revision moves away from the
 * captured one.
 */
public final class WamFixtures {
    /**
     * Classpath prefix for every WAM fixture.
     */
    private static final String FIXTURE_ROOT = "fixtures/wam";

    /**
     * Hidden constructor; this is a static-helper class.
     */
    private WamFixtures() {
        throw new AssertionError("WamFixtures is not instantiable");
    }

    /**
     * Loads a JSON document from the WAM fixture corpus.
     *
     * <p>The document must be a JSON object; vector fixtures typically
     * carry a header section ({@code snapshotRevision},
     * {@code liveRuntimeRevision}, {@code capturedAt}, {@code capturedVia})
     * alongside a {@code vectors} array.
     *
     * @param resource the resource path relative to the WAM fixture
     *                 root, e.g. {@code "ed25519-live-bundle-vectors.json"}
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
     * Loads a vector-style fixture and maps each entry of the
     * {@code vectors} JSON array through {@code mapper} into a typed
     * record.
     *
     * <p>This is the shared loader for KAT-style tests, mirroring the
     * {@code Ed25519LiveBundleKatTest.loadVectors} pattern.
     *
     * @param <T>      the vector record type
     * @param resource the resource path under {@link #FIXTURE_ROOT}
     * @param mapper   a function turning each vector {@link JSONObject}
     *                 into a {@code T}
     * @return the list of mapped vectors, in capture order
     * @throws UncheckedIOException  if the fixture is missing or malformed
     * @throws IllegalStateException if the fixture has no
     *                               {@code vectors} array
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
     * Returns the expected-output JSON document paired with the given
     * fixture topic, loaded from {@code <topic>.expected.json}.
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
     * Returns the live-runtime result payload for an eval-style oracle
     * fixture, unwrapping the {@code result.value} stringified JSON
     * produced by {@code web_live_debug_eval_to_file}.
     *
     * <p>{@code web_live_debug_eval_to_file} captures wrap the
     * evaluation outcome as
     * {@code {schema, expression, result: {resultType: "string", value: "<json-string>"}}}.
     * The vast majority of oracle invocations stringify their result
     * before returning so the live runtime can deliver it through CDP
     * without structured-clone hazards; this helper undoes that
     * stringification.
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
     * <p>Used for stanza-style captures of WAM upload IQs emitted by
     * {@code mcp__whatsapp__web_live_stanza_dump_to_file}; each
     * captured line is expected to carry an {@code event} sub-object
     * shaped like the rest of the MCP stanza logger output.
     *
     * @param topic the fixture topic (without the {@code .jsonl}
     *              extension)
     * @return the list of {@code event} sub-objects
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
        } catch (IOException error) {
            throw new UncheckedIOException("failed to read fixture: " + resource, error);
        }
    }

    /**
     * Returns whether the given JSON fixture exists on the classpath.
     *
     * <p>Allows tests to skip cleanly when a corpus has not yet been
     * captured.
     *
     * @param resource the resource path relative to the WAM fixture
     *                 root (e.g. {@code "wam-tags-roundtrip.json"})
     * @return {@code true} when the resource is present on the
     *         classpath
     */
    public static boolean isAvailable(String resource) {
        Objects.requireNonNull(resource, "resource");
        return WamFixtures.class.getResourceAsStream("/" + FIXTURE_ROOT + "/" + resource) != null;
    }

    /**
     * Asserts that the given fixture header pins the given snapshot
     * revision, failing the test loudly if the captured revision drifts
     * away from what the live WA Web bundle is now on.
     *
     * @param fixtureHeader  the JSON object carrying a
     *                       {@code snapshotRevision} numeric field
     * @param expected       the snapshot revision the test was written
     *                       against
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
     * Returns the {@code vectors} array of a vector-style fixture as a
     * raw {@link JSONArray}, for callers that prefer to iterate without
     * a per-row mapper.
     *
     * @param resource the resource path under {@link #FIXTURE_ROOT}
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
     * Opens the named classpath resource.
     *
     * @param resourcePath the resource path under
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
