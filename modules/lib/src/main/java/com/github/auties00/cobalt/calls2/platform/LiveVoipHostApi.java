package com.github.auties00.cobalt.calls2.platform;

import com.github.auties00.cobalt.calls2.core.CallEventType;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppSessionException;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.store.linked.LinkedWhatsAppContactStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Wires the engine-to-host {@link VoipHostApi} downcalls onto the Cobalt client and JDK platform.
 *
 * <p>This is the production host for the wa-voip engine: each downcall the engine makes is satisfied by
 * a concrete Cobalt facility. Signaling stanzas go to the {@link LinkedWhatsAppClient}'s binary-XMPP
 * socket; the {@code call_sendto} host datagram downcall goes to an injected {@link MediaDatagramSink}
 * (the live web transport carries media as SCTP DATA over its data channel rather than through this seam);
 * randomness comes from a single {@link SecureRandom}; name resolution uses {@link InetAddress};
 * persistent storage resolves to a per-session directory under the Cobalt home; the contact lookup
 * consults the client's contact store; decoded frames and typed events are handed to injected sinks;
 * and structured logs go to a per-instance {@link System.Logger}. Every collaborator is supplied
 * through the constructor and held as a field, so the host reaches no service through a global accessor.
 *
 * <p>Because the WhatsApp Web original implements these as Emscripten {@code env.*_js_sync} WASM
 * imports backed by JavaScript, this class is where the browser-specific backing is replaced by JVM
 * facilities: a {@code DatagramChannel} for the browser's socket, {@link SecureRandom} for the
 * browser's CSPRNG, the JDK resolver for {@code gethostbyname}, the Cobalt session directory for the
 * browser's persistent storage, and the listener bus for the browser's event callback. No part of this
 * class binds native code; it is pure Java glue.
 *
 * @implNote This implementation maps each method to the corresponding recovered WASM import from the
 * wa-voip build {@code ff-tScznZ8P} import table (analysis.json): {@code sendSignalingXMPP_js_sync},
 * {@code call_sendto}, {@code gethostbyname}, {@code get_random_bytes_js},
 * {@code get_persistent_directory_path_js}, {@code get_bwe_ml_model_path_js},
 * {@code is_participant_known_contact_js}, {@code renderVideoFrame_js},
 * {@code query_browser_audio_processing_status_js_sync}, {@code loggingCallback_js_sync}, and
 * {@code on_call_event_js_sync}. The audio and video driver-control imports are intentionally not
 * mapped here; they belong to the driver contracts.
 */
public final class LiveVoipHostApi implements VoipHostApi {
    /**
     * The number of bytes the engine's key-sized random draw requests, recovered as the observed
     * argument to {@code get_random_bytes_js}.
     */
    private static final int OBSERVED_KEY_RANDOM_LENGTH = 32;

    /**
     * The audio-processing capability bitmask the host reports for its capture path.
     *
     * <p>The bitmask is the sum of {@code 1} for applied acoustic echo cancellation, {@code 2} for
     * applied noise suppression, and {@code 4} for applied automatic gain control, matching the engine's
     * {@code (echoCancellation?1)+(noiseSuppression?2)+(autoGainControl?4)} encoding. The
     * {@code javax.sound.sampled} {@code TargetDataLine} capture path applies none of the three, so its
     * bitmask is {@code 0}; this value makes the engine run its own full echo-cancellation,
     * noise-suppression, and gain-control chain rather than skip a stage it would wrongly believe the host
     * already applied.
     */
    private static final int CAPTURE_AUDIO_PROCESSING_STATUS = 0;

    /**
     * The directory name under the Cobalt home that holds per-session call-engine state.
     */
    private static final String CALLS_DIRECTORY = "calls";

    /**
     * Holds the owning client, used to send signaling stanzas and to reach the contact and account
     * sub-stores for the contact lookup and the session-scoped persistent directory.
     */
    private final LinkedWhatsAppClient whatsapp;

    /**
     * Holds the transport-owned datagram egress that backs {@link #sendDatagram(byte[], SocketAddress)}.
     */
    private final MediaDatagramSink datagramSink;

    /**
     * Holds the sink that presents decoded frames passed to {@link #renderVideoFrame(RenderedVideoFrame)}.
     */
    private final Consumer<RenderedVideoFrame> videoSink;

    /**
     * Holds the sink that receives typed events passed to {@link #onCallEvent(CallEventType, byte[])}.
     */
    private final BiConsumer<CallEventType, byte[]> callEventSink;

    /**
     * Holds the resolver that maps a model-type selector to a bundled model path for
     * {@link #bweMlModelPath(int)}, returning empty when the host ships no model for the type.
     */
    private final IntFunction<Optional<Path>> mlModelPathResolver;

    /**
     * Holds the base directory under which the per-session call directory is resolved.
     */
    private final Path baseDirectory;

    /**
     * Holds the cryptographically strong generator backing {@link #randomBytes(int)}.
     */
    private final SecureRandom secureRandom;

    /**
     * Holds the logger that receives engine log records routed through {@link #log}.
     */
    private final System.Logger logger;

    /**
     * Constructs a host bound to the given client, datagram egress, and event and frame sinks, rooting
     * the persistent directory at the default Cobalt home.
     *
     * <p>The persistent directory resolves under {@code $HOME/.cobalt}; the machine-learning model
     * resolver defaults to reporting no bundled model for any type, so the engine falls back to its
     * non-learned bandwidth estimator. Use
     * {@link #LiveVoipHostApi(LinkedWhatsAppClient, MediaDatagramSink, Consumer, BiConsumer, IntFunction, Path)}
     * to override either.
     *
     * @param whatsapp      the owning client
     * @param datagramSink  the transport-owned datagram egress
     * @param videoSink     the sink that presents decoded frames
     * @param callEventSink the sink that receives typed events
     */
    public LiveVoipHostApi(
            LinkedWhatsAppClient whatsapp,
            MediaDatagramSink datagramSink,
            Consumer<RenderedVideoFrame> videoSink,
            BiConsumer<CallEventType, byte[]> callEventSink
    ) {
        this(
                whatsapp,
                datagramSink,
                videoSink,
                callEventSink,
                modelType -> Optional.empty(),
                Path.of(System.getProperty("user.home"), ".cobalt")
        );
    }

    /**
     * Constructs a host bound to the given client, datagram egress, sinks, model-path resolver, and
     * persistent base directory.
     *
     * @param whatsapp            the owning client
     * @param datagramSink        the transport-owned datagram egress
     * @param videoSink           the sink that presents decoded frames
     * @param callEventSink       the sink that receives typed events
     * @param mlModelPathResolver the resolver mapping a model-type selector to a bundled model path
     * @param baseDirectory       the base directory under which the per-session call directory is resolved
     */
    public LiveVoipHostApi(
            LinkedWhatsAppClient whatsapp,
            MediaDatagramSink datagramSink,
            Consumer<RenderedVideoFrame> videoSink,
            BiConsumer<CallEventType, byte[]> callEventSink,
            IntFunction<Optional<Path>> mlModelPathResolver,
            Path baseDirectory
    ) {
        this.whatsapp = Objects.requireNonNull(whatsapp, "whatsapp cannot be null");
        this.datagramSink = Objects.requireNonNull(datagramSink, "datagramSink cannot be null");
        this.videoSink = Objects.requireNonNull(videoSink, "videoSink cannot be null");
        this.callEventSink = Objects.requireNonNull(callEventSink, "callEventSink cannot be null");
        this.mlModelPathResolver = Objects.requireNonNull(mlModelPathResolver, "mlModelPathResolver cannot be null");
        this.baseDirectory = Objects.requireNonNull(baseDirectory, "baseDirectory cannot be null");
        this.secureRandom = new SecureRandom();
        this.logger = System.getLogger(LiveVoipHostApi.class.getName());
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation dispatches the stanza through
     * {@link LinkedWhatsAppClient#sendNodeWithNoResponse(Stanza)}, matching the native import's
     * {@code void} return: the server acknowledgement for a call stanza arrives on the inbound signaling
     * path, not as a reply correlated here. A {@link WhatsAppSessionException.Closed} from a
     * already-closed socket is caught and logged rather than propagated, because a closed socket means
     * the call is already tearing down and the engine treats this layer as fire-and-forget.
     */
    @Override
    public void sendSignaling(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        try {
            whatsapp.sendNodeWithNoResponse(stanza);
        } catch (WhatsAppSessionException.Closed exception) {
            logger.log(System.Logger.Level.DEBUG, "Dropping call signaling on a closed socket", exception);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation forwards to the injected {@link MediaDatagramSink}, which backs the
     * native {@code call_sendto} host downcall with a {@code DatagramChannel} send. The pure-Java web
     * transport does not exercise this downcall: media rides as SCTP DATA over the DTLS-wrapped data channel
     * and the ICE and DTLS bytes leave through the per-call transport socket, so this seam carries no media
     * on the live path.
     */
    @Override
    public int sendDatagram(byte[] payload, SocketAddress destination) {
        Objects.requireNonNull(payload, "payload cannot be null");
        Objects.requireNonNull(destination, "destination cannot be null");
        return datagramSink.send(payload, destination);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation resolves through {@link InetAddress#getAllByName(String)} and maps
     * an {@link UnknownHostException} to an empty list, so an unresolvable name lets the engine fall
     * through to the next transport candidate rather than fail.
     */
    @Override
    public List<InetAddress> resolveHost(String hostName) {
        Objects.requireNonNull(hostName, "hostName cannot be null");
        try {
            return List.of(InetAddress.getAllByName(hostName));
        } catch (UnknownHostException exception) {
            return List.of();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation fills the array from a single {@link SecureRandom}; the engine's
     * key-sized draw requests {@value #OBSERVED_KEY_RANDOM_LENGTH} bytes, but the length is honoured as
     * given for any non-negative value.
     */
    @Override
    public byte[] randomBytes(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length cannot be negative: " + length);
        }
        var bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation resolves {@code <baseDirectory>/calls/<clientType>/<sessionUuid>}
     * from the client's account store, mirroring the persistence layer's
     * {@code <clientType>/<sessionId>} session layout, and creates the directory tree before returning
     * it so the engine can write immediately.
     */
    @Override
    public Path persistentDirectory() {
        var accountStore = whatsapp.store().accountStore();
        var directory = baseDirectory
                .resolve(CALLS_DIRECTORY)
                .resolve(accountStore.clientType().name().toLowerCase())
                .resolve(accountStore.uuid().toString());
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to create the call persistent directory: " + directory, exception);
        }
        return directory;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation delegates to the injected model-path resolver, which reports no
     * bundled model by default; an empty result is what the engine reads as the model being
     * unavailable.
     */
    @Override
    public Optional<Path> bweMlModelPath(int modelType) {
        return mlModelPathResolver.apply(modelType);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation maps to a {@code ContactStore} lookup through
     * {@link LinkedWhatsAppContactStore#findContactByJid}: a present contact is
     * known, an absent one is not, so an unloaded address book reports not known and the engine defaults
     * to the conservative unknown-caller handling.
     */
    @Override
    public boolean isKnownContact(Jid participant) {
        Objects.requireNonNull(participant, "participant cannot be null");
        return whatsapp.store()
                .contactStore()
                .findContactByJid(participant)
                .isPresent();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation forwards the frame to the injected video sink; the engine guarantees
     * the {@link RenderedVideoFrame} plane segments are valid only for the duration of this call, so the
     * sink must copy or upload them synchronously.
     */
    @Override
    public void renderVideoFrame(RenderedVideoFrame frame) {
        Objects.requireNonNull(frame, "frame cannot be null");
        videoSink.accept(frame);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation returns {@value #CAPTURE_AUDIO_PROCESSING_STATUS}, the
     * applied-processing bitmask of the {@code javax.sound.sampled} {@code TargetDataLine} capture path:
     * it applies no acoustic echo cancellation, noise suppression, or automatic gain control, so the
     * bitmask {@code (echoCancellation?1)+(noiseSuppression?2)+(autoGainControl?4)} is {@code 0} and the
     * engine runs its own full processing chain. This mirrors {@code WAWebVoipBrowserAudioStatus}, where
     * the bitmask is computed from the acquired {@code MediaStreamTrack} settings and the explicit
     * no-processing branch yields {@code 0}; that module reports {@code -1}
     * ({@code BROWSER_AUDIO_PROCESSING_STATUS_UNKNOWN}) only while no capture track has been acquired,
     * which this host has no analogue for because it binds no media stream of its own. A future capture
     * backend that honors echo cancellation, noise suppression, or gain control must compute the bitmask
     * from its actual device settings instead of returning this constant.
     */
    @Override
    public int browserAudioProcessingStatus() {
        return CAPTURE_AUDIO_PROCESSING_STATUS;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation routes the record to a per-instance {@link System.Logger}, replacing
     * the native structured-log sink so the engine's diagnostics land in the host's logging facility at
     * the engine-chosen level.
     */
    @Override
    public void log(System.Logger.Level level, String message) {
        Objects.requireNonNull(level, "level cannot be null");
        logger.log(level, message);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation forwards to the injected event sink, which the lifecycle layer backs
     * with the listener bus; the engine has already applied its should-emit gate, so every event
     * reaching here is one the host is meant to surface.
     */
    @Override
    public void onCallEvent(CallEventType eventType, byte[] payload) {
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(payload, "payload cannot be null");
        callEventSink.accept(eventType, payload);
    }

    /**
     * Carries one outbound media datagram from {@link LiveVoipHostApi} to the transport's UDP egress.
     *
     * <p>The transport layer owns the actual sockets a call uses, so {@link LiveVoipHostApi} does not
     * open one itself: it forwards each {@code call_sendto} host downcall to an implementation of this seam
     * that the host supplies. An implementation sends the payload to the destination on its
     * datagram channel and returns the number of bytes the channel accepted, which is the payload length
     * on success and a non-positive value when the channel could not send (for example because it is
     * closed). The live web transport carries media as SCTP DATA over its data channel and routes ICE and
     * DTLS through the per-call transport socket, so this {@code call_sendto} seam is not on the media path.
     */
    @FunctionalInterface
    public interface MediaDatagramSink {
        /**
         * Sends one datagram to the given destination on the transport's egress.
         *
         * @param payload     the datagram bytes to send
         * @param destination the address to send to
         * @return the number of bytes accepted by the transport, or a non-positive value on failure
         */
        int send(byte[] payload, SocketAddress destination);
    }
}
