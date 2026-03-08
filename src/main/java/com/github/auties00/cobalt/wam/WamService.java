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
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentLinkedQueue;
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
 * <p>Events committed before {@link #initialize()} is called are
 * queued in an init queue and replayed once initialization completes,
 * matching WhatsApp Web's {@code WAWebWamInitQueue} mechanism.
 *
 * <p>This service does not persist unsent buffers across sessions.
 * WhatsApp Web persists pending buffers to IndexedDB every 5 seconds
 * and restores them on page reload; this implementation treats all
 * in-flight data as ephemeral — buffers that have not been uploaded
 * when the service is closed are silently discarded.
 *
 * @apiNote This service does not emit {@code WamClientErrorsWamEvent}
 * or {@code WamDroppedEventWamEvent} for internal health monitoring.
 * WhatsApp Web uses these events to track buffer drops, validation
 * failures, and WAM processing errors for operational visibility.
 * This implementation logs warnings instead, as WAM system health
 * telemetry is not a goal of this project.
 *
 * @see WamEventSpec
 * @see WamGlobalEncoder
 * @see WamChannel
 */
public final class WamService {
    private static final Logger LOGGER = Logger.getLogger(WamService.class.getName());

    private static final VarHandle SHORT_HANDLE =
            MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);

    /**
     * Size of the WAM buffer header in bytes:
     * {@code "WAM"(3) + version(1) + streamId(1) + seqNum(2 LE) + channel(1)}.
     */
    private static final int HEADER_SIZE = 8;

    private static final byte[] WAM_MAGIC = {'W', 'A', 'M'};
    private static final int PROTOCOL_VERSION = 5;
    private static final int STREAM_ID = 1;

    /**
     * Interval in seconds between serialization checks. Matches the
     * WhatsApp Web two-tier timing where events are serialized every 5
     * seconds and the rotation/upload cycle runs every 120 seconds.
     */
    private static final int SERIALIZE_INTERVAL_SECONDS = 5;

    /**
     * Interval in seconds between rotation/upload cycles.
     */
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

    /**
     * Timeout in milliseconds to wait for connectivity before
     * attempting a WAM buffer upload.
     */
    private static final long CONNECTIVITY_WAIT_TIMEOUT_MS = 30_000;

    private final WhatsAppClient client;
    private final ABPropsService abPropsService;
    private final ConcurrentMap<WamChannel, List<WamPendingEvent>> pending;
    private final Map<WamChannel, AtomicInteger> sequenceNumbers;
    private final WamBeaconing beaconing;
    private final WamPrivateStatsId privateStatsId;
    private final WamSamplingOverride samplingOverride;
    private final Map<WamChannel, Map<Integer, Object>> prevSessionGlobals;
    private final ConcurrentLinkedQueue<Runnable> initQueue;

    private volatile boolean initialized;
    private ScheduledExecutorService scheduler;

    private volatile long platform;
    private volatile String appVersion;
    private volatile String deviceName;
    private volatile int memClass;
    private volatile int numCpu;
    private volatile String browser;
    private volatile String browserVersion;
    private volatile String osVersion;
    private volatile String deviceVersion;
    private volatile String webcTabId;
    private volatile String abKey2;
    private volatile int webcRevision;
    private volatile String companionAppVersion;
    private volatile String psCountryCode;
    private volatile boolean serviceImprovementOptOut;

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
        this.sequenceNumbers = new EnumMap<>(WamChannel.class);
        for (var channel : WamChannel.values()) {
            sequenceNumbers.put(channel, new AtomicInteger(1));
        }
        this.beaconing = new WamBeaconing();
        this.privateStatsId = new WamPrivateStatsId();
        this.samplingOverride = new WamSamplingOverride();
        this.prevSessionGlobals = new EnumMap<>(WamChannel.class);
        this.initQueue = new ConcurrentLinkedQueue<>();
    }

    /**
     * Initializes the service by snapshotting session globals from the
     * client store, loading sampling overrides from AB props, and
     * starting the periodic flush threads.
     *
     * <p>This method should be called once after the client has
     * authenticated and the store's JID and version are available.
     */
    public void initialize() {
        var store = client.store();
        var version = store.clientVersion();
        this.appVersion = version != null ? version.toString() : null;
        this.platform = store.clientType() == WhatsAppClientType.WEB ? 8L : 2L;
        this.deviceName = store.name();
        this.memClass = (int) (Runtime.getRuntime().maxMemory() / (1024 * 1024));
        this.numCpu = Runtime.getRuntime().availableProcessors();
        this.browser = "Chrome";
        this.browserVersion = appVersion;
        this.osVersion = System.getProperty("os.name", "") + " " + System.getProperty("os.version", "");
        this.deviceVersion = osVersion;
        this.webcTabId = UUID.randomUUID().toString();
        this.abKey2 = abPropsService.getBool(ABProp.WAM_DISABLE_ABKEY_ATTRIBUTE)
                ? null
                : abPropsService.abKey().orElse("");
        this.webcRevision = version != null ? version.tertiary().orElse(0) : 0;
        this.companionAppVersion = store.companionVersion()
                .map(Object::toString)
                .orElse(null);
        this.psCountryCode = derivePsCountryCode();
        this.serviceImprovementOptOut = abPropsService.getBool(ABProp.SERVICE_IMPROVEMENT_OPT_OUT_FLAG);

        // Load WAM event sampling overrides from AB props
        var configs = abPropsService.samplingConfigs();
        if (!configs.isEmpty()) {
            samplingOverride.replaceAll(configs);
        }

        this.initialized = true;

        // Drain the init queue: replay events committed before initialization
        Runnable action;
        while ((action = initQueue.poll()) != null) {
            action.run();
        }

        this.scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
        scheduler.scheduleWithFixedDelay(this::checkMidCycleUpload, SERIALIZE_INTERVAL_SECONDS, SERIALIZE_INTERVAL_SECONDS, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::flush, FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Derives the PS country code from the user's phone number using
     * libphonenumber, matching WhatsApp Web's
     * {@code WAWebL10NCountryCodes.getCountryShortcodeByPhone}.
     *
     * @return the two-letter ISO 3166-1 alpha-2 country code, or
     *         {@code null} if not derivable
     */
    private String derivePsCountryCode() {
        var phoneNumber = client.store().phoneNumber();
        if (phoneNumber.isEmpty()) {
            return null;
        }

        try {
            var parsed = PhoneNumberUtil.getInstance().parse("+" + phoneNumber.getAsLong(), null);
            var regionCode = PhoneNumberUtil.getInstance().getRegionCodeForNumber(parsed);
            return regionCode != null ? regionCode.toLowerCase(Locale.ROOT) : null;
        } catch (Exception _) {
            return null;
        }
    }

    /**
     * Commits an event for later transmission.
     *
     * <p>The event is first validated via {@link WamEventSpec#validate()}.
     * If validation fails, the event is silently discarded and a warning
     * is logged, matching WhatsApp Web's {@code runPreCommitValidation()}
     * behaviour.
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
        if (!event.markCommitted()) {
            LOGGER.warning("WAM redundant commit: " + event.getClass().getSimpleName());
            return;
        }

        if (!event.validate()) {
            LOGGER.warning("WAM event failed validation: " + event.getClass().getSimpleName());
            return;
        }

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
            Thread.ofVirtual().start(() -> flushChannel(WamChannel.REALTIME));
        }
    }

    /**
     * Commits an event and returns a future that completes when the
     * buffer containing the event is flushed to the server.
     *
     * <p>This matches WhatsApp Web's {@code commitAndWaitForFlush()}
     * which returns a Promise resolved on buffer flush.
     *
     * @param event the event to commit, must not be {@code null}
     * @return a future that completes when the event's buffer is flushed,
     *         or completes immediately if the event was sampled out or
     *         failed validation
     */
    public CompletableFuture<Void> commitAndWaitForFlush(WamEventSpec event) {
        Objects.requireNonNull(event, "event cannot be null");
        if (!event.markCommitted()) {
            LOGGER.warning("WAM redundant commit: " + event.getClass().getSimpleName());
            return CompletableFuture.completedFuture(null);
        }

        if (!event.validate()) {
            LOGGER.warning("WAM event failed validation: " + event.getClass().getSimpleName());
            return CompletableFuture.completedFuture(null);
        }

        var weight = effectiveWeight(event);
        if (weight > 1) {
            if (FastRandomUtils.randomInt(weight) != 0) {
                return CompletableFuture.completedFuture(null);
            }
        }

        var future = new CompletableFuture<Void>();
        var pe = new WamPendingEvent(event, Instant.now().getEpochSecond(), future);
        pending.compute(event.channel(), (_, list) -> {
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(pe);
            return list;
        });

        if (event.channel() == WamChannel.REALTIME) {
            Thread.ofVirtual().start(() -> flushChannel(WamChannel.REALTIME));
        }

        return future;
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
     * Checks whether any non-realtime channel has accumulated more than
     * {@link #MAX_BUFFER_SIZE} bytes of pending events and triggers an
     * early flush if so.
     *
     * <p>This implements the mid-cycle upload behaviour from WhatsApp
     * Web's two-tier timing system, where a 5-second serialization
     * timer checks for oversized buffers between the 120-second
     * rotation cycles.
     */
    private void checkMidCycleUpload() {
        if (!initialized) {
            return;
        }

        for (var channel : WamChannel.values()) {
            if (channel == WamChannel.REALTIME) {
                continue;
            }

            var oversized = new boolean[1];
            pending.compute(channel, (_, list) -> {
                if (list != null && !list.isEmpty()) {
                    var size = HEADER_SIZE;
                    for (var pe : list) {
                        size += pe.event().sizeOf();
                        if (size > MAX_BUFFER_SIZE) {
                            oversized[0] = true;
                            break;
                        }
                    }
                }
                return list;
            });

            if (oversized[0]) {
                flushChannel(channel);
            }
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
            flushPrivateByPsIdGroup(events);
        } else {
            var bufferKey = channel == WamChannel.REGULAR ? "regular" : "realtime";
            flushEventList(channel, events, bufferKey);
        }
    }

    /**
     * Groups private events by their PS ID hash and flushes each group
     * as a separate buffer, matching the per-PS-ID-group buffer
     * separation in WhatsApp Web's {@code _executePendingForContext}.
     *
     * @param events the drained private events
     */
    private void flushPrivateByPsIdGroup(List<WamPendingEvent> events) {
        var groups = new LinkedHashMap<Integer, List<WamPendingEvent>>();
        for (var pe : events) {
            groups.computeIfAbsent(pe.event().privateStatsId(), _ -> new ArrayList<>()).add(pe);
        }
        for (var entry : groups.entrySet()) {
            var bufferKey = privateStatsId.getKeyNameForHash(entry.getKey());
            flushEventList(WamChannel.PRIVATE, entry.getValue(), bufferKey);
        }
    }

    /**
     * Flushes a list of events for the given channel, building one or
     * more buffers capped at {@link #MAX_BUFFER_SIZE}.
     *
     * <p>Buffer rotation uses a post-check: the event that pushes the
     * buffer over the limit stays in the current buffer, and a new
     * buffer is started for subsequent events. This matches WhatsApp
     * Web's behaviour where a buffer may momentarily exceed the limit
     * by one event.
     *
     * @param channel   the transport channel
     * @param events    the events to flush
     * @param bufferKey the beaconing buffer key
     */
    private void flushEventList(WamChannel channel, List<WamPendingEvent> events, String bufferKey) {
        var beacons = new OptionalInt[events.size()];
        var weights = new int[events.size()];
        for (var i = 0; i < events.size(); i++) {
            beacons[i] = beaconing.nextSequenceNumber(bufferKey);
            weights[i] = effectiveWeight(events.get(i).event());
        }

        var batchStart = 0;
        var globalsBytes = encodeGlobals(channel);
        var batchSize = HEADER_SIZE + globalsBytes.length;

        for (var i = 0; i < events.size(); i++) {
            var pe = events.get(i);
            var eventSize = computePerEventGlobalsSize(pe, channel, beacons[i]) + pe.event().sizeOf(weights[i]);
            batchSize += eventSize;

            if (batchSize > MAX_BUFFER_SIZE) {
                buildAndSend(channel, events, weights, beacons, globalsBytes, batchStart, i + 1, batchSize);
                batchStart = i + 1;
                if (batchStart < events.size()) {
                    globalsBytes = encodeGlobals(channel);
                    batchSize = HEADER_SIZE + globalsBytes.length;
                }
            }
        }

        if (batchStart < events.size()) {
            buildAndSend(channel, events, weights, beacons, globalsBytes, batchStart, events.size(), batchSize);
        }
    }

    /**
     * Builds a single WAM buffer from a slice of events and sends it.
     *
     * @param channel     the transport channel
     * @param events      the full event list
     * @param weights     pre-computed sampling weights parallel to the events
     *                    list, re-fetched at flush time
     * @param beacons     pre-computed beacon sequence numbers parallel to
     *                    the events list
     * @param globalsBytes the pre-encoded session globals bytes
     * @param from        the inclusive start index in the event list
     * @param to          the exclusive end index in the event list
     * @param size        the pre-computed total buffer size
     */
    private void buildAndSend(WamChannel channel, List<WamPendingEvent> events, int[] weights, OptionalInt[] beacons, byte[] globalsBytes, int from, int to, int size) {
        if (size > MAX_UPLOAD_SIZE) {
            LOGGER.warning("Dropping WAM buffer of " + size + " bytes (exceeds upload limit)");
            completeFutures(events, from, to);
            return;
        }

        var buffer = new byte[size];
        var offset = writeHeader(buffer, channel);
        System.arraycopy(globalsBytes, 0, buffer, offset, globalsBytes.length);
        offset += globalsBytes.length;

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

        completeFutures(events, from, to);
    }

    /**
     * Completes flush futures for the given range of events.
     *
     * @param events the event list
     * @param from   the inclusive start index
     * @param to     the exclusive end index
     */
    private static void completeFutures(List<WamPendingEvent> events, int from, int to) {
        for (var i = from; i < to; i++) {
            var future = events.get(i).flushFuture();
            if (future != null) {
                future.complete(null);
            }
        }
    }

    /**
     * Encodes the session globals for the given channel, writing only
     * globals that have changed since the last flush for this channel.
     *
     * <p>On first call for a channel, all globals are written. On
     * subsequent calls, only dirty (changed) globals and null
     * transitions are written. This matches WhatsApp Web's
     * dirty-tracking approach where each {@code WamContext} maintains
     * its own independent {@code prevGlobals}.
     *
     * @param channel the transport channel
     * @return the encoded globals as a byte array
     */
    private byte[] encodeGlobals(WamChannel channel) {
        var current = buildFullCurrentGlobals(channel);
        var prev = prevSessionGlobals.get(channel);

        var dirty = new ArrayList<Map.Entry<Integer, Object>>();
        var nullTransitions = new ArrayList<Integer>();

        for (var entry : current.entrySet()) {
            var prevValue = prev != null ? prev.get(entry.getKey()) : null;
            if (!Objects.equals(prevValue, entry.getValue())) {
                dirty.add(entry);
            }
        }

        if (prev != null) {
            for (var fieldId : prev.keySet()) {
                if (!current.containsKey(fieldId)) {
                    nullTransitions.add(fieldId);
                }
            }
        }

        var size = 0;
        for (var entry : dirty) {
            size += WamGlobalEncoder.dynamicGlobalSize(entry.getKey(), entry.getValue());
        }
        for (var fieldId : nullTransitions) {
            size += WamGlobalEncoder.nullGlobalSize(fieldId);
        }

        var bytes = new byte[size];
        var offset = 0;
        for (var entry : dirty) {
            offset = WamGlobalEncoder.writeDynamicGlobal(entry.getKey(), entry.getValue(), bytes, offset);
        }
        for (var fieldId : nullTransitions) {
            offset = WamGlobalEncoder.writeNullGlobal(fieldId, bytes, offset);
        }

        prevSessionGlobals.put(channel, current);
        return bytes;
    }

    /**
     * Builds a map of all current session global values keyed by field
     * ID for the given channel. Used for dirty-tracking comparisons.
     *
     * @param channel the transport channel
     * @return the current globals snapshot
     */
    private Map<Integer, Object> buildFullCurrentGlobals(WamChannel channel) {
        var globals = new LinkedHashMap<Integer, Object>();
        // 11 - platform (regular, private)
        globals.put(11, platform);
        // 13 - deviceName (regular, private)
        if (deviceName != null) globals.put(13, deviceName);
        // 15 - osVersion (regular, private)
        if (osVersion != null) globals.put(15, osVersion);
        // 17 - appVersion (regular, private)
        if (appVersion != null) globals.put(17, appVersion);
        // 21 - appIsBetaRelease (regular, private)
        globals.put(21, false);
        if (channel != WamChannel.PRIVATE) {
            // 23 - networkIsWifi (regular)
            globals.put(23, true);
            // 295 - browserVersion (regular)
            if (browserVersion != null) globals.put(295, browserVersion);
            // 633 - webcEnv (regular)
            globals.put(633, (long) WEBC_ENV_PROD);
        }
        // 655 - memClass (regular, private)
        globals.put(655, (long) memClass);
        if (channel != WamChannel.PRIVATE) {
            // 779 - browser (regular)
            if (browser != null) globals.put(779, browser);
        }
        // 899 - webcWebPlatform (regular, private)
        globals.put(899, (long) PLATFORM_WEBCLIENT);
        if (channel != WamChannel.PRIVATE) {
            // 1005 - webcPhoneAppVersion (regular)
            if (companionAppVersion != null) globals.put(1005, companionAppVersion);
        }
        // 1657 - appBuild (regular, private)
        globals.put(1657, (long) APP_BUILD_RELEASE);
        // 3543 - streamId (regular, private)
        globals.put(3543, (long) STREAM_ID);
        if (channel != WamChannel.PRIVATE) {
            // 3727 - webcTabId (regular)
            if (webcTabId != null) globals.put(3727, webcTabId);
            // 4473 - abKey2 (regular)
            if (abKey2 != null) globals.put(4473, abKey2);
            // 4505 - deviceVersion (regular)
            if (deviceVersion != null) globals.put(4505, deviceVersion);
        }
        // 6251 - ocVersion (regular, private)
        globals.put(6251, 1L);
        if (channel == WamChannel.PRIVATE) {
            // 6833 - psCountryCode (private)
            if (psCountryCode != null) globals.put(6833, psCountryCode);
        }
        if (channel != WamChannel.PRIVATE) {
            // 10317 - numCpu (regular)
            globals.put(10317, (long) numCpu);
        }
        // 13293 - serviceImprovementOptOut (regular, private)
        globals.put(13293, serviceImprovementOptOut);
        if (channel != WamChannel.PRIVATE) {
            // 14507 - deviceClassification (regular)
            globals.put(14507, (long) DEVICE_CLASSIFICATION_DESKTOP);
            // 18491 - webcRevision (regular)
            globals.put(18491, (long) webcRevision);
        }
        return globals;
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
        SHORT_HANDLE.set(buffer, offset, (short) nextSequenceNumber(channel));
        offset += 2;
        buffer[offset++] = (byte) channel.id();
        return offset;
    }

    /**
     * Returns the next sequence number for the given channel, wrapping
     * from {@link #MAX_SEQUENCE_NUMBER} back to 1.
     *
     * <p>Each channel maintains an independent sequence counter,
     * matching WhatsApp Web's {@code SequenceNumberGenerator} which
     * creates per-channel counters.
     *
     * @param channel the transport channel
     * @return the next sequence number in {@code [1, 65535]}
     */
    private int nextSequenceNumber(WamChannel channel) {
        return sequenceNumbers.get(channel).getAndUpdate(current -> {
            var next = current + 1;
            return next > MAX_SEQUENCE_NUMBER ? 1 : next;
        });
    }

    /**
     * Waits for the client to be connected before attempting a buffer
     * upload, with a timeout of {@link #CONNECTIVITY_WAIT_TIMEOUT_MS}.
     *
     * <p>This matches WhatsApp Web's {@code waitIfOffline()} with a
     * 30-second timeout, preserving retry budget by not attempting
     * uploads while disconnected.
     */
    private void waitIfDisconnected() {
        if (client.isConnected()) {
            return;
        }

        var deadline = System.currentTimeMillis() + CONNECTIVITY_WAIT_TIMEOUT_MS;
        try {
            while (!client.isConnected() && System.currentTimeMillis() < deadline) {
                Thread.sleep(1_000);
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sends a WAM buffer via an XMPP {@code <iq>} stanza, retrying
     * with exponential backoff on transient server errors.
     *
     * <p>Before the first attempt, waits for connectivity if the client
     * is currently disconnected, matching WhatsApp Web's
     * {@code waitIfOffline()} behaviour.
     *
     * <p>The server response is inspected: a {@code type="result"}
     * response indicates success; a {@code type="error"} response with
     * a {@code 5xx} status code is retried; all other errors are
     * permanent and the buffer is dropped.
     *
     * @param buffer the encoded WAM buffer
     */
    private void sendWithRetry(byte[] buffer) {
        waitIfDisconnected();

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
     * Stops the flush threads and performs a final flush of all pending
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
