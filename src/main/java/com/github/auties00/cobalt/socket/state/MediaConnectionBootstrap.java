package com.github.auties00.cobalt.socket.state;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.media.MediaConnection;
import com.github.auties00.cobalt.media.MediaHost;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.media.MediaPath;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.github.auties00.cobalt.client.WhatsAppClientErrorHandler.Location.MEDIA_CONNECTION;

final class MediaConnectionBootstrap {
    private static final int DEFAULT_MEDIA_CONNECTION_TTL = 300;
    private static final System.Logger LOGGER = System.getLogger(MediaConnectionBootstrap.class.getName());

    private final WhatsAppClient whatsapp;
    private final AtomicBoolean started;
    private final AtomicLong generation;

    MediaConnectionBootstrap(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
        this.started = new AtomicBoolean(false);
        this.generation = new AtomicLong(0);
    }

    void start() throws Exception {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        var generation = this.generation.get();
        try {
            var mediaConnection = fetchMediaConnection(generation);
            scheduleRefresh(generation, mediaConnection.ttl());
        } catch (Exception exception) {
            if (generation == this.generation.get()) {
                whatsapp.store().setMediaConnection(null);
                scheduleRefresh(generation, DEFAULT_MEDIA_CONNECTION_TTL);
            }
            throw exception;
        }
    }

    void reset() {
        generation.incrementAndGet();
        started.set(false);
    }

    private void refresh(long generation) {
        if (generation != this.generation.get()) {
            return;
        }

        try {
            var mediaConnection = fetchMediaConnection(generation);
            scheduleRefresh(generation, mediaConnection.ttl());
        } catch (Exception exception) {
            if (generation != this.generation.get()) {
                return;
            }

            LOGGER.log(System.Logger.Level.WARNING, "[media_conn] phase=failure errorType={0} error={1}", exception.getClass().getSimpleName(), exception.getMessage());
            whatsapp.store().setMediaConnection(null);
            whatsapp.handleFailure(MEDIA_CONNECTION, exception);
            scheduleRefresh(generation, DEFAULT_MEDIA_CONNECTION_TTL);
        }
    }

    private MediaConnection fetchMediaConnection(long generation) {
        if (generation != this.generation.get()) {
            throw new IllegalStateException("Stale media connection bootstrap generation");
        }

        LOGGER.log(System.Logger.Level.INFO, "[media_conn] phase=request-start");
        var queryRequestBody = new NodeBuilder()
                .description("media_conn")
                .build();
        var queryRequest = new NodeBuilder()
                .description("iq")
                .attribute("to", JidServer.user())
                .attribute("type", "set")
                .attribute("xmlns", "w:m")
                .content(queryRequestBody);
        var queryResponse = whatsapp.sendNode(queryRequest);
        var mediaConn = queryResponse.getChild("media_conn")
                .orElse(queryResponse);
        var auth = mediaConn.getRequiredAttributeAsString("auth");
        var ttl = Math.toIntExact(mediaConn.getRequiredAttributeAsLong("ttl"));
        var maxBuckets = Math.toIntExact(mediaConn.getRequiredAttributeAsLong("max_buckets"));
        var timestamp = System.currentTimeMillis();
        var hosts = mediaConn.streamChildren("host")
                .map(this::parseHost)
                .toList();
        LOGGER.log(System.Logger.Level.INFO, "[media_conn] phase=response-received ttl={0} maxBuckets={1} hostCount={2}", ttl, maxBuckets, hosts.size());
        var mediaConnection = new MediaConnection(auth, ttl, maxBuckets, timestamp, hosts);
        if (generation != this.generation.get()) {
            throw new IllegalStateException("Stale media connection bootstrap generation");
        }
        whatsapp.store().setMediaConnection(mediaConnection);
        LOGGER.log(System.Logger.Level.INFO, "[media_conn] phase=store-set ttl={0} hostCount={1}", ttl, hosts.size());
        return mediaConnection;
    }

    private void scheduleRefresh(long generation, int delaySeconds) {
        if (generation != this.generation.get()) {
            return;
        }

        LOGGER.log(System.Logger.Level.INFO, "[media_conn] phase=reschedule delaySeconds={0}", delaySeconds);
        var executor = CompletableFuture.delayedExecutor(delaySeconds, TimeUnit.SECONDS);
        executor.execute(() -> refresh(generation));
    }

    private MediaHost parseHost(Node host) {
        var type = host.getRequiredAttributeAsString("type");
        return switch (type) {
            case "primary" -> parsePrimaryHost(host);
            case "fallback" -> parseFallbackHost(host);
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    private MediaHost.Primary parsePrimaryHost(Node host) {
        var hostname = host.getRequiredAttributeAsString("hostname");
        var fallbackHostname = host.getAttributeAsString("fallback_hostname");
        var ip4 = host.getRequiredAttributeAsString("ip4");
        var fallbackIp4 = host.getRequiredAttributeAsString("fallback_ip4");
        var ip6 = host.getRequiredAttributeAsString("ip6");
        var fallbackIp6 = host.getRequiredAttributeAsString("fallback_ip6");
        var downloads = host.streamChild("download")
                .flatMap(Node::streamChildren)
                .flatMap(download -> MediaPath.ofId(download.description()).stream())
                .collect(Collectors.toUnmodifiableSet());
        var uploads = host.hasChild("upload") ? MediaPath.known() : Set.<MediaPath>of();
        return new MediaHost.Primary(hostname, fallbackHostname, ip4, fallbackIp4, ip6, fallbackIp6, downloads, uploads);
    }

    private MediaHost.Fallback parseFallbackHost(Node host) {
        var hostname = host.getRequiredAttributeAsString("hostname");
        return new MediaHost.Fallback(hostname);
    }
}
