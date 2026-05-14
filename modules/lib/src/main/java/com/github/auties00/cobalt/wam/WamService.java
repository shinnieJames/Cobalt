package com.github.auties00.cobalt.wam;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientType;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.commerce.OrderMessage;
import com.github.auties00.cobalt.model.message.commerce.ProductMessage;
import com.github.auties00.cobalt.model.message.contact.ContactMessage;
import com.github.auties00.cobalt.model.message.contact.ContactsArrayMessage;
import com.github.auties00.cobalt.model.message.event.EncEventResponseMessage;
import com.github.auties00.cobalt.model.message.event.EventMessage;
import com.github.auties00.cobalt.model.message.event.EventResponseMessage;
import com.github.auties00.cobalt.model.message.interactive.InteractiveMessage;
import com.github.auties00.cobalt.model.message.interactive.InteractiveMessageContent;
import com.github.auties00.cobalt.model.message.list.ListMessage;
import com.github.auties00.cobalt.model.message.list.ListResponseMessage;
import com.github.auties00.cobalt.model.message.location.LiveLocationMessage;
import com.github.auties00.cobalt.model.message.location.LocationMessage;
import com.github.auties00.cobalt.model.message.media.AlbumMessage;
import com.github.auties00.cobalt.model.message.media.AudioMessage;
import com.github.auties00.cobalt.model.message.media.DocumentMessage;
import com.github.auties00.cobalt.model.message.media.ImageMessage;
import com.github.auties00.cobalt.model.message.media.StickerMessage;
import com.github.auties00.cobalt.model.message.media.StickerPackMessage;
import com.github.auties00.cobalt.model.message.media.VideoMessage;
import com.github.auties00.cobalt.model.message.poll.PollCreationMessage;
import com.github.auties00.cobalt.model.message.poll.PollUpdateMessage;
import com.github.auties00.cobalt.model.message.security.EncReactionMessage;
import com.github.auties00.cobalt.model.message.system.PinInChatMessage;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessage;
import com.github.auties00.cobalt.model.message.text.ReactionMessage;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.util.DataUtils;
import com.github.auties00.cobalt.wam.binary.WamEventDecoder;
import com.github.auties00.cobalt.wam.binary.WamEventEncoder;
import com.github.auties00.cobalt.wam.binary.WamGlobalEncoder;
import com.github.auties00.cobalt.wam.event.PsIdUpdateEventBuilder;
import com.github.auties00.cobalt.wam.event.WamEventRegistry;
import com.github.auties00.cobalt.wam.privatestats.WamPrivateStatsTokenIssuer;
import com.github.auties00.cobalt.wam.privatestats.WamPrivateStatsUploader;
import com.github.auties00.cobalt.wam.event.SendDocumentEventBuilder;
import com.github.auties00.cobalt.wam.event.WamClientErrorsEventBuilder;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.model.WamChannel;
import com.github.auties00.cobalt.wam.privatestats.WamPrivateStatsId;
import com.github.auties00.cobalt.wam.type.AgentEngagementEnumType;
import com.github.auties00.cobalt.wam.type.BotType;
import com.github.auties00.cobalt.wam.type.DocumentType;
import com.github.auties00.cobalt.wam.type.E2eDeviceType;
import com.github.auties00.cobalt.wam.type.InvisibleMessageCategoryType;
import com.github.auties00.cobalt.wam.type.MediaType;
import com.github.auties00.cobalt.wam.type.MessageType;
import com.github.auties00.cobalt.wam.type.PsIdAction;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import java.io.IOException;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * A service that collects, batches, and uploads WhatsApp Metrics
 * (WAM) telemetry events over the three transport channels.
 *
 * <p>Events are committed via {@link #commit(WamEventSpec)} and held
 * in memory until the next periodic flush, or until an immediate
 * flush for {@link WamChannel#REALTIME} events. On flush, one or more
 * byte buffers are allocated for each channel with pending events,
 * each capped at {@link #MAX_BUFFER_SIZE} bytes.
 *
 * <p>Events committed before {@link #initialize()} is called are
 * queued in an init queue and replayed once initialization completes,
 * matching WhatsApp Web's {@code WAWebWamInitQueue} mechanism.
 *
 * <p>This service does not persist unsent buffers across sessions.
 * WhatsApp Web persists pending buffers to IndexedDB every five
 * seconds and restores them on page reload. Cobalt treats all
 * in-flight data as ephemeral, so buffers that have not been uploaded
 * when the service is closed are silently discarded.
 *
 * @see WamEventSpec
 * @see WamGlobalEncoder
 * @see WamChannel
 */
@WhatsAppWebModule(moduleName = "WAWebWam")
@WhatsAppWebModule(moduleName = "WAWebWamCodegenWamEvent")
@WhatsAppWebModule(moduleName = "WAWebL10NCountryCodes")
@WhatsAppWebModule(moduleName = "WAWebBrowserApi")
@WhatsAppWebModule(moduleName = "WAWebWamMsgUtils")
@WhatsAppWebModule(moduleName = "WAWebProcessRawMediaLogging")
@WhatsAppWebExport(moduleName = "WAWebWam", exports = "Wam", adaptation = WhatsAppAdaptation.ADAPTED)
public abstract class WamService {
    private static final Logger LOGGER = Logger.getLogger(WamService.class.getName());

    /**
     * Size of the WAM buffer header in bytes. Layout is
     * {@code "WAM"(3) + version(1) + streamId(1) + seqNum(2 LE) + channel(1)}.
     */
    private static final int HEADER_SIZE = 8;

    /**
     * Magic bytes that prefix every WAM buffer.
     */
    private static final byte[] WAM_MAGIC = {'W', 'A', 'M'};

    /**
     * Protocol version written into the WAM buffer header.
     */
    private static final int PROTOCOL_VERSION = 5;

    /**
     * Stream identifier written into the WAM buffer header. Always
     * {@code 1} for the regular client stream.
     */
    private static final int STREAM_ID = 1;

    /**
     * Interval in seconds between serialization checks. Matches the
     * WhatsApp Web two-tier timing where events are serialized every
     * five seconds and the rotation and upload cycle runs every 120
     * seconds.
     */
    private static final int SERIALIZE_INTERVAL_SECONDS = 5;

    /**
     * Interval in seconds between rotation and upload cycles.
     */
    private static final int FLUSH_INTERVAL_SECONDS = 120;

    /**
     * Maximum size of a single WAM buffer in bytes before it is
     * rotated and a new buffer is started. Matches the JavaScript
     * constant {@code WAM_MAX_BUFFER_SIZE}.
     */
    private static final int MAX_BUFFER_SIZE = 50_000;

    /**
     * Maximum size of a WAM buffer that may be uploaded. Buffers
     * exceeding this size are dropped. Matches the JavaScript
     * constant {@code WAM_MAX_BUFFER_SIZE_FOR_UPLOAD}.
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
     * back to {@code 1}.
     */
    private static final int MAX_SEQUENCE_NUMBER = 0xFFFF;

    /**
     * Wire field id of the commit-time global, mirroring
     * {@code WamGlobalEncoder.COMMIT_TIME}. Used by
     * {@link #restorePendingBuffers} to recognise the per-event
     * commit-time entry that precedes each persisted event.
     */
    private static final int COMMIT_TIME_FIELD_ID = 47;

    /**
     * Device classification value for DESKTOP, matching the
     * JavaScript enum {@code DEVICE_CLASSIFICATION.DESKTOP}.
     */
    @WhatsAppWebExport(moduleName = "WAWebFalcoCanonicalDeviceClassification", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    private static final int DEVICE_CLASSIFICATION_DESKTOP = 4;

    /**
     * App build type value for RELEASE, matching the JavaScript enum
     * {@code APP_BUILD_TYPE.RELEASE}.
     */
    private static final int APP_BUILD_RELEASE = 4;

    /**
     * Web client environment value for PROD, matching the JavaScript
     * enum {@code WEBC_ENV_CODE.PROD}.
     */
    private static final int WEBC_ENV_PROD = 0;

    /**
     * Web platform value for WEB, matching the JavaScript enum
     * {@code WEBC_WEB_PLATFORM_TYPE.WEB}.
     */
    private static final int PLATFORM_WEBCLIENT = 1;

    /**
     * Timeout in milliseconds to wait for connectivity before
     * attempting a WAM buffer upload.
     */
    private static final long CONNECTIVITY_WAIT_TIMEOUT_MS = 30_000;

    /**
     * The bound WhatsApp client used to dispatch IQ stanzas and
     * inspect connectivity state.
     */
    private final WhatsAppClient client;

    /**
     * AB props service used to read sampling configs and feature
     * flags driving the WAM pipeline.
     */
    private final ABPropsService abPropsService;

    /**
     * Pending events keyed by channel. Each value is a list of
     * uncommitted events awaiting flush.
     */
    private final ConcurrentMap<WamChannel, List<WamPendingEvent>> pending;

    /**
     * Per-channel sequence counter written into the buffer header.
     * Counters wrap from {@link #MAX_SEQUENCE_NUMBER} back to
     * {@code 1}.
     */
    private final Map<WamChannel, AtomicInteger> sequenceNumbers;

    /**
     * Daily-sampled beaconing state, queried for every flushed event
     * to determine whether a beacon sequence number must be emitted.
     */
    private final WamBeaconing beaconing;

    /**
     * Rotating pseudonymous identifiers attached to private-channel
     * events.
     */
    private final WamPrivateStatsId privateStatsId;

    /**
     * Uploader for private-channel buffers, performing the
     * blinded-token VOPRF round-trip and the multipart POST.
     */
    private final WamPrivateStatsUploader privateStatsUploader;

    /**
     * Per-event-id sampling weight overrides loaded from AB props at
     * initialization and updated at runtime.
     */
    private final WamSamplingOverride samplingOverride;

    /**
     * Last-emitted session globals for each channel, used by
     * {@link #encodeGlobals} to write only dirty globals on
     * subsequent flushes.
     */
    private final Map<WamChannel, Map<Integer, Object>> prevSessionGlobals;

    /**
     * Queue of pre-init commit actions, drained on the first call to
     * {@link #initialize()}.
     */
    private final ConcurrentLinkedQueue<Runnable> initQueue;

    /**
     * Initialization gate. Events committed before {@code true} are
     * buffered into {@link #initQueue} and replayed on
     * {@link #initialize()}.
     */
    private volatile boolean initialized;

    /**
     * Platform identifier written as global {@code 11}. Resolved from
     * {@link WhatsAppClientType} at
     * initialization.
     */
    private volatile long platform;

    /**
     * Stringified WhatsApp client version written as global
     * {@code 17} ({@code appVersion}).
     */
    private volatile String appVersion;

    /**
     * Device name reported as global {@code 13} ({@code deviceName}).
     */
    private volatile String deviceName;
    /**
     * Approximate device memory class in megabytes, reported as the
     * WAM global with index {@code 655} ({@code memClass}).
     */
    @WhatsAppWebExport(
            moduleName = "WAWebBrowserApi",
            exports = "getMemClass",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    private volatile int memClass;

    /**
     * Number of logical CPUs reported as the WAM global with index
     * {@code 10317} ({@code numCpu}).
     */
    @WhatsAppWebExport(
            moduleName = "WAWebBrowserApi",
            exports = "getNumCpu",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    private volatile int numCpu;

    /**
     * Browser name reported as global {@code 779} ({@code browser}).
     */
    private volatile String browser;

    /**
     * Browser version reported as global {@code 295}
     * ({@code browserVersion}).
     */
    private volatile String browserVersion;

    /**
     * OS version reported as global {@code 15} ({@code osVersion}).
     */
    private volatile String osVersion;

    /**
     * Device version reported as global {@code 4505}
     * ({@code deviceVersion}).
     */
    private volatile String deviceVersion;

    /**
     * Per-session tab id reported as global {@code 3727}
     * ({@code webcTabId}).
     */
    private volatile String webcTabId;

    /**
     * Optional AB-experiment key reported as global {@code 4473}
     * ({@code abKey2}). May be {@code null} when WAM AB-key reporting
     * is disabled.
     */
    private volatile String abKey2;

    /**
     * Web client revision reported as global {@code 18491}
     * ({@code webcRevision}).
     */
    private volatile int webcRevision;

    /**
     * Companion app version reported as global {@code 1005}
     * ({@code webcPhoneAppVersion}).
     */
    private volatile String companionAppVersion;

    /**
     * Country code reported on the private channel as global
     * {@code 6833} ({@code psCountryCode}).
     */
    private volatile String psCountryCode;

    /**
     * Service-improvement opt-out flag reported as global
     * {@code 13293} ({@code serviceImprovementOptOut}).
     */
    private volatile boolean serviceImprovementOptOut;

    /**
     * Push-phase string reported as global {@code 6605}
     * ({@code webcWebArch}). Always {@code null} for Cobalt.
     */
    private volatile String pushPhase;

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
    protected WamService(WhatsAppClient client, ABPropsService abPropsService, WamBeaconing beaconing) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService cannot be null");
        this.pending = new ConcurrentHashMap<>();
        this.sequenceNumbers = new EnumMap<>(WamChannel.class);
        for (var channel : WamChannel.values()) {
            sequenceNumbers.put(channel, new AtomicInteger(1));
        }
        this.beaconing = Objects.requireNonNull(beaconing, "beaconing cannot be null");
        this.privateStatsId = new WamPrivateStatsId();
        this.privateStatsUploader = new WamPrivateStatsUploader(new WamPrivateStatsTokenIssuer(client));
        this.samplingOverride = new WamSamplingOverride();
        this.prevSessionGlobals = new EnumMap<>(WamChannel.class);
        this.initQueue = new ConcurrentLinkedQueue<>();
    }

    /**
     * Returns the current instant, used for the WAM commit-time
     * global ({@code 47}), the {@code t} attribute of the upload IQ
     * stanza, and the connectivity-wait deadline.
     *
     * <p>{@link DefaultWamService} returns {@link Instant#now()}; tests
     * return a controlled clock value.
     *
     * @return the current instant on the underlying clock
     */
    protected abstract Instant now();

    /**
     * Suspends the current thread for at least the given number of
     * milliseconds, used by the retry-backoff and connectivity-wait
     * loops.
     *
     * <p>{@link DefaultWamService} delegates to {@link Thread#sleep};
     * tests return immediately (or record the requested delay).
     *
     * @param millis the duration to sleep, in milliseconds
     * @throws InterruptedException if the thread is interrupted while
     *                              sleeping
     */
    protected abstract void sleep(long millis) throws InterruptedException;

    /**
     * Schedules a recurring task to run after {@code initialDelaySeconds}
     * and then every {@code periodSeconds} until
     * {@link #cancelAllScheduled()} is called.
     *
     * <p>Used by {@link #initialize()} to arm the five-second serialize
     * check and the 120-second flush cycle. {@link DefaultWamService}
     * backs this with a virtual-thread scheduled executor; tests record
     * the schedule and drive ticks deterministically.
     *
     * @param task                the task to run on every tick
     * @param initialDelaySeconds the delay before the first tick, in
     *                            seconds
     * @param periodSeconds       the delay between successive ticks,
     *                            in seconds
     */
    protected abstract void scheduleRecurring(Runnable task, long initialDelaySeconds, long periodSeconds);

    /**
     * Cancels every recurring task previously scheduled through
     * {@link #scheduleRecurring}. Called from {@link #close()}.
     */
    protected abstract void cancelAllScheduled();

    /**
     * Returns the number of events currently queued in the pending
     * list for the given channel.
     *
     * <p>Package-private hook used by behavioural tests to observe
     * commit dispatch without invoking the full flush pipeline. The
     * production code never reads this state externally.
     *
     * @param channel the channel to query
     * @return the number of queued events for {@code channel}
     */
    int pendingCount(WamChannel channel) {
        var list = pending.get(channel);
        return list == null ? 0 : list.size();
    }

    /**
     * Returns the number of deferred actions currently sitting in
     * {@link #initQueue}, waiting for the next call to
     * {@link #initialize()} to drain them.
     *
     * <p>Package-private hook used by behavioural tests covering
     * the pre-init {@code commit} / {@code commitAndWaitForFlush}
     * deferral path.
     *
     * @return the queue size
     */
    int initQueueSize() {
        return initQueue.size();
    }

    /**
     * Returns the current value of the per-channel sequence number
     * counter (the value the next call to
     * {@link #nextSequenceNumber} would observe before incrementing).
     *
     * <p>Package-private hook used by behavioural tests covering
     * the per-channel counter wrap and independence semantics.
     *
     * @param channel the channel to query
     * @return the next sequence number to be emitted on a flush
     */
    int sequenceNumberFor(WamChannel channel) {
        return sequenceNumbers.get(channel).get();
    }

    /**
     * Pre-sets the per-channel sequence counter so behavioural tests
     * can probe the wraparound from {@link #MAX_SEQUENCE_NUMBER} back
     * to {@code 1} without committing 65 535 events.
     *
     * @param channel the channel to update
     * @param value   the new counter value
     */
    void setSequenceNumberForTesting(WamChannel channel, int value) {
        sequenceNumbers.get(channel).set(value);
    }

    /**
     * Drains every deferred action currently sitting in
     * {@link #initQueue}, re-running each one against the now-ready
     * service.
     *
     * <p>Mirrors {@code WAWebWamInitQueue.processQueuedJobs} in the
     * live JS bundle. Invoked once from {@link #initialize()} as the
     * last setup step before recurring schedulers arm; package-private
     * so behavioural tests bypassing the full {@code initialize()}
     * sequence can still trigger the drain.
     */
    @WhatsAppWebExport(moduleName = "WAWebWamInitQueue", exports = "processQueuedJobs", adaptation = WhatsAppAdaptation.ADAPTED)
    void drainInitQueue() {
        Runnable action;
        while ((action = initQueue.poll()) != null) {
            action.run();
        }
    }

    /**
     * Forces the {@code initialized} flag to {@code true} without
     * running the full {@link #initialize()} sequence.
     *
     * <p>Package-private hook used by behavioural tests that want to
     * drive a deterministic {@link #flushChannel} or {@link #flush}
     * cycle without paying for the store-priming, AB-props snapshot,
     * and scheduler-arming steps that {@code initialize()} runs in
     * production. Tests are responsible for any volatile globals they
     * rely on being non-default — most leave them at their compile-time
     * defaults (null / 0), which {@link #buildFullCurrentGlobals}
     * already handles by emitting only the present entries.
     */
    void markInitializedForTesting() {
        this.initialized = true;
    }

    /**
     * Overwrites the {@link #deviceName} global without going through
     * {@link #initialize()}.
     *
     * <p>Package-private hook for in-process tests that need to mutate
     * a single global between flushes to assert the
     * {@code prevSessionGlobals} dirty-write behaviour.
     *
     * @param deviceName the new device name, or {@code null} to clear
     *                   it
     */
    void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    /**
     * Initializes the service by snapshotting session globals from the
     * client store, loading sampling overrides from AB props, and
     * starting the periodic flush threads.
     *
     * <p>This method should be called once after the client has
     * authenticated and the store's JID and version are available.
     */
    @WhatsAppWebExport(moduleName = "WAWebWam", exports = "initWamRuntime", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebWam", exports = "commitOnSet", adaptation = WhatsAppAdaptation.ADAPTED)
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
        this.pushPhase = getPushPhase();

        var configs = abPropsService.samplingConfigs();
        if (!configs.isEmpty()) {
            samplingOverride.replaceAll(configs);
        }

        primeSequenceNumbers();
        restorePendingBuffers();

        this.initialized = true;

        drainInitQueue();

        scheduleRecurring(this::checkMidCycleUpload, SERIALIZE_INTERVAL_SECONDS, SERIALIZE_INTERVAL_SECONDS);
        scheduleRecurring(this::flush, FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS);

        // Cobalt does not persist PS IDs across sessions, so every
        // rotation group is treated as freshly created on each
        // initialization, matching WAWebWamPrivateStats.initPrivateStats
        // emitting a CREATED PsIdUpdateEvent for entries with no prior
        // stored value.
        for (var info : privateStatsId.snapshotAll()) {
            commit(new PsIdUpdateEventBuilder()
                    .psIdAction(PsIdAction.CREATED)
                    .psIdKey(info.keyHashInt())
                    .psIdRotationFrequence(info.rotationDays())
                    .build());
        }
    }

    /**
     * Derives the PS country code from the user's phone number, matching
     * WhatsApp Web's {@code WAWebL10NCountryCodes.getCountryShortcodeByPhone}.
     * @return the two-letter ISO 3166-1 alpha-2 country code in
     *         lowercase, or {@code null} if not derivable
     */
    @WhatsAppWebExport(moduleName = "WAWebL10NCountryCodes",
            exports = "getCountryShortcodeByPhone",
            adaptation = WhatsAppAdaptation.ADAPTED)
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
     * <p>If the service has not yet been initialised, the commit is
     * deferred into {@link #initQueue} and replayed on the next call
     * to {@link #initialize()}, matching WhatsApp Web's
     * {@code WAWebWamInitQueue.queueEvent} fallback path used when
     * {@code WAWebWamRuntimeProvider.getWamRuntime()} returns
     * {@code null}. Validation and sampling run at drain time, against
     * the live state.
     * @param event the event to commit, must not be {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebWamCodegenWamEvent", exports = "WamEvent", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebWamInitQueue", exports = "queueEvent", adaptation = WhatsAppAdaptation.ADAPTED)
    public void commit(WamEventSpec event) {
        Objects.requireNonNull(event, "event cannot be null");

        // WAWebWamCodegenWamEvent.commit: getWamRuntime() ?? queueEvent.
        // When the runtime isn't initialised yet, defer the entire
        // commit (validation, sampling, dispatch) to drain time.
        if (!initialized) {
            initQueue.offer(() -> commit(event));
            return;
        }

        if (!event.markCommitted()) {
            LOGGER.warning("WAM redundant commit: " + event.getClass().getSimpleName());
            return;
        }

        // WAWebWamCodegenWamEvent.WamEvent.runPreCommitValidation: try { runPreCommitValidation() } catch { ... commit DroppedEvent }
        if (!event.validate()) {
            LOGGER.warning("WAM event failed validation: " + event.getClass().getSimpleName());
            return;
        }

        var weight = effectiveWeight(event);
        if (weight > 1) {
            if (DataUtils.randomInt(weight) != 0) {
                return;
            }
        }

        var pe = new WamPendingEvent(event, now().getEpochSecond());
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
     * <p>If the service has not yet been initialised, the commit is
     * deferred into {@link #initQueue} alongside a bridge that
     * completes the returned future once the drained commit's own
     * future completes, matching WhatsApp Web's
     * {@code WAWebWamInitQueue.queueEvent(event, waitForFlush=true)}
     * fallback path.
     * @param event the event to commit, must not be {@code null}
     * @return a future that completes when the event's buffer is flushed,
     *         or completes immediately if the event was sampled out or
     *         failed validation
     */
    @WhatsAppWebExport(moduleName = "WAWebWamCodegenWamEvent", exports = "WamEvent", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebWamInitQueue", exports = "queueEvent", adaptation = WhatsAppAdaptation.ADAPTED)
    public CompletableFuture<Void> commitAndWaitForFlush(WamEventSpec event) {
        Objects.requireNonNull(event, "event cannot be null");
        if (!initialized) {
            var deferred = new CompletableFuture<Void>();
            initQueue.offer(() -> {
                var inner = commitAndWaitForFlush(event);
                inner.whenComplete((value, error) -> {
                    if (error != null) {
                        deferred.completeExceptionally(error);
                    } else {
                        deferred.complete(value);
                    }
                });
            });
            return deferred;
        }
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
            if (DataUtils.randomInt(weight) != 0) {
                return CompletableFuture.completedFuture(null);
            }
        }

        var future = new CompletableFuture<Void>();
        var pe = new WamPendingEvent(event, now().getEpochSecond(), future);
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
     * <p>For each channel with pending events, one or more buffers
     * are built and sent. Each buffer is capped at
     * {@link #MAX_BUFFER_SIZE} bytes, and buffers exceeding
     * {@link #MAX_UPLOAD_SIZE} are dropped.
     *
     * <p>The pending list is atomically swapped to {@code null} so
     * that new events committed during the flush are not lost.
     */
    @WhatsAppWebExport(moduleName = "WAWebWam", exports = "sendAllLogs", adaptation = WhatsAppAdaptation.ADAPTED)
    public void flush() {
        if (!initialized) {
            return;
        }

        for (var channel : WamChannel.values()) {
            flushChannel(channel);
        }
    }

    /**
     * Checks whether any non-realtime channel has accumulated more
     * than {@link #MAX_BUFFER_SIZE} bytes of pending events and
     * triggers an early flush when it has.
     *
     * <p>Implements the mid-cycle upload behaviour from WhatsApp
     * Web's two-tier timing system, where a five-second serialization
     * timer checks for oversized buffers between the 120-second
     * rotation cycles.
     *
     * <p>Package-private so tests can drive a serialize check
     * deterministically instead of waiting on the scheduler.
     */
    void checkMidCycleUpload() {
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
     * <p>Package-private so behavioural tests can drive a specific
     * channel's flush deterministically without going through the
     * full {@link #flush()} sweep.
     *
     * @param channel the channel to flush
     */
    void flushChannel(WamChannel channel) {
        var events = swapPending(channel);
        if (events.isEmpty()) {
            return;
        }

        if (channel == WamChannel.PRIVATE) {
            var rotated = privateStatsId.rotateAndReportChanges();
            for (var info : rotated) {
                commit(new PsIdUpdateEventBuilder()
                        .psIdAction(PsIdAction.ROTATED)
                        .psIdKey(info.keyHashInt())
                        .psIdRotationFrequence(info.rotationDays())
                        .build());
            }
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
     * Flushes a list of events for the given channel, building one
     * or more buffers capped at {@link #MAX_BUFFER_SIZE}.
     *
     * <p>Buffer rotation uses a post-check. The event that pushes
     * the buffer over the limit stays in the current buffer, and a
     * new buffer is started for subsequent events, matching WhatsApp
     * Web's behaviour where a buffer may momentarily exceed the
     * limit by one event.
     *
     * @param channel   the transport channel
     * @param events    the events to flush
     * @param bufferKey the beaconing buffer key
     */
    private void flushEventList(WamChannel channel, List<WamPendingEvent> events, String bufferKey) {
        var beacons = new OptionalLong[events.size()];
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
    private void buildAndSend(WamChannel channel, List<WamPendingEvent> events, int[] weights, OptionalLong[] beacons, byte[] globalsBytes, int from, int to, int size) {
        if (size > MAX_UPLOAD_SIZE) {
            LOGGER.warning("Dropping WAM buffer of " + size + " bytes (exceeds upload limit)");
            commit(new WamClientErrorsEventBuilder()
                    .wamClientBufferDropErrorCount(1)
                    .build());
            completeFutures(events, from, to);
            return;
        }

        var buffer = new byte[size];
        var encoder = WamEventEncoder.of(buffer);
        writeHeader(encoder, channel);
        encoder.writeRaw(globalsBytes, 0, globalsBytes.length);

        for (var i = from; i < to; i++) {
            var pe = events.get(i);
            writePerEventGlobals(pe, channel, beacons[i], encoder);
            pe.event().encode(encoder, weights[i]);
        }

        assert encoder.written() == size : "Buffer size mismatch: wrote " + encoder.written() + " but allocated " + size;

        var saveKey = persistBuffer(events, weights, from, to);

        if (channel == WamChannel.PRIVATE) {
            sendPrivateWithRetry(buffer);
        } else {
            sendWithRetry(buffer);
        }

        removePersistedBuffer(saveKey);
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
     * Encodes the session globals for the given channel, writing
     * only globals that have changed since the last flush for this
     * channel.
     *
     * <p>On the first call for a channel all globals are written.
     * On subsequent calls only dirty globals and null transitions
     * are written. Matches WhatsApp Web's dirty-tracking approach
     * where each {@code WamContext} maintains its own independent
     * {@code prevGlobals}.
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
        var encoder = WamEventEncoder.of(bytes);
        for (var entry : dirty) {
            WamGlobalEncoder.writeDynamicGlobal(entry.getKey(), entry.getValue(), encoder);
        }
        for (var fieldId : nullTransitions) {
            WamGlobalEncoder.writeNullGlobal(fieldId, encoder);
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
            // 6605 - webcWebArch (regular) - WAWebWam.getPushPhase
            if (pushPhase != null) globals.put(6605, pushPhase);
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
    private int computePerEventGlobalsSize(WamPendingEvent pe, WamChannel channel, OptionalLong beacon) {
        var size = WamGlobalEncoder.commitTimeSize(pe.commitTimeSeconds());
        if (beacon.isPresent()) {
            size += WamGlobalEncoder.beaconSessionIdSize(beacon.getAsLong());
        }
        if (channel == WamChannel.PRIVATE) {
            var psId = privateStatsId.getValueForHash(pe.event().privateStatsId());
            size += WamGlobalEncoder.psIdSize(psId);
        }
        return size;
    }

    /**
     * Writes the per-event globals (commit time, beaconing sequence,
     * private stats id) into the encoder.
     *
     * @param pe      the pending event
     * @param channel the transport channel
     * @param beacon  the pre-computed beacon sequence number
     * @param encoder the destination encoder
     */
    private void writePerEventGlobals(WamPendingEvent pe, WamChannel channel, OptionalLong beacon, WamEventEncoder encoder) {
        WamGlobalEncoder.writeCommitTime(pe.commitTimeSeconds(), encoder);
        if (beacon.isPresent()) {
            WamGlobalEncoder.writeBeaconSessionId(beacon.getAsLong(), encoder);
        }
        if (channel == WamChannel.PRIVATE) {
            var psId = privateStatsId.getValueForHash(pe.event().privateStatsId());
            WamGlobalEncoder.writePsId(psId, encoder);
        }
    }

    /**
     * Atomically swaps the pending list for the given channel with
     * {@code null} and returns the old contents.
     *
     * <p>Uses {@link ConcurrentHashMap#compute} to ensure that no
     * concurrent {@link #commit} call can append to the returned
     * list after the swap.
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
     * Writes the 8-byte WAM buffer header into the encoder.
     *
     * @param encoder the destination encoder
     * @param channel the transport channel
     */
    private void writeHeader(WamEventEncoder encoder, WamChannel channel) {
        var headerBytes = new byte[HEADER_SIZE];
        System.arraycopy(WAM_MAGIC, 0, headerBytes, 0, WAM_MAGIC.length);
        headerBytes[3] = (byte) PROTOCOL_VERSION;
        headerBytes[4] = (byte) STREAM_ID;
        DataUtils.putShort(headerBytes, 5, (short) nextSequenceNumber(channel), ByteOrder.LITTLE_ENDIAN);
        headerBytes[7] = (byte) channel.id();
        encoder.writeRaw(headerBytes, 0, HEADER_SIZE);
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
        var counter = sequenceNumbers.get(channel);
        var oldValue = counter.getAndUpdate(current -> {
            var next = current + 1;
            return next > MAX_SEQUENCE_NUMBER ? 1 : next;
        });
        client.store().putWamSequenceNumber(channel, counter.get());
        return oldValue;
    }

    /**
     * Primes the in-memory sequence-number counters from the persisted
     * values in the store, ensuring the wire-level sequence does not
     * reset across sessions.
     */
    private void primeSequenceNumbers() {
        var store = client.store();
        for (var channel : WamChannel.values()) {
            var stored = store.findWamSequenceNumber(channel);
            if (stored.isPresent()) {
                sequenceNumbers.get(channel).set(stored.getAsInt());
            }
        }
    }

    /**
     * Persists the events in {@code [from, to)} to a new file under the
     * store-managed buffer directory and returns the save key that
     * identifies the file.
     *
     * <p>The file format is a stream of WAM event entries, each preceded
     * by a {@code commitTime} global written via
     * {@link WamGlobalEncoder#writeCommitTime}. On the next session the
     * events are decoded by {@link #restorePendingBuffers} and re-injected
     * into the pending list for their channel.
     *
     * @param events  the full event list
     * @param weights the resolved sampling weights parallel to events
     * @param from    the inclusive start index
     * @param to      the exclusive end index
     * @return the save key on success, or {@code null} on I/O failure
     */
    private String persistBuffer(List<WamPendingEvent> events, int[] weights, int from, int to) {
        if (from >= to) {
            return null;
        }
        var saveKey = generateSaveKey();
        try (var out = client.store().openWamPendingBufferWriter(saveKey)) {
            var encoder = WamEventEncoder.of(out);
            for (var i = from; i < to; i++) {
                var pe = events.get(i);
                WamGlobalEncoder.writeCommitTime(pe.commitTimeSeconds(), encoder);
                pe.event().encode(encoder, weights[i]);
            }
        } catch (IOException error) {
            LOGGER.warning("Failed to persist WAM buffer " + saveKey + ": " + error.getMessage());
            return null;
        }
        return saveKey;
    }

    /**
     * Removes a previously-persisted buffer file.
     *
     * @param saveKey the save key returned by {@link #persistBuffer},
     *                or {@code null} for a no-op
     */
    private void removePersistedBuffer(String saveKey) {
        if (saveKey == null) {
            return;
        }
        try {
            client.store().removeWamPendingBuffer(saveKey);
        } catch (IOException error) {
            LOGGER.warning("Failed to remove persisted WAM buffer " + saveKey + ": " + error.getMessage());
        }
    }

    /**
     * Reads every previously-persisted buffer file, decodes the events
     * back into {@link WamPendingEvent}s, and re-injects them into the
     * pending list for their respective channels.
     *
     * <p>Each file is deleted as soon as its contents have been
     * re-injected; the next flush cycle will re-persist whatever still
     * needs to ship.
     */
    private void restorePendingBuffers() {
        var keys = client.store().wamPendingBufferKeys();
        for (var saveKey : keys) {
            try {
                var stream = client.store().openWamPendingBufferReader(saveKey);
                if (stream.isEmpty()) {
                    continue;
                }
                try (var in = stream.get()) {
                    var decoder = WamEventDecoder.of(in);
                    while (decoder.hasMore()) {
                        var header = decoder.readHeader();
                        if (WamEventDecoder.fieldIdOf(header) != COMMIT_TIME_FIELD_ID) {
                            LOGGER.warning("Persisted WAM buffer " + saveKey
                                    + " has unexpected leading field "
                                    + WamEventDecoder.fieldIdOf(header));
                            break;
                        }
                        var commitTime = decoder.readInt(header);
                        var event = WamEventRegistry.decode(decoder);
                        var pendingEvent = new WamPendingEvent(event, commitTime);
                        pending.compute(event.channel(), (_, list) -> {
                            if (list == null) {
                                list = new ArrayList<>();
                            }
                            list.add(pendingEvent);
                            return list;
                        });
                    }
                }
                client.store().removeWamPendingBuffer(saveKey);
            } catch (Exception error) {
                LOGGER.warning("Failed to restore persisted WAM buffer " + saveKey + ": " + error.getMessage());
            }
        }
    }

    /**
     * Generates a fresh save key for a persisted buffer, combining a
     * monotonic timestamp suffix with a random prefix to avoid
     * collisions when many buffers are flushed in rapid succession.
     *
     * @return a filesystem-safe save key
     */
    private static String generateSaveKey() {
        return Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36)
                + "_" + System.currentTimeMillis();
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

        var deadline = now().toEpochMilli() + CONNECTIVITY_WAIT_TIMEOUT_MS;
        try {
            while (!client.isConnected() && now().toEpochMilli() < deadline) {
                sleep(1_000);
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
                    sleep(delay);
                    continue;
                }

                LOGGER.warning("WAM upload failed with error " + errorCode + ", dropping buffer");
                commit(new WamClientErrorsEventBuilder()
                        .wamClientBufferDropErrorCount(1)
                        .build());
                return;
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                if (attempt < MAX_RETRIES) {
                    try {
                        var delay = computeBackoffDelay(attempt);
                        LOGGER.fine("WAM upload failed (" + e.getMessage() + "), retrying in " + delay + "ms");
                        sleep(delay);
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    LOGGER.warning("WAM upload failed after " + MAX_RETRIES + " retries: " + e.getMessage());
                    commit(new WamClientErrorsEventBuilder()
                            .wamClientBufferDropErrorCount(1)
                            .build());
                }
            }
        }
    }

    /**
     * Sends a private-channel WAM buffer through the
     * {@link WamPrivateStatsUploader} with retry on transient server errors.
     *
     * <p>Mirrors the behaviour of the WA Web {@code privateStatsUpload}
     * module. Each attempt:
     *
     * <ol>
     *   <li>Acquires a fresh single-use authentication token via
     *       {@link WamPrivateStatsTokenIssuer} (the {@code <sign_credential>}
     *       IQ round-trip with the Ed25519 blinded-token VOPRF).</li>
     *   <li>POSTs a multipart {@code message} body to
     *       {@code https://dit.whatsapp.net/deidentified_telemetry} with
     *       the buffer authenticated by
     *       {@code HMAC-SHA256(sharedSecret, buffer)}.</li>
     * </ol>
     *
     * <p>Retry policy follows WA Web's classification:
     * {@code 200} returns immediately, {@code 500}/network errors retry up
     * to {@link #MAX_RETRIES} times with exponential backoff,
     * {@code 400}/{@code 401}/{@code 429}/other are permanent and the
     * buffer is dropped (with a {@code WamClientErrorsEvent} accounting
     * for the dropped buffer).
     *
     * @param buffer the encoded WAM buffer
     */
    private void sendPrivateWithRetry(byte[] buffer) {
        waitIfDisconnected();

        for (var attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            var result = privateStatsUploader.upload(buffer);
            switch (result.result()) {
                case SUCCESS -> {
                    return;
                }
                case ERROR_SERVER_OTHER, ERROR_OTHER, ERROR_CREDENTIAL -> {
                    if (attempt < MAX_RETRIES) {
                        try {
                            var delay = computeBackoffDelay(attempt);
                            LOGGER.fine("Private WAM upload got " + result.result()
                                    + " (HTTP " + result.httpResponseCode() + "), retrying in "
                                    + delay + "ms (attempt " + (attempt + 1) + ")");
                            sleep(delay);
                        } catch (InterruptedException _) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        continue;
                    }
                    LOGGER.warning("Private WAM upload failed after " + MAX_RETRIES
                            + " retries: " + result.result()
                            + " (HTTP " + result.httpResponseCode() + ")");
                    commit(new WamClientErrorsEventBuilder()
                            .wamClientBufferDropErrorCount(1)
                            .build());
                    return;
                }
                default -> {
                    LOGGER.warning("Private WAM upload failed permanently: " + result.result()
                            + " (HTTP " + result.httpResponseCode() + ")");
                    commit(new WamClientErrorsEventBuilder()
                            .wamClientBufferDropErrorCount(1)
                            .build());
                    return;
                }
            }
        }
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
                .attribute("t", String.valueOf(now().getEpochSecond()))
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
     * <p>Package-private for testing — the formula's behaviour at the
     * lower clamp ({@code attempt} small enough that {@code 2^attempt
     * < RETRY_BASE_DELAY_MS}), the unclamped middle, and the upper
     * clamp ({@code 2^attempt > RETRY_MAX_DELAY_MS}) is verified by
     * {@code WamServiceTest.RetryBackoff}.
     *
     * @param attempt the zero-based retry attempt number
     * @return the delay in milliseconds, in
     *         {@code [RETRY_BASE_DELAY_MS, RETRY_MAX_DELAY_MS * 1.1]}
     */
    static long computeBackoffDelay(int attempt) {
        var delay = attempt == 0 ? RETRY_BASE_DELAY_MS : (long) Math.pow(2, attempt);
        if (delay > RETRY_MAX_DELAY_MS) delay = RETRY_MAX_DELAY_MS;
        if (delay < RETRY_BASE_DELAY_MS) delay = RETRY_BASE_DELAY_MS;
        var jitter = (long) (delay * 0.1 * DataUtils.randomDouble());
        return delay + jitter;
    }

    /**
     * Returns the {@code webcWebArch} push-phase string for the current
     * build, or {@code null} if no phase is configured.
     *
     * <p>The JS implementation maps the build constant
     * {@code PUSH_PHASE} through a fixed alias table:
     * {@code "sandcastle" -> "dev"}, {@code "trunkstable" -> "C1"};
     * unmapped phases pass through. When the {@code 26256} gatekeeper is
     * set the value is forced to {@code "jest-e2e"}.
     * @return the push-phase string, or {@code null} when not applicable
     */
    @WhatsAppWebExport(moduleName = "WAWebWam", exports = "getPushPhase", adaptation = WhatsAppAdaptation.ADAPTED)
    private static String getPushPhase() {
        return null;
    }

    /**
     * Stops the flush threads and performs a final flush of all
     * pending events. Once closed, the service must be re-initialized
     * before further use.
     */
    public void close() {
        cancelAllScheduled();
        flush();
        initialized = false;
    }

    /**
     * Returns the WAM {@link MediaType} classification for the payload
     * carried by the given {@link ChatMessageInfo}.
     *
     * <p>The method delegates to {@link #getWamMediaType(MessageContainer)}
     * after resolving the wrapped message container. {@link MediaType#NONE}
     * is returned for unrecognised or unclassified message types.
     *
     * @param info the chat message info whose payload is being
     *             classified; may be {@code null}
     * @return the WAM media-type classification for the resolved
     * payload, or {@link MediaType#NONE} if no matching classification
     * exists
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "getWamMediaType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public MediaType getWamMediaType(ChatMessageInfo info) {
        return info == null ? MediaType.NONE : getWamMediaType(info.message());
    }

    /**
     * Returns the WAM {@link MediaType} classification for the resolved
     * content of the given {@link MessageContainer}.
     *
     * <p>The method mirrors WA Web's {@code getWamMediaType} switch table:
     * every branch of the WA Web function corresponds to an
     * {@code instanceof} check here. The mapping covers the common media
     * payloads used in PSA-style flows (image, video, GIF, document,
     * audio, PTT, sticker) plus the placeholder entries for the fallback
     * categories. Unrecognised types fall back to {@link MediaType#NONE}.
     *
     * @param container the container whose resolved content should be
     *                  classified; {@code null} yields
     *                  {@link MediaType#NONE}
     * @return the WAM media-type classification for the resolved
     * payload
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "getWamMediaType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public MediaType getWamMediaType(MessageContainer container) {
        if (container == null) {
            return MediaType.NONE;
        }
        var content = container.content();
        return switch (content) {
            case ImageMessage ignored -> MediaType.PHOTO;
            case VideoMessage video -> video.gifPlayback() ? MediaType.GIF : MediaType.VIDEO;
            case AudioMessage audio -> audio.ptt() ? MediaType.PTT : MediaType.AUDIO;
            case DocumentMessage ignored -> MediaType.DOCUMENT;
            case StickerMessage ignored -> MediaType.STICKER;
            case StickerPackMessage ignored -> MediaType.STICKER_PACK;
            case ReactionMessage ignored -> MediaType.REACTION;
            case EncReactionMessage ignored -> MediaType.REACTION;
            case PollCreationMessage ignored -> MediaType.POLL_CREATE;
            case PollUpdateMessage ignored -> MediaType.POLL_VOTE;
            case ContactMessage ignored -> MediaType.CONTACT;
            case ContactsArrayMessage ignored -> MediaType.CONTACT_ARRAY;
            case LocationMessage ignored -> MediaType.LOCATION;
            case LiveLocationMessage ignored -> MediaType.LIVE_LOCATION;
            case ProductMessage ignored -> MediaType.PRODUCT_IMAGE;
            case ListMessage ignored -> MediaType.LIST;
            case ListResponseMessage ignored -> MediaType.LIST_REPLY;
            case OrderMessage ignored -> MediaType.ORDER;
            case EventResponseMessage ignored -> MediaType.EVENT_RESPOND;
            case EncEventResponseMessage ignored -> MediaType.EVENT_RESPOND;
            case EventMessage ignored -> MediaType.EVENT_CREATE;
            case AlbumMessage ignored -> MediaType.MEDIA_ALBUM;
            case PinInChatMessage ignored -> MediaType.PIN_IN_CHAT;
            case ExtendedTextMessage ignored -> MediaType.TEXT;
            // Cobalt-only fallthrough: collection/unknown maps to NONE.
            case null, default -> MediaType.NONE;
        };
    }

    /**
     * Returns the WAM {@link MessageType} classification derived from the
     * chat JID carried by the given {@link ChatMessageInfo}.
     *
     * <p>The method mirrors WA Web's {@code WAWebWamMsgUtils.getWamMessageType}
     * switch on {@code e.isStatus() / e.isGroupMsg() / isBroadcast(e.id.remote)
     * / isNewsletter(e.id.remote)} fallback to
     * {@link MessageType#INDIVIDUAL}. The {@code STATUS} branch is
     * disambiguated before {@code BROADCAST} because status messages live on
     * the broadcast server but must not be reported as generic broadcasts.
     *
     * @param info the chat message info whose destination is being
     *             classified; {@code null} yields {@link MessageType#INDIVIDUAL}
     * @return the WAM message-type classification, defaulting to
     * {@link MessageType#INDIVIDUAL} when the destination does not match any
     * recognised server category
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "getWamMessageType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public MessageType getWamMessageType(ChatMessageInfo info) {
        if (info == null) {
            return MessageType.INDIVIDUAL;
        }
        var parent = info.key().parentJid().orElse(null);
        if (parent == null) {
            return MessageType.INDIVIDUAL;
        }
        return getWamMessageType(parent);
    }

    /**
     * Returns the WAM {@link MessageType} classification derived from the
     * given chat JID.
     *
     * <p>The method is the JID-level fan-out of
     * {@link #getWamMessageType(ChatMessageInfo)}: it classifies purely based
     * on the server component of the supplied JID.
     *
     * @param chatJid the chat JID whose server is being classified;
     *                {@code null} yields {@link MessageType#INDIVIDUAL}
     * @return the WAM message-type classification, defaulting to
     * {@link MessageType#INDIVIDUAL} when the server is a user / LID / bot
     * domain or unrecognised
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "getWamMessageType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public MessageType getWamMessageType(Jid chatJid) {
        if (chatJid == null) {
            return MessageType.INDIVIDUAL;
        }
        if (chatJid.isStatusBroadcastAccount()) {
            return MessageType.STATUS;
        }
        if (chatJid.hasGroupOrCommunityServer()) {
            return MessageType.GROUP;
        }
        if (chatJid.hasBroadcastServer()) {
            return MessageType.BROADCAST;
        }
        if (chatJid.hasNewsletterServer()) {
            return MessageType.CHANNEL;
        }
        return MessageType.INDIVIDUAL;
    }

    /**
     * Returns the WAM {@link MessageType} classification derived from the
     * stanza-level {@link com.github.auties00.cobalt.message.receive.stanza.MessageType}
     * enum produced during parsing.
     *
     * <p>WA Web feeds {@code msgInfo.type} (a string of {@code chat /
     * group / peer_broadcast / other_broadcast / direct_peer_status /
     * other_status}) into {@code getMessageTypeFromMsgInfoType}, which
     * normalises broadcasts into {@link MessageType#BROADCAST} and status
     * flavours into {@link MessageType#STATUS}. Cobalt classifies the
     * stanza once during parsing into
     * {@link com.github.auties00.cobalt.message.receive.stanza.MessageType},
     * so this helper performs the equivalent normalisation over that enum
     * directly.
     *
     * @param stanzaType the parser-level message type; {@code null}
     *                   yields {@link MessageType#INDIVIDUAL}
     * @return the WAM message-type classification, defaulting to
     * {@link MessageType#INDIVIDUAL} when the parser-level type maps to
     * {@code CHAT} or {@code PEER_CHAT} or is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "getMessageTypeFromMsgInfoType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public MessageType getWamMessageTypeFromStanzaType(
            com.github.auties00.cobalt.message.receive.stanza.MessageType stanzaType
    ) {
        if (stanzaType == null) {
            return MessageType.INDIVIDUAL;
        }
        return switch (stanzaType) {
            case CHAT, PEER_CHAT -> MessageType.INDIVIDUAL;
            case GROUP -> MessageType.GROUP;
            case PEER_BROADCAST, OTHER_BROADCAST -> MessageType.BROADCAST;
            case DIRECT_PEER_STATUS, OTHER_STATUS -> MessageType.STATUS;
        };
    }

    /**
     * Returns the WAM {@link E2eDeviceType} classification of the sender
     * JID relative to the current account.
     *
     * <p>The classification tree mirrors WA Web's {@code getWamE2eSenderType}:
     * the sender is first bucketed as {@code MY} (current account) or
     * {@code OTHER} based on whether its user JID matches the stored
     * self-JID; within each bucket the sender is further classified as
     * {@code PRIMARY}, {@code COMPANION}, or {@code HOSTED_COMPANION}
     * based on the device id and server domain.
     *
     * @param senderJid the sender's full device JID; {@code null} yields
     *                  {@code null}
     * @param selfJid   the logged-in account's primary JID; may be
     *                  {@code null} when the account is not yet bound
     * @return the WAM classification, or {@code null} when the sender is
     * not a user/LID JID, matching WA Web's {@code instanceof Wid} guard
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "getWamE2eSenderType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public E2eDeviceType getWamE2eSenderType(Jid senderJid, Jid selfJid) {
        if (senderJid == null) {
            return null;
        }
        if (!senderJid.hasUserServer() && !senderJid.hasLidServer()
                && !senderJid.hasHostedServer() && !senderJid.hasHostedLidServer()) {
            return null;
        }
        var isMe = selfJid != null
                && selfJid.toUserJid().equals(senderJid.toUserJid());
        var isCompanion = senderJid.hasDevice();
        var isHosted = senderJid.hasHostedServer() || senderJid.hasHostedLidServer();
        if (isMe) {
            if (isCompanion) {
                return isHosted ? E2eDeviceType.MY_HOSTED_COMPANION : E2eDeviceType.MY_COMPANION;
            }
            return E2eDeviceType.MY_PRIMARY;
        }
        if (isCompanion) {
            return isHosted ? E2eDeviceType.OTHER_HOSTED_COMPANION : E2eDeviceType.OTHER_COMPANION;
        }
        return E2eDeviceType.OTHER_PRIMARY;
    }

    /**
     * Returns the WAM {@link MediaType} classification for an interactive
     * message based on its body variant.
     *
     * <p>The classification mirrors WA Web's {@code getInteractiveWamType}:
     * shop storefront variants map to {@link MediaType#SHOP_STOREFRONT},
     * carousels map to {@link MediaType#INTERACTIVE_CAROUSEL}, and native
     * flow variants delegate to the inner native-flow disambiguator that
     * separates {@code CTA_FLOW} ({@link MediaType#NONE}) from any other
     * native flow ({@link MediaType#INTERACTIVE_NFM}).
     *
     * @param interactive the interactive message whose body variant is
     *                    being classified; {@code null} yields
     *                    {@link MediaType#NONE}
     * @return the WAM media-type classification for the interactive
     * variant, or {@link MediaType#NONE} when no variant is set
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "getInteractiveWamType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public MediaType getInteractiveWamType(InteractiveMessage interactive) {
        if (interactive == null) {
            return MediaType.NONE;
        }
        InteractiveMessageContent content = interactive.content().orElse(null);
        if (content == null) {
            return MediaType.NONE;
        }
        return switch (content) {
            case InteractiveMessage.ShopMessage ignored -> MediaType.SHOP_STOREFRONT;
            case InteractiveMessage.CarouselMessage ignored -> MediaType.INTERACTIVE_CAROUSEL;
            case InteractiveMessage.NativeFlowMessage native_ -> getInteractiveNativeFlowWamType(native_);
            // Cobalt-only branch: collection messages have no WhatsApp Web counterpart in this mapper.
            case InteractiveMessage.CollectionMessage ignored -> MediaType.NONE;
        };
    }

    /**
     * Disambiguates the WAM {@link MediaType} for an interactive native
     * flow message based on the resolved native flow name.
     *
     * <p>WA Web treats the {@code CTA_FLOW} (galaxy) variant as
     * {@link MediaType#NONE} since it is filtered from interactive WAM
     * reporting; any other native flow name falls through to
     * {@link MediaType#INTERACTIVE_NFM}.
     *
     * @param nativeFlow the native flow message whose name drives the
     *                   classification; must not be {@code null}
     * @return {@link MediaType#NONE} when the native flow is the
     * {@code CTA_FLOW} (galaxy) variant, otherwise
     * {@link MediaType#INTERACTIVE_NFM}
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "getInteractiveWamType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static MediaType getInteractiveNativeFlowWamType(InteractiveMessage.NativeFlowMessage nativeFlow) {
        for (var button : nativeFlow.buttons()) {
            var name = button.name().orElse(null);
            // WAWebInteractiveMessagesNativeFlowName.CTA_FLOW resolves to "galaxy_message".
            if ("galaxy_message".equals(name)) {
                return MediaType.NONE;
            }
        }
        return MediaType.INTERACTIVE_NFM;
    }

    /**
     * Returns the WAM {@link AgentEngagementEnumType} classification for a
     * message exchanged with a bot.
     *
     * <p>The classification mirrors WA Web's {@code getWamAgentEngagementType}:
     * messages whose chat JID is itself a bot map to
     * {@link AgentEngagementEnumType#DIRECT_CHAT}; messages whose payload is
     * recognised as a bot query or a Meta-bot response map to
     * {@link AgentEngagementEnumType#INVOKED}; otherwise the helper returns
     * {@code null} so callers can omit the property from the WAM event.
     *
     * @param chatJid       the chat JID that hosts the message; {@code null}
     *                      yields {@code null}
     * @param isBotInvoked  {@code true} when the message originates from a
     *                      bot query or Meta-bot response; this corresponds
     *                      to the disjunction of WA Web's
     *                      {@code getIsBotQuery} and {@code getIsMetaBotResponse}
     * @return the WAM agent-engagement classification, or {@code null}
     * when the message is unrelated to a bot conversation
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "getWamAgentEngagementType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public AgentEngagementEnumType getWamAgentEngagementType(Jid chatJid, boolean isBotInvoked) {
        if (chatJid == null) {
            return null;
        }
        if (chatJid.isBot()) {
            return AgentEngagementEnumType.DIRECT_CHAT;
        }
        if (isBotInvoked) {
            return AgentEngagementEnumType.INVOKED;
        }
        return null;
    }

    /**
     * Returns the WAM {@link BotType} classification of a bot interaction.
     *
     * <p>The classification mirrors WA Web's {@code getWamBotType}: a
     * Meta-bot JID maps to {@link BotType#METABOT}; a 1P business bot
     * (either via the {@code BizBotType.BIZ_1P} flag or the
     * {@code BizBotAutomatedType.PARTIAL_1P} automated flag) maps to
     * {@link BotType#BOT_1P_BIZ}; a 3P business bot (via
     * {@code BizBotType.BIZ_3P} or {@code BizBotAutomatedType.FULL_3P})
     * maps to {@link BotType#BOT_3P_BIZ}; everything else falls through to
     * {@link BotType#UNKNOWN}.
     *
     * @param botJid        the JID involved in the bot interaction;
     *                      may be {@code null}
     * @param is1pBizBot    {@code true} when WA Web would classify the
     *                      interaction as {@code BizBotType.BIZ_1P} or
     *                      {@code BizBotAutomatedType.PARTIAL_1P}
     * @param is3pBizBot    {@code true} when WA Web would classify the
     *                      interaction as {@code BizBotType.BIZ_3P} or
     *                      {@code BizBotAutomatedType.FULL_3P}
     * @return the matching WAM bot type, defaulting to
     * {@link BotType#UNKNOWN}
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "getWamBotType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public BotType getWamBotType(Jid botJid, boolean is1pBizBot, boolean is3pBizBot) {
        if (botJid != null && botJid.isBot()) {
            return BotType.METABOT;
        }
        if (is1pBizBot) {
            return BotType.BOT_1P_BIZ;
        }
        if (is3pBizBot) {
            return BotType.BOT_3P_BIZ;
        }
        return BotType.UNKNOWN;
    }

    /**
     * Returns the WAM {@link InvisibleMessageCategoryType} classification
     * for the supplied stanza-level message category attribute.
     *
     * <p>WA Web defines a single recognised category, {@code MSG_CATEGORY.peer},
     * which maps to {@link InvisibleMessageCategoryType#PEER}; any other
     * value (including {@code null} and the empty string) yields
     * {@code null} so callers omit the property from the WAM event.
     *
     * @param category the {@code category} attribute carried on the
     *                 incoming stanza; may be {@code null}
     * @return {@link InvisibleMessageCategoryType#PEER} for {@code "peer"},
     * otherwise {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "getWamInvisibleMessageCatgoryType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public InvisibleMessageCategoryType getWamInvisibleMessageCategoryType(String category) {
        if (category == null || category.isEmpty()) {
            return null;
        }
        if ("peer".equals(category)) {
            return InvisibleMessageCategoryType.PEER;
        }
        return null;
    }

    /**
     * Returns whether any of the JIDs that participated in the message
     * exchange are LID-addressed.
     *
     * <p>WA Web's {@code msgIsLid} returns:
     * <ul>
     *   <li>for groups: the supplied {@code participantIsLid} flag
     *       (truthy-coerced).</li>
     *   <li>for status updates: whether the
     *       {@code key.id.participant} is a LID JID; missing values
     *       evaluate to {@code false}.</li>
     *   <li>otherwise: whether either the {@code from} or the {@code to}
     *       JID is LID-addressed.</li>
     * </ul>
     *
     * @param fromJid             the sender JID; may be {@code null}
     * @param toJid               the recipient JID; may be {@code null}
     * @param keyParticipantJid   the {@code key.participant} JID, used for
     *                            status updates; may be {@code null}
     * @param chatType            the WAM message-type classification of
     *                            the chat (used to disambiguate group /
     *                            status / other); must not be {@code null}
     * @param participantIsLid    {@code true} when the {@code participant}
     *                            attribute on a group stanza is LID-addressed
     * @return {@code true} when the relevant JID for the chat type is
     * LID-addressed, otherwise {@code false}
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMsgUtils", exports = "msgIsLid",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean msgIsLid(
            Jid fromJid,
            Jid toJid,
            Jid keyParticipantJid,
            MessageType chatType,
            boolean participantIsLid
    ) {
        if (chatType == MessageType.GROUP) {
            return participantIsLid;
        }
        if (chatType == MessageType.STATUS) {
            return keyParticipantJid != null && keyParticipantJid.hasLidServer();
        }
        var fromIsLid = fromJid != null && fromJid.hasLidServer();
        var toIsLid = toJid != null && toJid.hasLidServer();
        return fromIsLid || toIsLid;
    }

    /**
     * Fixed mapping from lower-case file extensions (without the leading
     * dot) to the corresponding WAM {@link DocumentType} bucket.
     *
     * <p>Populated verbatim from WA Web's
     * {@code WAWebProcessRawMediaLogging} extension table so that
     * {@link #logSendDocumentEvent(String, long)} produces identical
     * {@code documentType} / {@code documentExt} values for every known
     * extension. Unknown extensions fall back to
     * {@link DocumentType#OTHER} with an empty {@code documentExt}, again
     * mirroring WA Web.
     */
    @WhatsAppWebExport(moduleName = "WAWebProcessRawMediaLogging", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final Map<String, DocumentType> DOCUMENT_EXT_TO_TYPE = Map.<String, DocumentType>ofEntries(
            Map.entry("ai", DocumentType.IMAGE),
            Map.entry("ico", DocumentType.IMAGE),
            Map.entry("jpeg", DocumentType.IMAGE),
            Map.entry("jpg", DocumentType.IMAGE),
            Map.entry("png", DocumentType.IMAGE),
            Map.entry("ps", DocumentType.IMAGE),
            Map.entry("psd", DocumentType.IMAGE),
            Map.entry("svg", DocumentType.IMAGE),
            Map.entry("tif", DocumentType.IMAGE),
            Map.entry("tiff", DocumentType.IMAGE),
            Map.entry("3g2", DocumentType.VIDEO),
            Map.entry("3gp", DocumentType.VIDEO),
            Map.entry("avi", DocumentType.VIDEO),
            Map.entry("flv", DocumentType.VIDEO),
            Map.entry("h264", DocumentType.VIDEO),
            Map.entry("m4v", DocumentType.VIDEO),
            Map.entry("mkv", DocumentType.VIDEO),
            Map.entry("mov", DocumentType.VIDEO),
            Map.entry("mp4", DocumentType.VIDEO),
            Map.entry("mpg", DocumentType.VIDEO),
            Map.entry("mpeg", DocumentType.VIDEO),
            Map.entry("rm", DocumentType.VIDEO),
            Map.entry("vob", DocumentType.VIDEO),
            // The WhatsApp Web table maps "wmv" to AUDIO. The classification
            // is kept verbatim even though wmv is conventionally a video
            // container.
            Map.entry("wmv", DocumentType.AUDIO),
            Map.entry("aif", DocumentType.AUDIO),
            Map.entry("cda", DocumentType.AUDIO),
            Map.entry("mpa", DocumentType.AUDIO),
            Map.entry("opus", DocumentType.AUDIO),
            Map.entry("ogg", DocumentType.AUDIO),
            Map.entry("wlp", DocumentType.AUDIO),
            Map.entry("amr", DocumentType.AUDIO),
            Map.entry("mp3", DocumentType.AUDIO),
            Map.entry("m4a", DocumentType.AUDIO),
            Map.entry("aac", DocumentType.AUDIO),
            Map.entry("wav", DocumentType.AUDIO),
            Map.entry("wma", DocumentType.AUDIO),
            Map.entry("pdf", DocumentType.DOCUMENT),
            Map.entry("doc", DocumentType.DOCUMENT),
            Map.entry("docx", DocumentType.DOCUMENT),
            Map.entry("ppt", DocumentType.DOCUMENT),
            Map.entry("pptx", DocumentType.DOCUMENT),
            Map.entry("xls", DocumentType.DOCUMENT),
            Map.entry("xlsx", DocumentType.DOCUMENT),
            Map.entry("txt", DocumentType.DOCUMENT),
            Map.entry("rtf", DocumentType.DOCUMENT),
            Map.entry("tex", DocumentType.DOCUMENT),
            Map.entry("csv", DocumentType.DOCUMENT),
            Map.entry("wpd", DocumentType.DOCUMENT),
            Map.entry("7z", DocumentType.COMPRESSED_FILE),
            Map.entry("arj", DocumentType.COMPRESSED_FILE),
            Map.entry("deb", DocumentType.COMPRESSED_FILE),
            Map.entry("pkg", DocumentType.COMPRESSED_FILE),
            Map.entry("rar", DocumentType.COMPRESSED_FILE),
            Map.entry("rpm", DocumentType.COMPRESSED_FILE),
            Map.entry("gz", DocumentType.COMPRESSED_FILE),
            Map.entry("z", DocumentType.COMPRESSED_FILE),
            Map.entry("zip", DocumentType.COMPRESSED_FILE),
            Map.entry("apk", DocumentType.EXECUTABLE),
            Map.entry("bat", DocumentType.EXECUTABLE),
            Map.entry("bin", DocumentType.EXECUTABLE),
            Map.entry("cgi", DocumentType.EXECUTABLE),
            Map.entry("pl", DocumentType.EXECUTABLE),
            Map.entry("com", DocumentType.EXECUTABLE),
            Map.entry("exe", DocumentType.EXECUTABLE),
            Map.entry("gadget", DocumentType.EXECUTABLE),
            Map.entry("jar", DocumentType.EXECUTABLE),
            Map.entry("msi", DocumentType.EXECUTABLE),
            Map.entry("py", DocumentType.EXECUTABLE),
            Map.entry("wsf", DocumentType.EXECUTABLE)
    );

    /**
     * Commits the {@code SendDocumentEvent} (id 2172) for an outgoing
     * document send.
     *
     * <p>Mirrors WA Web's {@code WAWebProcessRawMediaLogging.logSendDocumentEvent}:
     * splits the filename on {@code .} and takes the last segment as the
     * extension, looks it up in {@link #DOCUMENT_EXT_TO_TYPE} to resolve
     * the {@link DocumentType}, and populates {@code documentSize} with
     * the raw file size in bytes. When the filename has no extension or
     * the extension is not a known key, the {@code documentExt} property
     * is emitted as the empty string and the type falls back to
     * {@link DocumentType#OTHER}, matching WA Web.
     *
     * <p>The {@code documentPageSize} WAM property is declared in the
     * event spec but never populated by WA Web's emission site, so it is
     * intentionally left unset here too.
     *
     * @param filename   the user-visible document filename; when
     *                   {@code null} the extension is resolved as the
     *                   empty string, matching WA Web's
     *                   {@code e?.split(".").pop() ?? ""} fallback
     * @param size       the raw decrypted document size in bytes
     */
    @WhatsAppWebExport(moduleName = "WAWebProcessRawMediaLogging", exports = "logSendDocumentEvent",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void logSendDocumentEvent(String filename, long size) {
        String extension;
        if (filename == null || filename.isEmpty()) {
            extension = "";
        } else {
            var dotIndex = filename.lastIndexOf('.');
            var tail = dotIndex < 0 ? filename : filename.substring(dotIndex + 1);
            extension = tail.toLowerCase(Locale.ROOT);
        }
        var normalizedExt = DOCUMENT_EXT_TO_TYPE.containsKey(extension) ? extension : "";
        var documentType = DOCUMENT_EXT_TO_TYPE.getOrDefault(normalizedExt, DocumentType.OTHER);
        commit(new SendDocumentEventBuilder()
                .documentSize((double) size)
                .documentType(documentType)
                .documentExt(normalizedExt)
                .build());
    }
}
