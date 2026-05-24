package com.github.auties00.cobalt.util;

import net.dongliu.apk.parser.ByteArrayApkFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration smoke tests for {@link PlayStoreUtils} that download
 * the live WhatsApp and WhatsApp Business APKs.
 *
 * @apiNote
 * Exercises the full Aurora-dispenser plus FDFE plus CDN flow
 * against the production Google Play backend for the two
 * package identifiers the Cobalt mobile-companion tooling
 * cares about. Each test asserts the downloaded base APK and
 * splits parse as APKs whose package name matches the request.
 *
 * @implNote
 * This class is Cobalt-internal; there is no WA Web counterpart.
 * The synthetic device profile reports an x86_64 emulator, so
 * any ABI split returned by the server is expected to be either
 * {@code x86} or {@code x86_64}; an ARM split would indicate the
 * dispenser fed the wrong profile through.
 */
public class PlayStoreUtilsTest {
    /**
     * Regex that matches Android per-ABI configuration splits.
     *
     * @apiNote
     * Used by {@link #assertDownloadsValidApk(String)} to detect
     * ABI-keyed splits in the delivery response so the test can
     * pin the device-profile-implied ABI subset.
     */
    private static final Pattern ABI_SPLIT = Pattern.compile(
            "^config\\.(armeabi(?:_v7a)?|arm64_v8a|x86|x86_64|mips|mips64)$"
    );

    /**
     * Downloads the consumer WhatsApp APK and asserts every
     * stream parses as the {@code com.whatsapp} package.
     */
    @Test
    public void downloadsWhatsApp() throws IOException {
        assertDownloadsValidApk("com.whatsapp");
    }

    /**
     * Downloads the WhatsApp Business APK and asserts every
     * stream parses as the {@code com.whatsapp.w4b} package.
     */
    @Test
    public void downloadsWhatsAppBusiness() throws IOException {
        assertDownloadsValidApk("com.whatsapp.w4b");
    }

    /**
     * Downloads {@code packageName} via {@link PlayStoreUtils}
     * and verifies every returned stream parses as an APK
     * declaring that package.
     *
     * @apiNote
     * Shared by the per-package tests; closes the
     * {@link PlayStoreUtils.DownloadedApk} via
     * try-with-resources so the underlying HTTP streams are
     * always released even on assertion failure.
     *
     * @implNote
     * This implementation also asserts that any ABI-keyed split
     * resolves to an x86 ABI, since the dispenser device
     * profile reports an x86_64 emulator and any ARM split
     * would mean the profile leaked.
     *
     * @param packageName the Android package identifier to
     *     download
     * @throws IOException if the download or any APK-parse
     *     step fails
     */
    private static void assertDownloadsValidApk(String packageName) throws IOException {
        try (var downloaded = PlayStoreUtils.downloadApk(packageName)) {
            assertEquals(packageName, downloaded.packageName());
            assertApkPackage(packageName, "base", downloaded.baseApk());
            for (var entry : downloaded.splits().entrySet()) {
                var splitName = entry.getKey();
                assertFalse(splitName.isBlank(),
                        () -> packageName + " split has a blank name");
                var abiMatch = ABI_SPLIT.matcher(splitName);
                if (abiMatch.matches()) {
                    var abi = abiMatch.group(1);
                    assertTrue(abi.equals("x86") || abi.equals("x86_64"),
                            () -> packageName + " received non-x86 ABI split '" + splitName
                                    + "' despite x86_64 device profile");
                }
                assertApkPackage(packageName, splitName, entry.getValue());
            }
        }
    }

    /**
     * Drains {@code stream}, parses the result as an APK, and
     * asserts the parsed package name equals
     * {@code expectedPackage}.
     *
     * @apiNote
     * Used by {@link #assertDownloadsValidApk(String)} to
     * validate both the base APK and every split.
     *
     * @implNote
     * This implementation buffers the full APK in memory; the
     * test APKs are small enough that this is cheaper than
     * spooling to a temp file.
     *
     * @param expectedPackage the Android package identifier
     *     the stream is expected to carry
     * @param label           a short label used in assertion
     *     messages (the split identifier, or {@code "base"} for
     *     the base APK)
     * @param stream          the APK stream to consume
     * @throws IOException if reading or parsing the APK fails
     */
    private static void assertApkPackage(String expectedPackage, String label, InputStream stream) throws IOException {
        try (var parsed = new ByteArrayApkFile(stream.readAllBytes())) {
            var meta = parsed.getApkMeta();
            assertNotNull(meta, () -> expectedPackage + "/" + label + " has no parseable ApkMeta");
            assertEquals(expectedPackage, meta.getPackageName(),
                    () -> expectedPackage + "/" + label + " has unexpected package name");
        }
    }
}
