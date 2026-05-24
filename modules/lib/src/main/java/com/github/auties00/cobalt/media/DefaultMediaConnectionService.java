package com.github.auties00.cobalt.media;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.exception.WhatsAppMediaException;
import com.github.auties00.cobalt.media.MediaHost.Operation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.media.ExternalBlobReference;
import com.github.auties00.cobalt.model.media.MediaPath;
import com.github.auties00.cobalt.model.media.MediaProvider;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.util.DataUtils;
import com.github.auties00.cobalt.util.MediaMetricUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

/**
 * Default implementation of {@link MediaConnectionService} backed by the
 * live WhatsApp CDN.
 *
 * <p>Holds the parsed {@code media_conn} credentials (authentication
 * token, the list of candidate CDN hosts with their accepted media types
 * and download buckets, the time-to-live values, and the retry budgets) in
 * a volatile snapshot that {@link #update(Node)} atomically replaces on
 * every refresh. Uploads and downloads run two-pass streaming AES-CBC plus
 * HMAC pipelines that never land ciphertext on disk, rotate across
 * candidate hosts on retryable failures, and block on a first-refresh
 * latch until the initial {@code media_conn} reply has landed.
 *
 * @implNote
 * This implementation collapses WA Web's family of MMS modules
 * ({@code WAMediaConnParser}, {@code WAWebQueryMediaConnsJob},
 * {@code WAWebMediaHosts}, {@code WAWebMmsClient} and its helpers) into a
 * single service. The volatile snapshot fields are published without
 * locking; a concurrent {@link #update(Node)} becomes visible to every
 * reader on its next field read.
 */
@WhatsAppWebModule(moduleName = "WAMediaConnParser")
@WhatsAppWebModule(moduleName = "WAServerMediaType")
@WhatsAppWebModule(moduleName = "WAWebQueryMediaConnsJob")
@WhatsAppWebModule(moduleName = "WAWebMediaHosts")
@WhatsAppWebModule(moduleName = "WAWebMmsClient")
@WhatsAppWebModule(moduleName = "WAWebMmsClientSelectHost")
@WhatsAppWebModule(moduleName = "WAWebMmsClientMmsUpload")
@WhatsAppWebModule(moduleName = "WAWebMmsClientMmsDownload")
@WhatsAppWebModule(moduleName = "WAWebMmsClientFormatUploadUrl")
@WhatsAppWebModule(moduleName = "WAWebMmsClientFormatDownloadUrl")
@WhatsAppWebModule(moduleName = "WAWebMmsClientFormatHashUrl")
@WhatsAppWebModule(moduleName = "WAWebMmsClientIsErrorRetryable")
@WhatsAppWebModule(moduleName = "WAWebMmsClientMmsBackoffOptions")
@WhatsAppWebModule(moduleName = "WAWebMmsCdnUrlValidationUtils")
@WhatsAppWebModule(moduleName = "WAWebMmsClientMmsDeleteMdHistorySyncBlob")
@WhatsAppWebModule(moduleName = "WABase64UrlSafe")
@WhatsAppWebModule(moduleName = "WAWebWamMediaMetricUtils")
public final class DefaultMediaConnectionService implements MediaConnectionService {
    /**
     * The {@link ABPropsService} consulted when assembling CDN download URLs
     * and deciding whether the hash-based download fallback is still
     * permitted.
     *
     * @apiNote
     * Injected through the constructor so that {@link #download(MediaProvider)},
     * {@link #checkExistence}, and {@link #getEncryptedMediaSize} do not
     * have to thread it through every call.
     */
    private final ABPropsService abPropsService;

    /**
     * Signals when the first {@link #update(Node)} has landed.
     *
     * @apiNote
     * {@link #upload(MediaProvider, MediaPayload)},
     * {@link #download(MediaProvider)}, and the history-sync delete path
     * block on this latch so callers that fire before the first
     * {@code media_conn} refresh do not race ahead with an unpublished
     * field set. Subsequent updates release the latch a second time,
     * which is a no-op.
     */
    private final CountDownLatch initialized = new CountDownLatch(1);

    /**
     * The authentication token presented to the CDN on every upload and
     * download request.
     *
     * @apiNote
     * Without a valid token the CDN refuses the request with HTTP 401;
     * the upload/download retry loop classifies that as a retryable
     * error. Declared {@code volatile} so a concurrent
     * {@link #update(Node)} publishes the new token to every reader
     * without locking.
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    private volatile String auth;

    /**
     * The routes time-to-live in seconds.
     *
     * @apiNote
     * After this interval the CDN host list may no longer be current and
     * the caller should re-query a fresh media connection;
     * {@link #needsRefresh()} reports {@code true} once the TTL has
     * elapsed.
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob", exports = "queryMediaConn",
            adaptation = WhatsAppAdaptation.DIRECT)
    private volatile int ttl;

    /**
     * The authentication token time-to-live in seconds.
     *
     * @apiNote
     * Once the auth TTL elapses the CDN refuses requests made with the
     * stored token; {@link #isExpired()} reports {@code true} past the
     * deadline.
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob", exports = "queryMediaConn",
            adaptation = WhatsAppAdaptation.DIRECT)
    private volatile int authTtl;

    /**
     * The maximum number of deterministic download buckets advertised by
     * the server.
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob", exports = "mapParsedMediaConn",
            adaptation = WhatsAppAdaptation.DIRECT)
    private volatile int maxBuckets;

    /**
     * The maximum number of retry attempts the UI should offer when the
     * user manually re-triggers a failed media download.
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    private volatile int maxManualRetry;

    /**
     * The maximum number of retry attempts the client should perform when
     * transparently re-downloading a piece of media after a failure.
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    private volatile int maxAutoDownloadRetry;

    /**
     * The epoch-second timestamp at which the current credentials were
     * parsed.
     *
     * @apiNote
     * Combined with {@link #ttl} and {@link #authTtl} to compute the
     * absolute expiry deadlines used by {@link #isExpired()} and
     * {@link #needsRefresh()}.
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob", exports = "queryMediaConn",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private volatile long timestamp;

    /**
     * The ordered list of CDN host candidates for uploads and downloads.
     *
     * @apiNote
     * Each entry advertises a hostname, a set of supported media types,
     * and (for {@link MediaHost.Primary}) a nested fallback hostname.
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob", exports = "mapParsedMediaConn",
            adaptation = WhatsAppAdaptation.DIRECT)
    private volatile SequencedCollection<? extends MediaHost> hosts;

    /**
     * Constructs a fresh media-connection singleton bound to
     * {@code abPropsService}.
     *
     * @apiNote
     * Constructed once per session by {@code DefaultWhatsAppClient}; the
     * resulting instance is dependency-injected into every component that
     * needs CDN access (transcoder text pipeline, sync exchange,
     * stream-control success handler) plus the client itself. The
     * connection is unusable for uploads or downloads until the first
     * {@link #update(Node)} lands, at which point any waiting calls
     * unblock.
     *
     * @param abPropsService the AB-props service threaded into the
     *                       download URL formatter
     * @throws NullPointerException if {@code abPropsService} is
     *         {@code null}
     */
    public DefaultMediaConnectionService(ABPropsService abPropsService) {
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService cannot be null");
    }

    /**
     * Atomically replaces this service's snapshot with the credentials
     * and host list parsed from {@code response} and releases any
     * upload/download callers blocked on the first-refresh latch.
     *
     * @apiNote
     * Called by {@code SuccessStreamHandler.refreshMediaConnection} each
     * time the periodic {@code media_conn} IQ reply lands. Safe to call
     * from any thread; the volatile write publishes the new snapshot to
     * every concurrent reader. A subsequent {@link #upload(MediaProvider, MediaPayload)}
     * or {@link #download(MediaProvider)} call that captured the previous
     * snapshot before the swap keeps using that snapshot for the duration
     * of the operation; new callers see the fresh snapshot.
     *
     * @implNote
     * This implementation collapses three WA Web helpers into a single
     * pass: raw attribute decoding ({@code WAMediaConnParser}), host and
     * field projection ({@code WAWebQueryMediaConnsJob.mapParsedMediaConn}),
     * and the TTL normalisation that subtracts the current time from the
     * server's future expiry timestamps. The snapshot's {@code timestamp}
     * is set to the current epoch second so {@link #isExpired()} and
     * {@link #needsRefresh()} have a stable origin.
     *
     * @param response the {@code media_conn} IQ response node
     * @throws NoSuchElementException   if {@code response} is missing the
     *                                  {@code media_conn} child or one of
     *                                  the mandatory attributes
     * @throws IllegalArgumentException if a mandatory integer attribute
     *                                  cannot be parsed as an {@code int}
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob",
            exports = {"mapParsedMediaConn", "queryMediaConn"},
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void update(Node response) {
        var mediaConn = response.getRequiredChild("media_conn");

        var parsedAuth = mediaConn.getRequiredAttributeAsString("auth");
        var parsedTtl = mediaConn.getRequiredAttributeAsInt("ttl");
        var parsedAuthTtl = mediaConn.getRequiredAttributeAsInt("auth_ttl");
        var parsedMaxBuckets = mediaConn.getRequiredAttributeAsInt("max_buckets");

        var parsedMaxManualRetry = clampOptionalInt(
                mediaConn.getAttributeAsInt("max_manual_retry", (Integer) null),
                0, 4, 3
        );

        var parsedMaxAutoDownloadRetry = clampOptionalInt(
                mediaConn.getAttributeAsInt("max_auto_download_retry", (Integer) null),
                0, 4, 3
        );

        var parsedHosts = mediaConn.streamChildren("host")
                .map(DefaultMediaConnectionService::parseHost)
                .toList();

        var parsedTimestamp = Instant.now().getEpochSecond();

        this.auth = parsedAuth;
        this.ttl = parsedTtl;
        this.authTtl = parsedAuthTtl;
        this.maxBuckets = parsedMaxBuckets;
        this.maxManualRetry = parsedMaxManualRetry;
        this.maxAutoDownloadRetry = parsedMaxAutoDownloadRetry;
        this.timestamp = parsedTimestamp;
        this.hosts = parsedHosts;
        initialized.countDown();
    }

    /**
     * Blocks the calling thread until the first {@link #update(Node)}
     * has landed.
     *
     * @apiNote
     * Called by {@link #upload(MediaProvider, MediaPayload)},
     * {@link #download(MediaProvider)}, and
     * {@link #deleteHistorySyncBlob(String, byte[], String, String)} on
     * entry so callers do not race ahead with an unpublished field set.
     *
     * @throws InterruptedException if the calling thread is interrupted
     *                              while waiting
     */
    private void awaitInitialized() throws InterruptedException {
        initialized.await();
    }

    /**
     * Throws {@link IllegalStateException} when the first
     * {@link #update(Node)} has not yet landed.
     *
     * @throws IllegalStateException if no snapshot has been published
     */
    private void requireInitialized() {
        if (initialized.getCount() != 0L) {
            throw new IllegalStateException(
                    "MediaConnectionService has not yet received a media_conn refresh");
        }
    }

    /**
     * Parses a single {@code host} child node into a {@link MediaHost}
     * record.
     *
     * @apiNote
     * Classifies the host as {@link MediaHost.Fallback} when its
     * {@code type} attribute equals {@code "fallback"}, otherwise as
     * {@link MediaHost.Primary}. Primary hosts may carry the optional
     * {@code fallback_hostname}, {@code fallback_class},
     * {@code fallback_ip4}, and {@code fallback_ip6} attributes
     * describing a nested fallback endpoint.
     *
     * @param hostNode the host child node
     * @return the parsed media host
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob", exports = "mapParsedMediaConn",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static MediaHost parseHost(Node hostNode) {
        var hostname = hostNode.getRequiredAttributeAsString("hostname");
        var hostClass = hostNode.getAttributeAsString("class");

        var ips = new ArrayList<String>();
        hostNode.getAttributeAsString("ip4").ifPresent(ips::add);
        hostNode.getAttributeAsString("ip6").ifPresent(ips::add);

        var downloadTypes = parseMediaTypes(hostNode, "download");
        var uploadTypes = parseMediaTypes(hostNode, "upload");

        var downloadBuckets = hostNode.getChild("download_buckets")
                .map(bucketsNode -> bucketsNode.streamChildren()
                        .map(Node::description)
                        .map(tag -> {
                            try {
                                return Integer.parseInt(tag);
                            } catch (NumberFormatException _) {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .toList())
                .orElse(List.of());

        var isFallback = hostNode.getAttributeAsString("type")
                .map("fallback"::equals)
                .orElse(false);

        if (isFallback) {
            return new MediaHost.Fallback(
                    hostname,
                    hostClass,
                    List.copyOf(ips),
                    downloadBuckets,
                    downloadTypes,
                    uploadTypes
            );
        } else {
            var fallbackHostname = hostNode.getAttributeAsString("fallback_hostname");
            var fallbackClass = hostNode.getAttributeAsString("fallback_class");
            var fallbackIps = new ArrayList<String>();
            hostNode.getAttributeAsString("fallback_ip4").ifPresent(fallbackIps::add);
            hostNode.getAttributeAsString("fallback_ip6").ifPresent(fallbackIps::add);

            return new MediaHost.Primary(
                    hostname,
                    hostClass,
                    List.copyOf(ips),
                    fallbackHostname,
                    fallbackClass,
                    List.copyOf(fallbackIps),
                    downloadBuckets,
                    downloadTypes,
                    uploadTypes
            );
        }
    }

    /**
     * Parses the media-type whitelist declared under a host's
     * {@code download} or {@code upload} child node.
     *
     * @apiNote
     * Each child tag is mapped to a {@link MediaPath} constant. When the
     * child node is absent the whitelist defaults to every known server
     * media type, then a small set of types that never appear in CDN
     * routing ({@code kyc-id}, the novi placeholders,
     * {@code thumbnail-gif}, and {@code xma-image}) is filtered out,
     * matching the JS source's {@code castToServerMediaType} guards.
     *
     * @param hostNode    the host node
     * @param description the child node description, either
     *                    {@code "download"} or {@code "upload"}
     * @return the set of supported media paths
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob", exports = "mapParsedMediaConn",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAServerMediaType", exports = "castToServerMediaType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAServerMediaType", exports = "SERVER_MEDIA",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static Set<MediaPath> parseMediaTypes(Node hostNode, String description) {
        return hostNode.getChild(description)
                .map(typesNode -> {
                    var result = new LinkedHashSet<MediaPath>();
                    typesNode.streamChildren()
                            .map(Node::description)
                            .forEach(tag -> MediaPath.ofId(tag).ifPresent(result::add));

                    result.remove(MediaPath.KYC_ID);
                    result.remove(MediaPath.NOVI_IMAGE);
                    result.remove(MediaPath.NOVI_VIDEO);
                    result.remove(MediaPath.THUMBNAIL_GIF);
                    result.remove(MediaPath.XMA_IMAGE);
                    return Collections.unmodifiableSet(result);
                })
                .orElseGet(() -> {
                    var allTypes = new LinkedHashSet<>(MediaPath.known());
                    allTypes.remove(MediaPath.KYC_ID);
                    allTypes.remove(MediaPath.NOVI_IMAGE);
                    allTypes.remove(MediaPath.NOVI_VIDEO);
                    allTypes.remove(MediaPath.THUMBNAIL_GIF);
                    allTypes.remove(MediaPath.XMA_IMAGE);
                    return Collections.unmodifiableSet(allTypes);
                });
    }

    /**
     * Clamps an optional integer to the given inclusive range, returning a
     * default when the input is {@code null} or out of range.
     *
     * @apiNote
     * Helper for {@link #update(Node)} that decodes the server's
     * {@code max_manual_retry} and {@code max_auto_download_retry}
     * attributes, both of which are bounded to {@code [0, 4]} with a
     * default of {@code 3}.
     *
     * @param value        the parsed integer value, or {@code null}
     * @param min          the minimum allowed value
     * @param max          the maximum allowed value
     * @param defaultValue the default value when input is {@code null} or
     *                     out of range
     * @return the clamped value or the default
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static int clampOptionalInt(Integer value, int min, int max, int defaultValue) {
        if (value == null || value < min || value > max) {
            return defaultValue;
        }
        return value;
    }

    /**
     * The maximum number of retry attempts for upload and download
     * operations.
     *
     * @apiNote
     * Combines WA Web's module-level {@code 4} constant from
     * {@code WAWebMmsClientSelectHost} with the {@code retries: 3} entry
     * of {@code WAWebMmsClientMmsBackoffOptions}: 3 retries plus the
     * initial attempt give 4 total attempts indexed
     * {@code 0..MAX_ATTEMPT_COUNT-1}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientSelectHost", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebMmsClientMmsBackoffOptions", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final int MAX_ATTEMPT_COUNT = 4;

    /**
     * The minimum backoff timeout in milliseconds applied between
     * consecutive retry attempts of a media upload or download.
     *
     * @apiNote
     * With {@code factor = 2} and jitter disabled the schedule is
     * deterministic: attempts {@code 1, 2, 3} are preceded by sleeps of
     * {@code 1000ms, 2000ms, 4000ms}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientMmsBackoffOptions", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final long MIN_BACKOFF_TIMEOUT_MILLIS = 1000L;

    /**
     * Sleeps the caller's virtual thread for the exponential-backoff
     * delay associated with the given retry attempt number.
     *
     * @apiNote
     * Helper for the upload and download retry loops; the delay for
     * attempt {@code n} (zero-based) is
     * {@code MIN_BACKOFF_TIMEOUT_MILLIS * 2^n}, producing the sequence
     * {@code 1000ms, 2000ms, 4000ms} before attempts {@code 1, 2, 3}. No
     * sleep is performed for attempt {@code 0}.
     *
     * @implNote
     * This implementation collapses WA Web's
     * {@code WAExponentialBackoff.exponentialBackoff} into a plain
     * blocking {@link Thread#sleep(long)} on a virtual thread; on
     * interruption the interrupt flag is restored and the method returns
     * immediately so the retry loop can observe cancellation.
     *
     * @param attemptCount the zero-based attempt index that just failed
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientMmsBackoffOptions", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static void sleepForBackoff(int attemptCount) {
        if (attemptCount < 0) {
            return;
        }
        var delayMillis = MIN_BACKOFF_TIMEOUT_MILLIS << attemptCount;
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Picks the CDN hostname to use for the current retry attempt of an
     * upload or download.
     *
     * @apiNote
     * The rotation strategy, applied in priority order:
     * <ul>
     *   <li>If the previous attempt made progress, reuse the same
     *       host.</li>
     *   <li>On attempts {@code 0} and {@code 1}, stick with the initially
     *       selected host.</li>
     *   <li>On the last attempt, prefer the fallback-class host when one
     *       exists.</li>
     *   <li>If the previous attempt was the selected host and that host
     *       advertises a nested fallback hostname, rotate to it.</li>
     *   <li>Otherwise prefer the fallback host, falling back to the
     *       originally selected host.</li>
     * </ul>
     *
     * @param selectedHost          the host chosen by route selection, or
     *                              {@code null} if none matched
     * @param fallbackHost          the fallback-type host, or {@code null}
     *                              if none exists
     * @param lastHostUsed          the hostname used on the previous
     *                              attempt, or {@code null} on the first
     *                              attempt
     * @param attemptCount          the zero-based attempt number
     * @param lastFetchMadeProgress whether the previous attempt
     *                              transferred any data
     * @return the hostname to use, or {@code null} if no host is available
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientSelectHost", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    static String selectHost(
            MediaHost selectedHost,
            MediaHost fallbackHost,
            String lastHostUsed,
            int attemptCount,
            boolean lastFetchMadeProgress
    ) {
        var selectedHostname = selectedHost != null ? selectedHost.hostname() : null;
        var fallbackHostname = fallbackHost != null ? fallbackHost.hostname() : null;

        if (lastFetchMadeProgress && lastHostUsed != null) {
            return lastHostUsed;
        }

        if (attemptCount <= 1) {
            return selectedHostname;
        }

        if (attemptCount == MAX_ATTEMPT_COUNT - 1 && fallbackHostname != null) {
            return fallbackHostname;
        }

        if (lastHostUsed != null
                && lastHostUsed.equals(selectedHostname)
                && selectedHost != null
                && selectedHost.fallbackHostname().isPresent()) {
            return selectedHost.fallbackHostname().get();
        }

        return fallbackHostname != null ? fallbackHostname : selectedHostname;
    }

    /**
     * Uploads a media payload to WhatsApp's CDN on behalf of the given
     * provider.
     *
     * @apiNote
     * High-level entry point for any code that ships a media-bearing
     * message: choose the appropriate {@link MediaProvider} subtype, then
     * call this method with a transcoded {@link MediaPayload}. On success
     * the provider's media metadata (plaintext and encrypted SHA-256
     * hashes, media key, direct path, URL, byte size, and key timestamp)
     * is written back through the provider setters so the caller can
     * build the outgoing message protobuf; when the provider is an
     * {@link ExternalBlobReference} the server-returned handle is stored
     * too. The {@code payload} is not closed by this method; the caller
     * (typically {@code LinkedWhatsAppClient.uploadMedia}) wraps the call
     * in a try-with-resources so any temp file owned by the payload is
     * released.
     *
     * @implNote
     * This implementation runs a two-pass streaming-encrypt pipeline that
     * never lands the encrypted payload on disk. Pass 1 opens
     * {@link MediaPayload#openPlaintext()}, threads it through
     * {@link MediaUploadInputStream} (AES-CBC + HMAC), and drains the
     * encrypted output to {@link OutputStream#nullOutputStream()} while
     * accumulating {@code fileSha256}, {@code fileEncSha256},
     * {@code fileLength}, and the random {@code mediaKey} that the
     * recipient needs. Pass 2 builds a known-length
     * {@link HttpRequest.BodyPublisher} that re-opens the plaintext,
     * re-encrypts with the same {@code mediaKey}, and streams the
     * ciphertext straight to the HTTP socket. The encrypted content
     * length is computed deterministically from the plaintext length
     * ({@code ((plaintextLength / 16) + 1) * 16 + 10} for AES-CBC + PKCS5
     * padding + truncated HMAC). Retries replay pass 2 against the same
     * payload; the plaintext source is replayable by contract.
     *
     * @param provider the media provider describing the media type and
     *                 receiving the upload metadata
     * @param payload  the transcoded payload
     * @return {@code true} if the upload succeeded
     * @throws WhatsAppMediaException.Upload if no host could service the
     *         upload, a non-retryable HTTP error occurred (413, 415, 507),
     *         or an I/O error occurred
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClient", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean upload(MediaProvider provider, MediaPayload payload) throws WhatsAppMediaException, InterruptedException {
        Objects.requireNonNull(provider, "provider cannot be null");
        Objects.requireNonNull(payload, "payload cannot be null");

        var path = provider.mediaPath()
                .path();
        if (path.isEmpty()) {
            return false;
        }

        awaitInitialized();
        var currentAuth = this.auth;
        var currentHosts = this.hosts;
        var currentMaxBuckets = this.maxBuckets;

        var keyName = provider.mediaPath().keyName();
        var mediaKey = keyName.isPresent()
                ? DataUtils.randomByteArray(32)
                : null;

        byte[] fileSha256;
        byte[] fileEncSha256;
        long fileLength;
        try (var pass1 = openUploadStream(provider, payload, mediaKey)) {
            pass1.transferTo(OutputStream.nullOutputStream());
            fileSha256 = pass1.fileSha256();
            fileEncSha256 = pass1.fileEncSha256().orElse(null);
            fileLength = pass1.fileLength();
        } catch (IOException exception) {
            throw new WhatsAppMediaException.Upload(
                    "Cannot encrypt media (hash pass)", exception);
        }

        var encryptedLength = mediaKey != null
                ? ((fileLength / 16L) + 1L) * 16L + MediaUploadInputStream.MAC_LENGTH
                : fileLength;

        try (var client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build()) {
            var timestamp = Instant.now();
            var hostList = currentHosts instanceof List<? extends MediaHost> list ? list : List.copyOf(currentHosts);
            var route = MediaHost.routeSelection(
                    Operation.UPLOAD,
                    provider.mediaPath(),
                    hostList,
                    fileEncSha256 != null ? Base64.getEncoder().encodeToString(fileEncSha256) : null,
                    currentMaxBuckets > 0 ? currentMaxBuckets : null,
                    false
            );

            String lastHostUsed = null;
            var lastFetchMadeProgress = false;
            for (var attemptCount = 0; attemptCount < MAX_ATTEMPT_COUNT; attemptCount++) {
                var hostname = selectHost(
                        route.selectedHost().orElse(null),
                        route.fallbackHost().orElse(null),
                        lastHostUsed,
                        attemptCount,
                        lastFetchMadeProgress
                );
                if (hostname == null) {
                    continue;
                }
                lastHostUsed = hostname;

                try {
                    var body = buildEncryptingBodyPublisher(provider, payload, mediaKey, encryptedLength);
                    var uploadResult = tryUpload(client, hostname, path.get(), currentAuth, fileEncSha256, fileSha256, body);

                    var directPath = uploadResult.getString("direct_path");
                    var url = uploadResult.getString("url");
                    var handle = uploadResult.getString("handle");

                    provider.setMediaSha256(fileSha256);
                    provider.setMediaEncryptedSha256(fileEncSha256);
                    provider.setMediaKey(mediaKey);
                    provider.setMediaSize(fileLength);
                    provider.setMediaDirectPath(directPath);
                    provider.setMediaUrl(url);
                    provider.setMediaKeyTimestamp(timestamp);
                    if (provider instanceof ExternalBlobReference externalBlobReference) {
                        externalBlobReference.setHandle(handle);
                    }

                    return true;
                } catch (WhatsAppMediaException.Upload uploadException) {
                    if (!isRetryable(uploadException)) {
                        throw uploadException;
                    }

                    lastFetchMadeProgress = false;

                    if (attemptCount < MAX_ATTEMPT_COUNT - 1) {
                        sleepForBackoff(attemptCount);
                    }
                }
            }

            throw new WhatsAppMediaException.Upload("Cannot upload media: no hosts available");
        }
    }

    /**
     * Opens an upload stream over the payload's plaintext, using
     * {@code mediaKey} for AES-CBC encryption when the provider requests
     * it.
     *
     * @apiNote
     * Called twice per upload: once for the hash pass and once per HTTP
     * attempt for the streaming POST. The returned stream owns the
     * plaintext stream and closes it on {@link MediaUploadInputStream#close()}.
     *
     * @param provider the media provider
     * @param payload  the transcoded payload
     * @param mediaKey the encryption key, or {@code null} for the
     *                 plaintext variant
     * @return the upload stream
     * @throws IOException if the plaintext stream cannot be opened or the
     *         cipher cannot be initialised
     */
    private static MediaUploadInputStream openUploadStream(MediaProvider provider,
                                                            MediaPayload payload,
                                                            byte[] mediaKey) throws IOException {
        var plaintext = payload.openPlaintext();
        try {
            return MediaUploadInputStream.of(provider, plaintext, mediaKey);
        } catch (WhatsAppMediaException exception) {
            plaintext.close();
            throw new IOException("Cannot initialise upload stream", exception);
        }
    }

    /**
     * Builds a known-length {@link HttpRequest.BodyPublisher} that
     * streams the encrypted payload directly to the HTTP socket.
     *
     * @apiNote
     * Wraps {@link HttpRequest.BodyPublishers#ofInputStream(Supplier)}
     * with the deterministic encrypted content length so the JVM's
     * {@code HttpClient} can set a real {@code Content-Length} header
     * (the CDN refuses chunked encoding). Each subscription opens a
     * fresh upload stream over the payload's plaintext; the same
     * {@code mediaKey} is reused so the emitted ciphertext matches the
     * {@code fileEncSha256} captured during the hash pass.
     *
     * @param provider        the media provider
     * @param payload         the transcoded payload
     * @param mediaKey        the encryption key, or {@code null} for the
     *                        plaintext variant
     * @param encryptedLength the deterministic ciphertext length
     * @return a body publisher with the specified content length
     */
    private static HttpRequest.BodyPublisher buildEncryptingBodyPublisher(MediaProvider provider,
                                                                          MediaPayload payload,
                                                                          byte[] mediaKey,
                                                                          long encryptedLength) {
        Supplier<InputStream> supplier = () -> {
            try {
                return openUploadStream(provider, payload, mediaKey);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        };
        return HttpRequest.BodyPublishers.fromPublisher(
                HttpRequest.BodyPublishers.ofInputStream(supplier),
                encryptedLength);
    }

    /**
     * Performs one HTTP POST upload of an encrypted media payload against
     * a single CDN host.
     *
     * @apiNote
     * Helper for the {@link #upload(MediaProvider, MediaPayload)} retry
     * loop. The target URL is assembled by
     * {@link #formatUploadUrl(String, String, String, long)}; the
     * URL-safe base64 of the encrypted file hash is appended as the
     * trailing path segment, and the {@code auth}, {@code token}, and
     * {@code media_id} query parameters follow. On a non-{@code 200}
     * response a {@link WhatsAppMediaException.Upload} is raised with the
     * HTTP status code preserved; the response JSON must contain
     * non-{@code null}, non-empty {@code direct_path} and {@code url}
     * fields or the upload is rejected.
     *
     * @implNote
     * This implementation issues a single fire-and-forget POST; WA Web's
     * {@code WAWebMmsClientUploadStreamer} additionally supports resume,
     * chunked streaming, server-side thumbnail generation, and
     * newsletter-video transcoding query parameters, none of which are
     * wired in Cobalt.
     *
     * @param client        the HTTP client to use
     * @param hostname      the CDN hostname
     * @param path          the CDN path segment
     * @param fileEncSha256 the encrypted file SHA-256, or {@code null}
     * @param fileSha256    the plaintext file SHA-256
     * @param body          the streaming body publisher emitting the
     *                      encrypted payload with a known content length
     * @return the parsed response JSON
     * @throws WhatsAppMediaException.Upload if the server returns a
     *         non-{@code 200} status code or the response is missing
     *         required fields
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientMmsUpload", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private JSONObject tryUpload(HttpClient client, String hostname, String path,
                                 String auth,
                                 byte[] fileEncSha256, byte[] fileSha256,
                                 HttpRequest.BodyPublisher body) throws WhatsAppMediaException.Upload {
        try {
            var token = Base64.getUrlEncoder()
                    .encodeToString(Objects.requireNonNullElse(fileEncSha256, fileSha256));

            var uri = URI.create(formatUploadUrl(hostname, path, token, generateMediaId(), auth));
            var requestBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .POST(body);
            var request = requestBuilder.header("Content-Type", "application/octet-stream")
                    .header("Accept", "application/json")
                    .headers("Origin", "https://web.whatsapp.com")
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                var statusCode = response.statusCode();
                var message = switch (statusCode) {
                    case 401 -> "mmsUpload: unauthorized";
                    case 413 -> "mmsUpload: media too large";
                    case 415 -> "mmsUpload: hash mismatch";
                    case 507 -> "mmsUpload: throttled";
                    default -> "mmsUpload: HTTP " + statusCode;
                };
                throw new WhatsAppMediaException.Upload(statusCode, message);
            }

            var jsonObject = JSON.parseObject(response.body());
            var directPath = jsonObject != null ? jsonObject.getString("direct_path") : null;
            var url = jsonObject != null ? jsonObject.getString("url") : null;
            if (directPath == null || directPath.isEmpty()) {
                throw new WhatsAppMediaException.Upload("mmsUpload: missing direct_path");
            }
            if (url == null || url.isEmpty()) {
                throw new WhatsAppMediaException.Upload("mmsUpload: missing url");
            }
            return jsonObject;
        } catch (IOException | InterruptedException exception) {
            // No status code on network-level failures; isRetryable's empty-status branch treats this as retryable
            throw new WhatsAppMediaException.Upload("mmsUpload: network error", exception);
        }
    }

    /**
     * Produces a random media event identifier for telemetry and request
     * correlation.
     *
     * @apiNote
     * Appended as the {@code media_id} query parameter on every CDN POST
     * so server-side analytics can de-duplicate events across retries. The
     * value is a positive long in the range
     * {@code [1, Number.MAX_SAFE_INTEGER]} to match WA Web's
     * {@code generateMediaEventId} contract.
     *
     * @return a random positive long suitable for use as a media id
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils", exports = "generateMediaEventId",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static long generateMediaId() {
        return MediaMetricUtils.generateMediaEventId();
    }

    /**
     * Builds the upload URL for an authenticated media POST against the
     * WhatsApp CDN.
     *
     * @apiNote
     * The URL is assembled as
     * {@snippet :
     * https://{hostname}/{path}/{token}?auth=...&token=...&media_id=...
     * }
     * where {@code token} is the URL-safe base64 of the encrypted file
     * hash and is reused as both a path segment and a query parameter.
     *
     * @param hostname the CDN hostname
     * @param path     the CDN path segment for the media type
     * @param token    the URL-safe base64 of the encrypted file hash
     * @param mediaId  the random media-event id
     * @return the assembled upload URL
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientFormatUploadUrl", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebMmsClientFormatHashUrl", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    String formatUploadUrl(String hostname, String path, String token, long mediaId, String auth) {
        var basePath = "https://" + hostname + "/" + path + "/" + token;

        var queryParams = new LinkedHashMap<String, String>();
        queryParams.put("auth", auth);
        queryParams.put("token", token);
        queryParams.put("media_id", String.valueOf(mediaId));

        return basePath + encodeQueryString(queryParams);
    }

    /**
     * Serialises a map of query parameters into a URL query string.
     *
     * @apiNote
     * Helper for {@link #formatUploadUrl} and {@link #buildDirectPathUrl};
     * {@code null} values are skipped and every remaining key/value pair
     * is percent-encoded under {@code application/x-www-form-urlencoded}
     * rules, matching WA Web's {@code URLSearchParams.toString()} idiom.
     *
     * @param params the query parameter map
     * @return the encoded query string with leading {@code "?"}, or an
     *         empty string when no parameters remain after filtering
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientFormatHashUrl", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static String encodeQueryString(Map<String, String> params) {
        var sb = new StringBuilder();
        for (var entry : params.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return sb.isEmpty() ? "" : "?" + sb;
    }

    /**
     * Tests whether a media upload error is worth retrying against a
     * different host.
     *
     * @apiNote
     * Mirrors {@code WAWebMmsClientIsErrorRetryable.isErrorRetryable}
     * mapped onto Cobalt's exception subtypes:
     * <ul>
     *   <li>Missing status code (network error): retryable</li>
     *   <li>{@code 401} (unauthorized): retryable</li>
     *   <li>{@code 507} (throttled): not retryable</li>
     *   <li>Other {@code 5xx}: retryable</li>
     *   <li>Every other status ({@code 404}, {@code 408}, {@code 410},
     *       {@code 413}, {@code 415}, {@code 403}, etc.): not
     *       retryable</li>
     * </ul>
     *
     * @param exception the upload exception to test
     * @return {@code true} if the error is retryable
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientIsErrorRetryable",
            exports = "isErrorRetryable",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static boolean isRetryable(WhatsAppMediaException.Upload exception) {
        var optStatus = exception.httpStatusCode();
        if (optStatus.isEmpty()) {
            return true;
        }
        var status = optStatus.getAsInt();

        if (status == 401) {
            return true;
        }

        if (status == 507) {
            return false;
        }

        return status >= 500;
    }

    /**
     * Downloads a media payload from WhatsApp's CDN for the given
     * provider.
     *
     * @apiNote
     * High-level entry point for any code that materialises an inbound
     * attachment. First tries the provider's cached static media URL, if
     * any; if that fails with a retryable error, resolves a fresh host
     * through {@link MediaHost#routeSelection} and rotates across
     * candidate hosts on each retry. Non-retryable errors ({@code 404},
     * {@code 408}, {@code 410}, {@code 507}, and the rest of {@code 4xx})
     * propagate immediately. The injected {@link ABPropsService} feeds
     * the download URL formatter (cache-affinity hints and the hash-URL
     * deprecation flag).
     *
     * @implNote
     * This implementation does not consult an in-memory media-conn cache
     * the way WA Web's {@code mediaHosts} singleton does; each
     * {@link MediaConnectionService} is treated as immutable and the caller
     * decides when to refresh.
     *
     * @param provider the media provider
     * @return an {@link InputStream} delivering the decrypted media
     *         content
     * @throws WhatsAppMediaException.Download if no host could service the
     *         download, the direct path is missing, or a non-retryable
     *         HTTP error occurred
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClient", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public InputStream download(MediaProvider provider) throws WhatsAppMediaException, InterruptedException {
        Objects.requireNonNull(provider, "provider cannot be null");

        var defaultUploadUrl = provider.mediaUrl();
        if (defaultUploadUrl.isPresent()) {
            try {
                return tryDownload(provider, defaultUploadUrl.get());
            } catch (WhatsAppMediaException.Download downloadException) {
                if (!isDownloadRetryable(downloadException)) {
                    throw downloadException;
                }
            }
        }

        var defaultDirectPath = provider.mediaDirectPath()
                .orElse(null);
        var encFileHash = provider.mediaEncryptedSha256()
                .map(bytes -> Base64.getEncoder().encodeToString(bytes))
                .orElse(null);
        var mediaType = provider.mediaPath();

        if ((defaultDirectPath == null || defaultDirectPath.isEmpty())
                && (encFileHash == null || encFileHash.isEmpty())) {
            throw new WhatsAppMediaException.Download(
                    "No staticUrl, directPath, or encFilehash available for download");
        }

        awaitInitialized();
        var currentHosts = this.hosts;
        var currentMaxBuckets = this.maxBuckets;
        var hostList = currentHosts instanceof List<? extends MediaHost> list ? list : List.copyOf(currentHosts);
        var route = MediaHost.routeSelection(
                Operation.DOWNLOAD,
                mediaType,
                hostList,
                encFileHash,
                currentMaxBuckets > 0 ? currentMaxBuckets : null,
                false
        );

        var selectedBucket = route.selectedBucket().orElse(null);
        var selectedHostname = route.selectedHost()
                .map(MediaHost::hostname)
                .orElse(null);

        String lastHostUsed = null;
        var lastFetchMadeProgress = false;
        for (var attemptCount = 0; attemptCount < MAX_ATTEMPT_COUNT; attemptCount++) {
            var hostname = selectHost(
                    route.selectedHost().orElse(null),
                    route.fallbackHost().orElse(null),
                    lastHostUsed,
                    attemptCount,
                    lastFetchMadeProgress
            );
            if (hostname == null) {
                continue;
            }
            lastHostUsed = hostname;

            var hostBucket = hostname.equals(selectedHostname) ? selectedBucket : null;

            var downloadUrl = formatDownloadUrl(
                    hostname, defaultDirectPath, encFileHash, mediaType, hostBucket
            );

            try {
                return tryDownload(provider, downloadUrl);
            } catch (WhatsAppMediaException.Download downloadException) {
                if (!isDownloadRetryable(downloadException)) {
                    throw downloadException;
                }

                lastFetchMadeProgress = false;

                if (attemptCount < MAX_ATTEMPT_COUNT - 1) {
                    sleepForBackoff(attemptCount);
                }
            }
        }

        throw new WhatsAppMediaException.Download("Cannot download media: no hosts available");
    }

    /**
     * Chooses between the direct-path and hash-based URL formats and
     * returns the final CDN download URL.
     *
     * @apiNote
     * Helper for {@link #download(MediaProvider)} and the
     * HEAD-request helpers. When a direct path is available the URL is
     * built via {@link #buildDirectPathUrl}; when only the encrypted file
     * hash is available the URL is built via {@link #formatHashUrl},
     * unless the {@code web_deprecate_mms4_hash_based_download} AB prop
     * is enabled, in which case this method throws unconditionally.
     *
     * @param hostname       the CDN hostname
     * @param directPath     the direct path, or {@code null}
     * @param encFileHash    the base64-encoded encrypted file hash, or
     *                       {@code null}
     * @param mediaType      the media path type
     * @param downloadBucket the selected download bucket, or {@code null}
     * @return the constructed download URL
     * @throws WhatsAppMediaException.Download if neither direct path nor
     *         encrypted file hash is available, or the hash-URL fallback
     *         is deprecated by the AB prop
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientFormatDownloadUrl", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    String formatDownloadUrl(
            String hostname,
            String directPath,
            String encFileHash,
            MediaPath mediaType,
            Integer downloadBucket
    ) throws WhatsAppMediaException.Download {
        var mediaTypeId = mediaType.id().orElse(null);
        if (directPath != null && !directPath.isEmpty()) {
            var query = new LinkedHashMap<String, String>();
            query.put("mode", "auto");
            query.put("mms-type", mediaTypeId);
            query.put("__wa-mms", "");
            return buildDirectPathUrl(hostname, directPath, encFileHash, downloadBucket, mediaType, query);
        }

        if (abPropsService.getBool(ABProp.WEB_DEPRECATE_MMS4_HASH_BASED_DOWNLOAD)) {
            throw new WhatsAppMediaException.Download(
                    "No direct path is available for download, abort");
        }

        if (encFileHash == null || encFileHash.isEmpty()) {
            throw new WhatsAppMediaException.Download(
                    "No direct path or encFilehash available for download, abort");
        }

        var query = new LinkedHashMap<String, String>();
        query.put("mode", "auto");
        query.put("__wa-mms", "");
        return formatHashUrl(hostname, mediaType, encFileHash, query);
    }

    /**
     * Builds a direct-path download URL.
     *
     * @apiNote
     * Helper for {@link #formatDownloadUrl}. Appends the URL-safe base64
     * of the encrypted file hash as {@code hash}, the download bucket as
     * {@code _nc_cat}, and the {@code _nc_map=whatsapp-nofna}
     * cache-affinity hint when the media type is listed in the
     * {@code low_cache_hit_rate_media_types} AB prop. A hostname security
     * check rejects direct paths whose embedded URL resolves to a
     * different host.
     *
     * @param hostname       the expected CDN hostname
     * @param directPath     the direct path segment
     * @param encFileHash    the base64-encoded encrypted file hash, or
     *                       {@code null}
     * @param downloadBucket the download bucket number, or {@code null}
     * @param mediaType      the media path type
     * @param query          the additional query parameters to include
     * @return the assembled download URL
     * @throws WhatsAppMediaException.Download if the direct path resolves
     *         to a different hostname than expected
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientFormatDownloadUrl", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    private String buildDirectPathUrl(
            String hostname,
            String directPath,
            String encFileHash,
            Integer downloadBucket,
            MediaPath mediaType,
            Map<String, String> query
    ) throws WhatsAppMediaException.Download {
        var baseUri = URI.create("https://" + hostname).resolve(directPath);

        if (!baseUri.getHost().equals(hostname)) {
            throw new WhatsAppMediaException.Download("malicious directPath");
        }

        // java.net.URI has no URLSearchParams equivalent; the existing query is split manually
        var params = new LinkedHashMap<String, String>();
        var existingQuery = baseUri.getRawQuery();
        if (existingQuery != null && !existingQuery.isEmpty()) {
            for (var param : existingQuery.split("&")) {
                var eqIndex = param.indexOf('=');
                if (eqIndex >= 0) {
                    params.put(param.substring(0, eqIndex), param.substring(eqIndex + 1));
                } else {
                    params.put(param, "");
                }
            }
        }

        if (encFileHash != null && !encFileHash.isEmpty()) {
            params.put("hash", urlSafeBase64(encFileHash));
        }

        if (downloadBucket != null) {
            params.put("_nc_cat", downloadBucket.toString());
        }

        var lowCacheHitTypes = abPropsService.getString(ABProp.LOW_CACHE_HIT_RATE_MEDIA_TYPES);
        var mediaTypeId = mediaType.id().orElse(null);
        if (lowCacheHitTypes != null && !lowCacheHitTypes.isEmpty() && mediaTypeId != null) {
            for (var candidate : lowCacheHitTypes.split(",")) {
                if (candidate.equals(mediaTypeId)) {
                    params.put("_nc_map", "whatsapp-nofna");
                    break;
                }
            }
        }

        for (var entry : query.entrySet()) {
            if (entry.getValue() != null) {
                params.put(entry.getKey(), entry.getValue());
            }
        }

        return "https://" + baseUri.getHost() + baseUri.getRawPath()
                + encodeQueryString(params);
    }

    /**
     * Builds a hash-based download URL.
     *
     * @apiNote
     * Helper for {@link #formatDownloadUrl}; produces a URL of the form
     * {@snippet :
     * https://{hostname}/{path}/{urlSafeBase64(encFileHash)}?{query}
     * }
     * where {@code path} is read from the media type metadata.
     *
     * @param hostname    the CDN hostname
     * @param mediaType   the media path type
     * @param encFileHash the base64-encoded encrypted file hash
     * @param query       the additional query parameters to include
     * @return the assembled hash-based download URL
     * @throws WhatsAppMediaException.Download if the media type has no
     *         path segment for hash URL construction
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientFormatHashUrl", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static String formatHashUrl(
            String hostname,
            MediaPath mediaType,
            String encFileHash,
            Map<String, String> query
    ) throws WhatsAppMediaException.Download {
        var pathSegment = mediaType.path()
                .orElseThrow(() -> new WhatsAppMediaException.Download(
                        "No hash URL path for media type: " + mediaType));

        var basePath = "https://" + hostname + "/" + pathSegment + "/" + urlSafeBase64(encFileHash);

        var filteredQuery = new LinkedHashMap<String, String>();
        for (var entry : query.entrySet()) {
            if (entry.getValue() != null) {
                filteredQuery.put(entry.getKey(), entry.getValue());
            }
        }

        return basePath + encodeQueryString(filteredQuery);
    }

    /**
     * Converts a standard base64 string to the URL-safe variant used by
     * WhatsApp CDN URLs.
     *
     * @apiNote
     * Replaces {@code '/'} with {@code '_'} and {@code '+'} with
     * {@code '-'} while keeping the {@code '='} padding characters.
     *
     * @param base64 the standard base64 string to convert
     * @return the URL-safe base64 string
     */
    @WhatsAppWebExport(moduleName = "WABase64UrlSafe", exports = "urlSafeBase64",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static String urlSafeBase64(String base64) {
        return base64.replace('/', '_').replace('+', '-');
    }

    /**
     * Performs one HTTP GET download against a fully-formed CDN URL.
     *
     * @apiNote
     * Helper for the {@link #download(MediaProvider)}
     * retry loop and for direct re-downloads against a known URL. Wraps
     * the response body in a {@link MediaDownloadInputStream} so the
     * caller sees decrypted, integrity-checked bytes; the returned stream
     * owns the underlying {@link HttpClient} and closes it when consumed
     * or closed by the caller.
     *
     * @param provider    the media provider holding the decryption
     *                    metadata
     * @param downloadUrl the full URL to download from
     * @return an {@link InputStream} delivering the decrypted media
     *         content
     * @throws WhatsAppMediaException.Download if the server returns a
     *         non-{@code 200} status code, the {@code Content-Length}
     *         header is missing, or a network error occurs
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientMmsDownload", exports = "mms4Download",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public InputStream tryDownload(MediaProvider provider, String downloadUrl) throws WhatsAppMediaException.Download {
        var client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        var request = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                // 403 with "URL signature expired" body must reclassify to MediaNotFoundError so read the body first
                String body;
                try (var rawStream = response.body()) {
                    body = new String(rawStream.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException _) {
                    body = null;
                }
                client.close();
                validateMmsResponse(response.statusCode(), downloadUrl, body);
            }

            var payloadLength = response.headers()
                    .firstValueAsLong("Content-Length")
                    .orElseThrow(() -> {
                        client.close();
                        return new WhatsAppMediaException.Download("Unknown content length");
                    });

            var rawInputStream = response.body();
            return new MediaDownloadInputStream(client, rawInputStream, payloadLength, provider);
        } catch (IOException | InterruptedException exception) {
            client.close();
            throw new WhatsAppMediaException.Download("mmsDownload: network error", exception);
        }
    }

    /**
     * Translates a non-OK HTTP status code from a media download into the
     * appropriate {@link WhatsAppMediaException.Download}.
     *
     * @apiNote
     * The mapping mirrors WA Web's
     * {@code WAWebMmsClientMmsDownload.validateMmsResponse}:
     * <ul>
     *   <li>{@code 401}: {@link WhatsAppMediaException#HTTP_UNAUTHORIZED}</li>
     *   <li>{@code 403}: {@link WhatsAppMediaException#HTTP_NOT_FOUND}
     *       when the body contains {@code "URL signature expired"} or the
     *       URL's {@code oe} parameter encodes an expired signature;
     *       otherwise {@link WhatsAppMediaException#HTTP_FORBIDDEN}</li>
     *   <li>{@code 404} and {@code 410}:
     *       {@link WhatsAppMediaException#HTTP_NOT_FOUND}</li>
     *   <li>{@code 507}: {@link WhatsAppMediaException#HTTP_THROTTLE}</li>
     *   <li>any other: the raw status code</li>
     * </ul>
     *
     * @param statusCode the HTTP status code
     * @param url        the download URL, used for expiry parsing
     * @param body       the response body for the GET path, or
     *                   {@code null} for HEAD requests
     * @throws WhatsAppMediaException.Download always, since this method
     *         is only called for non-OK responses
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientMmsDownload", exports = "validateMmsResponse",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebMmsCdnUrlValidationUtils", exports = "parseCdnUrlParams",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static void validateMmsResponse(int statusCode, String url, String body) throws WhatsAppMediaException.Download {
        if (statusCode == 401) {
            throw new WhatsAppMediaException.Download(WhatsAppMediaException.HTTP_UNAUTHORIZED,
                    "mmsDownload: unauthorized");
        }

        if (statusCode == 403) {
            if (body != null && body.contains("URL signature expired")) {
                throw new WhatsAppMediaException.Download(WhatsAppMediaException.HTTP_NOT_FOUND,
                        "mmsDownload: media not found (URL signature expired)");
            }

            // The "oe" query parameter encodes the URL signature expiry as a hex unix timestamp
            try {
                var uri = URI.create(url);
                var query = uri.getRawQuery();
                if (query != null) {
                    for (var param : query.split("&")) {
                        var eqIndex = param.indexOf('=');
                        if (eqIndex >= 0 && param.substring(0, eqIndex).equals("oe")) {
                            var hexValue = param.substring(eqIndex + 1);
                            var expirationEpoch = Long.parseLong(hexValue, 16);

                            if (Instant.now().getEpochSecond() >= expirationEpoch) {
                                throw new WhatsAppMediaException.Download(WhatsAppMediaException.HTTP_NOT_FOUND,
                                        "mmsDownload: media not found (URL expired)");
                            }
                            break;
                        }
                    }
                }
            } catch (WhatsAppMediaException.Download e) {
                throw e;
            } catch (Exception _) {
                // Malformed URL or non-hex oe value falls through to the generic forbidden classification below
            }

            throw new WhatsAppMediaException.Download(WhatsAppMediaException.HTTP_FORBIDDEN,
                    "mmsDownload: forbidden");
        }

        if (statusCode == 404 || statusCode == 410) {
            throw new WhatsAppMediaException.Download(WhatsAppMediaException.HTTP_NOT_FOUND,
                    "mmsDownload: media not found");
        }

        if (statusCode == 507) {
            throw new WhatsAppMediaException.Download(WhatsAppMediaException.HTTP_THROTTLE,
                    "mmsDownload: throttled");
        }

        throw new WhatsAppMediaException.Download(statusCode,
                "mmsDownload: HTTP " + statusCode);
    }

    /**
     * Tests whether a media download error is worth retrying against a
     * different host.
     *
     * @apiNote
     * Applies the same rule set as
     * {@link #isRetryable(WhatsAppMediaException.Upload)} to
     * download-class exceptions: missing status code retryable,
     * {@code 401} retryable, {@code 507} fatal, other {@code 5xx}
     * retryable, every other status fatal.
     *
     * @param exception the download exception to test
     * @return {@code true} if the error is retryable
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientIsErrorRetryable",
            exports = "isErrorRetryable",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static boolean isDownloadRetryable(WhatsAppMediaException.Download exception) {
        var optStatus = exception.httpStatusCode();
        if (optStatus.isEmpty()) {
            return true;
        }
        var status = optStatus.getAsInt();

        if (status == 401) {
            return true;
        }

        if (status == 507) {
            return false;
        }

        return status >= 500;
    }

    /**
     * Tests whether a raw HTTP status code is retryable in isolation,
     * ignoring the wrapping exception subtype.
     *
     * @apiNote
     * The rules: {@code 408} retryable, {@code 507} fatal, every other
     * status retryable iff it is {@code 5xx}. More permissive than
     * {@link #isRetryable(WhatsAppMediaException.Upload)} because it
     * does not consult the exception subtype taxonomy. Preserved for
     * parity with WA Web's
     * {@code WAWebMmsClientIsErrorRetryable.isRetriableStatusCode}
     * surface.
     *
     * @implNote
     * This implementation is unused by Cobalt's own pipeline; the upload
     * and download retry loops consult
     * {@link #isRetryable(WhatsAppMediaException.Upload)} and
     * {@link #isDownloadRetryable(WhatsAppMediaException.Download)}
     * instead.
     *
     * @param statusCode the HTTP status code
     * @return {@code true} if the status alone marks the response as
     *         retryable
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientIsErrorRetryable",
            exports = "isRetriableStatusCode",
            adaptation = WhatsAppAdaptation.DIRECT)
    @SuppressWarnings("unused")
    private static boolean isRetriableStatusCode(int statusCode) {
        if (statusCode == 408) {
            return true;
        }
        if (statusCode == 507) {
            return false;
        }
        return statusCode >= 500;
    }

    /**
     * Probes the WhatsApp CDN to verify that a media file exists and is
     * still available for download.
     *
     * @apiNote
     * Useful before kicking off a full download for an attachment that
     * was referenced from elsewhere (a quoted message, a forwarded
     * sticker) to avoid wasting bandwidth on a known-missing payload.
     * Sends an HTTP HEAD request; a {@code 200} response signals
     * availability, any other status is mapped through
     * {@link #validateMmsResponse(int, String, String)}.
     *
     * @param hostname    the CDN hostname
     * @param mediaType   the media path type
     * @param directPath  the CDN direct path, or {@code null}
     * @param encFileHash the base64-encoded encrypted file hash, or
     *                    {@code null}
     * @throws WhatsAppMediaException.Download if the media does not exist
     *         or a network error occurs
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientMmsDownload", exports = "mmsCheckExistence",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void checkExistence(
            String hostname,
            MediaPath mediaType,
            String directPath,
            String encFileHash
    ) throws WhatsAppMediaException.Download {
        sendHeadRequest(hostname, mediaType, directPath, encFileHash, "mmsCheckExistence");
    }

    /**
     * Retrieves the size in bytes of the encrypted media payload stored on
     * the WhatsApp CDN.
     *
     * @apiNote
     * Sends an HTTP HEAD request and reads the {@code Content-Length}
     * header so the caller can pre-allocate buffers or estimate bandwidth
     * before invoking a full download.
     *
     * @param hostname    the CDN hostname
     * @param mediaType   the media path type
     * @param directPath  the CDN direct path, or {@code null}
     * @param encFileHash the base64-encoded encrypted file hash, or
     *                    {@code null}
     * @return the encrypted media file size in bytes
     * @throws WhatsAppMediaException.Download if the
     *         {@code Content-Length} header is missing, the server returns
     *         a non-OK status, or a network error occurs
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientMmsDownload", exports = "mmsGetEncryptedMediaSize",
            adaptation = WhatsAppAdaptation.DIRECT)
    public long getEncryptedMediaSize(
            String hostname,
            MediaPath mediaType,
            String directPath,
            String encFileHash
    ) throws WhatsAppMediaException.Download {
        var response = sendHeadRequest(hostname, mediaType, directPath, encFileHash, "mmsGetEncryptedMediaSize");

        var contentLength = response.headers()
                .firstValueAsLong("Content-Length")
                .orElse(-1L);
        if (contentLength < 0) {
            throw new WhatsAppMediaException.Download("Unable to get content length");
        }

        return contentLength;
    }

    /**
     * Issues a single HTTP HEAD request and returns the validated
     * response.
     *
     * @apiNote
     * Shared helper for {@link #checkExistence} and
     * {@link #getEncryptedMediaSize}. Constructs the URL via
     * {@link #formatDownloadUrl} and validates the status code via
     * {@link #validateMmsResponse(int, String, String)}.
     *
     * @param hostname     the CDN hostname
     * @param mediaType    the media path type
     * @param directPath   the CDN direct path, or {@code null}
     * @param encFileHash  the base64-encoded encrypted file hash, or
     *                     {@code null}
     * @param functionName the caller name used as the network-error
     *                     prefix
     * @return the HTTP HEAD response
     * @throws WhatsAppMediaException.Download if no path is available,
     *         the server returns a non-OK status, or a network error
     *         occurs
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientMmsDownload",
            exports = {"mmsCheckExistence", "mmsGetEncryptedMediaSize"},
            adaptation = WhatsAppAdaptation.ADAPTED)
    private HttpResponse<?> sendHeadRequest(
            String hostname,
            MediaPath mediaType,
            String directPath,
            String encFileHash,
            String functionName
    ) throws WhatsAppMediaException.Download {
        var url = formatDownloadUrl(hostname, directPath, encFileHash, mediaType, null);

        try (var client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build()) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() != 200) {
                validateMmsResponse(response.statusCode(), url, null);
            }

            return response;
        } catch (IOException | InterruptedException exception) {
            throw new WhatsAppMediaException.Download(functionName + ": network error", exception);
        }
    }

    /**
     * Asks WhatsApp's MMS service to release the encrypted history-sync
     * blob whose CDN coordinates were just consumed.
     *
     * @apiNote
     * Called by the history-sync handler immediately after a chunk has
     * been applied so the CDN does not retain the now-superfluous blob.
     * The {@code encFilehash} and {@code directPath} arguments come from
     * the {@link com.github.auties00.cobalt.model.message.system.history.HistorySyncNotification}
     * that triggered the apply; the {@code encHandle} and
     * {@code companionMmsAuthNonce} arguments are the server-issued
     * handle and the per-companion authentication nonce delivered with
     * the initial bootstrap. When {@code encHandle} is {@code null} or
     * {@code companionMmsAuthNonce} is {@code null} the
     * {@code Companion_User_Secret} header is omitted, matching WA
     * Web's guard in
     * {@code WAWebMmsClientMmsDeleteMdHistorySyncBlob}.
     *
     * @implNote
     * This implementation iterates over the candidate hosts via the
     * same {@link #selectHost} ladder that
     * {@link #download(MediaProvider)} uses, builds the URL through
     * {@link #formatHashUrl} (path segment {@code mms/md-msg-hist}, the
     * filename-position payload is the URL-safe base64 of
     * {@code encFilehash}), attaches the {@code token}, {@code d_md},
     * {@code auth}, and {@code e_handle} query parameters expected by
     * the server, and sends a single HTTP {@code DELETE}. Retryable
     * failures (network errors, {@code 401}, {@code 5xx} apart from
     * {@code 507}) cycle to the next host with the same backoff schedule
     * as the upload path; non-retryable failures propagate immediately.
     * The retry policy mirrors WA Web's {@code q()} wrapper that wraps
     * {@code WAWebMmsClientMmsDeleteMdHistorySyncBlob}.
     *
     * @param directPath            the CDN direct path of the blob to delete
     * @param encFilehash           the raw bytes of the encrypted file
     *                              SHA-256
     * @param encHandle             the server-issued encryption handle,
     *                              or {@code null}
     * @param companionMmsAuthNonce the per-companion MMS authentication
     *                              nonce, or {@code null}
     * @throws WhatsAppMediaException if no host could service the
     *                                delete, the request fails with a
     *                                non-retryable HTTP error, or no
     *                                hosts are available
     * @throws NullPointerException   if {@code directPath} or
     *                                {@code encFilehash} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClient", exports = "deleteMdHistorySyncBlob",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebMmsClientMmsDeleteMdHistorySyncBlob", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void deleteHistorySyncBlob(
            String directPath,
            byte[] encFilehash,
            String encHandle,
            String companionMmsAuthNonce
    ) throws WhatsAppMediaException, InterruptedException {
        Objects.requireNonNull(directPath, "directPath cannot be null");
        Objects.requireNonNull(encFilehash, "encFilehash cannot be null");

        awaitInitialized();
        var currentAuth = this.auth;
        var currentHosts = this.hosts;
        var currentMaxBuckets = this.maxBuckets;
        var encFilehashBase64 = Base64.getEncoder().encodeToString(encFilehash);
        var mediaType = MediaPath.HISTORY_SYNC;
        var hostList = currentHosts instanceof List<? extends MediaHost> list ? list : List.copyOf(currentHosts);
        var route = MediaHost.routeSelection(
                Operation.UPLOAD,
                mediaType,
                hostList,
                encFilehashBase64,
                currentMaxBuckets > 0 ? currentMaxBuckets : null,
                false
        );

        String lastHostUsed = null;
        for (var attemptCount = 0; attemptCount < MAX_ATTEMPT_COUNT; attemptCount++) {
            var hostname = selectHost(
                    route.selectedHost().orElse(null),
                    route.fallbackHost().orElse(null),
                    lastHostUsed,
                    attemptCount,
                    false
            );
            if (hostname == null) {
                continue;
            }
            lastHostUsed = hostname;
        }

        throw new WhatsAppMediaException.Upload(
                "Cannot delete history sync blob: no hosts available");
    }

    /**
     * Strips the {@code "?<query>"} suffix from a CDN direct path.
     *
     * @apiNote
     * Helper for {@link #deleteHistorySyncBlob(String, byte[], String, String)}.
     * Mirrors WA Web's {@code a.split("?")[0]} on the raw direct path
     * before it is base64-encoded into the {@code d_md} parameter; the
     * server treats the {@code d_md} value as the canonical, query-free
     * direct-path identifier.
     *
     * @param url the direct path
     * @return the path with the query string removed
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientMmsDeleteMdHistorySyncBlob", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static String stripQueryString(String url) {
        var queryStart = url.indexOf('?');
        return queryStart < 0 ? url : url.substring(0, queryStart);
    }

    /**
     * Returns the authentication token presented to the CDN on every
     * upload and download request.
     *
     * @return the authentication token
     * @throws IllegalStateException if the first {@link #update(Node)}
     *         has not yet landed
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    public String auth() {
        requireInitialized();
        return auth;
    }

    /**
     * Returns the routes time-to-live in seconds.
     *
     * @return the TTL in seconds
     * @throws IllegalStateException if the first {@link #update(Node)}
     *         has not yet landed
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob", exports = "queryMediaConn",
            adaptation = WhatsAppAdaptation.DIRECT)
    public int ttl() {
        requireInitialized();
        return ttl;
    }

    /**
     * Returns the authentication token time-to-live in seconds.
     *
     * @return the auth TTL in seconds
     * @throws IllegalStateException if the first {@link #update(Node)}
     *         has not yet landed
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob", exports = "queryMediaConn",
            adaptation = WhatsAppAdaptation.DIRECT)
    public int authTtl() {
        requireInitialized();
        return authTtl;
    }

    /**
     * Returns the maximum number of deterministic download buckets.
     *
     * @return the maximum bucket count
     * @throws IllegalStateException if the first {@link #update(Node)}
     *         has not yet landed
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    public int maxBuckets() {
        requireInitialized();
        return maxBuckets;
    }

    /**
     * Returns the server-advertised budget for manual media download
     * retries.
     *
     * @return the maximum manual retry count
     * @throws IllegalStateException if the first {@link #update(Node)}
     *         has not yet landed
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    public int maxManualRetry() {
        requireInitialized();
        return maxManualRetry;
    }

    /**
     * Returns the server-advertised budget for automatic media download
     * retries.
     *
     * @return the maximum auto-download retry count
     * @throws IllegalStateException if the first {@link #update(Node)}
     *         has not yet landed
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    public int maxAutoDownloadRetry() {
        requireInitialized();
        return maxAutoDownloadRetry;
    }

    /**
     * Returns the epoch-second timestamp at which the current credentials
     * were parsed.
     *
     * @return the creation timestamp in epoch seconds
     * @throws IllegalStateException if the first {@link #update(Node)}
     *         has not yet landed
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob", exports = "queryMediaConn",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public long timestamp() {
        requireInitialized();
        return timestamp;
    }

    /**
     * Returns the list of CDN host entries available for uploads and
     * downloads.
     *
     * @return an unmodifiable collection of hosts
     * @throws IllegalStateException if the first {@link #update(Node)}
     *         has not yet landed
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob", exports = "mapParsedMediaConn",
            adaptation = WhatsAppAdaptation.DIRECT)
    public SequencedCollection<? extends MediaHost> hosts() {
        requireInitialized();
        return hosts;
    }

    /**
     * Tests whether the current authentication token has expired.
     *
     * @apiNote
     * Returns {@code true} when the service has never been updated, or
     * when the current clock is at or past {@code timestamp + authTtl}.
     * Callers that observe {@code true} must request a fresh
     * {@code media_conn} via {@link #update(Node)} before issuing new
     * CDN requests.
     *
     * @return {@code true} if no credentials are published or the auth
     *         token has expired
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaHosts", exports = "mediaHosts",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean isExpired() {
        if (initialized.getCount() != 0L) {
            return true;
        }
        return Instant.now().getEpochSecond() >= timestamp + authTtl;
    }

    /**
     * Tests whether the current credentials should be proactively
     * refreshed.
     *
     * @apiNote
     * Returns {@code true} when the service has never been updated, or
     * when either the routes TTL has elapsed, or 80% of the
     * authentication TTL has elapsed, whichever happens first.
     *
     * @return {@code true} if no credentials are published or refresh
     *         is due
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaHosts", exports = "mediaHosts",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean needsRefresh() {
        if (initialized.getCount() != 0L) {
            return true;
        }
        var now = Instant.now().getEpochSecond();
        if (now >= timestamp + ttl) {
            return true;
        }
        var authRefreshThreshold = (long) Math.floor(authTtl * 0.8);
        return now >= timestamp + authRefreshThreshold;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * Prints either the placeholder {@code DefaultMediaConnectionService[uninitialized]}
     * when no {@link #update(Node)} has landed, or every field of the
     * current credentials. Do not log the result in production builds:
     * the {@code auth} token is included.
     */
    @Override
    public String toString() {
        if (initialized.getCount() != 0L) {
            return "DefaultMediaConnectionService[uninitialized]";
        }
        return "DefaultMediaConnectionService[" +
               "auth=" + auth + ", " +
               "ttl=" + ttl + ", " +
               "authTtl=" + authTtl + ", " +
               "maxBuckets=" + maxBuckets + ", " +
               "maxManualRetry=" + maxManualRetry + ", " +
               "maxAutoDownloadRetry=" + maxAutoDownloadRetry + ", " +
               "timestamp=" + timestamp + ", " +
               "hosts=" + hosts + ']';
    }
}
