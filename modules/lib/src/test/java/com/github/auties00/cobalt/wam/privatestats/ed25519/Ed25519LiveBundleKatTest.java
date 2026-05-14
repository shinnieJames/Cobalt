package com.github.auties00.cobalt.wam.privatestats.ed25519;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.wam.privatestats.WamPrivateStatsTokenBlinder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Byte-identical agreement tests against vectors captured from the live
 * WhatsApp Web JavaScript bundle.
 *
 * <p>Each vector pins the output of {@code WACryptoEd25519.hashToPoint},
 * {@code WAWamPrivateStatsToken.blindToken}, and
 * {@code WAWamPrivateStatsToken.unblindToken} on a deterministic input
 * tuple. Agreement on these vectors is the strongest validation possible:
 * if any constant or formula in the Java port diverges from the JavaScript
 * reference, at least one of the three outputs will mismatch.
 *
 * <p>Vectors live in {@code fixtures/wam/ed25519-live-bundle-vectors.json}
 * and were captured against snapshot revision {@code 1038176432}
 * (live-runtime revision {@code 1038189736}) on 2026-04-27 via the
 * {@code mcp__whatsapp__web_live_debug_eval} tool. Re-capture if the WA Web
 * implementation changes.
 */
class Ed25519LiveBundleKatTest {
    /**
     * Classpath path of the captured-vectors fixture.
     */
    private static final String FIXTURE = "fixtures/wam/ed25519-live-bundle-vectors.json";

    /**
     * Documented byte length of every vector field.
     */
    private static final int VECTOR_BYTE_LENGTH = 32;

    /**
     * The vectors loaded from the fixture file.
     */
    private static final List<Vector> VECTORS = loadVectors();

    /**
     * One captured vector. Inputs are seeded deterministically; outputs are
     * the bytes returned by the live JS bundle.
     */
    private record Vector(int index, byte[] msg, byte[] scalar, byte[] sk,
                          byte[] hashToPoint, byte[] blinded, byte[] pk,
                          byte[] signed, byte[] unblinded) {
    }

    /**
     * Asserts {@link Ed25519HashToPoint#compute} produces the byte-identical
     * output of the live JS {@code WACryptoEd25519.hashToPoint} for every
     * vector.
     */
    @Test
    void hashToPointMatchesLiveBundle() {
        for (var v : VECTORS) {
            var p = Ed25519HashToPoint.compute(v.msg());
            var actual = new byte[32];
            Ed25519Point.pack(actual, p);
            assertArrayEquals(v.hashToPoint(), actual,
                    "hashToPoint mismatch on vector " + v.index());
        }
    }

    /**
     * Asserts {@link WamPrivateStatsTokenBlinder#blind} produces the
     * byte-identical output of the live JS
     * {@code WAWamPrivateStatsToken.blindToken} for every vector.
     */
    @Test
    void blindMatchesLiveBundle() {
        for (var v : VECTORS) {
            var actual = WamPrivateStatsTokenBlinder.blind(v.msg(), v.scalar());
            assertArrayEquals(v.blinded(), actual,
                    "blind mismatch on vector " + v.index());
        }
    }

    /**
     * Asserts {@link WamPrivateStatsTokenBlinder#unblind} produces the
     * byte-identical output of the live JS
     * {@code WAWamPrivateStatsToken.unblindToken} for every vector.
     */
    @Test
    void unblindMatchesLiveBundle() {
        for (var v : VECTORS) {
            var actual = WamPrivateStatsTokenBlinder.unblind(v.signed(), v.scalar(), v.pk());
            assertArrayEquals(v.unblinded(), actual,
                    "unblind mismatch on vector " + v.index());
        }
    }

    /**
     * Sanity assertion that all vectors have the documented 32-byte length
     * (catches transcription typos in the fixture).
     */
    @Test
    void vectorByteLengthsAreCorrect() {
        for (var v : VECTORS) {
            assertEquals(VECTOR_BYTE_LENGTH, v.msg().length, "msg length on vector " + v.index());
            assertEquals(VECTOR_BYTE_LENGTH, v.scalar().length, "scalar length on vector " + v.index());
            assertEquals(VECTOR_BYTE_LENGTH, v.sk().length, "sk length on vector " + v.index());
            assertEquals(VECTOR_BYTE_LENGTH, v.hashToPoint().length, "hashToPoint length on vector " + v.index());
            assertEquals(VECTOR_BYTE_LENGTH, v.blinded().length, "blinded length on vector " + v.index());
            assertEquals(VECTOR_BYTE_LENGTH, v.pk().length, "pk length on vector " + v.index());
            assertEquals(VECTOR_BYTE_LENGTH, v.signed().length, "signed length on vector " + v.index());
            assertEquals(VECTOR_BYTE_LENGTH, v.unblinded().length, "unblinded length on vector " + v.index());
        }
    }

    /**
     * Loads and parses the fixture file from the test classpath.
     *
     * @return the vectors in the order they appear in the fixture
     * @throws UncheckedIOException if the fixture is missing or unreadable
     */
    private static List<Vector> loadVectors() {
        try (var in = Ed25519LiveBundleKatTest.class.getResourceAsStream("/" + FIXTURE)) {
            if (in == null) {
                throw new IOException("fixture not found on classpath: " + FIXTURE);
            }
            var json = JSON.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            var arr = json.getJSONArray("vectors");
            var out = new ArrayList<Vector>(arr.size());
            for (var i = 0; i < arr.size(); i++) {
                out.add(toVector(arr.getJSONObject(i)));
            }
            return List.copyOf(out);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read fixture: " + FIXTURE, e);
        }
    }

    /**
     * Converts one fixture entry into a {@link Vector}.
     *
     * @param o the JSON object holding the vector fields
     * @return the parsed vector
     */
    private static Vector toVector(JSONObject o) {
        return new Vector(
                o.getIntValue("index"),
                fromHex(o.getString("msg")),
                fromHex(o.getString("scalar")),
                fromHex(o.getString("sk")),
                fromHex(o.getString("hashToPoint")),
                fromHex(o.getString("blinded")),
                fromHex(o.getString("pk")),
                fromHex(o.getString("signed")),
                fromHex(o.getString("unblinded")));
    }

    /**
     * Parses a lowercase hexadecimal string into the byte array it
     * encodes.
     *
     * @param hex the hex string (length must be even)
     * @return the decoded bytes
     */
    private static byte[] fromHex(String hex) {
        var out = new byte[hex.length() / 2];
        for (var i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }
}
