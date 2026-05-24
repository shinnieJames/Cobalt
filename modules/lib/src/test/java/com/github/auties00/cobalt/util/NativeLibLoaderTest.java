package com.github.auties00.cobalt.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke tests for {@link NativeLibLoader} and the FFM toolchain.
 *
 * @apiNote
 * Exercises {@link NativeLibLoader} via three Cobalt-internal
 * smoke paths: classifier resolution from {@code os.name} /
 * {@code os.arch}; an FFM linker round-trip via the platform's
 * standard C runtime ({@code abs(int)}) so the toolchain is
 * verified on the running platform; an offline-mode hard-failure
 * when the requested binary is neither on the classpath nor in
 * the cache. The success path (download then SHA-256 verify then
 * cache then load) is exercised end-to-end by the per-binding
 * smoke tests (for example {@code OpusCodecTest},
 * {@code SpeexDspTest}, {@code VP8Test}, {@code H264Test},
 * {@code SctpAssociationTest}).
 *
 * @implNote
 * This class is Cobalt-internal; there is no WA Web counterpart.
 */
public class NativeLibLoaderTest {

    /**
     * The set of classifiers Cobalt publishes natives for.
     *
     * @apiNote
     * Mirrors {@code NativeLibLoader.KNOWN_CLASSIFIERS} sans the
     * Windows-on-ARM64 entry, which the test asserts is not
     * currently a published target for running tests.
     */
    private static final Set<String> SUPPORTED_CLASSIFIERS = Set.of(
            "linux-x86_64", "linux-aarch64",
            "darwin-x86_64", "darwin-aarch64",
            "windows-x86_64");

    /**
     * Resolves to one of the published classifiers so the
     * natives bundle can be shipped.
     */
    @Test
    public void classifierMatchesSupportedSet() {
        var classifier = NativeLibLoader.classifier();
        assertNotNull(classifier);
        assertTrue(SUPPORTED_CLASSIFIERS.contains(classifier),
                "classifier '" + classifier + "' is not in the supported set " + SUPPORTED_CLASSIFIERS);
    }

    /**
     * Binds {@code int abs(int)} from the C runtime via the
     * linker's default lookup and validates the FFM toolchain.
     *
     * @apiNote
     * Pins the toolchain end-to-end: linker availability,
     * default lookup population, and downcall handle invocation.
     *
     * @throws Throwable if the bound method handle's invocation
     *     fails
     */
    @Test
    public void canBindAbsFromDefaultLookup() throws Throwable {
        var linker = Linker.nativeLinker();
        var defaultLookup = linker.defaultLookup();
        var absSymbol = defaultLookup.find("abs")
                .orElseThrow(() -> new AssertionError("abs not in default lookup"));
        var abs = linker.downcallHandle(
                absSymbol,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        assertEquals(42, (int) abs.invokeExact(-42));
        assertEquals(0, (int) abs.invokeExact(0));
        assertEquals(123, (int) abs.invokeExact(123));
    }

    /**
     * Asking for a library with no manifest entry fails fast
     * without an HTTP attempt.
     *
     * @apiNote
     * Pins the fail-fast guarantee for download-disabled
     * deployments: the loader skips the download leg entirely
     * (no network attempt, no timeout wait) since there is
     * nothing to verify the download against, and falls through
     * to {@link System#loadLibrary(String)} which then fails for
     * an unknown name.
     */
    @Test
    public void unmanifestedLibraryFailsFastWithoutDownload() {
        try {
            NativeLibLoader.clearCache();
            var error = assertThrows(UnsatisfiedLinkError.class,
                    () -> NativeLibLoader.load("nonexistent-test-lib", Arena.global()));
            assertTrue(error.getMessage().contains("nonexistent-test-lib"),
                    "error must mention the requested library: " + error.getMessage());
        } finally {
            NativeLibLoader.clearCache();
        }
    }

    /**
     * Reproduces the parallel-JVM extraction failure on a
     * Windows-locked target and pins the loader's fast-path
     * recovery.
     *
     * @apiNote
     * When two Cobalt processes start at once, the first one's
     * {@link System#load(String)} call hands {@code opus.dll}
     * to Windows {@code LoadLibrary}, which opens it without
     * {@code FILE_SHARE_DELETE}. The second process's
     * {@code extractFromClasspath} previously tried
     * {@code Files.copy(REPLACE_EXISTING)} into the same cache
     * slot and crashed with
     * {@link java.nio.file.AccessDeniedException} because the
     * delete-then-rewrite step could not unlink a loaded DLL.
     * The fixed loader fast-paths on a size-matching cached
     * file and never attempts to delete the locked target. On
     * Linux and macOS the LoadLibrary-style file lock does not
     * exist, but this test still exercises the fast path
     * regression-wise.
     *
     * @implNote
     * This implementation reproduces the bug inside one JVM by
     * loading {@code opus} once (extracts the binary and calls
     * {@link System#load(String)}, which locks the file on
     * Windows), calling {@link NativeLibLoader#clearCache()}
     * (drops the in-process {@code SymbolLookup} cache without
     * unloading the native library so the OS lock persists),
     * then calling {@link NativeLibLoader#load(String, Arena)}
     * again, re-entering {@code extractFromClasspath} against
     * the locked file just like a parallel JVM would do.
     */
    @Test
    public void parallelExtractionDoesNotFailWhenTargetIsLocked() {
        try {
            NativeLibLoader.load("opus", Arena.global());
            NativeLibLoader.clearCache();
            assertDoesNotThrow(() -> NativeLibLoader.load("opus", Arena.global()),
                    "second load on a target locked by an earlier System.load "
                            + "must reuse the cached binary, not re-extract over it");
        } finally {
            NativeLibLoader.clearCache();
        }
    }

    /**
     * Two synthetic manifests with disjoint keys merge into one
     * lookup map without a conflict.
     *
     * @apiNote
     * Pins the dual-manifest coexistence guarantee: the toolkit
     * manifest declares {@code ffmpeg-*} entries, the lib
     * manifest declares {@code opus} / {@code vpx} / etc., and
     * both must coexist on the classpath at runtime.
     */
    @Test
    public void disjointManifestsMergeWithoutConflict() {
        var libManifest = parseManifest(
                "lib.jar!/META-INF/native-checksums.json",
                readFixture("fixtures/native-lib/manifest-lib-opus.json"));
        var toolkitManifest = parseManifest(
                "toolkit.jar!/META-INF/native-checksums.json",
                readFixture("fixtures/native-lib/manifest-toolkit-ffmpeg-avformat.json"));
        NativeLibLoader.verifyNoConflicts(List.of(libManifest, toolkitManifest));
    }

    /**
     * Two manifests declaring the same {@code <lib>/<classifier>}
     * key with different SHA-256 values throw and name both
     * manifests.
     *
     * @apiNote
     * Pins the tamper-detection guarantee: a conflicting SHA
     * across modules indicates a tampered binary or a name
     * collision, and the loader rejects rather than picking one
     * arbitrarily.
     */
    @Test
    public void conflictingManifestsThrow() {
        var first = parseManifest(
                "first.jar!/META-INF/native-checksums.json",
                readFixture("fixtures/native-lib/manifest-lib-opus.json"));
        var second = parseManifest(
                "second.jar!/META-INF/native-checksums.json",
                readFixture("fixtures/native-lib/manifest-lib-opus-conflicting-sha.json"));
        var thrown = assertThrows(IllegalStateException.class,
                () -> NativeLibLoader.verifyNoConflicts(List.of(first, second)));
        assertTrue(thrown.getMessage().contains("first.jar")
                        && thrown.getMessage().contains("second.jar"),
                "conflict message must name both manifests: " + thrown.getMessage());
    }

    /**
     * Two manifests declaring identical entries (same key, same
     * SHA-256) merge silently.
     *
     * @apiNote
     * Pins the duplicate-entry tolerance: a transitive dep + a
     * direct dep could each pull the same module's natives, and
     * the loader treats that as a no-op rather than an error.
     */
    @Test
    public void identicalEntriesAcrossManifestsAreAllowed() {
        var manifestJson = readFixture("fixtures/native-lib/manifest-lib-opus-identical.json");
        var first = parseManifest(
                "first.jar!/META-INF/native-checksums.json", manifestJson);
        var second = parseManifest(
                "second.jar!/META-INF/native-checksums.json", manifestJson);
        NativeLibLoader.verifyNoConflicts(List.of(first, second));
    }

    /**
     * Parses a manifest body straight from a JSON string by
     * wrapping it in a {@link ByteArrayInputStream} and calling
     * {@link NativeLibLoader#parseManifest(String, java.io.InputStream)}.
     *
     * @apiNote
     * Used by the conflict-detection tests as a thin wrapper to
     * avoid having to plumb every fixture through a file URL.
     *
     * @param source the manifest source URL string
     * @param json   the manifest body
     * @return the parsed {@link NativeLibLoader.ModuleManifest}
     */
    private static NativeLibLoader.ModuleManifest parseManifest(String source, String json) {
        return NativeLibLoader.parseManifest(source,
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Loads a manifest fixture from the test classpath as a
     * UTF-8 string.
     *
     * @apiNote
     * Used by the conflict-detection tests to feed the bundled
     * synthetic-manifest JSON fixtures into
     * {@link #parseManifest(String, String)}.
     *
     * @param resourcePath the path under
     *                     {@code src/test/resources/} (for
     *                     example
     *                     {@code "fixtures/native-lib/manifest-lib-opus.json"})
     * @return the file contents
     * @throws UncheckedIOException if the resource is missing
     *                              or cannot be read
     */
    private static String readFixture(String resourcePath) {
        try (var in = NativeLibLoaderTest.class.getResourceAsStream("/" + resourcePath)) {
            if (in == null) {
                throw new IOException("fixture not found on classpath: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read fixture: " + resourcePath, e);
        }
    }
}
