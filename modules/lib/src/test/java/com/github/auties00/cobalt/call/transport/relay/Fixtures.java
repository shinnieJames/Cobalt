package com.github.auties00.cobalt.call.transport.relay;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Test helper that loads relay parity-test fixtures from the test classpath.
 *
 * <p>Fixtures live under {@code src/test/resources/fixtures/relay/}. Loading via
 * the classpath rather than a relative path avoids depending on the surefire
 * working directory, which for a multi-module reactor build can be either the
 * module directory or the reactor root depending on how tests are invoked.
 */
final class Fixtures {
    private Fixtures() {
        throw new AssertionError("Fixtures is not instantiable");
    }

    static JSONObject readJson(String resourcePath) {
        return JSON.parseObject(readString(resourcePath));
    }

    static String readString(String resourcePath) {
        try (var in = open(resourcePath)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read fixture: " + resourcePath, e);
        }
    }

    static InputStream open(String resourcePath) throws IOException {
        var stream = Fixtures.class.getResourceAsStream("/" + resourcePath);
        if (stream == null) {
            throw new IOException("fixture not found on classpath: " + resourcePath);
        }
        return stream;
    }
}
