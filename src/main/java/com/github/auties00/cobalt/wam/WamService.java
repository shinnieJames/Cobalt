package com.github.auties00.cobalt.wam;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientType;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.util.FastRandomUtils;
import com.github.auties00.cobalt.wam.binary.WamGlobalEncoder;
import com.github.auties00.cobalt.wam.event.WamEventSpec;
import com.github.auties00.cobalt.wam.type.WamChannel;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * A service that collects, batches, and uploads WhatsApp Metrics (WAM)
 * telemetry events over the three transport channels.
 *
 * <p>Events are committed via {@link #commit(WamEventSpec)} and held
 * in memory until the next periodic flush or an immediate flush for
 * {@link WamChannel#REALTIME} events. On flush, one or more byte
 * buffers are allocated for each channel with pending events, each
 * capped at {@link #MAX_BUFFER_SIZE} bytes.
 *
 * @see WamEventSpec
 * @see WamGlobalEncoder
 * @see WamChannel
 */
public final class WamService {
    private static final Logger LOGGER = Logger.getLogger(WamService.class.getName());

    private static final VarHandle SHORT_HANDLE =
            MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);

    /**
     * Size of the WAM buffer header in bytes:
     * {@code "WAM"(3) + version(1) + streamId(1) + seqNum(2 BE) + channel(1)}.
     */
    private static final int HEADER_SIZE = 8;

    private static final byte[] WAM_MAGIC = {'W', 'A', 'M'};
    private static final int PROTOCOL_VERSION = 5;
    private static final int STREAM_ID = 1;
    private static final int FLUSH_INTERVAL_SECONDS = 120;

    /**
     * Maximum size of a single WAM buffer in bytes before it is rotated
     * and a new buffer is started. Matches the JS constant
     * {@code WAM_MAX_BUFFER_SIZE}.
     */
    private static final int MAX_BUFFER_SIZE = 50_000;

    /**
     * Maximum size of a WAM buffer that may be uploaded. Buffers
     * exceeding this size are dropped. Matches the JS constant
     * {@code WAM_MAX_BUFFER_SIZE_FOR_UPLOAD}.
     */
    private static final int MAX_UPLOAD_SIZE = 64_000;

    /**
     * Maximum number of retry attempts for a failed buffer upload.
     */
    private static final int MAX_RETRIES = 2;

    /**
     * Base delay in milliseconds for exponential backoff between
     * upload retries.
     */
    private static final long RETRY_BASE_DELAY_MS = 1_000;

    /**
     * Maximum delay in milliseconds for exponential backoff between
     * upload retries.
     */
    private static final long RETRY_MAX_DELAY_MS = 120_000;

    /**
     * Maximum value for the uint16 sequence number before wrapping
     * back to 1.
     */
    private static final int MAX_SEQUENCE_NUMBER = 0xFFFF;

    /**
     * Device classification value for DESKTOP, matching the JS enum
     * {@code DEVICE_CLASSIFICATION.DESKTOP}.
     */
    private static final int DEVICE_CLASSIFICATION_DESKTOP = 4;

    /**
     * App build type value for RELEASE, matching the JS enum
     * {@code APP_BUILD_TYPE.RELEASE}.
     *
     * @apiNote WAWebWamEnumAppBuildType.APP_BUILD_TYPE: ALPHA, BETA,
     * RELEASE values.
     */
    private static final int APP_BUILD_RELEASE = 4;

    /**
     * Web client environment value for PROD, matching the JS enum
     * {@code WEBC_ENV_CODE.PROD}.
     */
    private static final int WEBC_ENV_PROD = 0;

    /**
     * Web platform value for WEB, matching the JS enum
     * {@code WEBC_WEB_PLATFORM_TYPE.WEB}.
     */
    private static final int PLATFORM_WEBCLIENT = 1;

    private final WhatsAppClient client;
    private final ABPropsService abPropsService;
    private final ConcurrentMap<WamChannel, List<WamPendingEvent>> pending;
    private final AtomicInteger sequenceNumber;
    private final WamBeaconing beaconing;
    private final WamPrivateStatsId privateStatsId;
    private final WamSamplingOverride samplingOverride;

    private volatile boolean initialized;
    private ScheduledExecutorService scheduler;
    private Map<Integer, Object> prevSessionGlobals;

    private long platform;
    private String appVersion;
    private String deviceName;
    private int memClass;
    private int numCpu;
    private String browser;
    private String browserVersion;
    private String osVersion;
    private String deviceVersion;
    private String webcTabId;
    private String abKey2;

    /**
     * Constructs a new {@code WamService} bound to the given client.
     *
     * <p>The service is not active until {@link #initialize()} is called
     * after the client has authenticated.
     *
     * @param client         the WhatsApp client instance, must not be
     *                       {@code null}
     * @param abPropsService the AB props service for reading the AB key
     *                       and feature flags, must not be {@code null}
     */
    public WamService(WhatsAppClient client, ABPropsService abPropsService) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService cannot be null");
        this.pending = new ConcurrentHashMap<>();
        this.sequenceNumber = new AtomicInteger(1);
        this.beaconing = new WamBeaconing();
        this.privateStatsId = new WamPrivateStatsId();
        this.samplingOverride = new WamSamplingOverride();
    }

    /**
     * Initializes the service by snapshotting session globals from the
     * client store and starting the periodic flush thread.
     *
     * <p>This method should be called once after the client has
     * authenticated and the store's JID and version are available. The
     * store is read exactly once here so that subsequent flushes never
     * race with store mutations.
     */
    public void initialize() {
        var store = client.store();
        var version = store.clientVersion();
        this.appVersion = version != null ? version.toString() : null;
        this.platform = store.clientType() == WhatsAppClientType.WEB ? 8L : 2L;
        this.deviceName = store.name();
        this.memClass = (int) (Runtime.getRuntime().maxMemory() / (1024 * 1024));
        this.numCpu = Runtime.getRuntime().availableProcessors();
        this.browser = "Cobalt";
        this.browserVersion = appVersion;
        this.osVersion = System.getProperty("os.name", "") + " " + System.getProperty("os.version", "");
        this.deviceVersion = osVersion;
        this.webcTabId = UUID.randomUUID().toString();
        this.abKey2 = abPropsService.getBool(ABProp.WAM_DISABLE_ABKEY_ATTRIBUTE)
                ? null
                : abPropsService.abKey().orElse("");
        this.initialized = true;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
        scheduler.scheduleWithFixedDelay(this::flush, FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Commits an event for later transmission.
     *
     * <p>The event's sampling weight is resolved by first checking for
     * a runtime override via {@link WamSamplingOverride}, then falling
     * back to the static {@link WamEventSpec#releaseWeight()}. If the
     * event is sampled out, it is silently discarded.
     *
     * <p>For {@link WamChannel#REALTIME} events, an immediate flush is
     * scheduled.
     *
     * @param event the event to commit, must not be {@code null}
     */
    public void commit(WamEventSpec event) {
        Objects.requireNonNull(event, "event cannot be null");
        var weight = effectiveWeight(event);
        if (weight > 1) {
            if (FastRandomUtils.randomInt(weight) != 0) {
                return;
            }
        }

        var pe = new WamPendingEvent(event, Instant.now().getEpochSecond());
        pending.compute(event.channel(), (_, list) -> {
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(pe);
            return list;
        });

        if (event.channel() == WamChannel.REALTIME) {
            Thread.ofVirtual().start(this::flush);
        }
    }

    /**
     * Registers a runtime sampling weight override for the given event
     * id. The override takes precedence over the static annotation
     * weight until removed.
     *
     * @param eventId the numeric WAM event identifier
     * @param weight  the overridden sampling weight (must be positive)
     */
    public void setSamplingOverride(int eventId, int weight) {
        samplingOverride.put(eventId, weight);
    }

    /**
     * Removes any runtime sampling weight override for the given event
     * id, reverting to the static annotation weight.
     *
     * @param eventId the numeric WAM event identifier
     */
    public void removeSamplingOverride(int eventId) {
        samplingOverride.remove(eventId);
    }

    /**
     * Replaces all current sampling overrides with the entries from the
     * given map.
     *
     * @param overrides a map from event id to sampling weight
     */
    public void replaceSamplingOverrides(Map<Integer, Integer> overrides) {
        samplingOverride.replaceAll(overrides);
    }

    /**
     * Flushes all pending events across all channels.
     *
     * <p>For each channel with pending events, one or more buffers are
     * built and sent — each capped at {@link #MAX_BUFFER_SIZE} bytes.
     * Buffers exceeding {@link #MAX_UPLOAD_SIZE} are dropped.
     *
     * <p>The pending list is atomically swapped to {@code null} so that
     * new events committed during the flush are not lost.
     */
    public void flush() {
        if (!initialized) {
            return;
        }

        for (var channel : WamChannel.values()) {
            flushChannel(channel);
        }
    }

    /**
     * Drains and uploads all pending events for the given channel.
     *
     * <p>Events are consumed in order and packed into buffers up to
     * {@link #MAX_BUFFER_SIZE}. Each full buffer is encoded and sent
     * before the next batch begins.
     *
     * <p>For the {@link WamChannel#PRIVATE} channel, PS IDs are rotated
     * before flushing.
     *
     * @param channel the channel to flush
     */
    private void flushChannel(WamChannel channel) {
        var events = swapPending(channel);
        if (events.isEmpty()) {
            return;
        }

        if (channel == WamChannel.PRIVATE) {
            privateStatsId.rotateAndGet();
        }

        var beacons = new OptionalInt[events.size()];
        var weights = new int[events.size()];
        for (var i = 0; i < events.size(); i++) {
            var bufferKey = switch (channel) {
                case REGULAR -> "regular";
                case REALTIME -> "realtime";
                case PRIVATE -> privateStatsId.getKeyNameForHash(events.get(i).event().privateStatsId());
            };
            beacons[i] = beaconing.nextSequenceNumber(bufferKey);
            weights[i] = effectiveWeight(events.get(i).event());
        }

        var batchStart = 0;
        var globalsSize = computeGlobalsSize(channel);
        var batchSize = HEADER_SIZE + globalsSize;

        for (var i = 0; i < events.size(); i++) {
            var pe = events.get(i);
            var eventSize = computePerEventGlobalsSize(pe, channel, beacons[i]) + pe.event().sizeOf(weights[i]);

            if (batchStart < i && batchSize + eventSize > MAX_BUFFER_SIZE) {
                buildAndSend(channel, events, weights, beacons, batchStart, i, batchSize);
                batchStart = i;
                globalsSize = computeGlobalsSize(channel);
                batchSize = HEADER_SIZE + globalsSize;
            }

            batchSize += eventSize;
        }

        if (batchStart < events.size()) {
            buildAndSend(channel, events, weights, beacons, batchStart, events.size(), batchSize);
        }
    }

    /**
     * Builds a single WAM buffer from a slice of events and sends it.
     *
     * @param channel the transport channel
     * @param events  the full event list
     * @param weights pre-computed sampling weights parallel to the events
     *                list, re-fetched at flush time
     * @param beacons pre-computed beacon sequence numbers parallel to
     *                the events list
     * @param from    the inclusive start index in the event list
     * @param to      the exclusive end index in the event list
     * @param size    the pre-computed total buffer size
     */
    private void buildAndSend(WamChannel channel, List<WamPendingEvent> events, int[] weights, OptionalInt[] beacons, int from, int to, int size) {
        if (size > MAX_UPLOAD_SIZE) {
            LOGGER.warning("Dropping WAM buffer of " + size + " bytes (exceeds upload limit)");
            return;
        }

        var buffer = new byte[size];
        var offset = writeHeader(buffer, channel);
        offset = writeGlobals(buffer, offset, channel);

        for (var i = from; i < to; i++) {
            var pe = events.get(i);
            offset = writePerEventGlobals(pe, channel, beacons[i], buffer, offset);
            offset = pe.event().encode(buffer, offset, weights[i]);
        }

        assert offset == size : "Buffer size mismatch: wrote " + offset + " but allocated " + size;

        if (channel == WamChannel.PRIVATE) {
            sendPrivateWithRetry(buffer);
        } else {
            sendWithRetry(buffer);
        }
    }

    /**
     * Returns the total byte count of the session globals section.
     *
     * <p>All session globals are written unconditionally into every
     * buffer header. The two-phase (size then write) encoding pattern
     * is incompatible with delta tracking because the size pass would
     * mark values as "seen" and the write pass would then skip them.
     *
     * @param channel the transport channel
     * @return the globals size in bytes
     */
    private int computeGlobalsSize(WamChannel channel) {
        var size = 0;
        // 11
        size += WamGlobalEncoder.platformSize(platform);
        // 13
        if (deviceName != null) {
            size += WamGlobalEncoder.deviceNameSize(deviceName);
        }
        // 15
        if (osVersion != null) {
            size += WamGlobalEncoder.osVersionSize(osVersion);
        }
        // 17
        if (appVersion != null) {
            size += WamGlobalEncoder.appVersionSize(appVersion);
        }
        // 21
        size += WamGlobalEncoder.appIsBetaReleaseSize(false);
        if (channel != WamChannel.PRIVATE) {
            // 295
            if (browserVersion != null) {
                size += WamGlobalEncoder.browserVersionSize(browserVersion);
            }
            // 633
            size += WamGlobalEncoder.webcEnvSize(WEBC_ENV_PROD);
        }
        // 655
        size += WamGlobalEncoder.memClassSize(memClass);
        if (channel != WamChannel.PRIVATE) {
            // 779
            if (browser != null) {
                size += WamGlobalEncoder.browserSize(browser);
            }
        }
        // 899
        size += WamGlobalEncoder.webcWebPlatformSize(PLATFORM_WEBCLIENT);
        // 1657
        size += WamGlobalEncoder.appBuildSize(APP_BUILD_RELEASE);
        // 3543
        size += WamGlobalEncoder.streamIdSize(STREAM_ID);
        if (channel != WamChannel.PRIVATE) {
            // 3727
            if (webcTabId != null) {
                size += WamGlobalEncoder.webcTabIdSize(webcTabId);
            }
            // 4473
            if (abKey2 != null) {
                size += WamGlobalEncoder.abKey2Size(abKey2);
            }
            // 4505
            if (deviceVersion != null) {
                size += WamGlobalEncoder.deviceVersionSize(deviceVersion);
            }
        }
        // 6251
        size += WamGlobalEncoder.ocVersionSize(1);
        if (channel != WamChannel.PRIVATE) {
            // 10317
            size += WamGlobalEncoder.numCpuSize(numCpu);
            // 14507
            size += WamGlobalEncoder.deviceClassificationSize(DEVICE_CLASSIFICATION_DESKTOP);
            // 18491
            size += WamGlobalEncoder.webcRevisionSize(0);
        }
        size += computeNullTransitionsSize(channel);
        return size;
    }

    /**
     * Builds a map of the current session global values keyed by field
     * ID. Only nullable globals (strings and the {@code abKey2} field)
     * are tracked, since non-nullable globals never transition to
     * {@code null}.
     *
     * @param channel the transport channel
     * @return the current globals snapshot
     */
    private Map<Integer, Object> buildCurrentGlobals(WamChannel channel) {
        var globals = new LinkedHashMap<Integer, Object>();
        if (deviceName != null) globals.put(13, deviceName);
        if (osVersion != null) globals.put(15, osVersion);
        if (appVersion != null) globals.put(17, appVersion);
        if (channel != WamChannel.PRIVATE) {
            if (browserVersion != null) globals.put(295, browserVersion);
            if (browser != null) globals.put(779, browser);
            if (webcTabId != null) globals.put(3727, webcTabId);
            if (abKey2 != null) globals.put(4473, abKey2);
            if (deviceVersion != null) globals.put(4505, deviceVersion);
        }
        return globals;
    }

    /**
     * Returns the byte count of VALUE_NULL entries for globals that
     * transitioned from non-{@code null} to {@code null} since the
     * last flush.
     *
     * @param channel the transport channel
     * @return the null-transition size in bytes
     */
    private int computeNullTransitionsSize(WamChannel channel) {
        if (prevSessionGlobals == null) {
            return 0;
        }
        var current = buildCurrentGlobals(channel);
        var size = 0;
        for (var fieldId : prevSessionGlobals.keySet()) {
            if (!current.containsKey(fieldId)) {
                size += WamGlobalEncoder.nullGlobalSize(fieldId);
            }
        }
        return size;
    }

    /**
     * Writes VALUE_NULL entries for globals that transitioned from
     * non-{@code null} to {@code null} since the last flush, then
     * updates the previous-globals snapshot.
     *
     * @param channel the transport channel
     * @param buffer  the output byte array
     * @param offset  the current offset
     * @return the new offset after writing
     */
    private int writeNullTransitions(WamChannel channel, byte[] buffer, int offset) {
        var current = buildCurrentGlobals(channel);
        if (prevSessionGlobals != null) {
            for (var fieldId : prevSessionGlobals.keySet()) {
                if (!current.containsKey(fieldId)) {
                    offset = WamGlobalEncoder.writeNullGlobal(fieldId, buffer, offset);
                }
            }
        }
        prevSessionGlobals = current;
        return offset;
    }

    /**
     * Writes all session globals into the given buffer at the specified
     * offset.
     *
     * @param buffer  the output byte array
     * @param offset  the current offset in the output array
     * @param channel the transport channel
     * @return the new offset after writing all globals
     */
    private int writeGlobals(byte[] buffer, int offset, WamChannel channel) {
        // 11
        offset = WamGlobalEncoder.writePlatform(platform, buffer, offset);
        // 13
        if (deviceName != null) {
            offset = WamGlobalEncoder.writeDeviceName(deviceName, buffer, offset);
        }
        // 15
        if (osVersion != null) {
            offset = WamGlobalEncoder.writeOsVersion(osVersion, buffer, offset);
        }
        // 17
        if (appVersion != null) {
            offset = WamGlobalEncoder.writeAppVersion(appVersion, buffer, offset);
        }
        // 21
        offset = WamGlobalEncoder.writeAppIsBetaRelease(false, buffer, offset);
        if (channel != WamChannel.PRIVATE) {
            // 295
            if (browserVersion != null) {
                offset = WamGlobalEncoder.writeBrowserVersion(browserVersion, buffer, offset);
            }
            // 633
            offset = WamGlobalEncoder.writeWebcEnv(WEBC_ENV_PROD, buffer, offset);
        }
        // 655
        offset = WamGlobalEncoder.writeMemClass(memClass, buffer, offset);
        if (channel != WamChannel.PRIVATE) {
            // 779
            if (browser != null) {
                offset = WamGlobalEncoder.writeBrowser(browser, buffer, offset);
            }
        }
        // 899
        offset = WamGlobalEncoder.writeWebcWebPlatform(PLATFORM_WEBCLIENT, buffer, offset);
        // 1657
        offset = WamGlobalEncoder.writeAppBuild(APP_BUILD_RELEASE, buffer, offset);
        // 3543
        offset = WamGlobalEncoder.writeStreamId(STREAM_ID, buffer, offset);
        if (channel != WamChannel.PRIVATE) {
            // 3727
            if (webcTabId != null) {
                offset = WamGlobalEncoder.writeWebcTabId(webcTabId, buffer, offset);
            }
            // 4473
            if (abKey2 != null) {
                offset = WamGlobalEncoder.writeAbKey2(abKey2, buffer, offset);
            }
            // 4505
            if (deviceVersion != null) {
                offset = WamGlobalEncoder.writeDeviceVersion(deviceVersion, buffer, offset);
            }
        }
        // 6251
        offset = WamGlobalEncoder.writeOcVersion(1, buffer, offset);
        if (channel != WamChannel.PRIVATE) {
            // 10317
            offset = WamGlobalEncoder.writeNumCpu(numCpu, buffer, offset);
            // 14507
            offset = WamGlobalEncoder.writeDeviceClassification(DEVICE_CLASSIFICATION_DESKTOP, buffer, offset);
            // 18491
            offset = WamGlobalEncoder.writeWebcRevision(0, buffer, offset);
        }
        offset = writeNullTransitions(channel, buffer, offset);
        return offset;
    }

    /**
     * Returns the byte count of per-event globals (commit time,
     * beaconing sequence, private stats id).
     *
     * @param pe      the pending event
     * @param channel the transport channel
     * @param beacon  the pre-computed beacon sequence number
     * @return the per-event globals size in bytes
     */
    private int computePerEventGlobalsSize(WamPendingEvent pe, WamChannel channel, OptionalInt beacon) {
        var size = WamGlobalEncoder.commitTimeSize(pe.commitTimeSeconds());
        if (beacon.isPresent()) {
            size += WamGlobalEncoder.beaconSessionIdSize(beacon.getAsInt());
        }
        if (channel == WamChannel.PRIVATE) {
            var psId = privateStatsId.getValueForHash(pe.event().privateStatsId());
            size += WamGlobalEncoder.psIdSize(psId);
        }
        return size;
    }

    /**
     * Writes the per-event globals (commit time, beaconing sequence,
     * private stats id) into the output buffer.
     *
     * @param pe      the pending event
     * @param channel the transport channel
     * @param beacon  the pre-computed beacon sequence number
     * @param buffer  the output byte array
     * @param offset  the current offset
     * @return the new offset after writing
     */
    private int writePerEventGlobals(WamPendingEvent pe, WamChannel channel, OptionalInt beacon, byte[] buffer, int offset) {
        offset = WamGlobalEncoder.writeCommitTime(pe.commitTimeSeconds(), buffer, offset);
        if (beacon.isPresent()) {
            offset = WamGlobalEncoder.writeBeaconSessionId(beacon.getAsInt(), buffer, offset);
        }
        if (channel == WamChannel.PRIVATE) {
            var psId = privateStatsId.getValueForHash(pe.event().privateStatsId());
            offset = WamGlobalEncoder.writePsId(psId, buffer, offset);
        }
        return offset;
    }

    /**
     * Atomically swaps the pending list for the given channel with
     * {@code null}, returning the old contents.
     *
     * <p>Uses {@link ConcurrentHashMap#compute} to ensure that no
     * concurrent {@link #commit} call can append to the returned list
     * after the swap.
     *
     * @param channel the channel to drain
     * @return the drained events, never {@code null}
     */
    private List<WamPendingEvent> swapPending(WamChannel channel) {
        var result = new ArrayList<WamPendingEvent>();
        pending.compute(channel, (_, list) -> {
            if (list != null && !list.isEmpty()) {
                result.addAll(list);
            }
            return null;
        });
        return result;
    }

    /**
     * Returns the effective sampling weight for the given event, checking
     * the runtime override map first.
     *
     * @param event the event to query
     * @return the effective sampling weight
     */
    private int effectiveWeight(WamEventSpec event) {
        var override = samplingOverride.get(event.id());
        return override.isPresent() ? Math.abs(override.getAsInt()) : event.releaseWeight();
    }

    /**
     * Writes the 8-byte WAM buffer header.
     *
     * @param buffer  the output buffer
     * @param channel the transport channel
     * @return the offset after the header (always {@value HEADER_SIZE})
     */
    private int writeHeader(byte[] buffer, WamChannel channel) {
        var offset = 0;
        System.arraycopy(WAM_MAGIC, 0, buffer, offset, WAM_MAGIC.length);
        offset += WAM_MAGIC.length;
        buffer[offset++] = (byte) PROTOCOL_VERSION;
        buffer[offset++] = (byte) STREAM_ID;
        SHORT_HANDLE.set(buffer, offset, (short) nextSequenceNumber());
        offset += 2;
        buffer[offset++] = (byte) channel.id();
        return offset;
    }

    /**
     * Returns the next sequence number, wrapping from
     * {@link #MAX_SEQUENCE_NUMBER} back to 1.
     *
     * @return the next sequence number in {@code [1, 65535]}
     */
    private int nextSequenceNumber() {
        return sequenceNumber.getAndUpdate(current -> {
            var next = current + 1;
            return next > MAX_SEQUENCE_NUMBER ? 1 : next;
        });
    }

    /**
     * Sends a WAM buffer via an XMPP {@code <iq>} stanza, retrying
     * with exponential backoff on transient server errors.
     *
     * <p>The server response is inspected: a {@code type="result"}
     * response indicates success; a {@code type="error"} response with
     * a {@code 5xx} status code is retried; all other errors are
     * permanent and the buffer is dropped.
     *
     * @param buffer the encoded WAM buffer
     */
    private void sendWithRetry(byte[] buffer) {
        for (var attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                var response = sendViaIq(buffer);
                var type = response.getAttributeAsString("type", "");
                if ("result".equals(type)) {
                    return;
                }

                var errorCode = parseErrorCode(response);
                if (errorCode >= 500 && attempt < MAX_RETRIES) {
                    var delay = computeBackoffDelay(attempt);
                    LOGGER.fine("WAM upload got " + errorCode + ", retrying in " + delay + "ms (attempt " + (attempt + 1) + ")");
                    Thread.sleep(delay);
                    continue;
                }

                LOGGER.warning("WAM upload failed with error " + errorCode + ", dropping buffer");
                return;
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                if (attempt < MAX_RETRIES) {
                    try {
                        var delay = computeBackoffDelay(attempt);
                        LOGGER.fine("WAM upload failed (" + e.getMessage() + "), retrying in " + delay + "ms");
                        Thread.sleep(delay);
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    LOGGER.warning("WAM upload failed after " + MAX_RETRIES + " retries: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Sends a private-channel WAM buffer using the same IQ path with
     * retry logic.
     *
     * <p>The blinded-token upload path used by WhatsApp Web
     * ({@code privateStatsUpload}) requires a server-issued token that
     * is not available in the XMPP protocol alone. This implementation
     * uses the standard IQ path as a best-effort fallback.
     *
     * @apiNote WAWebUploadPrivateStatsBackend: uploads private stats
     * via a separate HTTP endpoint with blinded authentication tokens
     * and exponential backoff retry. The token is obtained from
     * WAWebIssuePrivateStatsToken.getToken.
     *
     * @param buffer the encoded WAM buffer
     */
    private void sendPrivateWithRetry(byte[] buffer) {
        sendWithRetry(buffer);
    }

    /**
     * Sends a WAM buffer via an XMPP {@code <iq>} stanza and waits
     * for the server response.
     *
     * @param buffer the encoded WAM buffer
     * @return the server response node
     */
    private Node sendViaIq(byte[] buffer) {
        var add = new NodeBuilder()
                .description("add")
                .attribute("t", String.valueOf(Instant.now().getEpochSecond()))
                .content(buffer)
                .build();
        var iq = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:stats")
                .attribute("to", "s.whatsapp.net")
                .attribute("type", "set")
                .content(add);
        return client.sendNode(iq);
    }

    /**
     * Parses the HTTP-style error code from an IQ error response.
     *
     * <p>The error code is extracted from the {@code code} attribute of
     * a child {@code <error>} element. If no such element exists,
     * returns {@code 0}.
     *
     * @param response the server response node
     * @return the error code, or {@code 0} if not found
     */
    private static int parseErrorCode(Node response) {
        var children = response.getChildren("error");
        if (children.isEmpty()) {
            return 0;
        }
        var error = children.getFirst();
        return (int) error.getAttributeAsLong("code", 0L);
    }

    /**
     * Computes the backoff delay for the given retry attempt using
     * exponential backoff with 10% jitter. Matches the JS implementation
     * bug where Math.pow(2, attempt) does not multiply by base delay.
     *
     * @param attempt the zero-based retry attempt number
     * @return the delay in milliseconds
     */
    private static long computeBackoffDelay(int attempt) {
        var delay = attempt == 0 ? RETRY_BASE_DELAY_MS : (long) Math.pow(2, attempt);
        if (delay > RETRY_MAX_DELAY_MS) delay = RETRY_MAX_DELAY_MS;
        if (delay < RETRY_BASE_DELAY_MS) delay = RETRY_BASE_DELAY_MS;
        var jitter = (long) (delay * 0.1 * FastRandomUtils.randomDouble());
        return delay + jitter;
    }

    /**
     * Stops the flush thread and performs a final flush of all pending
     * events.
     */
    public void close() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        flush();
        initialized = false;
    }
}
