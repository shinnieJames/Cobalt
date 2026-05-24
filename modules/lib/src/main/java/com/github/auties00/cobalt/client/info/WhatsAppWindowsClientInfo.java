package com.github.auties00.cobalt.client.info;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.meta.model.WhatsAppWebPlatform;
import com.github.auties00.cobalt.model.device.pairing.ClientAppVersion;
import com.github.auties00.cobalt.model.device.pairing.ClientAppVersionBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * {@link WhatsAppClientInfo} flavour for the Microsoft Store WhatsApp Desktop hybrid shell on Windows.
 *
 * @apiNote Selected automatically by {@link WhatsAppClientInfo#of(com.github.auties00.cobalt.model.device.pairing.ClientPlatformType)}
 *          for {@code WINDOWS}. Reuses the {@link WhatsAppWebClientInfo} JS bundle version and folds the Microsoft Store
 *          package build into the {@code quaternary} slot of the resulting {@link ClientAppVersion}; that quaternary slot is
 *          what the server uses to identify a connection as coming from the hybrid shell rather than a plain browser tab. On
 *          the wire the resulting payload also overrides {@code mcc} and {@code mnc} from halves of the six digit build, so
 *          providing a plausible value here matters for handshake acceptance.
 * @implNote This implementation approximates from outside the path the real shell follows, where the native binary appends
 *           {@code ?windowsBuild=XXXXXX} to the bundle URL and {@code WAWebBuildConstants} reads it back via
 *           {@code URLSearchParams}; Cobalt instead queries the public Microsoft Store display catalog for the
 *           {@code 5319275A.WhatsAppDesktop} package and folds its version into the same six digit form. When the catalog
 *           query fails the {@link #DEFAULT_WINDOWS_BUILD} fallback is used so that pairing still succeeds offline.
 * @see WhatsAppClientInfo
 * @see WhatsAppWebClientInfo
 */
@WhatsAppWebModule(moduleName = "WAWebBuildConstants", platform = WhatsAppWebPlatform.WINDOWS)
final class WhatsAppWindowsClientInfo implements WhatsAppClientInfo {
    /**
     * Cached singleton for the resolved Windows client identity.
     *
     * @apiNote Populated lazily by the first call to {@link #of()}.
     * @implNote This implementation pairs the field with {@link #windowsInfoLock} for the double checked locking idiom; the
     *           {@code volatile} keyword is what makes the unsynchronised fast path observe a fully constructed instance.
     */
    private static volatile WhatsAppWindowsClientInfo windowsInfo;

    /**
     * Monitor that serialises initialisation of {@link #windowsInfo}.
     *
     * @apiNote Not exposed; callers go through {@link #of()}.
     */
    private static final Object windowsInfoLock = new Object();

    /**
     * Microsoft Store display catalog endpoint that returns metadata for the {@code 5319275A.WhatsAppDesktop} UWP package.
     *
     * @apiNote The product id {@code 9NKSQGP7F2NH} is the public Microsoft Store identifier for WhatsApp Desktop. The
     *          response is a JSON document whose {@code Packages[].Version} fields carry the shipping build string used by
     *          {@link #queryWindowsBuild()}.
     */
    private static final URI WINDOWS_STORE_URL = URI.create(
            "https://displaycatalog.mp.microsoft.com/v7.0/products/9NKSQGP7F2NH?languages=en-us&market=US&fieldsTemplate=Details"
    );

    /**
     * JSON marker that precedes the dotted version inside the Microsoft Store {@code PackageFullName} field.
     *
     * @apiNote The full name has the form {@code "5319275A.WhatsAppDesktop_2.2613.101.0_neutral_~_cv1g1gvanyjgm"}. Anchoring
     *          on {@code "PackageFullName":"5319275A.WhatsAppDesktop_"} skips the preceding {@code PackageFamilyName} entry
     *          ({@code "5319275A.WhatsAppDesktop_cv1g1gvanyjgm"}, the family hash with no version) so that the next
     *          underscore reliably terminates the dotted version substring.
     */
    private static final String PACKAGE_MARKER = "\"PackageFullName\":\"5319275A.WhatsAppDesktop_";

    /**
     * Fallback build number used when the Microsoft Store catalog query fails for any reason.
     *
     * @apiNote {@code 261301} corresponds to {@code 2.2613.1.0}, a plausible recent shell build that the server still
     *          accepts; pick a fresh value if WhatsApp deprecates it.
     */
    private static final int DEFAULT_WINDOWS_BUILD = 261301;

    /**
     * Resolved {@link ClientAppVersion} this instance advertises.
     *
     * @apiNote Returned verbatim from {@link #version()}.
     */
    private final ClientAppVersion version;

    /**
     * Constructs an immutable instance carrying the given resolved version.
     *
     * @apiNote Package private; callers always go through {@link #of()}.
     * @param version the version combining the web base with the Windows store build number in the {@code quaternary} slot
     */
    private WhatsAppWindowsClientInfo(ClientAppVersion version) {
        this.version = version;
    }

    /**
     * Returns the cached Windows client identity, performing the catalog query on the first call.
     *
     * @apiNote Subsequent calls in the same JVM return the same instance. A failed query is not cached, so callers may
     *          retry by simply calling this method again.
     * @implNote This implementation uses double checked locking; the {@code volatile} {@link #windowsInfo} field publishes
     *           the fully constructed instance to readers on the unsynchronised fast path.
     * @return the resolved Windows client identity
     */
    public static WhatsAppWindowsClientInfo of() {
        if (windowsInfo == null) {
            synchronized (windowsInfoLock) {
                if (windowsInfo == null) {
                    windowsInfo = queryWindowsInfo();
                }
            }
        }
        return windowsInfo;
    }

    /**
     * Builds the resolved Windows client identity from the cached web version and the current Microsoft Store build number.
     *
     * @apiNote Called at most once per JVM by {@link #of()}.
     * @implNote This implementation pulls the {@code primary}, {@code secondary} and {@code tertiary} components from
     *           {@link WhatsAppWebClientInfo#of()} (the bundle the shell hosts) and assigns the catalog derived
     *           {@link #queryWindowsBuild()} value to {@code quaternary}, mirroring how WA Web's
     *           {@code VERSION_BASE_WITH_WINDOWS_BUILD} concatenates {@code VERSION_BASE} with {@code WINDOWS_BUILD}.
     * @return a fresh info instance with a version of the form {@code 2.3000.X.WINDOWS_BUILD}
     */
    private static WhatsAppWindowsClientInfo queryWindowsInfo() {
        var webVersion = WhatsAppWebClientInfo.of().version();
        var primary = webVersion.primary();
        var secondary = webVersion.secondary();
        var tertiary = webVersion.tertiary();
        var version = new ClientAppVersionBuilder()
                .primary(primary.isPresent() ? primary.getAsInt() : null)
                .secondary(secondary.isPresent() ? secondary.getAsInt() : null)
                .tertiary(tertiary.isPresent() ? tertiary.getAsInt() : null)
                .quaternary(queryWindowsBuild())
                .build();
        return new WhatsAppWindowsClientInfo(version);
    }

    /**
     * Queries the Microsoft Store display catalog for the {@code 5319275A.WhatsAppDesktop} package version and converts it
     * into the six digit build integer the hybrid shell injects as {@code windowsBuild}.
     *
     * @apiNote The shell exposes this number through {@code WAWebBuildConstants.WINDOWS_BUILD}, where
     *          {@link WhatsAppWindowsClientInfo} ({@code WAWebClientPayload})
     *          additionally reads it as the {@code quaternary} slot of {@code userAgent.appVersion} and (when six digits
     *          long) splits it into {@code mcc} and {@code mnc}; staying inside that range therefore matters even though
     *          this method is not directly exposed.
     * @implNote This implementation folds the catalog version {@code 2.SECONDARY.TERTIARY.0} into
     *           {@code SECONDARY * 100 + (TERTIARY % 100)} because the shell's tertiary component encodes a
     *           {@code channel}{@code build} pair (leading digit is the release channel, {@code 1} for {@code RELEASE};
     *           trailing two digits are the build counter). For {@code 2.2613.101.0} this yields
     *           {@code 2613 * 100 + 1 = 261301}, matching the value observed on the wire from {@code WhatsApp.Root.dll}.
     *           Any failure (network error, missing marker, malformed body, out of range result) returns
     *           {@link #DEFAULT_WINDOWS_BUILD}.
     * @return the resolved six digit build number, or {@link #DEFAULT_WINDOWS_BUILD} when discovery fails
     */
    @WhatsAppWebExport(moduleName = "WAWebBuildConstants", exports = "WINDOWS_BUILD", platform = WhatsAppWebPlatform.WINDOWS, adaptation = WhatsAppAdaptation.ADAPTED)
    private static int queryWindowsBuild() {
        try (var httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build()) {
            var request = HttpRequest.newBuilder()
                    .uri(WINDOWS_STORE_URL)
                    .GET()
                    .header("Accept", "application/json")
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return DEFAULT_WINDOWS_BUILD;
            }
            var body = response.body();
            var markerStart = body.indexOf(PACKAGE_MARKER);
            if (markerStart < 0) {
                return DEFAULT_WINDOWS_BUILD;
            }
            var versionStart = markerStart + PACKAGE_MARKER.length();
            var versionEnd = body.indexOf('_', versionStart);
            if (versionEnd < 0) {
                return DEFAULT_WINDOWS_BUILD;
            }
            var versionStr = body.substring(versionStart, versionEnd);
            var parts = versionStr.split("\\.");
            if (parts.length < 3) {
                return DEFAULT_WINDOWS_BUILD;
            }
            var secondary = Integer.parseInt(parts[1]);
            var tertiary = Integer.parseInt(parts[2]);
            var build = secondary * 100 + (tertiary % 100);
            if (build < 100000 || build > 999999) {
                return DEFAULT_WINDOWS_BUILD;
            }
            return build;
        } catch (IOException | InterruptedException | NumberFormatException exception) {
            return DEFAULT_WINDOWS_BUILD;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote The returned {@link ClientAppVersion} matches WA Web's {@code VERSION_BASE_WITH_WINDOWS_BUILD}, the
     *          {@code VERSION_BASE + "." + WINDOWS_BUILD} string read by the WAM event uploader
     *          ({@code WAWebWam.appVersion}) and {@code WAWebFalcoCanonicalAppVersion} when running inside the hybrid shell.
     * @implNote This implementation always populates the {@code quaternary} slot, even on a fallback path where the catalog
     *           query failed; servers reject Windows handshakes whose advertised build is {@code null}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBuildConstants", exports = "VERSION_BASE_WITH_WINDOWS_BUILD", platform = WhatsAppWebPlatform.WINDOWS, adaptation = WhatsAppAdaptation.ADAPTED)
    public ClientAppVersion version() {
        return version;
    }
}
