package com.github.auties00.cobalt.device.adv;

import com.github.auties00.cobalt.device.DeviceConstants;
import com.github.auties00.cobalt.exception.WhatsAppAdvValidationException;
import com.github.auties00.cobalt.model.device.identity.*;
import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.util.FastRandomUtils;
import com.github.auties00.curve25519.Curve25519;
import com.github.auties00.libsignal.SignalProtocolAddress;
import com.github.auties00.libsignal.key.SignalIdentityKey;
import it.auties.protobuf.exception.ProtobufDeserializationException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;

/**
 * Service for validating ADV (Account Device Verification) signatures for companion devices.
 *
 * <p>ADV prevents MITM attacks by cryptographically linking companion device identities
 * to the primary account. For companion devices (device != 0), the prekey response must
 * contain a device-identity node with a SignedDeviceIdentity protobuf.
 *
 * @implNote WAWebAdvSignatureApi: provides signature verification for E2EE and hosted devices.
 * WAWebHandleAdvDeviceNotificationUtils: handles key index list validation.
 * WAWebBizCoexGatingUtils: provides bizHostedDevicesEnabled gating.
 */
public final class DeviceADVValidator {
    /**
     * Header for E2EE account signature verification.
     *
     * @implNote WAWebAdvSignatureConstants.ADV_PREFIX_DEVICE_IDENTITY_ACCOUNT_SIGNATURE: header
     * bytes [6, 0] for E2EE account signatures.
     */
    private static final byte[] E2EE_ACCOUNT_SIGNATURE_HEADER = {6, 0};

    /**
     * Header for E2EE device signature verification/creation.
     *
     * @implNote WAWebAdvSignatureConstants.ADV_PREFIX_DEVICE_IDENTITY_DEVICE_SIGNATURE: header
     * bytes [6, 1] for E2EE device signatures.
     */
    private static final byte[] E2EE_DEVICE_SIGNATURE_HEADER = {6, 1};

    /**
     * Header for key index list account signature verification.
     *
     * @implNote WAWebAdvSignatureConstants.ADV_PREFIX_KEY_INDEX_LIST_ACCOUNT_SIGNATURE: header
     * bytes [6, 2] for signed key index lists.
     */
    private static final byte[] KEY_INDEX_LIST_SIGNATURE_HEADER = {6, 2};

    /**
     * Header for hosted account signature verification.
     *
     * @implNote WAWebAdvSignatureConstants.ADV_HOSTED_PREFIX_DEVICE_IDENTITY_ACCOUNT_SIGNATURE:
     * header bytes [6, 5] for hosted account signatures.
     */
    private static final byte[] HOSTED_ACCOUNT_SIGNATURE_HEADER = {6, 5};

    /**
     * Header for hosted device signature verification.
     * Only used for verification, not generation. WA Web always generates with [6, 1].
     *
     * @implNote WAWebAdvSignatureConstants.ADV_HOSTED_PREFIX_DEVICE_IDENTITY_DEVICE_SIGNATURE:
     * header bytes [6, 6] for hosted device signatures.
     */
    private static final byte[] HOSTED_DEVICE_SIGNATURE_HEADER = {6, 6};

    private final WhatsAppStore store;
    private final ABPropsService abProps;

    /**
     * Creates a new ADV validator service.
     *
     * @implNote WAWebAdvSignatureApi: instantiated with store and AB props dependencies for
     * signature verification gated by {@code bizHostedDevicesEnabled}.
     * @param store   the WhatsApp store for accessing keys and identity
     * @param abProps the AB props service for feature gating
     */
    public DeviceADVValidator(WhatsAppStore store, ABPropsService abProps) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
        this.abProps = Objects.requireNonNull(abProps, "abProps cannot be null");
    }

    /**
     * Checks if hosted device validation is enabled.
     *
     * @implNote WAWebBizCoexGatingUtils.bizHostedDevicesEnabled: returns
     * {@code getABPropConfigValue("adv_accept_hosted_devices")}.
     * @return {@code true} if hosted devices should be validated with HOSTED headers
     */
    public boolean isBizHostedDevicesEnabled() {
        // Don't wait for sync
        return abProps.getBool(ABProp.ADV_ACCEPT_HOSTED_DEVICES, false);
    }

    /**
     * Extracts and validates a local device identity from a pairing response.
     *
     * @implNote WAWebHandlePairSuccess: validates {@code ADVSignedDeviceIdentityHMAC} during
     * pairing, verifies account signature via
     * {@code WAWebAdvSignatureApi.verifyDeviceIdentityAccountSignature}, and generates device
     * signature via {@code WAWebAdvSignatureApi.generateDeviceSignature} using local identity key.
     * Uses advSecretKey (not companion public key) for HMAC verification.
     * @param deviceIdentityNode the device identity node from pairing response
     * @return the validated signed device identity with generated device signature
     * @throws WhatsAppAdvValidationException if validation fails
     * @throws IllegalStateException          if required store values are missing
     */
    public ADVSignedDeviceIdentity extractAndValidateLocalSignedDeviceIdentity(Node deviceIdentityNode) {
        Objects.requireNonNull(deviceIdentityNode, "deviceIdentityNode cannot be null");

        var localJid = store.jid()
                .orElseThrow(() -> new IllegalStateException("Local JID not set in store"));
        var advSecretKey = store.advSecretKey()
                .orElseThrow(() -> new IllegalStateException("ADV secret key not set in store"));
        var localIdentityKeyPair = store.identityKeyPair();

        if (advSecretKey.length != 32) {
            throw new IllegalArgumentException("advSecretKey must be 32 bytes, got " + advSecretKey.length);
        }

        try {
            // WAWebHandlePairSuccess: extracts device-identity from pair-success response
            var deviceIdentityHmacBytes = deviceIdentityNode.getChild("device-identity")
                    .orElseThrow(() -> new WhatsAppAdvValidationException.MissingDeviceIdentity(localJid))
                    .toContentBytes()
                    .orElseThrow(() -> new WhatsAppAdvValidationException.EmptyDeviceIdentity(localJid));

            // WAWebHandlePairSuccess: decode ADVSignedDeviceIdentityHMACSpec
            var deviceIdentityHmac = ADVSignedDeviceIdentityHMACSpec.decode(deviceIdentityHmacBytes);
            var details = deviceIdentityHmac.details()
                    .orElseThrow(() -> new NullPointerException("details cannot be null"));
            var hmac = deviceIdentityHmac.hmac()
                    .orElseThrow(() -> new NullPointerException("hmac cannot be null"));

            // WAWebHandlePairSuccess: determine HMAC input based on platform and encryption type
            // The HOSTED header is ONLY prepended when:
            // 1. The PRIMARY device is SMB (WhatsApp Business) - checked via smbHostedPrimaryPairingAllowed()
            // 2. AND the accountType in the protobuf is HOSTED
            // For regular consumer accounts (android/iphone), the header is never prepended
            byte[] hmacInput;
            var outerEncryptionType = deviceIdentityHmac.accountType()
                    .orElse(ADVEncryptionType.E2EE);
            var platform = store.device().platform();
            var isSMB = platform == ClientPlatformType.ANDROID_BUSINESS || platform == ClientPlatformType.IOS_BUSINESS;
            if (isSMB && outerEncryptionType == ADVEncryptionType.HOSTED) {
                // WAWebHandlePairSuccess: Binary.build(ADV_HOSTED_PREFIX_DEVICE_IDENTITY_ACCOUNT_SIGNATURE, details)
                hmacInput = FastRandomUtils.concatByteArrays(HOSTED_ACCOUNT_SIGNATURE_HEADER, details);
            } else {
                // E2EE, null, or non-SMB defaults to using details directly
                hmacInput = details;
            }

            // WAWebHandlePairSuccess: verifies HMAC using advSecretKey (NOT companion public key)
            var mac = Mac.getInstance("HmacSHA256");
            var secretKey = new SecretKeySpec(advSecretKey, "HmacSHA256");
            mac.init(secretKey);
            var computedHmac = mac.doFinal(hmacInput);
            if (!Arrays.equals(hmac, computedHmac)) {
                throw new WhatsAppAdvValidationException.HmacValidationFailed(localJid);
            }

            // Decode the inner SignedDeviceIdentity
            var deviceIdentity = ADVSignedDeviceIdentitySpec.decode(details);
            var deviceIdentityDetails = deviceIdentity.details()
                    .orElseThrow(() -> new NullPointerException("details cannot be null"));
            var deviceIdentityAccountSignatureKey = deviceIdentity.accountSignatureKey()
                    .orElseThrow(() -> new NullPointerException("accountSignatureKey cannot be null"));
            var deviceIdentityAccountSignature = deviceIdentity.accountSignature()
                    .orElseThrow(() -> new NullPointerException("AccountSignature cannot be null"));

            // WAWebAdvSignatureApi.verifyDeviceIdentityAccountSignature: select account signature
            // header based on deviceType from the INNER ADVDeviceIdentitySpec, gated by bizHostedDevicesEnabled
            var accountSignatureHeader = E2EE_ACCOUNT_SIGNATURE_HEADER;
            if (isBizHostedDevicesEnabled()) {
                try {
                    var innerDeviceIdentity = ADVDeviceIdentitySpec.decode(deviceIdentityDetails);
                    var advEncryptionType = innerDeviceIdentity.deviceType()
                            .orElse(ADVEncryptionType.E2EE);
                    if (advEncryptionType == ADVEncryptionType.HOSTED) {
                        accountSignatureHeader = HOSTED_ACCOUNT_SIGNATURE_HEADER;
                    }
                } catch (ProtobufDeserializationException e) {
                    // If decoding fails, fall back to E2EE header
                }
            }

            // WAWebAdvSignatureApi: verifies account signature: sign(header + details + identityKey)
            var localIdentityKey = localIdentityKeyPair.publicKey().toEncodedPoint();
            var message = FastRandomUtils.concatByteArrays(accountSignatureHeader, deviceIdentityDetails, localIdentityKey);
            if (!Curve25519.verifySignature(deviceIdentityAccountSignatureKey, message, deviceIdentityAccountSignature)) {
                throw new WhatsAppAdvValidationException.AccountSignatureFailed(localJid);
            }

            // WAWebAdvSignatureApi.generateDeviceSignature: creates device signature: sign(header + details + identityKey + accountSignatureKey)
            // IMPORTANT: WA Web ALWAYS uses E2EE header [6, 1] for device signature GENERATION
            // The HOSTED header [6, 6] is only used for VERIFICATION of remote devices
            var deviceSignatureMessage = FastRandomUtils.concatByteArrays(
                    E2EE_DEVICE_SIGNATURE_HEADER,
                    deviceIdentityDetails,
                    localIdentityKey,
                    deviceIdentityAccountSignatureKey
            );
            var deviceSignature = Curve25519.sign(localIdentityKeyPair.privateKey().toEncodedPoint(), deviceSignatureMessage);

            return new ADVSignedDeviceIdentityBuilder()
                    .details(deviceIdentityDetails)
                    .accountSignatureKey(deviceIdentityAccountSignatureKey)
                    .accountSignature(deviceIdentityAccountSignature)
                    .deviceSignature(deviceSignature)
                    .build();
        } catch (GeneralSecurityException exception) {
            throw new WhatsAppAdvValidationException.CryptoError(localJid, exception);
        }
    }

    /**
     * Extracts and validates a remote device identity from a prekey response.
     *
     * @implNote WAWebAdvSignatureApi.validateADVwithIdentityKey: validates both account signature
     * (via internal function {@code x/verifyDeviceIdentityAccountSignature}) and device signature
     * (via internal function {@code A}) for companion devices. Uses stored identity key as
     * fallback when {@code accountSignatureKey} is missing from protobuf. Header selection uses
     * {@code deviceType} from protobuf for account signature (gated by
     * {@code bizHostedDevicesEnabled}), and {@code isHosted} parameter for device signature.
     * @param remoteJid          the remote device JID
     * @param remoteIdentityNode the remote device identity node
     * @param remoteIdentityKey  the remote device's claimed identity key (32 bytes)
     * @param isHostedFromJid    whether the remote JID indicates a hosted device
     * @return the validated signed device identity, or empty if not required or already known
     * @throws WhatsAppAdvValidationException if validation fails
     */
    public Optional<ADVSignedDeviceIdentity> extractAndValidateRemoteSignedDeviceIdentity(
            Jid remoteJid,
            Node remoteIdentityNode,
            byte[] remoteIdentityKey,
            boolean isHostedFromJid
    ) {
        Objects.requireNonNull(remoteJid, "remoteJid cannot be null");
        Objects.requireNonNull(remoteIdentityNode, "remoteIdentityNode cannot be null");
        Objects.requireNonNull(remoteIdentityKey, "remoteIdentityKey cannot be null");

        // WAWebAdvSignatureApi: ADV validation only required for companion devices (device != 0)
        if (!requiresValidation(remoteJid)) {
            return Optional.empty();
        }

        // WAWebAdvSignatureApi: early exit optimization - if we already have this device's
        // identity key stored and it matches, skip full ADV validation
        var storedDeviceIdentityKey = findStoredDeviceIdentityKey(remoteJid);
        if (storedDeviceIdentityKey.isPresent() && Arrays.equals(storedDeviceIdentityKey.get(), remoteIdentityKey)) {
            return Optional.empty();
        }

        // Get stored identity key for user (device 0) for fallback
        var storedUserIdentityKey = findStoredUserIdentityKey(remoteJid);

        var remoteIdentityBytes = remoteIdentityNode.getChild("device-identity")
                .orElseThrow(() -> new WhatsAppAdvValidationException.MissingDeviceIdentity(remoteJid))
                .toContentBytes()
                .orElseThrow(() -> new WhatsAppAdvValidationException.EmptyDeviceIdentity(remoteJid));
        var remoteIdentity = ADVSignedDeviceIdentitySpec.decode(remoteIdentityBytes);

        // WAWebAdvSignatureApi.verifyDeviceIdentityAccountSignature: for account signature, decode
        // details and check deviceType. This is different from device signature which uses isHosted from WID
        var remoteIdentityDetails = remoteIdentity.details()
                .orElseThrow(() -> new NullPointerException("details cannot be null"));

        // WAWebAdvSignatureApi.verifyDeviceIdentityAccountSignature: select account signature header
        // based on deviceType from protobuf. Only check deviceType if bizHostedDevicesEnabled
        var accountSignatureHeader = E2EE_ACCOUNT_SIGNATURE_HEADER;
        if (isBizHostedDevicesEnabled()) {
            try {
                var decodedDeviceIdentity = ADVDeviceIdentitySpec.decode(remoteIdentityDetails);
                var deviceType = decodedDeviceIdentity.deviceType()
                        .orElse(ADVEncryptionType.E2EE);
                if (deviceType == ADVEncryptionType.HOSTED) {
                    accountSignatureHeader = HOSTED_ACCOUNT_SIGNATURE_HEADER;
                }
            } catch (ProtobufDeserializationException e) {
                // If decoding fails, fall back to E2EE header
            }
        }

        // WAWebAdvSignatureApi.validateADVwithIdentityKey: select device signature header based on
        // isHosted from WID. Also gated by bizHostedDevicesEnabled
        var deviceSignatureHeader = E2EE_DEVICE_SIGNATURE_HEADER;
        if (isBizHostedDevicesEnabled() && isHostedFromJid) {
            deviceSignatureHeader = HOSTED_DEVICE_SIGNATURE_HEADER;
        }

        // WAWebAdvSignatureApi.validateADVwithIdentityKey (internal function P): determine account
        // signature key with fallback logic. Always use protobuf's accountSignatureKey first, then
        // fallback to stored user identity key
        var remoteIdentityAccountSignatureKey = remoteIdentity.accountSignatureKey()
                .orElse(null);

        // WAWebAdvSignatureApi.validateADVwithIdentityKey (internal function P): fallback to stored if null
        if (remoteIdentityAccountSignatureKey == null) {
            remoteIdentityAccountSignatureKey = storedUserIdentityKey.orElse(null);
        }

        // WAWebAdvSignatureApi.validateADVwithIdentityKey (internal function P): for bizHostedDevicesEnabled,
        // also check if empty (r && r.byteLength > 0 ? r : t) - fallback to stored if empty
        if (isBizHostedDevicesEnabled()) {
            if (remoteIdentityAccountSignatureKey != null && remoteIdentityAccountSignatureKey.length == 0) {
                remoteIdentityAccountSignatureKey = storedUserIdentityKey.orElse(null);
            }
        }

        if (remoteIdentityAccountSignatureKey == null || remoteIdentityAccountSignatureKey.length == 0) {
            return Optional.empty();
        }

        // WAWebAdvSignatureApi.verifyDeviceIdentityAccountSignature: verify account signature: sign(header + details + identityKey)
        var remoteIdentityAccountSignature = remoteIdentity.accountSignature()
                .orElseThrow(() -> new NullPointerException("accountSignature cannot be null"));
        var accountMessage = FastRandomUtils.concatByteArrays(accountSignatureHeader, remoteIdentityDetails, remoteIdentityKey);
        if (!Curve25519.verifySignature(remoteIdentityAccountSignatureKey, accountMessage, remoteIdentityAccountSignature)) {
            throw new WhatsAppAdvValidationException.AccountSignatureFailed(remoteJid);
        }

        // WAWebAdvSignatureApi.validateADVwithIdentityKey: verify device signature: sign(header + details + identityKey + accountSignatureKey)
        var remoteIdentityDeviceSignature = remoteIdentity.deviceSignature()
                .orElseThrow(() -> new NullPointerException("deviceSignature cannot be null"));
        var deviceMessage = FastRandomUtils.concatByteArrays(deviceSignatureHeader, remoteIdentityDetails, remoteIdentityKey, remoteIdentityAccountSignatureKey);
        if (!Curve25519.verifySignature(remoteIdentityKey, deviceMessage, remoteIdentityDeviceSignature)) {
            throw new WhatsAppAdvValidationException.DeviceSignatureFailed(remoteJid);
        }

        return Optional.of(remoteIdentity);
    }

    /**
     * Validates and decodes a signed key index list from raw bytes.
     *
     * @implNote WAWebHandleAdvDeviceNotificationUtils.verifySKeyIndexWithAccSigKey: decodes
     * protobuf and validates account signature using ONLY the embedded
     * {@code accountSignatureKey}. Returns {@code null} if the embedded key is missing --
     * no fallback to stored key. Signature verification delegates to
     * {@code WAWebAdvKeyIndexSignatureVerify.verifyKeyIndexListAccountSignature}.
     * @param signedKeyIndexBytes the raw signed key index list bytes
     * @return the validated key index list data, or empty if validation fails
     */
    public Optional<ValidatedKeyIndexListResult> validateAndDecodeSignedKeyIndexList(byte[] signedKeyIndexBytes) {
        Objects.requireNonNull(signedKeyIndexBytes, "signedKeyIndexBytes cannot be null");

        try {
            // WAWebHandleAdvDeviceNotificationUtils: decode outer SignedKeyIndexList protobuf
            var signedKeyIndexList = ADVSignedKeyIndexListSpec.decode(signedKeyIndexBytes);
            var details = signedKeyIndexList.details();
            if (details.isEmpty()) {
                return Optional.empty();
            }

            // WAWebHandleAdvDeviceNotificationUtils.verifySKeyIndexWithAccSigKey: use ONLY embedded accountSignatureKey
            // No fallback to stored key - return empty if missing
            var accountSignatureKey = signedKeyIndexList.accountSignatureKey();
            if (accountSignatureKey.isEmpty() || accountSignatureKey.get().length == 0) {
                return Optional.empty();
            }

            // WAWebHandleAdvDeviceNotificationUtils: verify account signature
            var accountSignature = signedKeyIndexList.accountSignature();
            if (accountSignature.isEmpty() || accountSignature.get().length == 0) {
                return Optional.empty();
            }

            // WAWebAdvKeyIndexSignatureVerify.verifyKeyIndexListSignature: verify signature with [6, 2] header
            var message = FastRandomUtils.concatByteArrays(KEY_INDEX_LIST_SIGNATURE_HEADER, details.get());
            if (!Curve25519.verifySignature(accountSignatureKey.get(), message, accountSignature.get())) {
                return Optional.empty();
            }

            // WAWebHandleAdvDeviceNotificationUtils: decode inner KeyIndexList protobuf
            var keyIndexList = ADVKeyIndexListSpec.decode(details.get());

            // WAWebHandleAdvDeviceNotificationUtils.verifySKeyIndexWithAccSigKey: return null if timestamp OR rawId is null
            var keyIndexListRawId = keyIndexList.rawId();
            var keyIndexListTimestamp = keyIndexList.timestamp();
            if (keyIndexListRawId.isEmpty() || keyIndexListTimestamp.isEmpty()) {
                return Optional.empty();
            }
            var keyIndexListValidIndexesSet = new LinkedHashSet<>(keyIndexList.validIndexes());
            var keyIndexListCurrentIndex = keyIndexList.currentIndex()
                    .orElse(0);
            var keyIndexListAccountType = keyIndexList.accountType()
                    .orElse(ADVEncryptionType.E2EE);

            var result = new ValidatedKeyIndexListResult(
                    keyIndexListRawId.getAsInt(),
                    keyIndexListTimestamp.get(),
                    keyIndexListValidIndexesSet,
                    keyIndexListCurrentIndex,
                    keyIndexListAccountType,
                    accountSignatureKey.get()
            );

            return Optional.of(result);
        } catch (ProtobufDeserializationException e) {
            return Optional.empty();
        }
    }

    /**
     * Finds a stored identity key for a specific device.
     *
     * @implNote WAWebAdvSignatureApi.validateADVwithIdentityKey: loads the stored identity key
     * via {@code WAWebSignalProtocolStore.loadIdentityKey(createSignalAddress(t))} for the early
     * exit optimization when the device's identity key is already known and matches.
     * @param deviceJid the device JID (with device number)
     * @return the identity key bytes, or empty if not found
     */
    private Optional<byte[]> findStoredDeviceIdentityKey(Jid deviceJid) {
        if (deviceJid == null) {
            return Optional.empty();
        }
        var address = new SignalProtocolAddress(deviceJid.user(), deviceJid.device());
        return store.findIdentityByAddress(address)
                .map(SignalIdentityKey::toEncodedPoint);
    }

    /**
     * Finds a stored identity key for a user (device 0).
     *
     * @implNote WAWebAdvSignatureApi.validateADVwithIdentityKey: loads the stored identity key
     * for the user (device 0) via
     * {@code WAWebSignalProtocolStore.loadIdentityKey(createSignalAddress(asUserWidOrThrow(t)))}.
     * Used as fallback for {@code accountSignatureKey} when missing from protobuf.
     * @param jid the JID (device number is ignored, always looks up device 0)
     * @return the identity key bytes, or empty if not found
     */
    private Optional<byte[]> findStoredUserIdentityKey(Jid jid) {
        if (jid == null) {
            return Optional.empty();
        }
        var address = new SignalProtocolAddress(jid.user(), 0);
        return store.findIdentityByAddress(address)
                .map(SignalIdentityKey::toEncodedPoint);
    }

    /**
     * Checks if a device requires ADV validation.
     *
     * @implNote WAWebAdvSignatureApi.validateADVwithIdentityKey: only companion devices
     * (device != 0) require ADV validation. Primary device identity is established
     * through the main account verification.
     * @param jid the JID to check
     * @return {@code true} if the device requires ADV validation
     */
    private boolean requiresValidation(Jid jid) {
        Objects.requireNonNull(jid, "jid cannot be null");
        return jid.device() != DeviceConstants.PRIMARY_DEVICE_ID;
    }
}
