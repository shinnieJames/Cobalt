package com.github.auties00.cobalt.client.info;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.device.pairing.ClientAppVersion;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Resolves the current WhatsApp Web build version by scraping the
 * {@code web.whatsapp.com} landing page.
 *
 * <p>WhatsApp Web ships the running build version as a JSON property named
 * {@code client_revision} inlined into the HTML delivered on the first page
 * load. This class fetches that page with a browser-like User-Agent, scans
 * the response for the {@code "client_revision":} marker and reads the
 * following integer, which it then folds into a fixed {@code 2.3000.X}
 * {@link ClientAppVersion}. The result is cached in a lazily initialised
 * volatile field protected by a double-checked lock so that a Cobalt process
 * performs at most one HTTP request for the web version regardless of how
 * many web/desktop clients it creates.
 *
 * <p>Desktop Windows and macOS clients reuse this class because they ship
 * the same JavaScript bundle as web.whatsapp.com and therefore have the same
 * {@code client_revision}.
 *
 * @implNote WA Web's own source exposes the equivalent value through
 * {@code WAWebBuildConstants.VERSION_STR}, which is built at runtime from
 * {@code SiteData.client_revision}. Cobalt cannot read {@code SiteData}
 * directly because it is not running inside the web bundle, so it scrapes
 * the same underlying value from the HTML body.
 * @see WhatsAppClientInfo
 */
@WhatsAppWebModule(moduleName = "WAWebBuildConstants")
final class WhatsAppWebClientInfo implements WhatsAppClientInfo {
    /**
     * Cached singleton instance of the resolved web client info.
     *
     * <p>Populated lazily on the first call to {@link #of()} and protected by
     * {@link #webInfoLock} using the double-checked locking idiom.
     */
    private static volatile WhatsAppWebClientInfo webInfo;

    /**
     * Monitor used to serialise initialisation of {@link #webInfo}.
     */
    private static final Object webInfoLock = new Object();

    /**
     * User-Agent string sent when fetching the web landing page.
     *
     * <p>Using a realistic desktop Chrome User-Agent avoids serving a mobile
     * redirect page that would not contain the {@code client_revision}
     * marker.
     */
    private static final String WEB_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36";

    /**
     * Target URL whose HTML response embeds the {@code client_revision}
     * field.
     */
    private static final URI WEB_UPDATE_URL = URI.create("https://web.whatsapp.com");

    /**
     * Character pattern used by {@link #queryWebInfo()} to locate the
     * {@code "client_revision":} JSON property inside the streamed HTML
     * response without requiring full DOM parsing.
     */
    private static final char[] WEB_UPDATE_PATTERN = "\"client_revision\":".toCharArray();

    /**
     * Resolved application version advertised by this info instance.
     */
    private final ClientAppVersion version;

    /**
     * Constructs a new immutable instance with the given resolved version.
     *
     * @param version the version parsed from {@code web.whatsapp.com}
     */
    private WhatsAppWebClientInfo(ClientAppVersion version) {
        this.version = version;
    }

    /**
     * Returns the cached web client info, performing the scrape on the first
     * call.
     *
     * <p>Subsequent invocations within the same JVM return the same instance.
     * If the initial scrape failed with a runtime exception the failure is
     * not cached: the next call will retry.
     *
     * @return the resolved web client info
     * @throws IllegalStateException if the scrape returns a non-200 response
     *                               or if the {@code client_revision} marker
     *                               cannot be located in the response
     * @throws RuntimeException if the HTTP request fails with an I/O or
     *                          interruption error
     */
    public static WhatsAppWebClientInfo of() {
        if (webInfo == null) {
            synchronized (webInfoLock) {
                if(webInfo == null) {
                    webInfo = queryWebInfo();
                }
            }
        }
        return webInfo;
    }

    /**
     * Fetches {@code web.whatsapp.com} and extracts the current
     * {@code client_revision} integer from the returned HTML.
     *
     * <p>The response body is streamed byte by byte while a rolling pattern
     * match tracks progress through {@link #WEB_UPDATE_PATTERN}. When the
     * pattern matches completely, the following ASCII digits are consumed
     * and assembled into the tertiary version component; the primary and
     * secondary components are fixed at {@code 2} and {@code 3000}
     * respectively to match WhatsApp Web's versioning scheme.
     *
     * @return a new {@code WhatsAppWebClientInfo} with the discovered version
     * @throws IllegalStateException if the server returns a non-200 status or
     *                               the marker is not found
     * @throws RuntimeException if the HTTP exchange fails
     */
    private static WhatsAppWebClientInfo queryWebInfo() {
        try(var httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build()) {
            var request = HttpRequest.newBuilder()
                    .uri(WEB_UPDATE_URL)
                    .GET()
                    .header("User-Agent", WEB_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if(response.statusCode() != 200) {
                throw new IllegalStateException("Cannot query web version: status code " + response.statusCode());
            }
            try (var inputStream = response.body()) {
                // Scans the streamed HTML byte by byte looking for the
                // "client_revision": marker using a rolling pattern match
                var patternIndex = 0;
                int value;
                while ((value = inputStream.read()) != -1) {
                    if (value == WEB_UPDATE_PATTERN[patternIndex]) {
                        if (++patternIndex == WEB_UPDATE_PATTERN.length) {
                            // Consumes the trailing digits into an integer
                            // that becomes the tertiary version component
                            var clientVersion = 0;
                            while ((value = inputStream.read()) != -1 && Character.isDigit(value)) {
                                clientVersion *= 10;
                                clientVersion += value - '0';
                            }
                            var version = new ClientAppVersion(2, 3000, clientVersion);
                            return new WhatsAppWebClientInfo(version);
                        }
                    } else {
                        patternIndex = 0;
                        if (value == WEB_UPDATE_PATTERN[0]) {
                            patternIndex = 1;
                        }
                    }
                }
                throw new IllegalStateException("Cannot find client_revision in web update response");
            }
        } catch (IOException | InterruptedException exception) {
            throw new RuntimeException("Cannot query web version", exception);
        }
    }

    /**
     * Returns the WhatsApp Web client application version scraped from
     * {@code web.whatsapp.com}.
     *
     * @return the resolved version
     */
    @Override
    public ClientAppVersion version() {
        return version;
    }
}
