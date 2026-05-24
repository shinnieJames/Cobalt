package com.github.auties00.cobalt.registration.push.apns;

import com.github.auties00.cobalt.client.WhatsAppDevice;
import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;
import com.github.auties00.cobalt.client.WhatsAppDevicePushClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The public-facing APNS-backed implementation of
 * {@link WhatsAppDevicePushClient} that receives WhatsApp's silent
 * verification push while pretending to be the
 * {@code net.whatsapp.WhatsApp} (or {@code net.whatsapp.WhatsAppSMB})
 * iOS app.
 *
 * <p>Owns three single-responsibility collaborators and orchestrates
 * the lifecycle around them:
 * <ul>
 *   <li>{@link ApnsActivation} runs the FairPlay-signed activation
 *       handshake against {@code albert.apple.com} on demand.</li>
 *   <li>{@link ApnsCourierConnection} owns the long-lived TLS
 *       courier stream, the request multiplexer, and the keep-alive
 *       loop.</li>
 *   <li>{@link ApnsPushCode} is the single-value sync primitive that
 *       hands the verification code back to
 *       {@link #getPushCode()}.</li>
 * </ul>
 *
 * <p>State is owned by the caller via {@link #getSession()} and
 * {@link #loadSession(ApnsSession)}; the client never touches the
 * file system. Typical first-run usage:
 *
 * <pre>{@code
 *   try (var apns = ApnsClient.newSession()) {
 *       apns.authenticate(device);
 *       String pushToken = apns.getPushToken();
 *       ApnsSession saved = apns.getSession();
 *       String code = apns.getPushCode();
 *   }
 * }</pre>
 *
 * <p>Subsequent runs reuse the activation certificate and skip the
 * {@code albert.apple.com} round-trip:
 *
 * <pre>{@code
 *   try (var apns = ApnsClient.loadSession(saved)) {
 *       String code = apns.getPushCode();
 *   }
 * }</pre>
 */
public final class ApnsClient implements WhatsAppDevicePushClient, AutoCloseable {
    /**
     * The cached, unmodifiable set of platforms this client can
     * authenticate against.
     *
     * @apiNote
     * Contains exactly {@link ClientPlatformType#IOS} and
     * {@link ClientPlatformType#IOS_BUSINESS}; returned by
     * {@link #supportedPlatforms()}.
     */
    private static final Set<ClientPlatformType> SUPPORTED_PLATFORMS =
            Set.of(ClientPlatformType.IOS, ClientPlatformType.IOS_BUSINESS);

    /**
     * The lifecycle states of the client.
     *
     * @apiNote
     * Transitions:
     * <ul>
     *   <li>{@link #UNAUTHENTICATED} via
     *       {@link #authenticate(WhatsAppDevice)} to
     *       {@link #AUTHENTICATING};</li>
     *   <li>{@link #AUTHENTICATING} via success to
     *       {@link #AUTHENTICATED} or via error back to
     *       {@link #UNAUTHENTICATED};</li>
     *   <li>any state via {@link #close()} to {@link #CLOSED}.</li>
     * </ul>
     */
    private enum State {
        /**
         * No session bound.
         *
         * @apiNote
         * Only {@link #authenticate(WhatsAppDevice)} and
         * {@link #close()} are valid in this state.
         */
        UNAUTHENTICATED,
        /**
         * {@link #authenticate(WhatsAppDevice)} is currently running
         * on another thread.
         *
         * @apiNote
         * Concurrent callers observing this state throw
         * {@link IllegalStateException}.
         */
        AUTHENTICATING,
        /**
         * Activation has succeeded and the courier is running.
         *
         * @apiNote
         * The read-only accessors ({@link #getSession()},
         * {@link #getPushToken()}, {@link #getPushCode()}) are
         * usable.
         */
        AUTHENTICATED,
        /**
         * {@link #close()} has been invoked.
         *
         * @apiNote
         * All accessors throw and the courier is being torn down.
         */
        CLOSED
    }

    /**
     * The pre-built activation helper bound to the configured proxy.
     *
     * @apiNote
     * Stateless beyond its dependencies, so a single instance is
     * reused across {@link #authenticate(WhatsAppDevice)} retries.
     */
    private final ApnsActivation activation;

    /**
     * The single-value sync primitive that holds the verification
     * code once it arrives over the courier stream.
     *
     * @apiNote
     * Shared with {@link #connection} so the read pump can hand the
     * code straight to any {@link #getPushCode()} caller blocked on
     * its {@code waitForCode} method.
     */
    private final ApnsPushCode pushCode;

    /**
     * The proxy URI handed to {@link #connection} on construction.
     *
     * @apiNote
     * {@code null} for direct connections; honoured for the bag HTTP
     * fetch but not for the courier TLS socket (which is dialled
     * directly).
     */
    private final URI proxy;

    /**
     * The lifecycle state.
     *
     * @apiNote
     * Transitioned via {@link AtomicReference#compareAndSet} so
     * concurrent {@link #authenticate(WhatsAppDevice)} callers see a
     * consistent view and only one wins the race.
     */
    private final AtomicReference<State> state;

    /**
     * The session bound during
     * {@link #authenticate(WhatsAppDevice)} or
     * {@link #loadSession(ApnsSession, URI)}.
     *
     * @apiNote
     * {@code null} until authentication succeeds; reset to
     * {@code null} on auth failure so a retry sees a clean slate.
     */
    private volatile ApnsSession session;

    /**
     * The courier connection owning the long-lived TLS stream.
     *
     * @apiNote
     * {@code null} until authentication completes; closed and
     * dereferenced by {@link #close()}.
     */
    private volatile ApnsCourierConnection connection;

    /**
     * Constructs an unauthenticated client.
     *
     * @apiNote
     * Builds the activation helper and the push-code holder; no
     * socket is opened. Public callers go through the static
     * factories.
     *
     * @param proxy proxy URI, or {@code null} for direct
     */
    private ApnsClient(URI proxy) {
        this.proxy = proxy;
        this.activation = new ApnsActivation(proxy);
        this.pushCode = new ApnsPushCode();
        this.state = new AtomicReference<>(State.UNAUTHENTICATED);
    }

    /**
     * Creates a fresh, unauthenticated client.
     *
     * @apiNote
     * The caller must call {@link #authenticate(WhatsAppDevice)}
     * before any of the read-only accessors become usable.
     *
     * @return a new unauthenticated client
     */
    public static ApnsClient newSession() {
        return new ApnsClient(null);
    }

    /**
     * Creates a fresh, unauthenticated client routed through a
     * proxy.
     *
     * @apiNote
     * The proxy is honoured for the bag HTTP fetch but not for the
     * courier TLS socket.
     *
     * @param proxy proxy URI ({@code http(s)://...},
     *              {@code socks://...}), or {@code null} for direct
     * @return a new unauthenticated client
     */
    public static ApnsClient newSession(URI proxy) {
        return new ApnsClient(proxy);
    }

    /**
     * Restores a client from a previously captured
     * {@link ApnsSession}.
     *
     * @apiNote
     * Skips the activation handshake (the device certificate
     * persists for roughly three years) and immediately starts the
     * courier connection. The returned client is already in
     * {@code AUTHENTICATED} state, so
     * {@link #authenticate(WhatsAppDevice)} would throw.
     *
     * @param session the session previously obtained from
     *                {@link #getSession()}
     * @return a restored, listening client
     * @throws IOException if the courier handshake fails
     */
    public static ApnsClient loadSession(ApnsSession session) throws IOException {
        return loadSession(session, null);
    }

    /**
     * Restores a client from a previously captured
     * {@link ApnsSession} routed through a proxy.
     *
     * @apiNote
     * Behaves like {@link #loadSession(ApnsSession)} but binds the
     * bag HTTP fetch to the given proxy.
     *
     * @param session the session previously obtained from
     *                {@link #getSession()}
     * @param proxy   proxy URI, or {@code null} for direct
     * @return a restored, listening client
     * @throws IOException if the courier handshake fails
     */
    public static ApnsClient loadSession(ApnsSession session, URI proxy) throws IOException {
        Objects.requireNonNull(session, "session");
        var client = new ApnsClient(proxy);
        client.session = session;
        client.activation.activate(session);
        client.connection = new ApnsCourierConnection(session, client.pushCode, proxy);
        client.connection.start();
        client.state.set(State.AUTHENTICATED);
        return client;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Returns an unmodifiable two-element set containing
     * {@link ClientPlatformType#IOS} and
     * {@link ClientPlatformType#IOS_BUSINESS}.
     */
    @Override
    public Set<ClientPlatformType> supportedPlatforms() {
        return SUPPORTED_PLATFORMS;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Selects {@link ApnsConfig#WHATSAPP_PERSONAL} or
     * {@link ApnsConfig#WHATSAPP_BUSINESS} from
     * {@code device.platform()}, runs the activation handshake
     * (skipping the network call when the session is already
     * populated), and starts the courier connection. After this
     * call returns successfully, {@link #isAuthenticated()} reports
     * {@code true} and the read-only accessors become usable.
     *
     * @implNote
     * This implementation is thread-safe via
     * {@link AtomicReference#compareAndSet}: only the first
     * concurrent caller actually runs the activation. Any other
     * caller observing {@code AUTHENTICATING} or
     * {@code AUTHENTICATED} throws {@link IllegalStateException}.
     * On failure the state reverts to {@code UNAUTHENTICATED} via
     * {@link #rollbackAuthentication()} so the caller may retry.
     *
     * @param device the device whose platform selects the WA config
     * @throws IllegalArgumentException if {@code device.platform()}
     *                                  is neither {@code IOS} nor
     *                                  {@code IOS_BUSINESS}
     * @throws IllegalStateException    if the client is already
     *                                  authenticating, authenticated,
     *                                  or closed
     * @throws UncheckedIOException     wrapping any HTTP, TLS, or
     *                                  protocol failure
     */
    @Override
    public void authenticate(WhatsAppDevice device) {
        Objects.requireNonNull(device, "device");
        var platform = device.platform();
        var config = switch (platform) {
            case IOS -> ApnsConfig.WHATSAPP_PERSONAL;
            case IOS_BUSINESS -> ApnsConfig.WHATSAPP_BUSINESS;
            default -> throw new IllegalArgumentException(
                    "ApnsClient.authenticate requires IOS or IOS_BUSINESS, got " + platform);
        };
        if (!state.compareAndSet(State.UNAUTHENTICATED, State.AUTHENTICATING)) {
            throw new IllegalStateException("ApnsClient already " + state.get());
        }
        try {
            this.session = ApnsSession.newSession(config);
            activation.activate(session);
            this.connection = new ApnsCourierConnection(session, pushCode, proxy);
            this.connection.start();
            state.set(State.AUTHENTICATED);
        } catch (IOException e) {
            rollbackAuthentication();
            throw new UncheckedIOException(e);
        } catch (RuntimeException | Error e) {
            rollbackAuthentication();
            throw e;
        }
    }

    /**
     * Wipes the partial state left by a failed
     * {@link #authenticate(WhatsAppDevice)} attempt.
     *
     * @apiNote
     * Reverts the lifecycle back to {@code UNAUTHENTICATED} so the
     * caller may retry; clears {@link #session} and
     * {@link #connection} so a subsequent attempt sees a clean
     * slate.
     */
    private void rollbackAuthentication() {
        this.session = null;
        this.connection = null;
        state.set(State.UNAUTHENTICATED);
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Returns {@code true} iff the client is in {@code AUTHENTICATED}
     * state, i.e. {@link #authenticate(WhatsAppDevice)} or
     * {@link #loadSession(ApnsSession)} has completed and the
     * courier is running.
     */
    @Override
    public boolean isAuthenticated() {
        return state.get() == State.AUTHENTICATED;
    }

    /**
     * Returns the live {@link ApnsSession} backing this client.
     *
     * @apiNote
     * Callers may pass the result back to
     * {@link #loadSession(ApnsSession)} on a future run to keep the
     * same device identity, or hand it to {@code ApnsSessionSpec}
     * for byte-level persistence.
     *
     * @return the live session
     * @throws IllegalStateException if the client is not
     *                               authenticated
     */
    public ApnsSession getSession() {
        requireAuthenticated();
        return session;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Returns the push token for the primary topic of the
     * configured WhatsApp flavour (the first entry in
     * {@code session.config().topics()}, e.g.
     * {@code "net.whatsapp.WhatsApp"} for personal or
     * {@code "net.whatsapp.WhatsAppSMB"} for business). Blocking;
     * safe to call repeatedly because subsequent calls reuse the
     * existing courier connection.
     *
     * @return the hex-encoded push token bytes
     * @throws UncheckedIOException  wrapping any courier or
     *                               protocol failure
     * @throws IllegalStateException if the client is not
     *                               authenticated or the session has
     *                               no topics
     */
    @Override
    public String getPushToken() {
        requireAuthenticated();
        var topics = session.config().topics();
        if (topics.isEmpty()) {
            throw new IllegalStateException("ApnsClient session has no topics");
        }
        try {
            return connection.requestToken(topics.get(0));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Returns the WhatsApp verification code carried by the silent
     * APNS notification WhatsApp's server emits in response to the
     * {@code /v2/exist} call. Returns immediately if the
     * notification has already arrived, otherwise blocks the
     * calling thread until either the notification arrives or
     * {@link #close()} is invoked. Safe to call from multiple
     * threads concurrently; every caller observes the same
     * delivered code because the WhatsApp registration flow only
     * ever sends one per session.
     *
     * @implNote
     * This implementation translates the {@link InterruptedException}
     * thrown by {@link ApnsPushCode#waitForCode()} into a
     * {@link RuntimeException} after restoring the interrupt flag,
     * because the interface contract does not include a checked
     * {@link InterruptedException}.
     *
     * @return the verification code from the notification's
     *         {@code regcode} JSON field
     * @throws UncheckedIOException  if the client has been closed
     *                               before a code was delivered
     * @throws RuntimeException      if the caller is interrupted
     *                               while waiting
     * @throws IllegalStateException if the client is not
     *                               authenticated
     */
    @Override
    public String getPushCode() {
        requireAuthenticated();
        try {
            return pushCode.waitForCode();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("ApnsClient.getPushCode interrupted", e);
        }
    }

    /**
     * Throws if the client is not in {@code AUTHENTICATED} state.
     *
     * @apiNote
     * Used to guard accessors that depend on a populated
     * {@link #session} and a running courier connection.
     *
     * @throws IllegalStateException if the client is not
     *                               authenticated
     */
    private void requireAuthenticated() {
        var current = state.get();
        if (current != State.AUTHENTICATED) {
            throw new IllegalStateException(
                    "ApnsClient must be authenticated first; current state=" + current);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Stops the courier read pump and keep-alive threads, tears
     * down the TLS socket, transitions the lifecycle to
     * {@code CLOSED}, and unblocks every pending
     * {@link #getPushCode()} caller. Idempotent.
     */
    @Override
    public void close() {
        var prev = state.getAndSet(State.CLOSED);
        if (prev == State.CLOSED) {
            return;
        }
        var c = connection;
        if (c != null) {
            c.close();
        }
        pushCode.close();
    }
}
