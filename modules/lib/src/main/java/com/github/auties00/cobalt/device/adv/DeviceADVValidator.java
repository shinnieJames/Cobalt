package com.github.auties00.cobalt.device.adv;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.device.DeviceConstants;
import com.github.auties00.cobalt.exception.WhatsAppAdvValidationException;
import com.github.auties00.cobalt.model.device.identity.*;
import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.util.DataUtils;
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
 * Validates Account Device Verification (ADV) signatures that bind companion device
 * identities to the primary WhatsApp account.
 *
 * <p>When a user links a companion device (Web, Desktop, tablet), the primary phone
 * signs the companion's identity key so the server and peers can verify that any
 * messages carrying that identity were authorized by the account owner. This
 * validator checks those signatures for the local account (during pairing), for
 * remote companion devices (during prekey fetches), and for signed key index lists
 * (during device list synchronization and notifications), covering both standard
 * end-to-end encrypted accounts and hosted business coexistence accounts.
 *
 * <p>Used by
 * {@link com.github.auties00.cobalt.device.DeviceService} and
 * {@link com.github.auties00.cobalt.device.stanza.DeviceUSyncResponseParser}.
 */
@WhatsAppWebModule(moduleName = "WAWebAdvSignatureApi")
@WhatsAppWebModule(moduleName = "WAWebHandleAdvDeviceNotificationUtils")
@WhatsAppWebModule(moduleName = "WAWebAdvSignatureConstants")
public final class DeviceADVValidator {
    /**
     * Domain-separation header for E2EE account signature verification ({@code [6, 0]}).
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSignatureConstants",
            exports = "ADV_PREFIX_DEVICE_IDENTITY_ACCOUNT_SIGNATURE",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final byte[] E2EE_ACCOUNT_SIGNATURE_HEADER = {6, 0};

    /**
     * Domain-separation header for E2EE device signature verification and generation
     * ({@code [6, 1]}).
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSignatureConstants",
            exports = "ADV_PREFIX_DEVICE_IDENTITY_DEVICE_SIGNATURE",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final byte[] E2EE_DEVICE_SIGNATURE_HEADER = {6, 1};

    /**
     * Domain-separation header for signed key index lists ({@code [6, 2]}).
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSignatureConstants",
            exports = "ADV_PREFIX_KEY_INDEX_LIST_ACCOUNT_SIGNATURE",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final byte[] KEY_INDEX_LIST_SIGNATURE_HEADER = {6, 2};

    /**
     * Domain-separation header for hosted account signature verification
     * ({@code [6, 5]}).
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSignatureConstants",
            exports = "ADV_HOSTED_PREFIX_DEVICE_IDENTITY_ACCOUNT_SIGNATURE",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final byte[] HOSTED_ACCOUNT_SIGNATURE_HEADER = {6, 5};

    /**
     * Domain-separation header for hosted device signature verification
     * ({@code [6, 6]}). Only used for verification. WA Web always generates with
     * {@link #E2EE_DEVICE_SIGNATURE_HEADER}.
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSignatureConstants",
            exports = "ADV_HOSTED_PREFIX_DEVICE_IDENTITY_DEVICE_SIGNATURE",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final byte[] HOSTED_DEVICE_SIGNATURE_HEADER = {6, 6};

    /**
     * The store providing the local identity key pair, advSecretKey, and stored
     * peer identities.
     */
    private final WhatsAppStore store;

    /**
     * The AB props service used to gate hosted-device behaviour.
     */
    private final ABPropsService abProps;

    /**
     * Constructs a new ADV validator.
     *
     * @param store   the store
     * @param abProps the AB props service
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSignatureApi",
            exports = "validateADVwithIdentityKey",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public DeviceADVValidator(WhatsAppStore store, ABPropsService abProps) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
        this.abProps = Objects.requireNonNull(abProps, "abProps cannot be null");
    }

    /**
     * Returns whether hosted-device signature handling is enabled.
     *
     * @return {@code true} when {@code adv_accept_hosted_devices} is set
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCoexGatingUtils",
            exports = "bizHostedDevicesEnabled",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean isBizHostedDevicesEnabled() {
        return abProps.getBool(ABProp.ADV_ACCEPT_HOSTED_DEVICES, false);
    }

    /**
     * Extracts and validates a local device identity from a pairing response.
     * @param deviceIdentityNode the device identity node from pairing response
     * @return the validated signed device identity with generated device signature
     * @throws WhatsAppAdvValidationException if validation fails
     * @throws IllegalStateException          if required store values are missing
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSignatureApi",
            exports = {"verifyDeviceIdentityAccountSignature", "generateDeviceSignature"},
            adaptation = WhatsAppAdaptation.DIRECT)
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
            var deviceIdentityHmacBytes = deviceIdentityNode.getChild("device-identity")
                    .orElseThrow(() -> new WhatsAppAdvValidationException.MissingDeviceIdentity(localJid))
                    .toContentBytes()
                    .orElseThrow(() -> new WhatsAppAdvValidationException.EmptyDeviceIdentity(localJid));

            var deviceIdentityHmac = ADVSignedDeviceIdentityHMACSpec.decode(deviceIdentityHmacBytes);
            var details = deviceIdentityHmac.details()
                    .orElseThrow(() -> new NullPointerException("details cannot be null"));
            var hmac = deviceIdentityHmac.hmac()
                    .orElseThrow(() -> new NullPointerException("hmac cannot be null"));

            // The HOSTED header is prepended only when the primary device is SMB (WhatsApp
            // Business) and the protobuf accountType is HOSTED. Consumer accounts always
            // pass details directly.
            byte[] hmacInput;
            var outerEncryptionType = deviceIdentityHmac.accountType()
                    .orElse(ADVEncryptionType.E2EE);
            var platform = store.device().platform();
            var isSMB = platform == ClientPlatformType.ANDROID_BUSINESS || platform == ClientPlatformType.IOS_BUSINESS;
            if (isSMB && outerEncryptionType == ADVEncryptionType.HOSTED) {
                hmacInput = DataUtils.concatByteArrays(HOSTED_ACCOUNT_SIGNATURE_HEADER, details);
            } else {
                hmacInput = details;
            }

            // HMAC verification uses advSecretKey, not the companion public key.
            var mac = Mac.getInstance("HmacSHA256");
            var secretKey = new SecretKeySpec(advSecretKey, "HmacSHA256");
            mac.init(secretKey);
            var computedHmac = mac.doFinal(hmacInput);
            if (!Arrays.equals(hmac, computedHmac)) {
                throw new WhatsAppAdvValidationException.HmacValidationFailed(localJid);
            }

            var deviceIdentity = ADVSignedDeviceIdentitySpec.decode(details);
            var deviceIdentityDetails = deviceIdentity.details()
                    .orElseThrow(() -> new NullPointerException("details cannot be null"));
            var deviceIdentityAccountSignatureKey = deviceIdentity.accountSignatureKey()
                    .orElseThrow(() -> new NullPointerException("accountSignatureKey cannot be null"));
            var deviceIdentityAccountSignature = deviceIdentity.accountSignature()
                    .orElseThrow(() -> new NullPointerException("AccountSignature cannot be null"));

            // The account signature header is selected from the inner deviceType, gated by
            // the hosted-devices feature flag.
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
                    // Fall back to the E2EE header on decode failure.
                }
            }

            var localIdentityKey = localIdentityKeyPair.publicKey().toEncodedPoint();
            var message = DataUtils.concatByteArrays(accountSignatureHeader, deviceIdentityDetails, localIdentityKey);
            if (!Curve25519.verifySignature(deviceIdentityAccountSignatureKey, message, deviceIdentityAccountSignature)) {
                throw new WhatsAppAdvValidationException.AccountSignatureFailed(localJid);
            }

            // Device signature generation always uses the E2EE header. The HOSTED device
            // header is only used during verification of remote devices.
            var deviceSignatureMessage = DataUtils.concatByteArrays(
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
     * @param remoteJid          the remote device JID
     * @param remoteIdentityNode the remote device identity node
     * @param remoteIdentityKey  the remote device's claimed identity key (32 bytes)
     * @param isHostedFromJid    whether the remote JID indicates a hosted device
     * @return the validated signed device identity, or empty if not required or already known
     * @throws WhatsAppAdvValidationException if validation fails
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSignatureApi",
            exports = "validateADVwithIdentityKey",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<ADVSignedDeviceIdentity> extractAndValidateRemoteSignedDeviceIdentity(
            Jid remoteJid,
            Node remoteIdentityNode,
            byte[] remoteIdentityKey,
            boolean isHostedFromJid
    ) {
        Objects.requireNonNull(remoteJid, "remoteJid cannot be null");
        Objects.requireNonNull(remoteIdentityNode, "remoteIdentityNode cannot be null");
        Objects.requireNonNull(remoteIdentityKey, "remoteIdentityKey cannot be null");

        // ADV validation is required only for companion devices.
        if (!requiresValidation(remoteJid)) {
            return Optional.empty();
        }

        // Skip full validation when the stored device identity already matches.
        var storedDeviceIdentityKey = findStoredDeviceIdentityKey(remoteJid);
        if (storedDeviceIdentityKey.isPresent() && Arrays.equals(storedDeviceIdentityKey.get(), remoteIdentityKey)) {
            return Optional.empty();
        }

        var storedUserIdentityKey = findStoredUserIdentityKey(remoteJid);

        var remoteIdentityBytes = remoteIdentityNode.getChild("device-identity")
                .orElseThrow(() -> new WhatsAppAdvValidationException.MissingDeviceIdentity(remoteJid))
                .toContentBytes()
                .orElseThrow(() -> new WhatsAppAdvValidationException.EmptyDeviceIdentity(remoteJid));
        var remoteIdentity = ADVSignedDeviceIdentitySpec.decode(remoteIdentityBytes);

        var remoteIdentityDetails = remoteIdentity.details()
                .orElseThrow(() -> new NullPointerException("details cannot be null"));

        // The account signature header derives from the protobuf deviceType; the device
        // signature header derives from the JID hosted flag. Both checks are gated by
        // the hosted-devices feature.
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
                // Fall back to the E2EE header on decode failure.
            }
        }

        var deviceSignatureHeader = E2EE_DEVICE_SIGNATURE_HEADER;
        if (isBizHostedDevicesEnabled() && isHostedFromJid) {
            deviceSignatureHeader = HOSTED_DEVICE_SIGNATURE_HEADER;
        }

        // The protobuf accountSignatureKey takes precedence; the stored user identity
        // key is the fallback when the embedded value is missing or empty.
        var remoteIdentityAccountSignatureKey = remoteIdentity.accountSignatureKey()
                .orElse(null);
        if (remoteIdentityAccountSignatureKey == null) {
            remoteIdentityAccountSignatureKey = storedUserIdentityKey.orElse(null);
        }
        if (isBizHostedDevicesEnabled()
                && remoteIdentityAccountSignatureKey != null
                && remoteIdentityAccountSignatureKey.length == 0) {
            remoteIdentityAccountSignatureKey = storedUserIdentityKey.orElse(null);
        }

        if (remoteIdentityAccountSignatureKey == null || remoteIdentityAccountSignatureKey.length == 0) {
            return Optional.empty();
        }

        var remoteIdentityAccountSignature = remoteIdentity.accountSignature()
                .orElseThrow(() -> new NullPointerException("accountSignature cannot be null"));
        var accountMessage = DataUtils.concatByteArrays(accountSignatureHeader, remoteIdentityDetails, remoteIdentityKey);
        if (!Curve25519.verifySignature(remoteIdentityAccountSignatureKey, accountMessage, remoteIdentityAccountSignature)) {
            throw new WhatsAppAdvValidationException.AccountSignatureFailed(remoteJid);
        }

        var remoteIdentityDeviceSignature = remoteIdentity.deviceSignature()
                .orElseThrow(() -> new NullPointerException("deviceSignature cannot be null"));
        var deviceMessage = DataUtils.concatByteArrays(deviceSignatureHeader, remoteIdentityDetails, remoteIdentityKey, remoteIdentityAccountSignatureKey);
        if (!Curve25519.verifySignature(remoteIdentityKey, deviceMessage, remoteIdentityDeviceSignature)) {
            throw new WhatsAppAdvValidationException.DeviceSignatureFailed(remoteJid);
        }

        return Optional.of(remoteIdentity);
    }

    /**
     * Decodes and verifies a signed key index list against the user's locally-stored
     * primary identity.
     *
     * <p>Standard E2EE path used for normal accounts: the verification key comes from
     * the local Signal store (device 0 of {@code userJid}), and any embedded
     * {@code accountSignatureKey} field on the wire is ignored. The server commonly
     * omits that field because peers are expected to verify against the identity they
     * already trust from their Signal session with the primary device. The returned
     * result therefore carries an empty {@code accountSignatureKey}.
     *
     * @param userJid             the user JID whose primary identity is the
     *                            verification key
     * @param signedKeyIndexBytes the raw signed key index list bytes
     * @return the validated key index list data, or empty when no local primary
     *         identity is known or validation fails
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvDeviceNotificationUtils",
            exports = "decodeSignedKeyIndexBytes",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<ValidatedKeyIndexListResult> decodeSignedKeyIndexBytes(Jid userJid, byte[] signedKeyIndexBytes) {
        Objects.requireNonNull(userJid, "userJid cannot be null");
        Objects.requireNonNull(signedKeyIndexBytes, "signedKeyIndexBytes cannot be null");

        var localPrimaryIdentity = findStoredUserIdentityKey(userJid).orElse(null);
        if (localPrimaryIdentity == null) {
            return Optional.empty();
        }

        return decodeAndVerifySignedKeyIndexList(signedKeyIndexBytes, localPrimaryIdentity, null);
    }

    /**
     * Decodes and verifies a signed key index list against the embedded account
     * signature key.
     *
     * <p>Hosted-business coexistence path: the verification key must be present in the
     * outer protobuf {@code accountSignatureKey} field; if it is missing or empty the
     * result is empty. Used when peers may not yet have a Signal session with the
     * primary phone (and therefore cannot resolve a stored identity) but still need to
     * trust a freshly-received key index list. The returned result carries the
     * embedded key so callers can persist it as the primary identity.
     *
     * @param signedKeyIndexBytes the raw signed key index list bytes
     * @return the validated key index list data, or empty if validation fails
     * @throws NullPointerException if {@code signedKeyIndexBytes} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvDeviceNotificationUtils",
            exports = "verifySKeyIndexWithAccSigKey",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<ValidatedKeyIndexListResult> verifySKeyIndexWithAccSigKey(byte[] signedKeyIndexBytes) {
        Objects.requireNonNull(signedKeyIndexBytes, "signedKeyIndexBytes cannot be null");

        try {
            var signedKeyIndexList = ADVSignedKeyIndexListSpec.decode(signedKeyIndexBytes);
            var accountSignatureKey = signedKeyIndexList.accountSignatureKey()
                    .filter(key -> key.length > 0)
                    .orElse(null);
            if (accountSignatureKey == null) {
                return Optional.empty();
            }
            return verifyAndBuildResult(signedKeyIndexList, accountSignatureKey, accountSignatureKey);
        } catch (ProtobufDeserializationException e) {
            return Optional.empty();
        }
    }

    /**
     * Decodes the outer {@code ADVSignedKeyIndexList} envelope and dispatches to
     * {@link #verifyAndBuildResult} for signature verification and inner decoding.
     *
     * @param signedKeyIndexBytes       the raw signed key index list bytes
     * @param verificationKey           the public key used to verify the signature
     * @param resultAccountSignatureKey the key embedded in the returned result, or
     *                                  {@code null} for the standard path that has
     *                                  no embedded key to forward
     * @return the validated key index list data, or empty if validation fails
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvDeviceNotificationUtils",
            exports = "decodeSignedKeyIndexBytes",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Optional<ValidatedKeyIndexListResult> decodeAndVerifySignedKeyIndexList(
            byte[] signedKeyIndexBytes,
            byte[] verificationKey,
            byte[] resultAccountSignatureKey
    ) {
        try {
            var signedKeyIndexList = ADVSignedKeyIndexListSpec.decode(signedKeyIndexBytes);
            return verifyAndBuildResult(signedKeyIndexList, verificationKey, resultAccountSignatureKey);
        } catch (ProtobufDeserializationException e) {
            return Optional.empty();
        }
    }

    /**
     * Verifies the account signature on a parsed {@code ADVSignedKeyIndexList}, decodes
     * the inner {@code ADVKeyIndexList}, and builds a {@link ValidatedKeyIndexListResult}.
     *
     * @param signedKeyIndexList        the parsed outer envelope
     * @param verificationKey           the public key used to verify the signature
     * @param resultAccountSignatureKey the key embedded in the returned result, or
     *                                  {@code null} for the standard path
     * @return the validated key index list data, or empty if any check fails
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvDeviceNotificationUtils",
            exports = "decodeSignedKeyIndexBytes",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Optional<ValidatedKeyIndexListResult> verifyAndBuildResult(
            ADVSignedKeyIndexList signedKeyIndexList,
            byte[] verificationKey,
            byte[] resultAccountSignatureKey
    ) {
        var details = signedKeyIndexList.details().orElse(null);
        if (details == null) {
            return Optional.empty();
        }

        var accountSignature = signedKeyIndexList.accountSignature()
                .filter(sig -> sig.length > 0)
                .orElse(null);
        if (accountSignature == null) {
            return Optional.empty();
        }

        var message = DataUtils.concatByteArrays(KEY_INDEX_LIST_SIGNATURE_HEADER, details);
        if (!Curve25519.verifySignature(verificationKey, message, accountSignature)) {
            return Optional.empty();
        }

        try {
            var keyIndexList = ADVKeyIndexListSpec.decode(details);
            var keyIndexListRawId = keyIndexList.rawId();
            var keyIndexListTimestamp = keyIndexList.timestamp();
            if (keyIndexListRawId.isEmpty() || keyIndexListTimestamp.isEmpty()) {
                return Optional.empty();
            }

            var keyIndexListValidIndexesSet = new LinkedHashSet<>(keyIndexList.validIndexes());
            var keyIndexListCurrentIndex = keyIndexList.currentIndex().orElse(0);
            var keyIndexListAccountType = keyIndexList.accountType().orElse(ADVEncryptionType.E2EE);

            return Optional.of(new ValidatedKeyIndexListResult(
                    keyIndexListRawId.getAsInt(),
                    keyIndexListTimestamp.get(),
                    keyIndexListValidIndexesSet,
                    keyIndexListCurrentIndex,
                    keyIndexListAccountType,
                    resultAccountSignatureKey
            ));
        } catch (ProtobufDeserializationException e) {
            return Optional.empty();
        }
    }

    /**
     * Returns the stored identity key for the exact device.
     *
     * @param deviceJid the device JID, including the device number
     * @return the identity key bytes, or empty when no key is stored
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSignatureApi",
            exports = "validateADVwithIdentityKey",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Optional<byte[]> findStoredDeviceIdentityKey(Jid deviceJid) {
        if (deviceJid == null) {
            return Optional.empty();
        }
        var address = new SignalProtocolAddress(deviceJid.user(), deviceJid.device());
        return store.findIdentityByAddress(address)
                .map(SignalIdentityKey::toEncodedPoint);
    }

    /**
     * Returns the stored identity key for the user (device 0).
     *
     * <p>Used as a fallback for {@code accountSignatureKey} when the protobuf field is
     * missing.
     *
     * @param jid the JID; the device number is ignored
     * @return the identity key bytes, or empty when no key is stored
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSignatureApi",
            exports = "validateADVwithIdentityKey",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Optional<byte[]> findStoredUserIdentityKey(Jid jid) {
        if (jid == null) {
            return Optional.empty();
        }
        var address = new SignalProtocolAddress(jid.user(), 0);
        return store.findIdentityByAddress(address)
                .map(SignalIdentityKey::toEncodedPoint);
    }

    /**
     * Returns whether the JID points to a device that requires ADV validation.
     *
     * @param jid the JID to test
     * @return {@code true} for companion devices (device id different from the
     *         primary id)
     * @throws NullPointerException if {@code jid} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvSignatureApi",
            exports = "validateADVwithIdentityKey",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean requiresValidation(Jid jid) {
        Objects.requireNonNull(jid, "jid cannot be null");
        return jid.device() != DeviceConstants.PRIMARY_DEVICE_ID;
    }
}
