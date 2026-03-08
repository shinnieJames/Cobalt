package com.github.auties00.cobalt.stream.iq;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.util.FastRandomUtils;

import java.util.ArrayDeque;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class IqStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER = System.getLogger(IqStreamHandler.class.getName());
    private static final long QR_ROTATION_MS = 60_000L;
    private static final long REFRESH_ROTATION_MS = 20_000L;

    private final WhatsAppClient whatsapp;
    private final WhatsAppClientVerificationHandler.Web webVerificationHandler;
    private final DeviceService deviceService;
    private final ScheduledExecutorService rotationExecutor;
    private final Object rotationLock;
    private ScheduledFuture<?> rotationTask;

    public IqStreamHandler(
            WhatsAppClient whatsapp,
            WhatsAppClientVerificationHandler.Web webVerificationHandler,
            DeviceService deviceService
    ) {
        this.whatsapp = whatsapp;
        this.webVerificationHandler = Objects.requireNonNull(webVerificationHandler, "webVerificationHandler cannot be null");
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService cannot be null");
        this.rotationLock = new Object();
        this.rotationExecutor = Executors.newSingleThreadScheduledExecutor(runnable ->
                Thread.ofPlatform()
                        .daemon()
                        .name("CobaltPairDeviceRotation")
                        .unstarted(runnable)
        );
    }

    @Override
    public void handle(Node node) {
        var xmlns = node.getAttributeAsString("xmlns", null);
        if ("urn:xmpp:ping".equals(xmlns)) {
            handlePing(node);
            return;
        }

        if (!"md".equals(xmlns)) {
            return;
        }

        var child = node.getChild().orElse(null);
        if (child == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring md iq without child: {0}", node);
            return;
        }

        switch (child.description()) {
            case "pair-device" -> handlePairDevice(child);
            case "pair-success" -> handlePairSuccess(child);
            default -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unsupported md iq child {0}", child.description());
        }
    }

    private void handlePing(Node node) {
        var from = node.getAttributeAsJid("from").orElse(null);
        if (from == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring ping iq without from attribute");
            return;
        }

        var response = new NodeBuilder()
                .description("iq")
                .attribute("type", "result")
                .attribute("to", from)
                .attribute("id", node.getAttributeAsString("id", null))
                .build();
        whatsapp.sendNodeWithNoResponse(response);
    }

    private void handlePairDevice(Node pairDevice) {
        whatsapp.store().setAdvSecretKey(FastRandomUtils.randomByteArray(32));

        var refs = extractPairRefs(pairDevice);
        if (refs.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING, "Received pair-device iq without any usable refs");
            return;
        }

        scheduleVerificationValues(refs);
    }

    private LinkedHashSet<String> extractPairRefs(Node pairDevice) {
        var refs = new LinkedHashSet<String>();
        decodeContentAsString(pairDevice).ifPresent(refs::add);

        for (var child : pairDevice.children()) {
            decodeContentAsString(child).ifPresent(refs::add);
            findStringAttribute(child, "ref", "value", "code")
                    .ifPresent(refs::add);
        }

        findStringAttribute(pairDevice, "ref", "value", "code")
                .ifPresent(refs::add);
        refs.removeIf(String::isBlank);
        return refs;
    }

    private void scheduleVerificationValues(LinkedHashSet<String> refs) {
        synchronized (rotationLock) {
            cancelRotationLocked();

            var queue = new ArrayDeque<>(refs);
            var rotationDelay = refs.size() == 6 ? QR_ROTATION_MS : REFRESH_ROTATION_MS;

            publishVerificationValue(queue.pollFirst());
            if (queue.isEmpty()) {
                return;
            }

            rotationTask = rotationExecutor.scheduleAtFixedRate(() -> {
                String next;
                synchronized (rotationLock) {
                    next = queue.pollFirst();
                    if (next == null) {
                        cancelRotationLocked();
                        return;
                    }
                }

                publishVerificationValue(next);

                synchronized (rotationLock) {
                    if (queue.isEmpty()) {
                        cancelRotationLocked();
                    }
                }
            }, rotationDelay, rotationDelay, TimeUnit.MILLISECONDS);
        }
    }

    private void publishVerificationValue(String ref) {
        if (ref == null || ref.isBlank()) {
            return;
        }

        var payload = webVerificationHandler instanceof WhatsAppClientVerificationHandler.Web.QrCode
                ? buildQrPayload(ref)
                : ref;
        webVerificationHandler.handle(payload);
    }

    private String buildQrPayload(String ref) {
        var store = whatsapp.store();
        var advSecret = store.advSecretKey().orElseGet(() -> {
            var generated = FastRandomUtils.randomByteArray(32);
            store.setAdvSecretKey(generated);
            return generated;
        });

        var encoder = Base64.getEncoder();
        var noise = encoder.encodeToString(store.noiseKeyPair().publicKey().toEncodedPoint());
        var identity = encoder.encodeToString(store.identityKeyPair().publicKey().toEncodedPoint());
        var secret = encoder.encodeToString(advSecret);
        return String.join(",",
                ref,
                noise,
                identity,
                secret,
                whatsapp.store().device().clientType().name().toLowerCase());
    }

    private void handlePairSuccess(Node pairSuccess) {
        synchronized (rotationLock) {
            cancelRotationLocked();
        }

        var store = whatsapp.store();
        resolvePairedJid(pairSuccess, false).ifPresent(jid -> {
            store.setJid(jid);
            if (store.phoneNumber().isEmpty()) {
                try {
                    store.setPhoneNumber(Long.parseLong(jid.user()));
                } catch (NumberFormatException ignored) {
                }
            }
        });
        resolvePairedJid(pairSuccess, true).ifPresent(store::setLid);

        deviceService.extractAndValidateLocalSignedDeviceIdentity(pairSuccess)
                .ifPresent(identity -> {
                    try {
                        store.setSignedDeviceIdentity((com.github.auties00.cobalt.model.device.identity.ADVSignedDeviceIdentity) (Object) identity);
                    } catch (ClassCastException ignored) {
                        LOGGER.log(System.Logger.Level.DEBUG,
                                "Validated pair-success device identity could not be persisted due to type mismatch");
                    }
                });

        store.setRegistered(true);
        store.setOnline(true);
        safeSave("pair-success");
    }

    private Optional<Jid> resolvePairedJid(Node node, boolean lid) {
        var attrNames = lid
                ? new String[]{"lid", "device_lid", "user_lid"}
                : new String[]{"jid", "device_jid", "user_jid"};

        var direct = findJidAttribute(node, attrNames).orElse(null);
        if (direct != null) {
            return Optional.of(direct);
        }

        for (var child : node.children()) {
            var fromChild = findJidAttribute(child, attrNames).orElse(null);
            if (fromChild != null) {
                return Optional.of(fromChild);
            }

            var content = decodeContentAsString(child).orElse(null);
            if (content == null || content.isBlank()) {
                continue;
            }

            try {
                var parsed = Jid.of(content);
                var matches = lid
                        ? parsed.hasLidServer() || parsed.hasHostedLidServer()
                        : !parsed.hasLidServer() && !parsed.hasHostedLidServer();
                if (matches) {
                    return Optional.of(parsed);
                }
            } catch (RuntimeException ignored) {
            }
        }

        return Optional.empty();
    }

    private void cancelRotationLocked() {
        var task = rotationTask;
        if (task != null) {
            task.cancel(false);
            rotationTask = null;
        }
    }

    @Override
    public void reset() {
        synchronized (rotationLock) {
            cancelRotationLocked();
        }
    }

    private Optional<String> findStringAttribute(Node node, String... keys) {
        for (var key : keys) {
            var value = node.getAttributeAsString(key).orElse(null);
            if (value != null && !value.isBlank()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private Optional<Jid> findJidAttribute(Node node, String... keys) {
        for (var key : keys) {
            var value = node.getAttributeAsJid(key).orElse(null);
            if (value != null) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private Optional<String> decodeContentAsString(Node node) {
        var text = node.toContentString().orElse(null);
        if (text != null && !text.isBlank()) {
            return Optional.of(text);
        }

        var bytes = node.toContentBytes().orElse(null);
        if (bytes == null || bytes.length == 0) {
            return Optional.empty();
        }

        var decoded = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        return decoded.isBlank() ? Optional.empty() : Optional.of(decoded);
    }

    private void safeSave(String context) {
        try {
            whatsapp.store().save();
        } catch (Exception exception) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "{0}: failed to persist store: {1}",
                    context,
                    exception.getMessage());
        }
    }
}
