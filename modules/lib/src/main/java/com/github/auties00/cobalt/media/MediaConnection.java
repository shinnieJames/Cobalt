package com.github.auties00.cobalt.media;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.exception.WhatsAppMediaException;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.media.ExternalBlobReference;
import com.github.auties00.cobalt.model.media.MediaPath;
import com.github.auties00.cobalt.model.media.MediaProvider;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.util.MediaMetricUtils;

import com.github.auties00.cobalt.media.MediaHost.Operation;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Parsed media connection credentials returned by WhatsApp's
 * {@code media_conn} query.
 *
 * <p>A media connection is the handshake the client must obtain before
 * uploading or downloading any encrypted attachment (images, videos,
 * audio, documents, stickers) through WhatsApp's CDN. It carries the
 * authentication token, the list of candidate CDN hosts with their
 * accepted media types and download buckets, the time-to-live values
 * for the credentials, and the retry budgets to apply when a host is
 * slow or refuses a request.
 *
 * <p>Typical lifecycle: send the IQ stanza produced by
 * {@link #queryNode()}, parse the reply with {@link #of(Node)}, then
 * call {@link #upload(MediaProvider, InputStream)} and
 * {@link #download(MediaProvider, ABPropsService)} to move bytes through
 * the CDN. Callers should consult {@link #isExpired()} and
 * {@link #needsRefresh()} to decide when the connection must be
 * re-queried.
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
@WhatsAppWebModule(moduleName = "WABase64UrlSafe")
@WhatsAppWebModule(moduleName = "WAWebWamMediaMetricUtils")
public final class MediaConnection {
    /**
     * The authentication token presented to the CDN on every upload
     * and download request.
     *
     * <p>Without a valid token the CDN refuses the request with HTTP
     * 401.
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final String auth;

    /**
     * The routes time-to-live in seconds.
     *
     * <p>After this interval the CDN host list returned by the server
     * may no longer be current and the caller should re-query a fresh
     * media connection.
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob", exports = "queryMediaConn",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final int ttl;

    /**
     * The authentication token time-to-live in seconds.
     *
     * <p>Once the auth TTL elapses the CDN no longer accepts requests
     * made with the stored token and the caller must refresh the
     * connection before issuing new ones.
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob", exports = "queryMediaConn",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final int authTtl;

    /**
     * The maximum number of deterministic download buckets advertised
     * by the server.
     *
     * <p>Drives the bucket-assignment arithmetic used by route
     * selection when vcache aggregation is enabled.
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob", exports = "mapParsedMediaConn",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final int maxBuckets;

    /**
     * The maximum number of retry attempts the UI should offer when
     * the user manually re-triggers a failed media download.
     *
     * <p>The server clamps the value to {@code [0, 4]} and Cobalt
     * defaults to {@code 3} when the attribute is absent.
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final int maxManualRetry;

    /**
     * The maximum number of retry attempts the client should perform
     * when transparently re-downloading a piece of media after a
     * failure.
     *
     * <p>The server clamps the value to {@code [0, 4]} and Cobalt
     * defaults to {@code 3} when the attribute is absent.
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final int maxAutoDownloadRetry;

    /**
     * The epoch-second timestamp at which this media connection was
     * parsed.
     *
     * <p>Combined with {@link #ttl} and {@link #authTtl} to compute
     * the absolute expiry deadlines used by {@link #isExpired()} and
     * {@link #needsRefresh()}.
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob", exports = "queryMediaConn",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private final long timestamp;

    /**
     * The ordered list of CDN host candidates for uploads and
     * downloads.
     *
     * <p>Each entry advertises a hostname, a set of supported media
     * types, and (for primary hosts) a nested fallback hostname.
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob", exports = "mapParsedMediaConn",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final SequencedCollection<? extends MediaHost> hosts;

    /**
     * Constructs a new media connection with the specified parsed
     * fields.
     *
     * @param auth                 the authentication token
     * @param ttl                  the routes TTL in seconds
     * @param authTtl              the auth token TTL in seconds
     * @param maxBuckets           the maximum bucket count
     * @param maxManualRetry       the maximum manual retry count
     * @param maxAutoDownloadRetry the maximum auto-download retry count
     * @param timestamp            the epoch-second creation timestamp
     * @param hosts                the list of CDN host entries
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob", exports = "queryMediaConn",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public MediaConnection(String auth, int ttl, int authTtl, int maxBuckets, int maxManualRetry, int maxAutoDownloadRetry, long timestamp, SequencedCollection<? extends MediaHost> hosts) {
        this.auth = auth;
        this.ttl = ttl;
        this.authTtl = authTtl;
        this.maxBuckets = maxBuckets;
        this.maxManualRetry = maxManualRetry;
        this.maxAutoDownloadRetry = maxAutoDownloadRetry;
        this.timestamp = timestamp;
        this.hosts = hosts;
    }

    /**
     * Builds the IQ stanza that asks the WhatsApp server for a fresh
     * media connection.
     *
     * <p>The returned builder produces a stanza of the form
     * {@code <iq to="s.whatsapp.net" xmlns="w:m" type="set"><media_conn/></iq>}.
     * The caller sends this stanza through the usual client IQ
     * pipeline and feeds the reply to {@link #of(Node)}.
     *
     * @return a node builder for the media connection query IQ
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob", exports = "queryMediaConn",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static NodeBuilder queryNode() {
        var mediaConnChild = new NodeBuilder()
                .description("media_conn");
        return new NodeBuilder()
                .description("iq")
                .attribute("to", JidServer.user())
                .attribute("xmlns", "w:m")
                .attribute("type", "set")
                .content(mediaConnChild.build());
    }

    /**
     * Parses a {@code media_conn} IQ reply into a usable media
     * connection.
     *
     * <p>Combines the responsibilities of three WhatsApp Web helpers:
     * raw attribute decoding, host and field projection, and TTL
     * normalisation (which subtracts the current time from the future
     * expiry timestamps).
     *
     * @param response the IQ response node
     * @return the parsed media connection
     * @throws NoSuchElementException if the response is missing the
     *         {@code media_conn} child or one of the mandatory
     *         attributes
     * @throws IllegalArgumentException if a mandatory integer attribute
     *         cannot be parsed as an {@code int}
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob",
            exports = {"mapParsedMediaConn", "queryMediaConn"},
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static MediaConnection of(Node response) {
        var mediaConn = response.getRequiredChild("media_conn");

        var auth = mediaConn.getRequiredAttributeAsString("auth");
        var ttl = mediaConn.getRequiredAttributeAsInt("ttl");
        var authTtl = mediaConn.getRequiredAttributeAsInt("auth_ttl");
        var maxBuckets = mediaConn.getRequiredAttributeAsInt("max_buckets");

        var maxManualRetry = clampOptionalInt(
                mediaConn.getAttributeAsInt("max_manual_retry", (Integer) null),
                0, 4, 3
        );

        var maxAutoDownloadRetry = clampOptionalInt(
                mediaConn.getAttributeAsInt("max_auto_download_retry", (Integer) null),
                0, 4, 3
        );

        var hosts = mediaConn.streamChildren("host")
                .map(MediaConnection::parseHost)
                .toList();

        var timestamp = Instant.now().getEpochSecond();

        return new MediaConnection(auth, ttl, authTtl, maxBuckets, maxManualRetry, maxAutoDownloadRetry, timestamp, hosts);
    }

    /**
     * Parses a single {@code host} child node into a {@link MediaHost}
     * record.
     *
     * <p>The host is classified as {@link MediaHost.Fallback} when its
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
     * <p>Each child tag is mapped to a {@link MediaPath} constant.
     * When the child node is absent the whitelist defaults to every
     * known server media type, then a small set of types that never
     * appear in CDN routing ({@code kyc-id}, the novi placeholders,
     * {@code thumbnail-gif}, and {@code xma-image}) is filtered out.
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
     * Clamps an optional integer to the given inclusive range,
     * returning a default when the input is {@code null} or out of
     * range.
     *
     * @param value        the parsed integer value, or {@code null}
     * @param min          the minimum allowed value
     * @param max          the maximum allowed value
     * @param defaultValue the default value when input is {@code null}
     *                     or out of range
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
     * <p>Combines the module-level {@code 4} constant from the host
     * selector with the {@code retries: 3} entry of the backoff
     * options: 3 retries plus the initial attempt give 4 total
     * attempts indexed from {@code 0} through {@code 3}.
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
     * <p>With {@code factor = 2} and jitter disabled the schedule is
     * deterministic: attempts {@code 1, 2, 3} are preceded by sleeps
     * of {@code 1000ms, 2000ms, 4000ms}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientMmsBackoffOptions", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final long MIN_BACKOFF_TIMEOUT_MILLIS = 1000L;

    /**
     * Sleeps the caller's virtual thread for the exponential-backoff
     * delay associated with the given retry attempt number.
     *
     * <p>The delay for attempt {@code n} (zero-based) is
     * {@code MIN_BACKOFF_TIMEOUT_MILLIS * 2^n}, producing the sequence
     * {@code 1000ms, 2000ms, 4000ms} before attempts {@code 1, 2, 3}.
     * No sleep is performed for attempt {@code 0}. If the sleep is
     * interrupted the thread's interrupt flag is restored and the
     * method returns immediately.
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
     * Picks the CDN hostname to use for the current retry attempt of
     * an upload or download.
     *
     * <p>The rotation strategy:
     * <ol>
     *   <li>If the previous attempt made progress, reuse the same
     *       host.</li>
     *   <li>On the first two attempts, stick with the initially
     *       selected host.</li>
     *   <li>On the last attempt, prefer the fallback-class host when
     *       one exists.</li>
     *   <li>If the previous attempt was the selected host and that
     *       host advertises a nested fallback hostname, rotate to it.</li>
     *   <li>Otherwise prefer the fallback host, falling back to the
     *       originally selected host.</li>
     * </ol>
     *
     * @param selectedHost          the host chosen by route selection,
     *                              or {@code null} if none matched
     * @param fallbackHost          the fallback-type host, or
     *                              {@code null} if none exists
     * @param lastHostUsed          the hostname used on the previous
     *                              attempt, or {@code null} on the
     *                              first attempt
     * @param attemptCount          the zero-based attempt number
     * @param lastFetchMadeProgress whether the previous attempt
     *                              transferred any data
     * @return the hostname to use, or {@code null} if no host is
     *         available
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
     * <p>Resolves the initial CDN host through route selection and
     * rotates across candidate hosts on each retry. On success the
     * provider's media metadata (plaintext and encrypted SHA-256
     * hashes, media key, direct path, URL, byte size, and key
     * timestamp) is written back through the provider setters so the
     * caller can build the outgoing message protobuf. When the
     * provider is an {@link ExternalBlobReference} the
     * server-returned handle is also stored.
     *
     * @param provider    the media provider describing the media type
     *                    and receiving the upload metadata
     * @param inputStream the input stream containing the media content
     * @return {@code true} if the upload succeeded
     * @throws WhatsAppMediaException.Upload if no host could service
     *         the upload, a non-retryable HTTP error occurred (413,
     *         415, 507), or an I/O error occurred
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClient", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean upload(MediaProvider provider, InputStream inputStream) throws WhatsAppMediaException {
        Objects.requireNonNull(provider, "provider cannot be null");
        Objects.requireNonNull(inputStream, "inputStream cannot be null");

        var path = provider.mediaPath()
                .path();
        if (path.isEmpty()) {
            return false;
        }

        try(var client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build()) {
            var uploadStream = MediaUploadInputStream.of(provider, inputStream);
            var tempFile = Files.createTempFile("upload", ".tmp");
            try {
                try (uploadStream; var outputStream = Files.newOutputStream(tempFile)) {
                    uploadStream.transferTo(outputStream);
                }
                var timestamp = Instant.now();
                var fileSha256 = uploadStream.fileSha256();
                var fileEncSha256 = uploadStream.fileEncSha256()
                        .orElse(null);
                var mediaKey = uploadStream.fileKey()
                        .orElse(null);
                var fileLength = uploadStream.fileLength();

                var hostList = hosts instanceof List<? extends MediaHost> list ? list : List.copyOf(hosts);
                var route = MediaHost.routeSelection(
                        Operation.UPLOAD,
                        provider.mediaPath(),
                        hostList,
                        fileEncSha256 != null ? Base64.getEncoder().encodeToString(fileEncSha256) : null,
                        maxBuckets > 0 ? maxBuckets : null,
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
                        var uploadResult = tryUpload(client, hostname, path.get(), fileEncSha256, fileSha256, tempFile);

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
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (WhatsAppMediaException.Upload uploadException) {
            throw uploadException;
        } catch (IOException exception) {
            throw new WhatsAppMediaException.Upload("Cannot upload media", exception);
        }
    }

    /**
     * Performs one HTTP POST upload of an encrypted media payload
     * against a single CDN host.
     *
     * <p>The target URL is assembled by
     * {@link #formatUploadUrl(String, String, String, long)}: the
     * URL-safe base64 of the encrypted file hash is appended as the
     * trailing path segment, and the {@code auth}, {@code token}, and
     * {@code media_id} query parameters are appended in order. The WA
     * Web export also supports resume, streaming, server-side
     * thumbnail generation, and newsletter-video transcoding query
     * parameters; Cobalt only performs single-shot uploads and does
     * not emit any of these.
     *
     * <p>On a non-200 response a {@link WhatsAppMediaException.Upload}
     * is raised with the HTTP status code preserved. The response JSON
     * must contain non-null, non-empty {@code direct_path} and
     * {@code url} fields or the upload is rejected.
     *
     * @param client        the HTTP client to use
     * @param hostname      the CDN hostname
     * @param path          the CDN path segment
     * @param fileEncSha256 the encrypted file SHA-256, or {@code null}
     * @param fileSha256    the plaintext file SHA-256
     * @param body          the path to the temporary file holding the
     *                      encrypted content
     * @return the parsed response JSON
     * @throws WhatsAppMediaException.Upload if the server returns a
     *         non-200 status code or the response is missing required
     *         fields
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientMmsUpload", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private JSONObject tryUpload(HttpClient client, String hostname, String path, byte[] fileEncSha256, byte[] fileSha256, Path body) throws WhatsAppMediaException.Upload {
        try {
            var token = Base64.getUrlEncoder()
                    .encodeToString(Objects.requireNonNullElse(fileEncSha256, fileSha256));

            var uri = URI.create(formatUploadUrl(hostname, path, token, generateMediaId()));
            var requestBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .POST(HttpRequest.BodyPublishers.ofFile(body));
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
        } catch (WhatsAppMediaException.Upload upload) {
            throw upload;
        } catch (IOException | InterruptedException exception) {
            // Wrap network-level failures without a status code so that
            // isRetryable can classify them as retryable via the
            // empty-status branch.
            throw new WhatsAppMediaException.Upload("mmsUpload: network error", exception);
        }
    }

    /**
     * Produces a random media event identifier for telemetry and
     * request correlation.
     *
     * <p>Returns a positive long in the range
     * {@code [1, Number.MAX_SAFE_INTEGER]} so server-side analytics
     * can de-duplicate events across retries.
     *
     * @return a random positive long suitable for use as a media id
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils", exports = "generateMediaEventId",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static long generateMediaId() {
        return MediaMetricUtils.generateMediaEventId();
    }

    /**
     * Builds the upload URL for an authenticated media POST against
     * the WhatsApp CDN.
     *
     * <p>The URL is assembled as
     * {@code https://{hostname}/{path}/{token}?auth=...&token=...&media_id=...}.
     * The {@code token} is the URL-safe base64 of the encrypted file
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
    String formatUploadUrl(String hostname, String path, String token, long mediaId) {
        var basePath = "https://" + hostname + "/" + path + "/" + token;

        var queryParams = new LinkedHashMap<String, String>();
        queryParams.put("auth", this.auth);
        queryParams.put("token", token);
        queryParams.put("media_id", String.valueOf(mediaId));

        return basePath + encodeQueryString(queryParams);
    }

    /**
     * Serialises a map of query parameters into a URL query string.
     *
     * <p>Mirrors the WA Web {@code URLSearchParams.toString()} idiom:
     * {@code null} values are skipped and every remaining key/value
     * pair is percent-encoded under
     * {@code application/x-www-form-urlencoded} rules.
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
     * <p>The rules:
     * <ul>
     *   <li>Missing status code (network error): retryable</li>
     *   <li>401 (unauthorized): retryable</li>
     *   <li>507 (throttled): not retryable</li>
     *   <li>Other 5xx: retryable</li>
     *   <li>Every other status (404, 408, 410, 413, 415, 403, etc.):
     *       not retryable</li>
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
     * <p>First tries the provider's cached static media URL, if any;
     * if that fails with a retryable error, resolves a fresh host
     * through route selection and rotates across candidate hosts on
     * each retry. Non-retryable errors (404, 408, 410, 507, and the
     * rest of 4xx) propagate immediately. The {@code abPropsService}
     * argument feeds the download URL formatter (cache-affinity hints
     * and the hash-URL deprecation flag).
     *
     * @param provider       the media provider
     * @param abPropsService the AB props service
     * @return an {@link InputStream} containing the downloaded media
     *         content
     * @throws WhatsAppMediaException.Download if no host could service
     *         the download, the direct path is missing, or a
     *         non-retryable HTTP error occurred
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClient", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public InputStream download(MediaProvider provider, ABPropsService abPropsService) throws WhatsAppMediaException {
        Objects.requireNonNull(provider, "provider cannot be null");
        Objects.requireNonNull(abPropsService, "abPropsService cannot be null");

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

        var hostList = hosts instanceof List<? extends MediaHost> list ? list : List.copyOf(hosts);
        var route = MediaHost.routeSelection(
                Operation.DOWNLOAD,
                mediaType,
                hostList,
                encFileHash,
                maxBuckets > 0 ? maxBuckets : null,
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

            // The bucket attaches only when the chosen host is the
            // selected one; fallback hosts never carry a bucket.
            var hostBucket = hostname.equals(selectedHostname) ? selectedBucket : null;

            var downloadUrl = formatDownloadUrl(
                    hostname, defaultDirectPath, encFileHash, mediaType, hostBucket, abPropsService
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
     * <p>When a direct path is available the URL is built via
     * {@link #buildDirectPathUrl}. When only the encrypted file hash
     * is available the URL is built via {@link #formatHashUrl}, unless
     * the {@code web_deprecate_mms4_hash_based_download} AB prop is
     * enabled in which case the call throws unconditionally regardless
     * of whether the hash is present.
     *
     * @param hostname       the CDN hostname
     * @param directPath     the direct path, or {@code null}
     * @param encFileHash    the base64-encoded encrypted file hash, or
     *                       {@code null}
     * @param mediaType      the media path type
     * @param downloadBucket the selected download bucket, or
     *                       {@code null}
     * @param abPropsService the AB props service
     * @return the constructed download URL
     * @throws WhatsAppMediaException.Download if neither direct path
     *         nor encrypted file hash is available, or the hash-URL
     *         fallback is deprecated by the AB prop
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientFormatDownloadUrl", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    String formatDownloadUrl(
            String hostname,
            String directPath,
            String encFileHash,
            MediaPath mediaType,
            Integer downloadBucket,
            ABPropsService abPropsService
    ) throws WhatsAppMediaException.Download {
        var mediaTypeId = mediaType.id().orElse(null);
        if (directPath != null && !directPath.isEmpty()) {
            var query = new LinkedHashMap<String, String>();
            query.put("mode", "auto");
            query.put("mms-type", mediaTypeId);
            query.put("__wa-mms", "");
            return buildDirectPathUrl(hostname, directPath, encFileHash, downloadBucket, mediaType, query, abPropsService);
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
     * <p>Appends the URL-safe base64 of the encrypted file hash as
     * {@code hash}, the download bucket as {@code _nc_cat}, and the
     * {@code _nc_map=whatsapp-nofna} cache-affinity hint when the
     * media type is listed in the {@code low_cache_hit_rate_media_types}
     * AB prop. A hostname security check rejects direct paths whose
     * embedded URL resolves to a different host.
     *
     * @param hostname       the expected CDN hostname
     * @param directPath     the direct path segment
     * @param encFileHash    the base64-encoded encrypted file hash, or
     *                       {@code null}
     * @param downloadBucket the download bucket number, or {@code null}
     * @param mediaType      the media path type
     * @param query          the additional query parameters to include
     * @param abPropsService the AB props service
     * @return the assembled download URL
     * @throws WhatsAppMediaException.Download if the direct path
     *         resolves to a different hostname than expected
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientFormatDownloadUrl", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static String buildDirectPathUrl(
            String hostname,
            String directPath,
            String encFileHash,
            Integer downloadBucket,
            MediaPath mediaType,
            Map<String, String> query,
            ABPropsService abPropsService
    ) throws WhatsAppMediaException.Download {
        var baseUri = URI.create("https://" + hostname).resolve(directPath);

        if (!baseUri.getHost().equals(hostname)) {
            throw new WhatsAppMediaException.Download("malicious directPath");
        }

        // java.net.URI does not expose URLSearchParams semantics, so the
        // existing query is split manually.
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
     * <p>Produces a URL of the form
     * {@code https://{hostname}/{path}/{urlSafeBase64(encFileHash)}?{query}}
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
     * Converts a standard base64 string to the URL-safe variant used
     * by WhatsApp CDN URLs.
     *
     * <p>Replaces {@code "/"} with {@code "_"} and {@code "+"} with
     * {@code "-"} while keeping the {@code "="} padding characters.
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
     * <p>Validates the HTTP status code and wraps the response body in
     * a {@link MediaDownloadInputStream} so the caller sees decrypted,
     * integrity-checked bytes. The returned stream owns the underlying
     * {@link HttpClient} and closes it when consumed or closed by the
     * caller.
     *
     * @param provider    the media provider holding the decryption
     *                    metadata
     * @param downloadUrl the full URL to download from
     * @return an {@link InputStream} containing the downloaded media
     *         content
     * @throws WhatsAppMediaException.Download if the server returns a
     *         non-200 status code, the {@code Content-Length} header
     *         is missing, or a network error occurs
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
                // Drain the response body so 403 with "URL signature expired"
                // can be reclassified as MediaNotFoundError.
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
        } catch (WhatsAppMediaException.Download downloadException) {
            throw downloadException;
        } catch (IOException | InterruptedException exception) {
            client.close();
            throw new WhatsAppMediaException.Download("mmsDownload: network error", exception);
        }
    }

    /**
     * Translates a non-OK HTTP status code from a media download into
     * the appropriate {@link WhatsAppMediaException.Download}.
     *
     * <p>The mapping:
     * <ul>
     *   <li>401: {@link WhatsAppMediaException#HTTP_UNAUTHORIZED}</li>
     *   <li>403: {@link WhatsAppMediaException#HTTP_NOT_FOUND} when
     *       the body contains {@code "URL signature expired"} or the
     *       URL's {@code oe} parameter encodes an expired signature;
     *       otherwise {@link WhatsAppMediaException#HTTP_FORBIDDEN}</li>
     *   <li>404 and 410: {@link WhatsAppMediaException#HTTP_NOT_FOUND}</li>
     *   <li>507: {@link WhatsAppMediaException#HTTP_THROTTLE}</li>
     *   <li>any other: the raw status code</li>
     * </ul>
     *
     * @param statusCode the HTTP status code
     * @param url        the download URL, used for expiry parsing
     * @param body       the response body for the GET path, or
     *                   {@code null} for HEAD requests
     * @throws WhatsAppMediaException.Download always, since this
     *         method is only called for non-OK responses
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

            // The "oe" query parameter encodes the URL signature expiry
            // as a hexadecimal unix timestamp.
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
                // Malformed URL or non-hex oe value falls through to the
                // generic forbidden classification below.
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
     * Tests whether a media download error is worth retrying against
     * a different host.
     *
     * <p>Applies the same rule set as
     * {@link #isRetryable(WhatsAppMediaException.Upload)} to
     * download-class exceptions: missing status code retryable, 401
     * retryable, 507 fatal, other 5xx retryable, every other status
     * fatal.
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
     * <p>The rules: 408 retryable, 507 fatal, every other status
     * retryable iff it is 5xx. More permissive than
     * {@link #isRetryable(WhatsAppMediaException.Upload)} because it
     * does not consult the exception subtype taxonomy. Preserved for
     * parity with the upstream module surface.
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
     * Probes the WhatsApp CDN to verify that a media file exists and
     * is still available for download.
     *
     * <p>Sends an HTTP HEAD request using the download URL format. A
     * 200 response signals availability; any other status is mapped to
     * the appropriate exception subtype.
     *
     * @param hostname       the CDN hostname
     * @param mediaType      the media path type
     * @param directPath     the CDN direct path, or {@code null}
     * @param encFileHash    the base64-encoded encrypted file hash, or
     *                       {@code null}
     * @param abPropsService the AB props service
     * @throws WhatsAppMediaException.Download if the media does not
     *         exist or a network error occurs
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientMmsDownload", exports = "mmsCheckExistence",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void checkExistence(
            String hostname,
            MediaPath mediaType,
            String directPath,
            String encFileHash,
            ABPropsService abPropsService
    ) throws WhatsAppMediaException.Download {
        sendHeadRequest(hostname, mediaType, directPath, encFileHash, "mmsCheckExistence", abPropsService);
    }

    /**
     * Retrieves the size in bytes of the encrypted media payload
     * stored on the WhatsApp CDN.
     *
     * <p>Sends an HTTP HEAD request and reads the
     * {@code Content-Length} header so the caller can pre-allocate
     * buffers or estimate bandwidth before invoking a full download.
     *
     * @param hostname       the CDN hostname
     * @param mediaType      the media path type
     * @param directPath     the CDN direct path, or {@code null}
     * @param encFileHash    the base64-encoded encrypted file hash, or
     *                       {@code null}
     * @param abPropsService the AB props service
     * @return the encrypted media file size in bytes
     * @throws WhatsAppMediaException.Download if the
     *         {@code Content-Length} header is missing, the server
     *         returns a non-OK status, or a network error occurs
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsClientMmsDownload", exports = "mmsGetEncryptedMediaSize",
            adaptation = WhatsAppAdaptation.DIRECT)
    public long getEncryptedMediaSize(
            String hostname,
            MediaPath mediaType,
            String directPath,
            String encFileHash,
            ABPropsService abPropsService
    ) throws WhatsAppMediaException.Download {
        var response = sendHeadRequest(hostname, mediaType, directPath, encFileHash, "mmsGetEncryptedMediaSize", abPropsService);

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
     * <p>Shared helper for {@link #checkExistence} and
     * {@link #getEncryptedMediaSize}. Constructs the URL via
     * {@link #formatDownloadUrl} and validates the status code via
     * {@link #validateMmsResponse(int, String, String)}.
     *
     * @param hostname       the CDN hostname
     * @param mediaType      the media path type
     * @param directPath     the CDN direct path, or {@code null}
     * @param encFileHash    the base64-encoded encrypted file hash, or
     *                       {@code null}
     * @param functionName   the caller name used as the network-error
     *                       prefix
     * @param abPropsService the AB props service
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
            String functionName,
            ABPropsService abPropsService
    ) throws WhatsAppMediaException.Download {
        var url = formatDownloadUrl(hostname, directPath, encFileHash, mediaType, null, abPropsService);

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
        } catch (WhatsAppMediaException.Download downloadException) {
            throw downloadException;
        } catch (IOException | InterruptedException exception) {
            throw new WhatsAppMediaException.Download(functionName + ": network error", exception);
        }
    }

    /**
     * Returns the authentication token presented to the CDN on every
     * upload and download request.
     *
     * @return the authentication token
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    public String auth() {
        return auth;
    }

    /**
     * Returns the routes time-to-live in seconds.
     *
     * @return the TTL in seconds
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob", exports = "queryMediaConn",
            adaptation = WhatsAppAdaptation.DIRECT)
    public int ttl() {
        return ttl;
    }

    /**
     * Returns the authentication token time-to-live in seconds.
     *
     * @return the auth TTL in seconds
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob", exports = "queryMediaConn",
            adaptation = WhatsAppAdaptation.DIRECT)
    public int authTtl() {
        return authTtl;
    }

    /**
     * Returns the maximum number of deterministic download buckets.
     *
     * @return the maximum bucket count
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    public int maxBuckets() {
        return maxBuckets;
    }

    /**
     * Returns the server-advertised budget for manual media download
     * retries.
     *
     * @return the maximum manual retry count
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    public int maxManualRetry() {
        return maxManualRetry;
    }

    /**
     * Returns the server-advertised budget for automatic media
     * download retries.
     *
     * @return the maximum auto-download retry count
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    public int maxAutoDownloadRetry() {
        return maxAutoDownloadRetry;
    }

    /**
     * Returns the epoch-second timestamp at which this media
     * connection was parsed.
     *
     * @return the creation timestamp in epoch seconds
     */
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob", exports = "queryMediaConn",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public long timestamp() {
        return timestamp;
    }

    /**
     * Returns the list of CDN host entries available for uploads and
     * downloads.
     *
     * @return an unmodifiable collection of hosts
     */
    @WhatsAppWebExport(moduleName = "WAMediaConnParser", exports = "mediaConnParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebQueryMediaConnsJob", exports = "mapParsedMediaConn",
            adaptation = WhatsAppAdaptation.DIRECT)
    public SequencedCollection<? extends MediaHost> hosts() {
        return hosts;
    }

    /**
     * Tests whether this media connection's authentication token has
     * expired.
     *
     * <p>Considered expired when the current clock is at or past
     * {@code timestamp + authTtl}. Callers that observe {@code true}
     * must request a fresh media connection before issuing new CDN
     * requests.
     *
     * @return {@code true} if the auth token has expired
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaHosts", exports = "mediaHosts",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean isExpired() {
        return Instant.now().getEpochSecond() >= timestamp + authTtl;
    }

    /**
     * Tests whether this media connection should be proactively
     * refreshed.
     *
     * <p>A refresh is needed when either the routes TTL has elapsed
     * or 80% of the authentication TTL has elapsed, whichever happens
     * first. This avoids serving requests with stale or
     * nearly-expired credentials.
     *
     * @return {@code true} if the media connection should be refreshed
     */
    @WhatsAppWebExport(moduleName = "WAWebMediaHosts", exports = "mediaHosts",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean needsRefresh() {
        var now = Instant.now().getEpochSecond();

        if (now >= timestamp + ttl) {
            return true;
        }

        var authRefreshThreshold = (long) Math.floor(authTtl * 0.8);
        return now >= timestamp + authRefreshThreshold;
    }

    /**
     * Returns a debug-friendly string representation of every field
     * of this media connection.
     *
     * @return a string representation of this object
     */
    @Override
    public String toString() {
        return "MediaConnection[" +
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
