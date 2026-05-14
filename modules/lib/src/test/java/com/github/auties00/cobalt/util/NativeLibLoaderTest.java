package com.github.auties00.cobalt.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke tests for {@link NativeLibLoader} and the FFM toolchain.
 *
 * <p>Covers:
 *
 * <ol>
 *   <li>Classifier resolution from {@code os.name}/{@code os.arch}.</li>
 *   <li>FFM linker round-trip via the platform's standard C runtime
 *       ({@code abs(int)}) — verifies the toolchain works on the
 *       running platform.</li>
 *   <li>Offline-mode hard-failure when the requested binary is
 *       neither on the classpath nor in the cache — pins the
 *       fail-fast guarantee for download-disabled deployments.</li>
 * </ol>
 *
 * <p>The success path (download → SHA-256 verify → cache → load)
 * is exercised end-to-end by the per-binding smoke tests
 * (e.g. {@code OpusCodecTest}, {@code SpeexDspTest}, {@code VP8Test},
 * {@code H264Test}, {@code SctpAssociationTest}).
 */
public class NativeLibLoaderTest {

    /**
     * The set of classifiers Cobalt publishes natives for.
     */
    private static final Set<String> SUPPORTED_CLASSIFIERS = Set.of(
            "linux-x86_64", "linux-aarch64",
            "darwin-x86_64", "darwin-aarch64",
            "windows-x86_64");

    /**
     * Asserts that the running JVM's classifier resolves to one of
     * the supported os-arch tokens, so that natives bundles can be
     * shipped for it.
     */
    @Test
    public void classifierMatchesSupportedSet() {
        var classifier = NativeLibLoader.classifier();
        assertNotNull(classifier);
        assertTrue(SUPPORTED_CLASSIFIERS.contains(classifier),
                "classifier '" + classifier + "' is not in the supported set " + SUPPORTED_CLASSIFIERS);
    }

    /**
     * Validates the FFM toolchain end-to-end by binding
     * {@code int abs(int)} from the C runtime via the linker's
     * default lookup, invoking it, and asserting the result.
     *
     * @throws Throwable if the bound method handle's invocation fails
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
     * Asking for a library with no manifest entry fails fast — the
     * loader skips the download leg entirely (no network attempt,
     * no timeout wait) since there's nothing to verify the
     * download against, and falls through to
     * {@link System#loadLibrary} which then fails for an
     * unknown name.
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
     * Reproduces the parallel-JVM extraction failure: when two Cobalt
     * processes start at once, the first one's {@link System#load}
     * call hands {@code opus.dll} to Windows {@code LoadLibrary},
     * which opens it without {@code FILE_SHARE_DELETE}. The second
     * process's {@code extractFromClasspath} then tried
     * {@code Files.copy(REPLACE_EXISTING)} into the same cache slot
     * and crashed with {@link java.nio.file.AccessDeniedException}
     * because the delete-then-rewrite step couldn't unlink a loaded
     * DLL.
     *
     * <p>The bug is reproduced inside one JVM by:
     *
     * <ol>
     *   <li>loading {@code opus} once — extracts the binary and
     *       calls {@link System#load}, which locks the file on
     *       Windows;</li>
     *   <li>calling {@link NativeLibLoader#clearCache()} — drops the
     *       in-process {@code SymbolLookup} cache without unloading
     *       the native library (the OS lock persists);</li>
     *   <li>calling {@link NativeLibLoader#load} again — re-enters
     *       {@code extractFromClasspath} against the locked file,
     *       which is exactly what a parallel JVM would do.</li>
     * </ol>
     *
     * <p>Before the fix this threw
     * {@code UncheckedIOException(AccessDeniedException)} on Windows.
     * After the fix the loader fast-paths on a size-matching cached
     * file and never attempts to delete the locked target. On Linux
     * and macOS the {@code LoadLibrary}-style file lock doesn't
     * exist, but the same test still exercises the fast path
     * regression-wise.
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
     * Two synthetic manifests with disjoint keys are merged into one
     * lookup map without a conflict — the toolkit's manifest will
     * declare {@code ffmpeg-*} entries, the lib's will declare
     * {@code opus}/{@code vpx}/etc., and both must coexist on the
     * classpath at runtime.
     *
     * @throws Exception if reflection fails
     */
    @Test
    public void disjointManifestsMergeWithoutConflict() throws Exception {
        var libManifest = parseManifestReflectively(
                "lib.jar!/META-INF/native-checksums.json",
                readFixture("fixtures/native-lib/manifest-lib-opus.json"));
        var toolkitManifest = parseManifestReflectively(
                "toolkit.jar!/META-INF/native-checksums.json",
                readFixture("fixtures/native-lib/manifest-toolkit-ffmpeg-avformat.json"));
        verifyNoConflictsReflectively(List.of(libManifest, toolkitManifest));
    }

    /**
     * Two manifests declaring the same {@code <lib>/<classifier>}
     * key with different SHA-256 values throw an
     * {@link IllegalStateException} naming both manifests — pins the
     * tamper-detection guarantee.
     *
     * @throws Exception if reflection fails
     */
    @Test
    public void conflictingManifestsThrow() throws Exception {
        var first = parseManifestReflectively(
                "first.jar!/META-INF/native-checksums.json",
                readFixture("fixtures/native-lib/manifest-lib-opus.json"));
        var second = parseManifestReflectively(
                "second.jar!/META-INF/native-checksums.json",
                readFixture("fixtures/native-lib/manifest-lib-opus-conflicting-sha.json"));
        var thrown = assertThrows(InvocationTargetException.class,
                () -> verifyNoConflictsReflectively(List.of(first, second)));
        var cause = thrown.getCause();
        assertInstanceOf(IllegalStateException.class, cause);
        assertTrue(cause.getMessage().contains("first.jar")
                        && cause.getMessage().contains("second.jar"),
                "conflict message must name both manifests: " + cause.getMessage());
    }

    /**
     * Two manifests declaring identical entries (same key, same
     * SHA-256) merge silently — a transitive dep + a direct dep
     * could each pull the same module's natives without a problem.
     *
     * @throws Exception if reflection fails
     */
    @Test
    public void identicalEntriesAcrossManifestsAreAllowed() throws Exception {
        var manifestJson = readFixture("fixtures/native-lib/manifest-lib-opus-identical.json");
        var first = parseManifestReflectively(
                "first.jar!/META-INF/native-checksums.json", manifestJson);
        var second = parseManifestReflectively(
                "second.jar!/META-INF/native-checksums.json", manifestJson);
        verifyNoConflictsReflectively(List.of(first, second));
    }

    /**
     * Loads a manifest fixture from the test classpath as a UTF-8
     * string.
     *
     * @param resourcePath the path under {@code src/test/resources/}
     *                     (e.g.
     *                     {@code "fixtures/native-lib/manifest-lib-opus.json"})
     * @return the file contents
     * @throws UncheckedIOException if the resource is missing or
     *                              cannot be read
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

    /**
     * Reflectively invokes the private {@code parseManifest(String,
     * String)} on {@link NativeLibLoader} to produce a {@code
     * ModuleManifest} instance for testing.
     *
     * @param source the manifest source URL string
     * @param json   the manifest body
     * @return the parsed {@code ModuleManifest} (an
     *         {@link Object} since the type is private)
     * @throws Exception if reflection fails
     */
    private static Object parseManifestReflectively(String source, String json) throws Exception {
        var method = lookupPrivateMethod("parseManifest", String.class, String.class);
        return method.invoke(null, source, json);
    }

    /**
     * Reflectively invokes the private {@code verifyNoConflicts(List)}
     * on {@link NativeLibLoader} so the test can drive its conflict
     * logic with synthetic manifests.
     *
     * @param manifests the manifests to check
     * @throws Exception if reflection fails (the underlying method
     *                   may also throw — wrapped in
     *                   {@link InvocationTargetException})
     */
    private static void verifyNoConflictsReflectively(List<?> manifests) throws Exception {
        var method = lookupPrivateMethod("verifyNoConflicts", List.class);
        method.invoke(null, manifests);
    }

    /**
     * Looks up a private static method on {@link NativeLibLoader} by
     * name + parameter types and unlocks it for invocation.
     *
     * @param name           the method name
     * @param parameterTypes the parameter types
     * @return the unlocked method
     * @throws Exception if the method cannot be found
     */
    private static Method lookupPrivateMethod(String name, Class<?>... parameterTypes) throws Exception {
        var method = NativeLibLoader.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }
}
