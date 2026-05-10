package com.github.auties00.cobalt.registration.push.apns;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.registration.push.apns.courier.ApnsBag;
import com.github.auties00.cobalt.registration.push.apns.courier.ApnsCourierCrypto;
import com.github.auties00.cobalt.registration.push.apns.courier.ApnsPacket;
import com.github.auties00.cobalt.registration.push.apns.courier.ApnsPayloadTag;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Owns the long-lived APNS courier TLS stream that the APNS client
 * keeps open after the {@link ApnsActivation} HTTP step completes.
 * Responsible for fetching the courier bag, picking a replica,
 * negotiating ALPN {@code apns-security-v3}, running the
 * {@code CONNECT}/{@code READY}/{@code STATE}/{@code FILTER}
 * handshake, pumping incoming frames, sending periodic keep-alives,
 * acking notifications, and multiplexing in-flight requests across
 * the same socket.
 *
 * <p>Two virtual threads are involved at steady state:
 * <ul>
 *   <li>the <em>read pump</em>, started after a successful TLS
 *       handshake, which decodes every inbound frame and routes it
 *       through {@link #dispatchPacket}.</li>
 *   <li>the <em>keep-alive thread</em>, started after the courier
 *       login succeeds, which writes a
 *       {@link ApnsPayloadTag#KEEP_ALIVE_SEND} every
 *       {@link #KEEP_ALIVE_INTERVAL_SECONDS} seconds.</li>
 * </ul>
 *
 * <p>Both writers go through {@link #writeLock} so frames cannot
 * interleave on the wire. Outbound request/response correlation
 * lives in {@link #pending}. Incoming
 * {@link ApnsPayloadTag#NOTIFICATION}s are auto-acked and any
 * embedded {@code regcode} is handed to {@link #pushCode}.
 */
final class ApnsCourierConnection {
    /**
     * Logger shared with the rest of the APNS client. Same logger
     * name {@code cobalt.apns} so consumers can configure verbosity
     * uniformly.
     */
    private static final Logger LOG = System.getLogger("cobalt.apns");

    /**
     * Endpoint for the bag, the JSON-in-plist directory listing
     * Apple uses to advertise the active courier replicas. HTTP, not
     * HTTPS. The file is unauthenticated.
     */
    private static final String BAG_URL = "http://init-p01st.push.apple.com/bag";

    /**
     * Standard HTTPS port the courier replicas listen on.
     */
    private static final int APNS_PORT = 443;

    /**
     * Keep-alive cadence. Matches the value the native {@code apsd}
     * uses to keep middlebox NAT entries alive on cellular networks.
     */
    private static final long KEEP_ALIVE_INTERVAL_SECONDS = 5L;

    /**
     * Wall-clock timeout applied to every {@link #exchange} caller.
     * Lower than the keep-alive jitter so a stalled request fails
     * before the next keep-alive masks the underlying problem.
     */
    private static final long REQUEST_TIMEOUT_MS = 30_000L;

    /**
     * Connect / request timeout applied to the bag HTTP call.
     */
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Trust-everything {@link X509TrustManager} used for the courier
     * socket. The APNS protocol authenticates the device with its
     * FairPlay-signed certificate in-band rather than via the TLS
     * PKI chain, and the courier endpoint is a moving target
     * ({@code 1-courier...} to {@code 50-courier...}), so we cannot pin
     * a single certificate. Skipping host PKI verification here is
     * the same trade-off Apple's own {@code apsd} makes. The protocol
     * is still authenticated by the application-layer ALPN string
     * {@code apns-security-v3} and the FairPlay nonce signature on
     * {@link ApnsPayloadTag#CONNECT}.
     */
    private static final TrustManager[] TRUST_ALL = {
            new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }
            }
    };

    // ─────────────────────────────────────────────────────────────
    // APNS field ids. Field ids are scoped to the enclosing
    // {@link ApnsPayloadTag}, so the same numeric value can mean
    // different things in different packets. See the references on
    // each constant.
    // ─────────────────────────────────────────────────────────────

    /**
     * In a {@link ApnsPayloadTag#READY}, carries the auth token the
     * courier has assigned to this connection.
     */
    private static final int FIELD_AUTH_TOKEN_READY = 0x03;

    /**
     * In a {@link ApnsPayloadTag#READY}, carries a single status byte
     * ({@code 0x00} for success).
     */
    private static final int FIELD_STATUS = 0x01;

    /**
     * In a {@link ApnsPayloadTag#NOTIFICATION}, carries the
     * server-assigned id the client must echo back in the matching
     * {@link ApnsPayloadTag#ACK}.
     */
    private static final int FIELD_NOTIFICATION_ID = 0x04;

    /**
     * In a {@link ApnsPayloadTag#NOTIFICATION}, carries the
     * application payload (typically a JSON object).
     */
    private static final int FIELD_PAYLOAD = 0x03;

    /**
     * In a {@link ApnsPayloadTag#TOKEN_RESPONSE}, carries the push
     * token bytes.
     */
    private static final int FIELD_TOKEN = 0x02;

    /**
     * In a {@link ApnsPayloadTag#ACK}, carries a single status byte.
     */
    private static final int FIELD_ACK_STATUS = 0x08;

    /**
     * In a {@link ApnsPayloadTag#GET_TOKEN}, carries the connection
     * auth token. Distinct from {@link #FIELD_AUTH_TOKEN_READY} which
     * lives at field id {@code 0x03} in the response.
     */
    private static final int FIELD_GET_TOKEN_AUTH = 0x01;

    /**
     * In a {@link ApnsPayloadTag#GET_TOKEN}, carries the SHA-1 hash
     * of the bundle id whose token we want.
     */
    private static final int FIELD_GET_TOKEN_TOPIC = 0x02;

    /**
     * In a {@link ApnsPayloadTag#GET_TOKEN}, a two-byte zero suffix
     * the apsd protocol expects.
     */
    private static final int FIELD_GET_TOKEN_PADDING = 0x03;

    /**
     * In a {@link ApnsPayloadTag#TOKEN_RESPONSE}, the topic hash
     * echoed back so callers can correlate against the original
     * request.
     */
    private static final int FIELD_TOKEN_RESPONSE_TOPIC = 0x03;

    /**
     * Session whose keypair, device certificate and topic list are
     * consulted during the courier handshake.
     */
    private final ApnsSession session;

    /**
     * Sink for verification codes extracted from incoming
     * {@link ApnsPayloadTag#NOTIFICATION} payloads.
     */
    private final ApnsPushCode pushCode;

    /**
     * HTTP client used for the single bag fetch. Built once with the
     * configured proxy and reused if {@link #start} is called more
     * than once across reconnects.
     */
    private final HttpClient http;

    /**
     * Source of randomness used to pick a courier replica index and
     * to seed the SSL context.
     */
    private final SecureRandom random;

    /**
     * Pending {@link #exchange} requests awaiting a matching response
     * frame. Keyed by sequential id so the read pump's iteration
     * order is deterministic. Map (rather than list) avoids snapshot
     * iteration when concurrent registrations happen.
     */
    private final Map<Long, Pending> pending;

    /**
     * Monotonically increasing id generator for {@link #pending}
     * keys. {@link AtomicLong} sidesteps the fragile
     * {@code threadHash << 32 | random} synthetic id used by the
     * pre-refactor code, which could collide under thread reuse.
     */
    private final AtomicLong nextPendingId;

    /**
     * Serialises every outbound write on the courier socket. Three
     * potential concurrent writers (read pump's ack, public API
     * threads, keep-alive thread) need this to keep frames atomic.
     */
    private final Object writeLock;

    /**
     * Currently-attached TLS socket, or {@code null} between
     * {@link #start} and the first successful handshake. Held
     * {@code volatile} so {@link #close} can read a fresh value from
     * any thread.
     */
    private volatile SSLSocket socket;

    /**
     * Cached output stream of {@link #socket}. Cached so the write
     * path does not hit the {@code SSLSocket.getOutputStream} guard
     * on every frame.
     */
    private volatile OutputStream socketOut;

    /**
     * Read pump virtual thread, started after the TLS handshake.
     * Held {@code volatile} so {@link #close} can interrupt it from
     * any thread.
     */
    private volatile Thread readPumpThread;

    /**
     * Keep-alive virtual thread, started after the courier
     * {@code READY} arrives. Held {@code volatile} for the same
     * reason as {@link #readPumpThread}.
     */
    private volatile Thread keepAliveThread;

    /**
     * Auth token assigned by the courier in the
     * {@link ApnsPayloadTag#READY} packet. Echoed back in every
     * subsequent {@link ApnsPayloadTag#FILTER} /
     * {@link ApnsPayloadTag#GET_TOKEN} / {@link ApnsPayloadTag#ACK}
     * so the courier can attribute the request to the right session.
     */
    private volatile byte[] authToken;

    /**
     * Stop flag flipped by {@link #close}. Read by the read pump and
     * the keep-alive loop so they can exit promptly.
     */
    private volatile boolean stopped;

    /**
     * Constructs a new courier connection bound to the given session
     * and push-code sink. Does not open a socket. The caller must
     * invoke {@link #start} after construction.
     *
     * @param session  the session that supplies the keypair, device
     *                 certificate and topic list
     * @param pushCode the holder where regcodes from incoming
     *                 notifications are delivered
     * @param proxy    proxy URI used for the bag HTTP fetch, or
     *                 {@code null} for direct
     */
    ApnsCourierConnection(ApnsSession session, ApnsPushCode pushCode, URI proxy) {
        this.session = session;
        this.pushCode = pushCode;
        this.http = newHttpClient(proxy);
        this.random = new SecureRandom();
        this.pending = new ConcurrentHashMap<>();
        this.nextPendingId = new AtomicLong();
        this.writeLock = new Object();
    }

    /**
     * Fetches the courier bag, opens the TLS socket, runs the
     * {@code CONNECT}/{@code READY}/{@code STATE}/{@code FILTER}
     * handshake, then spawns the read pump and the keep-alive
     * thread. Blocking until the handshake completes. Throws on any
     * step that fails.
     *
     * @throws IOException on any HTTP, TLS, or protocol failure
     */
    void start() throws IOException {
        LOG.log(Level.INFO, () -> "APNS bag -> " + BAG_URL);
        var bag = fetchBag();
        LOG.log(Level.INFO, () -> "APNS courier handshake -> " + bag.hostname());

        var courierIndex = 1 + random.nextInt(Math.max(1, bag.hostCount() - 1));
        var host = courierIndex + "-" + bag.hostname();
        try {
            var sslContext = SSLContext.getInstance("TLSv1.3");
            sslContext.init(null, TRUST_ALL, random);
            var sock = (SSLSocket) sslContext.getSocketFactory().createSocket(host, APNS_PORT);
            sock.setTcpNoDelay(true);
            sock.setKeepAlive(true);
            var params = sock.getSSLParameters();
            params.setApplicationProtocols(new String[]{"apns-security-v3"});
            sock.setSSLParameters(params);
            sock.startHandshake();
            this.socket = sock;
            this.socketOut = sock.getOutputStream();
        } catch (GeneralSecurityException e) {
            throw new IOException("APNS TLS setup failed", e);
        }

        startReadPump(socket.getInputStream());

        var keyPair = ApnsCourierCrypto.restoreKeyPair(session.publicKeyDer(), session.privateKeyDer());
        var nonce = ApnsCourierCrypto.createNonce(random);
        var signature = ApnsCourierCrypto.signNonce(keyPair, nonce);
        byte[] certDer;
        try {
            certDer = ApnsCourierCrypto.reencodeDeviceCertificate(session.deviceCertificate());
        } catch (Exception e) {
            throw new IOException("device certificate is invalid", e);
        }
        var ready = exchange(
                ApnsPayloadTag.CONNECT,
                Map.of(
                        0x02, new byte[]{0x01},
                        0x05, new byte[]{0x00, 0x00, 0x00, 0x41},
                        0x0C, certDer,
                        0x0D, nonce,
                        0x0E, signature),
                p -> p.tag() == ApnsPayloadTag.READY);

        var statusBytes = ready.fields().get(FIELD_STATUS);
        if (statusBytes == null || statusBytes.length == 0 || statusBytes[0] != 0) {
            throw new IOException("APNS courier rejected CONNECT: status="
                    + (statusBytes == null ? "null" : Byte.toUnsignedInt(statusBytes[0])));
        }
        var token = ready.fields().get(FIELD_AUTH_TOKEN_READY);
        if (token == null) {
            throw new IOException("APNS READY missing auth token");
        }
        this.authToken = token;
        LOG.log(Level.INFO, "APNS authenticated");

        send(ApnsPayloadTag.STATE, Map.of(
                0x01, new byte[]{0x01},
                0x02, new byte[]{0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}));
        sendFilter();
        startKeepAlive();
    }

    /**
     * Sends a {@link ApnsPayloadTag#GET_TOKEN} for {@code topic} and
     * blocks on the matching {@link ApnsPayloadTag#TOKEN_RESPONSE},
     * returning the hex-encoded push token bytes.
     *
     * @param topic the bundle id whose token to fetch
     * @return the hex-encoded push token
     * @throws IOException if the courier connection is not ready, the
     *                     send fails, the response times out, or the
     *                     response omits the token field
     */
    String requestToken(String topic) throws IOException {
        var topicHash = ApnsCourierCrypto.sha1(topic);
        var packet = exchange(
                ApnsPayloadTag.GET_TOKEN,
                Map.of(
                        FIELD_GET_TOKEN_AUTH, authToken,
                        FIELD_GET_TOKEN_TOPIC, topicHash,
                        FIELD_GET_TOKEN_PADDING, new byte[]{0x00, 0x00}),
                p -> p.tag() == ApnsPayloadTag.TOKEN_RESPONSE
                        && Arrays.equals(p.fields().get(FIELD_TOKEN_RESPONSE_TOPIC), topicHash));
        var bytes = packet.fields().get(FIELD_TOKEN);
        if (bytes == null) {
            throw new IOException("TOKEN_RESPONSE missing token field");
        }
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Tears down the read pump, the keep-alive thread, and the TLS
     * socket. Fails every still-pending request with an
     * {@link IOException}. Idempotent.
     */
    void close() {
        if (stopped) {
            return;
        }
        stopped = true;
        var s = socket;
        if (s != null) {
            try {
                s.close();
            } catch (IOException _) {
            }
        }
        var pump = readPumpThread;
        if (pump != null) {
            pump.interrupt();
        }
        var hb = keepAliveThread;
        if (hb != null) {
            hb.interrupt();
        }
        var disconnect = new IOException("APNS client closed");
        for (var p : pending.values()) {
            p.fail(disconnect);
        }
        pending.clear();
    }

    /**
     * Performs the bag GET and parses the resulting plist into the
     * structured {@link ApnsBag} record.
     *
     * @return the parsed bag
     * @throws IOException on transport failure or malformed plist
     */
    private ApnsBag fetchBag() throws IOException {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(BAG_URL))
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();
        return ApnsBag.ofPlist(sendBytes(request));
    }

    /**
     * Sends the topic-subscription {@link ApnsPayloadTag#FILTER}
     * packet so the courier delivers only pushes for the configured
     * bundle ids.
     *
     * @throws IOException if the write fails
     */
    private void sendFilter() throws IOException {
        var topics = session.config().topics();
        var hashes = new byte[topics.size()][];
        for (var i = 0; i < topics.size(); i++) {
            hashes[i] = ApnsCourierCrypto.sha1(topics.get(i));
        }
        send(ApnsPayloadTag.FILTER, Map.of(
                0x01, authToken,
                0x02, hashes));
    }

    /**
     * Spawns the read pump virtual thread.
     *
     * @param in the courier socket input stream
     */
    private void startReadPump(InputStream in) {
        readPumpThread = Thread.startVirtualThread(() -> runReadPump(in));
    }

    /**
     * Read pump loop. Decodes one frame at a time and dispatches it
     * via {@link #dispatchPacket}. On any I/O error fails every
     * still-pending request unless the connection is closing.
     *
     * @param in the courier socket input stream
     */
    private void runReadPump(InputStream in) {
        try {
            while (!stopped) {
                var packet = readPacket(in);
                dispatchPacket(packet);
            }
        } catch (IOException e) {
            if (!stopped) {
                LOG.log(Level.WARNING, "APNS read pump terminated", e);
                var disconnect = new IOException("APNS connection lost", e);
                for (var p : pending.values()) {
                    p.fail(disconnect);
                }
            }
        }
    }

    /**
     * Routes one decoded packet. Notifications are auto-acked and
     * any embedded {@code regcode} is delivered to {@link #pushCode}.
     * After that, the packet is offered to every pending request in
     * registration order. The first whose filter matches consumes it.
     *
     * @param packet the just-decoded packet
     */
    private void dispatchPacket(ApnsPacket packet) {
        if (packet.tag() == ApnsPayloadTag.NOTIFICATION) {
            sendAckSafe(packet.fields().get(FIELD_NOTIFICATION_ID));
            deliverPushCode(packet);
        }
        for (var entry : pending.entrySet()) {
            var p = entry.getValue();
            if (p.filter.test(packet)) {
                pending.remove(entry.getKey());
                p.deliver(packet);
                return;
            }
        }
    }

    /**
     * Extracts the {@code regcode} string from the JSON payload of a
     * {@link ApnsPayloadTag#NOTIFICATION} and hands it to
     * {@link #pushCode}. Quietly ignores notifications without a
     * payload, with a non-JSON payload, or without a {@code regcode}
     * field. Those are not registration codes and are still acked
     * for protocol correctness.
     *
     * @param packet the notification packet
     */
    private void deliverPushCode(ApnsPacket packet) {
        var payload = packet.fields().get(FIELD_PAYLOAD);
        if (payload == null) {
            return;
        }
        try {
            var json = JSON.parseObject(payload);
            if (json != null) {
                pushCode.deliver(json.getString("regcode"));
            }
        } catch (Exception e) {
            LOG.log(Level.DEBUG, "APNS notification payload not JSON or no regcode", e);
        }
    }

    /**
     * Sends an {@link ApnsPayloadTag#ACK} for the given notification
     * id. Logs and swallows any I/O failure so the read pump can
     * continue handling subsequent frames.
     *
     * @param notificationId the {@code FIELD_NOTIFICATION_ID} bytes
     *                       of the notification being acked, or
     *                       {@code null} when the source notification
     *                       did not carry one
     */
    private void sendAckSafe(byte[] notificationId) {
        if (notificationId == null) {
            return;
        }
        try {
            send(ApnsPayloadTag.ACK, Map.of(
                    0x01, authToken,
                    FIELD_NOTIFICATION_ID, notificationId,
                    FIELD_ACK_STATUS, new byte[]{0x00}));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "APNS ack failed", e);
        }
    }

    /**
     * Sends the {@code tag} / {@code fields} packet and blocks on
     * the first incoming packet whose {@code filter} matches.
     *
     * @param tag    the tag of the outbound packet
     * @param fields the TLV fields of the outbound packet (values
     *               are {@code byte[]} or {@code byte[][]})
     * @param filter the predicate used to identify the matching
     *               response
     * @return the matched response packet
     * @throws IOException on send failure, response timeout, or if
     *                     the connection is torn down before the
     *                     response arrives
     */
    private ApnsPacket exchange(ApnsPayloadTag tag, Map<Integer, ?> fields,
                                Predicate<ApnsPacket> filter) throws IOException {
        var pendingId = nextPendingId.getAndIncrement();
        var entry = new Pending(filter);
        this.pending.put(pendingId, entry);
        try {
            send(tag, fields);
            return entry.await(REQUEST_TIMEOUT_MS);
        } catch (IOException | RuntimeException e) {
            this.pending.remove(pendingId);
            throw e;
        }
    }

    /**
     * Spawns the keep-alive virtual thread that emits a
     * {@link ApnsPayloadTag#KEEP_ALIVE_SEND} every
     * {@link #KEEP_ALIVE_INTERVAL_SECONDS} seconds. Quietly returns
     * if the socket dies. The read pump will surface the underlying
     * error.
     */
    private void startKeepAlive() {
        keepAliveThread = Thread.startVirtualThread(() -> {
            while (!stopped) {
                try {
                    Thread.sleep(Duration.ofSeconds(KEEP_ALIVE_INTERVAL_SECONDS));
                    send(ApnsPayloadTag.KEEP_ALIVE_SEND, Map.of());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (IOException e) {
                    return;  // socket dead; pump will surface the error
                }
            }
        });
    }

    /**
     * Encodes and writes one APNS frame: tag byte, big-endian 4-byte
     * length, then the TLV-encoded payload.
     *
     * @param tag    the frame tag
     * @param fields the TLV fields (values are {@code byte[]} or
     *               {@code byte[][]})
     * @throws IOException if the socket is not connected or the
     *                     write fails
     */
    private void send(ApnsPayloadTag tag, Map<Integer, ?> fields) throws IOException {
        var out = socketOut;
        if (out == null) {
            throw new IOException("APNS socket not connected");
        }
        var payload = encodePayload(fields);
        var frame = new byte[1 + 4 + payload.length];
        frame[0] = (byte) tag.value();
        frame[1] = (byte) ((payload.length >>> 24) & 0xFF);
        frame[2] = (byte) ((payload.length >>> 16) & 0xFF);
        frame[3] = (byte) ((payload.length >>> 8) & 0xFF);
        frame[4] = (byte) (payload.length & 0xFF);
        System.arraycopy(payload, 0, frame, 5, payload.length);
        synchronized (writeLock) {
            out.write(frame);
            out.flush();
        }
    }

    /**
     * Encodes the TLV payload portion of an APNS frame. Each entry's
     * value must be either a single {@code byte[]} (one TLV record)
     * or a {@code byte[][]} (one record per element, all sharing the
     * same field id).
     *
     * @param fields the fields to encode
     * @return the encoded payload bytes
     * @throws IOException              if the underlying writer
     *                                  fails (impossible for an
     *                                  in-memory sink)
     * @throws IllegalArgumentException if any value is neither
     *                                  {@code byte[]} nor
     *                                  {@code byte[][]}
     */
    private static byte[] encodePayload(Map<Integer, ?> fields) throws IOException {
        var buf = new ByteArrayOutputStream();
        try (var dos = new DataOutputStream(buf)) {
            for (var entry : fields.entrySet()) {
                var value = entry.getValue();
                if (value == null) {
                    continue;
                }
                switch (value) {
                    case byte[] bytes -> {
                        dos.writeByte(entry.getKey().byteValue());
                        dos.writeShort(bytes.length);
                        dos.write(bytes);
                    }
                    case byte[][] groups -> {
                        dos.writeByte(entry.getKey().byteValue());
                        for (var g : groups) {
                            dos.writeShort(g.length);
                            dos.write(g);
                        }
                    }
                    default -> throw new IllegalArgumentException(
                            "unsupported APNS field type: " + value.getClass());
                }
            }
        }
        return buf.toByteArray();
    }

    /**
     * Reads one APNS frame: tag byte, big-endian 4-byte length, then
     * the TLV-encoded payload. Decodes the payload into a
     * {@code field-id -> bytes} map.
     *
     * @param in the courier socket input stream
     * @return the decoded packet
     * @throws IOException if the stream closes mid-frame, the length
     *                     is negative, or a field length exceeds the
     *                     remaining payload
     */
    private static ApnsPacket readPacket(InputStream in) throws IOException {
        var rawTag = readUnsignedByte(in);
        var length = readBigEndianInt(in);
        if (length < 0) {
            throw new IOException("APNS frame size negative: " + length);
        }
        var payload = new byte[length];
        var read = 0;
        while (read < length) {
            var n = in.read(payload, read, length - read);
            if (n < 0) {
                throw new IOException("APNS stream truncated");
            }
            read += n;
        }
        var fields = new LinkedHashMap<Integer, byte[]>();
        var bb = ByteBuffer.wrap(payload);
        while (bb.remaining() >= 3) {
            var fieldId = Byte.toUnsignedInt(bb.get());
            var fieldLen = Short.toUnsignedInt(bb.getShort());
            if (fieldLen > bb.remaining()) {
                throw new IOException("APNS field length exceeds frame");
            }
            var value = new byte[fieldLen];
            bb.get(value);
            fields.put(fieldId, value);
        }
        var tag = ApnsPayloadTag.of(rawTag);
        if (tag == null) {
            LOG.log(Level.DEBUG, "APNS unknown tag {0} ({1} B)", rawTag, length);
        }
        return new ApnsPacket(tag, fields);
    }

    /**
     * Reads exactly one unsigned byte from {@code in}, throwing if
     * the stream has been closed.
     *
     * @param in the source stream
     * @return the unsigned byte value in {@code [0, 255]}
     * @throws IOException if the stream is at EOF
     */
    private static int readUnsignedByte(InputStream in) throws IOException {
        var b = in.read();
        if (b < 0) {
            throw new IOException("APNS stream closed");
        }
        return b;
    }

    /**
     * Reads a 4-byte big-endian integer from {@code in}.
     *
     * @param in the source stream
     * @return the decoded {@code int}
     * @throws IOException if the stream closes before all four bytes
     *                     are read
     */
    private static int readBigEndianInt(InputStream in) throws IOException {
        var b1 = readUnsignedByte(in);
        var b2 = readUnsignedByte(in);
        var b3 = readUnsignedByte(in);
        var b4 = readUnsignedByte(in);
        return (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
    }

    /**
     * Sends {@code request} synchronously, returning the body bytes
     * on a {@code 2xx} status and rewriting non-2xx responses (and
     * interruptions) into {@link IOException}s.
     *
     * @param request the prepared HTTP request
     * @return the raw response body bytes
     * @throws IOException on any non-2xx status, transport failure,
     *                     or interruption during the call
     */
    private byte[] sendBytes(HttpRequest request) throws IOException {
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("APNS bag HTTP " + response.statusCode() + ": "
                        + new String(response.body(), StandardCharsets.UTF_8));
            }
            return response.body();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("APNS bag interrupted", ie);
        }
    }

    /**
     * Builds an {@link HttpClient} configured with
     * {@link #HTTP_TIMEOUT} and the optional caller-supplied proxy.
     * The default proxy port falls back to {@code 8080} when
     * {@code proxy.getPort()} returns {@code -1}.
     *
     * @param proxy proxy URI, or {@code null} for direct
     * @return a configured HTTP client
     */
    private static HttpClient newHttpClient(URI proxy) {
        var builder = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (proxy != null && proxy.getHost() != null) {
            var port = proxy.getPort() == -1 ? 8080 : proxy.getPort();
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxy.getHost(), port)));
        }
        return builder.build();
    }

    /**
     * One-shot synchronisation for an {@link #exchange} caller.
     * Uses an {@link ArrayBlockingQueue} of size {@code 1} so the
     * dispatcher's deliver/fail methods never block waiting for the
     * consumer to be ready, and a fast response that arrives between
     * {@code pending.put} and the consumer's {@code poll} is
     * buffered rather than lost.
     */
    private static final class Pending {
        /**
         * Predicate the read pump consults when routing each
         * incoming frame. The first pending entry whose filter
         * matches consumes the packet.
         */
        final Predicate<ApnsPacket> filter;

        /**
         * Single-slot mailbox. Holds either an {@link ApnsPacket} (on
         * delivery) or a {@link Throwable} (on failure / close). The
         * consumer disambiguates via {@code instanceof}.
         */
        final ArrayBlockingQueue<Object> slot = new ArrayBlockingQueue<>(1);

        /**
         * Constructs a pending entry waiting for a packet matching
         * {@code filter}.
         *
         * @param filter the response-matching predicate
         */
        Pending(Predicate<ApnsPacket> filter) {
            this.filter = filter;
        }

        /**
         * Delivers a successful response to the awaiting consumer.
         * Non-blocking. Subsequent calls are silently dropped.
         *
         * @param packet the matched response packet
         */
        void deliver(ApnsPacket packet) {
            slot.offer(packet);
        }

        /**
         * Delivers an error to the awaiting consumer so it surfaces
         * via {@link #await(long)} as an {@link IOException}.
         *
         * @param error the error to surface
         */
        void fail(Throwable error) {
            slot.offer(error);
        }

        /**
         * Blocks the calling thread until either a packet, an
         * error, or the timeout arrives.
         *
         * @param timeoutMs the wall-clock timeout in milliseconds
         * @return the delivered packet
         * @throws IOException if the timeout fires, the call is
         *                     interrupted, or {@link #fail} was
         *                     invoked
         */
        ApnsPacket await(long timeoutMs) throws IOException {
            try {
                var taken = slot.poll(timeoutMs, TimeUnit.MILLISECONDS);
                if (taken == null) {
                    throw new IOException(new TimeoutException("APNS request timed out"));
                }
                if (taken instanceof Throwable t) {
                    if (t instanceof IOException io) throw io;
                    throw new IOException(t);
                }
                return (ApnsPacket) taken;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("APNS request interrupted", ie);
            }
        }
    }
}
