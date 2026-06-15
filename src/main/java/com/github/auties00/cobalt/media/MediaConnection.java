package com.github.auties00.cobalt.media;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.exception.MediaDownloadException;
import com.github.auties00.cobalt.exception.MediaException;
import com.github.auties00.cobalt.exception.MediaUploadException;
import com.github.auties00.cobalt.model.media.MediaProvider;
import com.github.auties00.cobalt.util.Clock;

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
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedCollection;

public final class MediaConnection {
    private static final System.Logger LOGGER = System.getLogger(MediaConnection.class.getName());

    private final String auth;
    private final int ttl;
    private final int maxBuckets;
    private final long timestamp;
    private final SequencedCollection<? extends MediaHost> hosts;

    public MediaConnection(String auth, int ttl, int maxBuckets, long timestamp, SequencedCollection<? extends MediaHost> hosts) {
        this.auth = auth;
        this.ttl = ttl;
        this.maxBuckets = maxBuckets;
        this.timestamp = timestamp;
        this.hosts = hosts;
    }

    public boolean upload(MediaProvider provider, InputStream inputStream) throws MediaException {
        Objects.requireNonNull(provider, "provider cannot be null");
        Objects.requireNonNull(inputStream, "inputStream cannot be null");

        var path = provider.mediaPath()
                .path();
        LOGGER.log(System.Logger.Level.INFO, "[media_upload] phase=start provider={0} mediaPath={1} hostCount={2}", provider.getClass().getSimpleName(), path.orElse("unavailable"), hosts.size());
        if (path.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING, "[media_upload] phase=skip provider={0} reason=missing-media-path", provider.getClass().getSimpleName());
            return false;
        }

        try(var client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build()) {
            var uploadStream = MediaUploadInputStream.of(provider, inputStream);
            var tempFile = Files.createTempFile("upload", ".tmp");
            try (uploadStream; var outputStream = Files.newOutputStream(tempFile)) {
                uploadStream.transferTo(outputStream);
            }
            var timestamp = Clock.nowSeconds();
            var fileSha256 = uploadStream.fileSha256();
            var fileEncSha256 = uploadStream.fileEncSha256()
                    .orElse(null);
            var mediaKey = uploadStream.fileKey()
                    .orElse(null);
            var fileLength = uploadStream.fileLength();
            LOGGER.log(System.Logger.Level.INFO, "[media_upload] phase=buffered provider={0} mediaPath={1} fileBytes={2}", provider.getClass().getSimpleName(), path.get(), fileLength);

            var hostIndex = 0;
            for (var host : hosts) {
                hostIndex++;
                if(!host.canUpload(provider)) {
                    LOGGER.log(System.Logger.Level.INFO, "[media_upload] phase=host-skip host={0} hostIndex={1} reason=unsupported-path", host.hostname(), hostIndex);
                    continue;
                }

                LOGGER.log(System.Logger.Level.INFO, "[media_upload] phase=host-attempt host={0} hostIndex={1} mediaPath={2} fallbackAvailable={3}", host.hostname(), hostIndex, path.get(), host.fallbackHostname().isPresent());
                var uploadResult = tryUpload(client, host.hostname(), path.get(), fileEncSha256, fileSha256, tempFile, false)
                        .or(() -> host.fallbackHostname().flatMap(fallbackHostname -> tryUpload(client, fallbackHostname, path.get(), fileEncSha256, fileSha256, tempFile, true)));
                if(uploadResult.isPresent()) {
                    var directPath = uploadResult.get()
                            .getString("direct_path");
                    var url = uploadResult.get()
                            .getString("url");
                    // var handle = jsonObject.getString("handle");

                    provider.setMediaSha256(fileSha256);
                    provider.setMediaEncryptedSha256(fileEncSha256);
                    provider.setMediaKey(mediaKey);
                    provider.setMediaSize(fileLength);
                    provider.setMediaDirectPath(directPath);
                    provider.setMediaUrl(url);
                    provider.setMediaKeyTimestamp(timestamp);
                    LOGGER.log(System.Logger.Level.INFO, "[media_upload] phase=complete provider={0} host={1} mediaPath={2} fileBytes={3}", provider.getClass().getSimpleName(), host.hostname(), path.get(), fileLength);

                    return true;
                }
            }

            LOGGER.log(System.Logger.Level.ERROR, "[media_upload] phase=throw provider={0} mediaPath={1} reason=no-host-succeeded", provider.getClass().getSimpleName(), path.get());
            throw new MediaUploadException("Cannot upload media: no hosts available");
        }catch (IOException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "[media_upload] phase=throw provider={0} mediaPath={1} reason=io-failure error={2}", provider.getClass().getSimpleName(), path.orElse("unavailable"), exception.getMessage());
            throw new MediaUploadException("Cannot upload media", exception);
        }
    }

    private Optional<JSONObject> tryUpload(HttpClient client, String hostname, String path, byte[] fileEncSha256, byte[] fileSha256, Path body, boolean fallback) {
        try {
            var auth = URLEncoder.encode(this.auth, StandardCharsets.UTF_8);
            var token = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(Objects.requireNonNullElse(fileEncSha256, fileSha256));
            var uri = URI.create("https://%s/%s/%s?auth=%s&token=%s".formatted(hostname, path, token, auth, token));
            LOGGER.log(System.Logger.Level.INFO, "[media_upload] phase=http-start host={0} mediaPath={1} fallback={2}", hostname, path, fallback);
            var requestBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .POST(HttpRequest.BodyPublishers.ofFile(body));
            var request = requestBuilder.header("Content-Type", "application/octet-stream")
                    .header("Accept", "application/json")
                    .headers("Origin", "https://web.whatsapp.com")
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            LOGGER.log(System.Logger.Level.INFO, "[media_upload] phase=http-response host={0} mediaPath={1} fallback={2} status={3}", hostname, path, fallback, response.statusCode());
            if (response.statusCode() != 200) {
                throw new MediaUploadException("Cannot upload media: status code " + response.statusCode());
            }

            var jsonObject = JSON.parseObject(response.body());
            return Optional.ofNullable(jsonObject);
        }catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING, "[media_upload] phase=http-failure host={0} mediaPath={1} fallback={2} errorType={3} error={4}", hostname, path, fallback, throwable.getClass().getSimpleName(), throwable.getMessage());
            return Optional.empty();
        }
    }

    public InputStream download(MediaProvider provider) throws MediaException {
        Objects.requireNonNull(provider, "provider cannot be null");

        var defaultUploadUrl = provider.mediaUrl();
        if(defaultUploadUrl.isPresent()) {
            var result = tryDownload(provider, defaultUploadUrl.get());
            if(result.isPresent()) {
                return result.get();
            }
        }

        var defaultDirectPath = provider.mediaDirectPath()
                .orElseThrow(() -> new MediaDownloadException("Missing direct path from media"));
        for(var host : hosts) {
            if(!host.canDownload(provider)) {
                continue;
            }

            var uploadUrl = "https://" + host.hostname() + defaultDirectPath;
            var result = tryDownload(provider, uploadUrl);
            if(result.isPresent()) {
                return result.get();
            }
        }

        throw new MediaDownloadException("Cannot download media: no hosts available");
    }

    public Optional<InputStream> tryDownload(MediaProvider provider, String uploadUrl) throws MediaException {
        var client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        var request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new MediaDownloadException("Cannot download media: status code " + response.statusCode());
            }

            var payloadLength = response.headers()
                    .firstValueAsLong("Content-Length")
                    .orElseThrow(() -> new MediaDownloadException("Unknown content length"));

            var rawInputStream = response.body();
            return Optional.of(new MediaDownloadInputStream(client, rawInputStream, payloadLength, provider));
        } catch (Throwable throwable) {
            client.close();
            return Optional.empty();
        }
    }

    public String auth() {
        return auth;
    }

    public int ttl() {
        return ttl;
    }

    public int maxBuckets() {
        return maxBuckets;
    }

    public long timestamp() {
        return timestamp;
    }

    public SequencedCollection<? extends MediaHost> hosts() {
        return hosts;
    }

    @Override
    public String toString() {
        return "MediaConnection[" +
               "auth=" + auth + ", " +
               "ttl=" + ttl + ", " +
               "maxBuckets=" + maxBuckets + ", " +
               "timestamp=" + timestamp + ", " +
               "hosts=" + hosts + ']';
    }
}
