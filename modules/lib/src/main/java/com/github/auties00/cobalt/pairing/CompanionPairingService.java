package com.github.auties00.cobalt.pairing;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.exception.WhatsAppRegistrationException;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.util.DataUtils;
import com.github.auties00.curve25519.Curve25519;
import com.github.auties00.libsignal.key.SignalIdentityKeyPair;

import javax.crypto.Cipher;
import javax.crypto.KDF;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * Stateful orchestrator that drives the companion side of the
 * alt-device-linking (phone-number pairing-code) handshake.
 *
 * <p>One instance is created per {@link WhatsAppClient} and shared between
 * {@code IqStreamHandler} (which initiates the flow and handles the
 * {@code <pair-device>} IQ) and {@code NotificationLinkingStreamHandler}
 * (which processes the resulting {@code primary_hello} and
 * {@code refresh_code} notifications). The service caches the short
 * pairing code, the companion ephemeral keypair, the server-issued
 * {@code ref}, and a generation timestamp so the companion side can
 * validate the primary's response and finish the handshake.
 *
 * @implNote WAWebAltDeviceLinkingApi: PairingState, initializeAltDeviceLinking,
 *     startAltLinkingFlow, handlePrimaryHello, handleRefreshCode
 */
@WhatsAppWebModule(moduleName = "WAWebAltDeviceLinkingApi")
@WhatsAppWebModule(moduleName = "WAWebAltDeviceLinkingIq")
@WhatsAppWebModule(moduleName = "WAWebAltDeviceLinkingAlgorithm")
@WhatsAppWebModule(moduleName = "WAWebAltDeviceLinkingBase32Encode")
@WhatsAppWebModule(moduleName = "WACryptoHkdf")
public final class CompanionPairingService {
    /**
     * Maximum age in seconds of a pairing code before
     * {@code primary_hello} is rejected as belonging to an expired code.
     *
     * @implNote WAWebAltDeviceLinkingApi: var I = 180
     */
    private static final Duration CODE_MAX_AGE = Duration.ofSeconds(180);

    /**
     * Maximum number of {@code primary_hello} attempts accepted per
     * generated pairing code. A fourth attempt aborts the flow.
     *
     * @implNote WAWebAltDeviceLinkingApi: var T = 3
     */
    private static final int MAX_PRIMARY_HELLO_ATTEMPTS = 3;

    /**
     * Length in bytes of the salt that separates the PBKDF2 password space
     * for each pairing attempt. WA Web writes this salt as the first
     * segment of {@code link_code_pairing_wrapped_companion_ephemeral_pub}.
     *
     * @implNote WAWebcompanionHello: new Uint8Array(32)
     */
    private static final int PBKDF2_SALT_LENGTH = 32;

    /**
     * Length in bytes of the AES-CTR initial counter ({@value}). WA Web
     * writes this counter as the second segment of
     * {@code link_code_pairing_wrapped_companion_ephemeral_pub}.
     *
     * @implNote WAWebcompanionHello: new Uint8Array(16)
     */
    private static final int AES_CTR_IV_LENGTH = 16;

    /**
     * Length in bytes of the AES-GCM IV used to encrypt the final key
     * bundle. WebCrypto's AES-GCM default nonce length, chosen explicitly
     * by WA Web.
     *
     * @implNote WAWebcompanionFinish: new Uint8Array(12)
     */
    private static final int AES_GCM_IV_LENGTH = 12;

    /**
     * Length in bytes of the Curve25519 public key component of the
     * ephemeral keypair that the companion ships to the primary.
     *
     * @implNote WAWebAltDeviceLinkingAlgorithm: publicKey length 32
     */
    private static final int CURVE25519_PUBLIC_KEY_LENGTH = 32;

    /**
     * Length in bytes of the HKDF output used as the AES-GCM bundle
     * encryption key ({@value}) and as the derived ADV secret.
     *
     * @implNote WAWebAltDeviceLinkingAlgorithm.getBundleEncryptionKey
     *     (local function {@code E}) and
     *     WAWebAltDeviceLinkingAlgorithm.createAdvSecret (local function
     *     {@code $}): both pass {@code 32} as the
     *     {@code WACryptoHkdf.extractWithSaltAndExpand} length argument.
     */
    private static final int HKDF_OUTPUT_LENGTH = 32;

    /**
     * ASCII HKDF {@code info} label used when deriving the AES-GCM bundle
     * encryption key from the X25519 ephemeral shared secret.
     *
     * @implNote WAWebAltDeviceLinkingAlgorithm.getBundleEncryptionKey
     *     (local function {@code E}): passes
     *     {@code "link_code_pairing_key_bundle_encryption_key"} as the
     *     {@code info} argument to {@code WACryptoHkdf.extractWithSaltAndExpand}.
     */
    private static final String BUNDLE_ENCRYPTION_INFO = "link_code_pairing_key_bundle_encryption_key";

    /**
     * ASCII HKDF {@code info} label used when deriving the ADV master
     * secret from the concatenated X25519 shared secrets.
     *
     * @implNote WAWebAltDeviceLinkingAlgorithm.createAdvSecret (local
     *     function {@code $}): passes {@code "adv_secret"} as the
     *     {@code info} argument to {@code WACryptoHkdf.extractWithSaltAndExpand}.
     */
    private static final String ADV_SECRET_INFO = "adv_secret";

    /**
     * WA Web's pairing-code base32 alphabet. Starts at {@code 1} (no
     * {@code 0}), runs through {@code 9}, then appends
     * {@code ABCDEFGHJKLMNPQRSTVWXYZ} (omitting {@code I}, {@code O},
     * {@code U}), yielding 32 characters that are easy to read aloud and
     * copy by hand. The alphabet deviates from canonical Crockford (which
     * starts at {@code 0} and skips {@code L}): WA Web skips {@code 0}
     * and keeps {@code L}.
     *
     * @implNote WAWebAltDeviceLinkingBase32Encode.bytesToCrockford:
     *     {@code "123456789ABCDEFGHJKLMNPQRSTVWXYZ"}
     */
    private static final char[] CROCKFORD_ALPHABET =
            "123456789ABCDEFGHJKLMNPQRSTVWXYZ".toCharArray();

    /**
     * Number of PBKDF2 iterations used by WA Web when deriving an AES-CTR
     * key from the eight-character pairing code. Matches the literal
     * {@code 2 << 16} (131 072) hard-coded in
     * {@code WAWebderiveKey}.
     *
     * @implNote WAWebderiveKey: iterations: 2 << 16
     */
    private static final int PBKDF2_ITERATIONS = 2 << 16;

    /**
     * Length in bits of the AES key derived by PBKDF2 from the pairing code.
     *
     * @implNote WAWebderiveKey: length: 256
     */
    private static final int PBKDF2_KEY_BITS = 256;

    /**
     * Length in bits of the GCM authentication tag. WA Web relies on the
     * WebCrypto default (128 bits) when calling {@code AES-GCM} without an
     * explicit {@code tagLength} parameter.
     *
     * @implNote WAWebencryptKeyBundle: {name: "AES-GCM", iv: t}
     */
    private static final int GCM_TAG_BITS = 128;
    
    /**
     * The WhatsApp client instance, used to reach the store and send IQs.
     *
     * @implNote NO_WA_BASIS: constructor dependency-injection site
     */
    private final WhatsAppClient whatsapp;

    /**
     * The verification handler that receives the eight-character pairing
     * code once it is generated. Only invoked when the handler is a
     * {@link WhatsAppClientVerificationHandler.Web.PairingCode} instance.
     *
     * @implNote NO_WA_BASIS: constructor dependency-injection site
     */
    private final WhatsAppClientVerificationHandler.Web webVerificationHandler;

    /**
     * Lock guarding all mutable state.
     *
     * @implNote NO_WA_BASIS: Java concurrency adaptation of WA Web's
     *     single-threaded event loop
     */
    private final Object lock;

    /**
     * The handshake stage.
     *
     * @implNote WAWebAltDeviceLinkingApi.PairingState.stage
     */
    private CompanionPairingStage stage;

    /**
     * The pairing code produced by the most recent
     * {@code companion_hello}. Required to re-derive the PBKDF2 AES-CTR
     * key that unwraps the primary ephemeral public key.
     *
     * @implNote WAWebAltDeviceLinkingApi.PairingState.helloCached.linkCodePairingSecret
     */
    private String pairingCode;

    /**
     * The companion's ephemeral Curve25519 keypair for the current
     * pairing attempt. Required to run X25519 with the primary's
     * decrypted ephemeral public key during companion finish.
     *
     * @implNote WAWebAltDeviceLinkingApi.PairingState.helloCached.linkCodePairingCompanionADVEphemeralKeyPair
     */
    private SignalIdentityKeyPair companionEphemeralKeyPair;

    /**
     * The server-issued {@code ref} returned by the {@code companion_hello}
     * IQ result. Matched against the {@code ref} carried by the primary's
     * notification to detect replays or concurrent attempts.
     *
     * @implNote WAWebAltDeviceLinkingApi.PairingState.ref
     */
    private byte[] cachedRef;

    /**
     * The phone-number JID that owns the primary device being linked.
     *
     * @implNote WAWebAltDeviceLinkingApi.PairingState.phone
     */
    private Jid phoneJid;

    /**
     * Wall-clock time when the pairing code was generated, used to reject
     * stale codes. Falls back to {@link Instant#EPOCH} when cleared.
     *
     * @implNote WAWebAltDeviceLinkingApi.PairingState.codeGenerationTs
     */
    private Instant codeGenerationTs;

    /**
     * Count of {@code primary_hello} notifications processed under the
     * current code. Incremented on entry to
     * {@link #handlePrimaryHello(byte[], byte[], byte[])} and checked
     * against {@link #MAX_PRIMARY_HELLO_ATTEMPTS} before proceeding.
     *
     * @implNote WAWebAltDeviceLinkingApi.PairingState.primaryHelloAttemptCount
     */
    private int primaryHelloAttemptCount;

    /**
     * Creates a new service tied to the given client. The caller is
     * responsible for wiring the same instance into every handler that
     * participates in the alt-device-linking flow.
     *
     * @param whatsapp               the WhatsApp client
     * @param webVerificationHandler the verification handler, may be a
     *                               QR or pairing-code variant
     * @throws NullPointerException if {@code whatsapp} is {@code null}
     * @implNote NO_WA_BASIS: constructor wiring
     */
    public CompanionPairingService(WhatsAppClient whatsapp, WhatsAppClientVerificationHandler.Web webVerificationHandler) {
        this.whatsapp = Objects.requireNonNull(whatsapp, "whatsapp must not be null");
        this.webVerificationHandler = webVerificationHandler;
        this.lock = new Object();
        this.stage = CompanionPairingStage.NOT_STARTED;
    }

    /**
     * Returns the pairing code published by the most recent
     * {@link #start()} invocation, or {@code null} when no pairing
     * flow has been started (or the cached state has been cleared).
     *
     * <p>Callers that need to surface the code synchronously after
     * calling {@link #start()} can read it here; the normal delivery
     * path remains the verification handler supplied to the
     * constructor.
     *
     * @return the eight-character pairing code, or {@code null} when
     *         none is available
     * @implNote Exposes the cached
     *     {@code WAWebAltDeviceLinkingApi.PairingState.helloCached.linkCodePairingSecret}
     *     for read-only access from {@link WhatsAppClient}.
     */
    public String pairingCode() {
        synchronized (lock) {
            return pairingCode;
        }
    }

    /**
     * Returns whether this service should own the incoming pair-device
     * IQ. Returns {@code true} iff the verification handler is a
     * {@link WhatsAppClientVerificationHandler.Web.PairingCode} and the
     * store carries a phone number.
     *
     * @return whether the caller should route the flow through this
     *         service rather than through the QR path
     * @implNote ADAPTED: WA Web exposes this choice through an explicit
     *     UI toggle; Cobalt derives it from the configured verification
     *     handler and the presence of a phone number on the store
     */
    public boolean isEnabled() {
        return webVerificationHandler instanceof WhatsAppClientVerificationHandler.Web.PairingCode
                && whatsapp.store().phoneNumber().isPresent();
    }

    /**
     * Generates a fresh pairing code, sends the {@code companion_hello}
     * IQ and publishes the code to the verification handler. Must be
     * called before any primary/refresh-code notification is processed.
     *
     * @throws IllegalStateException    if the service is not enabled
     * @throws GeneralSecurityException if the JCE provider rejects any
     *                                  step of the pairing-code derivation
     * @implNote WAWebAltDeviceLinkingApi.initializeAltDeviceLinking,
     *     WAWebAltDeviceLinkingApi.startAltLinkingFlow
     */
    public void start() throws GeneralSecurityException {
        synchronized (lock) {
            if (!isEnabled()) {
                throw new IllegalStateException("Alt-device-linking is not configured");
            }

            clearLocked();
            stage = CompanionPairingStage.INITIALIZED;
            var phoneNumber = whatsapp.store().phoneNumber().orElseThrow();
            phoneJid = Jid.of(phoneNumber);
            codeGenerationTs = Instant.now();

            var hello = companionHello();
            pairingCode = hello.linkCodePairingSecret();
            companionEphemeralKeyPair = hello.companionEphemeralKeyPair();

            var ref = sendCompanionHello(phoneJid, hello.linkCodePairingWrappedCompanionEphemeralPub());
            if (ref == null || ref.length == 0) {
                clearLocked();
                throw new GeneralSecurityException("Server did not return a ref for companion_hello");
            }
            cachedRef = ref;
            stage = CompanionPairingStage.AFTER_SEND_COMPANION_HELLO;

            if (webVerificationHandler != null) {
                webVerificationHandler.handle(pairingCode);
            }
        }
    }

    /**
     * Handles a {@code refresh_code} notification. Updates the cached
     * ref if the notification references the ref we are currently
     * expecting; otherwise discards the notification.
     *
     * @param notificationRef the ref carried by the notification
     * @implNote WAWebAltDeviceLinkingHandleNotification.d,
     *     WAWebAltDeviceLinkingApi.refreshAltLinkingCode
     */
    public void handleRefreshCode(byte[] notificationRef) {
        synchronized (lock) {
            if (cachedRef == null || notificationRef == null) {
                return;
            }
            if (!Arrays.equals(cachedRef, notificationRef)) {
                return;
            }
            cachedRef = notificationRef;
        }
    }

    /**
     * Handles a {@code primary_hello} notification by running the
     * companion finish algorithm and sending the resulting
     * {@code companion_finish} IQ.
     *
     * @param wrappedPrimaryEphemeralPub the wrapped primary ephemeral public key bytes
     * @param primaryIdentityPublic      the primary's long-term identity public key
     * @param notificationRef            the ref carried by the notification; compared against the cached ref
     * @throws IllegalStateException    if the service is not in the right state
     * @throws GeneralSecurityException if decrypting the primary hello fails, the ref mismatches, or the code has expired
     * @implNote WAWebAltDeviceLinkingApi.handlePrimaryHello,
     *     WAWebAltDeviceLinkingApi.handlePrimaryHelloInternal
     */
    public void handlePrimaryHello(byte[] wrappedPrimaryEphemeralPub, byte[] primaryIdentityPublic, byte[] notificationRef) throws GeneralSecurityException {
        synchronized (lock) {
            primaryHelloAttemptCount++;
            if (stage == CompanionPairingStage.AFTER_SEND_COMPANION_FINISH) {
                if (primaryHelloAttemptCount > MAX_PRIMARY_HELLO_ATTEMPTS) {
                    throw new GeneralSecurityException("Exceeded primary_hello attempts for the current pairing code");
                }
                regenerateAdvSecret();
                stage = CompanionPairingStage.AFTER_SEND_COMPANION_HELLO;
            }

            if (stage != CompanionPairingStage.AFTER_SEND_COMPANION_HELLO) {
                throw new IllegalStateException("primary_hello received while in stage " + stage);
            }

            if (cachedRef == null) {
                throw new GeneralSecurityException("No cached ref from companion_hello");
            }
            if (notificationRef == null || !Arrays.equals(cachedRef, notificationRef)) {
                throw new GeneralSecurityException("primary_hello ref does not match cached ref");
            }
            if (companionEphemeralKeyPair == null || pairingCode == null) {
                throw new GeneralSecurityException("Cached companion hello is missing");
            }
            if (codeGenerationTs == null
                    || Duration.between(codeGenerationTs, Instant.now()).compareTo(CODE_MAX_AGE) > 0) {
                throw new GeneralSecurityException("Pairing code has expired");
            }

            var identityKeyPair = whatsapp.store().identityKeyPair();
            var finish = companionFinish(
                    pairingCode,
                    wrappedPrimaryEphemeralPub,
                    primaryIdentityPublic,
                    companionEphemeralKeyPair,
                    identityKeyPair);

            whatsapp.store().setAdvSecretKey(finish.advSecret());
            sendCompanionFinish(phoneJid, finish.linkCodePairingWrappedKeyBundle(), finish.companionIdentityPublic(), cachedRef);
            stage = CompanionPairingStage.AFTER_SEND_COMPANION_FINISH;
        }
    }

    /**
     * Resets the cached pairing state. Invoked when a fresh code must
     * be generated or when a completed handshake is recycled.
     *
     * @implNote WAWebAltDeviceLinkingApi.PairingState.clear
     */
    private void clearLocked() {
        stage = CompanionPairingStage.NOT_STARTED;
        pairingCode = null;
        companionEphemeralKeyPair = null;
        cachedRef = null;
        phoneJid = null;
        codeGenerationTs = null;
        primaryHelloAttemptCount = 0;
    }

    /**
     * Regenerates the store's ADV secret after a repeat
     * {@code primary_hello} in the same code window, in case the first
     * companion finish was not acknowledged.
     *
     * @implNote WAWebAltDeviceLinkingApi.G (regenerateADVSecretKey on retry)
     */
    private void regenerateAdvSecret() {
        var key = DataUtils.randomByteArray(32);
        whatsapp.store().setAdvSecretKey(key);
    }

    /**
     * Inspects a {@code <iq type="error">} response, distilling the
     * {@code <error>} child's {@code code} and {@code text} attributes
     * into a {@link WhatsAppRegistrationException} with a
     * flow-specific message. Returns {@code null} when the response is
     * an ordinary {@code type="result"} stanza.
     *
     * @param response the raw IQ response received from the server
     * @param flow     a short label identifying which sub-flow threw
     *                 the error (for example {@code "companion_hello"})
     * @return an exception to throw if the response is an error,
     *         otherwise {@code null}
     * @implNote WASmaxMdCompanionHelloRPC / WASmaxMdCompanionFinishRPC:
     *     parse the response, dispatch to {@code CompanionHelloError}
     *     / {@code CompanionFinishError} with the
     *     {@code errorIqMixinErrors} category
     */
    private WhatsAppRegistrationException extractIqError(Node response, String flow) {
        if (response == null || !response.hasAttribute("type", "error")) {
            return null;
        }
        var error = response.getChild("error").orElse(null);
        var code = error == null ? -1 : error.getAttributeAsInt("code", -1);
        var text = error == null ? null : error.getAttributeAsString("text", null);
        return new WhatsAppRegistrationException(
                "alt pairing: " + flow + " rejected by server (code=" + code + ", text=" + text + ")");
    }

    /**
     * Builds and sends the {@code companion_hello} IQ, returning the
     * server-issued ref extracted from the response.
     *
     * @param phoneJid                                    the phone-number JID being linked
     * @param linkCodePairingWrappedCompanionEphemeralPub the wrapped companion ephemeral public key bytes
     * @return the raw ref bytes
     * @implNote WAWebAltDeviceLinkingIq.sendCompanionHello,
     *     WASmaxOutMdCompanionHelloRequest.makeCompanionHelloRequest
     */
    private byte[] sendCompanionHello(Jid phoneJid, byte[] linkCodePairingWrappedCompanionEphemeralPub) {
        var wrappedChild = new NodeBuilder()
                .description("link_code_pairing_wrapped_companion_ephemeral_pub")
                .content(linkCodePairingWrappedCompanionEphemeralPub)
                .build();

        var companionServerAuthKeyPub = new NodeBuilder()
                .description("companion_server_auth_key_pub")
                .content(whatsapp.store().noiseKeyPair().publicKey().toEncodedPoint())
                .build();

        var companionPlatformId = new NodeBuilder()
                .description("companion_platform_id")
                .content(companionPlatformId().getBytes(StandardCharsets.UTF_8)) // WAWebCompanionRegClientUtils.DEVICE_PLATFORM: sent as raw UTF-8 bytes to bypass WAP dictionary tokenisation
                .build();

        var companionPlatformDisplay = new NodeBuilder()
                .description("companion_platform_display")
                .content(companionPlatformDisplay().getBytes(StandardCharsets.UTF_8)) // WASmaxOutMdCompanionPlatformDisplayElMixin: raw UTF-8 bytes; avoids tokenisation of common labels like "Chrome"
                .build();

        var linkCodePairingNonce = new NodeBuilder() // WAWebAltDeviceLinkingIq.sendCompanionHelloRPC: linkCodePairingNonceArgs: { linkCodePairingNonceElementValue: new Uint8Array(1) }
                .description("link_code_pairing_nonce")
                .content(new byte[]{0}) // WAWebAltDeviceLinkingIq: one-byte zero buffer (always sent despite being OPTIONAL_CHILD in the outbound mixin)
                .build();

        var linkCodeCompanionReg = new NodeBuilder()
                .description("link_code_companion_reg")
                .attribute("jid", phoneJid)
                .attribute("stage", "companion_hello")
                .attribute("should_show_push_notification", "true") // WAWebAltDeviceLinkingIq.sendCompanionHelloRPC: always stringified boolean
                .content(wrappedChild, companionServerAuthKeyPub, companionPlatformId, companionPlatformDisplay, linkCodePairingNonce)
                .build();

        var iq = new NodeBuilder()
                .description("iq")
                .attribute("to", Jid.userServer())
                .attribute("type", "set")
                .attribute("xmlns", "md")
                .content(linkCodeCompanionReg);

        var response = whatsapp.sendNode(iq);
        var error = extractIqError(response, "companion_hello"); // WASmaxMdCompanionHelloRPC: CompanionHelloResponseError branch
        if (error != null) {
            throw error;
        }
        return extractRef(response);
    }

    /**
     * Builds and sends the {@code companion_finish} IQ.
     *
     * @param phoneJid                         the phone-number JID being linked
     * @param linkCodePairingWrappedKeyBundle  the encrypted identity bundle
     * @param companionIdentityPublic          the companion's long-term identity public key
     * @param ref                              the server-issued ref echoed into the IQ
     * @implNote WAWebAltDeviceLinkingIq.sendCompanionFinish,
     *     WASmaxOutMdCompanionFinishRequest.makeCompanionFinishRequest
     */
    private void sendCompanionFinish(Jid phoneJid, byte[] linkCodePairingWrappedKeyBundle, byte[] companionIdentityPublic, byte[] ref) {
        var wrappedKeyBundle = new NodeBuilder()
                .description("link_code_pairing_wrapped_key_bundle")
                .content(linkCodePairingWrappedKeyBundle)
                .build();

        var companionIdentity = new NodeBuilder()
                .description("companion_identity_public")
                .content(companionIdentityPublic)
                .build();

        var pairingRef = new NodeBuilder()
                .description("link_code_pairing_ref")
                .content(ref)
                .build();

        var linkCodeCompanionReg = new NodeBuilder()
                .description("link_code_companion_reg")
                .attribute("jid", phoneJid)
                .attribute("stage", "companion_finish")
                .content(wrappedKeyBundle, companionIdentity, pairingRef)
                .build();

        var iq = new NodeBuilder()
                .description("iq")
                .attribute("to", Jid.userServer())
                .attribute("type", "set")
                .attribute("xmlns", "md")
                .content(linkCodeCompanionReg);

        var response = whatsapp.sendNode(iq);
        var error = extractIqError(response, "companion_finish"); // WASmaxMdCompanionFinishRPC: CompanionFinishResponseError branch
        if (error != null) {
            throw error;
        }
    }

    /**
     * Returns the {@code companion_platform_id} element content for the
     * current client impersonation. Mirrors
     * {@code WAWebCompanionRegClientUtils.DEVICE_PLATFORM} which maps the
     * running browser family to a numeric string.
     *
     * @return the decimal string matching the configured platform
     * @implNote WAWebCompanionRegClientUtils.DEVICE_PLATFORM:
     *     {@code CHROME=1}, {@code UWP=8}, {@code OTHER_WEB_CLIENT=9}
     */
    private String companionPlatformId() {
        return switch (whatsapp.store().device().platform()) {
            case WINDOWS -> "8";                 // UWP, WA Web's d() returns UWP whenever isWindows
            case MACOS -> "6";                   // MACOS, default browser on macOS
            default -> "1";                      // CHROME, default browser impersonation
        };
    }

    /**
     * Returns the {@code companion_platform_display} element content in
     * the {@code "Browser (OS)"} form WA Web emits. The browser label
     * must match the platform id picked by {@link #companionPlatformId()}
     * so the primary device sees a self-consistent identification.
     *
     * @return the human-readable label for the primary confirmation UI
     * @implNote WASmaxOutMdCompanionPlatformDisplayElMixin mirrors
     *     {@code WAWebMiscBrowserUtils.info().name + " (" + os + ")"}
     */
    private String companionPlatformDisplay() {
        return switch (whatsapp.store().device().platform()) {
            case WINDOWS -> "Edge (Windows)";
            case MACOS -> "Safari (macOS)";
            default -> {
                var raw = System.getProperty("os.name", "Unknown");
                var lower = raw.toLowerCase();
                String os;
                if (lower.contains("mac")) {
                    os = "macOS";
                } else if (lower.contains("win")) {
                    os = "Windows";
                } else if (lower.contains("linux")) {
                    os = "Linux";
                } else {
                    os = raw;
                }
                yield "Chrome (" + os + ")"; // WEB impersonation tracks the host OS
            }
        };
    }

    /**
     * Extracts the {@code <link_code_pairing_ref>} element value from the
     * {@code companion_hello} response stanza.
     *
     * @param response the IQ result node
     * @return the raw ref bytes, or {@code null} if the ref is absent
     * @implNote WAWebAltDeviceLinkingIq: returns
     *     {@code linkCodeCompanionRegLinkCodePairingRefElementValue} from
     *     the parsed {@code CompanionHelloResponseNotifyCompanion}
     */
    private byte[] extractRef(Node response) {
        if (response == null) {
            return null;
        }
        var ref = response.getChild("link_code_companion_reg")
                .flatMap(node -> node.getChild("link_code_pairing_ref"))
                .flatMap(Node::toContentBytes)
                .orElse(null);
        if (ref == null) {
            System.Logger logger = System.getLogger(CompanionPairingService.class.getName());
            logger.log(System.Logger.Level.WARNING, "companion_hello response missing ref: {0}", response);
        }
        return ref;
    }

    /**
     * Generates a fresh pairing code, a fresh ephemeral Curve25519 keypair,
     * a random 32-byte PBKDF2 salt, and a random 16-byte AES-CTR IV, then
     * wraps the ephemeral public key under the PBKDF2-derived key. The
     * salt, IV, and ciphertext are concatenated in that order to form the
     * value carried in
     * {@code <link_code_pairing_wrapped_companion_ephemeral_pub>}.
     *
     * @return a populated {@link CompanionPairingCompanionHello} record
     * @throws GeneralSecurityException if the JCE provider rejects any step
     * @implNote WAWebcompanionHello,
     *     WAWebcompanionHelloInternal
     */
    private static CompanionPairingCompanionHello companionHello() throws GeneralSecurityException {
        var pairingCode = bytesToCrockford(DataUtils.randomByteArray(5));
        var ephemeralKeyPair = SignalIdentityKeyPair.random();
        var salt = DataUtils.randomByteArray(PBKDF2_SALT_LENGTH);
        var counter = DataUtils.randomByteArray(AES_CTR_IV_LENGTH);

        var aesKey = derivePairingKey(pairingCode, salt);
        var cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, new IvParameterSpec(counter));
        var ciphertext = cipher.doFinal(ephemeralKeyPair.publicKey().toEncodedPoint());

        var wrapped = DataUtils.concatByteArrays(salt, counter, ciphertext);
        return new CompanionPairingCompanionHello(pairingCode, wrapped, ephemeralKeyPair);
    }

    /**
     * Encodes exactly five bytes (40 bits) as an eight-character
     * base32 string using WA Web's variant of the Crockford alphabet
     * ({@link #CROCKFORD_ALPHABET}). Used to produce the short pairing
     * code shown to the user.
     *
     * @apiNote WA Web's {@code bytesToCrockford} is written against an
     *     arbitrary-length buffer with a {@code DataView} loop and a
     *     trailing-bits branch; since the only call site passes a
     *     5-byte {@code Uint8Array} (40 bits fits exactly into eight
     *     5-bit groups), Cobalt specialises the loop to 8 straight-line
     *     extractions that produce identical output for any 5-byte
     *     input.
     * @param input the 5-byte buffer to encode
     * @return an 8-character base32 string over WA Web's alphabet
     * @throws IllegalArgumentException if {@code input.length != 5}
     * @implNote WAWebAltDeviceLinkingBase32Encode.bytesToCrockford: packs 5
     *     bytes into eight 5-bit groups against the 32-character
     *     alphabet {@code "123456789ABCDEFGHJKLMNPQRSTVWXYZ"}
     */
    private static String bytesToCrockford(byte[] input) {
        if (input.length != 5) {
            throw new IllegalArgumentException("Crockford encoder expects exactly 5 bytes, got " + input.length);
        }

        var b0 = input[0] & 0xFF;
        var b1 = input[1] & 0xFF;
        var b2 = input[2] & 0xFF;
        var b3 = input[3] & 0xFF;
        var b4 = input[4] & 0xFF;

        var out = new char[8];
        out[0] = CROCKFORD_ALPHABET[(b0 >>> 3) & 0x1F];
        out[1] = CROCKFORD_ALPHABET[((b0 << 2) | (b1 >>> 6)) & 0x1F];
        out[2] = CROCKFORD_ALPHABET[(b1 >>> 1) & 0x1F];
        out[3] = CROCKFORD_ALPHABET[((b1 << 4) | (b2 >>> 4)) & 0x1F];
        out[4] = CROCKFORD_ALPHABET[((b2 << 1) | (b3 >>> 7)) & 0x1F];
        out[5] = CROCKFORD_ALPHABET[(b3 >>> 2) & 0x1F];
        out[6] = CROCKFORD_ALPHABET[((b3 << 3) | (b4 >>> 5)) & 0x1F];
        out[7] = CROCKFORD_ALPHABET[b4 & 0x1F];
        return new String(out);
    }

    /**
     * Completes the handshake after {@code primary_hello} is received.
     * Unwraps the primary's ephemeral public key (using the pairing code
     * again), runs two X25519 agreements, and packages the companion's
     * identity bundle under an HKDF-derived AES-GCM key.
     *
     * @param pairingCode                       the pairing code originally displayed to the user
     * @param wrappedPrimaryEphemeralPub        the server-delivered salt || counter || ciphertext triplet
     * @param primaryIdentityPublic             the primary's long-term identity public key
     * @param companionEphemeralKeyPair         the companion keypair produced by {@link #companionHello}
     * @param companionIdentityKeyPair          the companion's long-term identity keypair
     * @return a populated {@link CompanionPairingCompanionFinish} record
     * @throws GeneralSecurityException if the JCE provider rejects any step or if
     *                                  the wrapped primary ephemeral pub is malformed
     * @implNote WAWebcompanionFinish,
     *     WAWebcompanionFinishInternal
     */
    private static CompanionPairingCompanionFinish companionFinish(
            String pairingCode,
            byte[] wrappedPrimaryEphemeralPub,
            byte[] primaryIdentityPublic,
            SignalIdentityKeyPair companionEphemeralKeyPair,
            SignalIdentityKeyPair companionIdentityKeyPair
    ) throws GeneralSecurityException {
        Objects.requireNonNull(pairingCode, "pairingCode");
        Objects.requireNonNull(wrappedPrimaryEphemeralPub, "wrappedPrimaryEphemeralPub");
        Objects.requireNonNull(primaryIdentityPublic, "primaryIdentityPublic");
        Objects.requireNonNull(companionEphemeralKeyPair, "companionEphemeralKeyPair");
        Objects.requireNonNull(companionIdentityKeyPair, "companionIdentityKeyPair");

        if (wrappedPrimaryEphemeralPub.length < PBKDF2_SALT_LENGTH + AES_CTR_IV_LENGTH + CURVE25519_PUBLIC_KEY_LENGTH) {
            throw new GeneralSecurityException(
                    "link_code_pairing_wrapped_primary_ephemeral_pub is truncated: " + wrappedPrimaryEphemeralPub.length + " bytes");
        }

        var kdf = KDF.getInstance("HKDF-SHA256");

        var buffer = ByteBuffer.wrap(wrappedPrimaryEphemeralPub);
        var primarySalt = new byte[PBKDF2_SALT_LENGTH];
        buffer.get(primarySalt);
        var primaryCounter = new byte[AES_CTR_IV_LENGTH];
        buffer.get(primaryCounter);
        var primaryCiphertext = new byte[buffer.remaining()];
        buffer.get(primaryCiphertext);

        var primaryAesKey = derivePairingKey(pairingCode, primarySalt);
        var aesCtrCipher = Cipher.getInstance("AES/CTR/NoPadding");
        aesCtrCipher.init(Cipher.DECRYPT_MODE, primaryAesKey, new IvParameterSpec(primaryCounter));
        var primaryEphemeralPublic = aesCtrCipher.doFinal(primaryCiphertext);
        if (primaryEphemeralPublic.length == 0) {
            throw new GeneralSecurityException("Decrypted primary ephemeral public key is empty");
        }

        var ephemeralShared = Curve25519.sharedKey(
                companionEphemeralKeyPair.privateKey().toEncodedPoint(), primaryEphemeralPublic);

        // WAWebAltDeviceLinkingAlgorithm.companionFinish (local function N): allocates
        // three independent randoms before delegating to companionFinishInternal:
        //   l = new Uint8Array(32) -> AAD/advSecret material salt (parameter "s" in w)
        //   s = new Uint8Array(32) -> HKDF salt for bundle encryption key + wrapped prefix (parameter "u" in w)
        //   u = new Uint8Array(12) -> AES-GCM IV (parameter "c" in w)
        var advSecretMaterialSalt = DataUtils.randomByteArray(HKDF_OUTPUT_LENGTH);
        var bundleHkdfSalt = DataUtils.randomByteArray(HKDF_OUTPUT_LENGTH);
        var gcmIv = DataUtils.randomByteArray(AES_GCM_IV_LENGTH);

        // WACryptoHkdf.extractWithSaltAndExpand(ikm=ephemeralShared, salt=bundleHkdfSalt,
        //   info="link_code_pairing_key_bundle_encryption_key", length=32)
        // as invoked by WAWebAltDeviceLinkingAlgorithm.getBundleEncryptionKey (local function E).
        var bundleKeyParams = HKDFParameterSpec.ofExtract()
                .addIKM(ephemeralShared)
                .addSalt(bundleHkdfSalt)
                .thenExpand(BUNDLE_ENCRYPTION_INFO.getBytes(StandardCharsets.US_ASCII), HKDF_OUTPUT_LENGTH);
        var bundleKey = kdf.deriveData(bundleKeyParams);

        // WAWebAltDeviceLinkingAlgorithm.getKeyBundle (local function I): the AES-GCM
        // plaintext is concatBuffers([companionIdentityPub, primaryIdentityPub, advSecretMaterialSalt]).
        // These update() calls feed plaintext (not AAD — AAD would require updateAAD()).
        var companionIdentityPublic = companionIdentityKeyPair.publicKey().toEncodedPoint();
        var aesGcmCipher = Cipher.getInstance("AES/GCM/NoPadding");
        aesGcmCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(bundleKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, gcmIv));
        aesGcmCipher.update(companionIdentityPublic);
        aesGcmCipher.update(primaryIdentityPublic);
        aesGcmCipher.update(advSecretMaterialSalt);
        var keyBundleCiphertext = aesGcmCipher.doFinal();

        // WAWebAltDeviceLinkingAlgorithm.companionFinishInternal (local function w):
        // Binary writes [u (bundleHkdfSalt-32), c (gcmIv-12), R (ciphertext)] into linkCodePairingWrappedKeyBundle.
        var wrappedKeyBundle = ByteBuffer
                .allocate(bundleHkdfSalt.length + gcmIv.length + keyBundleCiphertext.length)
                .put(bundleHkdfSalt)
                .put(gcmIv)
                .put(keyBundleCiphertext)
                .array();

        var identityShared = Curve25519.sharedKey(
                companionIdentityKeyPair.privateKey().toEncodedPoint(), primaryIdentityPublic);

        // WACryptoHkdf.extractWithSaltAndExpand(ikm=concat(ephemeralShared,
        //   identityShared, advSecretMaterialSalt), salt=null, info="adv_secret", length=32)
        // as invoked by WAWebAltDeviceLinkingAlgorithm.createAdvSecret (local function $)
        // after WAArrayBufferUtils.concatBuffers([ephemeralShared, identityShared, advSecretMaterialSalt]).
        // The third element is parameter "s" in w (the 32-byte AAD-style salt), NOT "u" (the HKDF salt).
        // salt=null in WA Web maps to extractSha256's 32-zero default; in JDK
        // HKDFParameterSpec this is the implicit default when addSalt() is omitted.
        // Multiple addIKM() calls concatenate ikm in call order, matching concatBuffers.
        var advSecretParams = HKDFParameterSpec.ofExtract()
                .addIKM(ephemeralShared)
                .addIKM(identityShared)
                .addIKM(advSecretMaterialSalt)
                .thenExpand(ADV_SECRET_INFO.getBytes(StandardCharsets.US_ASCII), HKDF_OUTPUT_LENGTH);
        var advSecret = kdf.deriveData(advSecretParams);

        return new CompanionPairingCompanionFinish(wrappedKeyBundle, advSecret, companionIdentityPublic);
    }

    /**
     * Derives a 256-bit AES-CTR key from the user-visible pairing code using
     * PBKDF2-HMAC-SHA256 with the caller-supplied salt and
     * {@value #PBKDF2_ITERATIONS} iterations.
     *
     * @param pairingCode the eight-character pairing code, UTF-8 encoded as the password
     * @param salt        the 32-byte salt carried alongside the ciphertext
     * @return a {@link SecretKey} suitable for {@code AES/CTR/NoPadding}
     * @throws GeneralSecurityException if the JCE provider rejects the parameters
     * @implNote WAWebderiveKey
     */
    private static SecretKey derivePairingKey(String pairingCode, byte[] salt) throws GeneralSecurityException {
        var factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        var spec = new PBEKeySpec(pairingCode.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS);
        var raw = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(raw, "AES");
    }
}
