package com.github.auties00.cobalt.device.stanza;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.device.DeviceConstants;
import com.github.auties00.cobalt.device.DeviceListResult;
import com.github.auties00.cobalt.device.adv.DeviceADVValidator;
import com.github.auties00.cobalt.model.device.info.DeviceInfo;
import com.github.auties00.cobalt.model.device.info.DeviceListBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Parses a USync IQ response and classifies each user entry as a full device list, an
 * omitted result, or an error.
 *
 * <p>WhatsApp's USync protocol answers a batch of user queries in a single IQ, and for the
 * device protocol each user can return either the full device list (with a signed key index
 * list), an "omitted" marker confirming the cached dhash is still valid, or a per-user
 * error. This parser walks the response tree, verifies the signed key index list via
 * {@link DeviceADVValidator}, filters devices whose keyIndex is not in the cryptographically
 * signed {@code validIndexes} set, and produces a list of typed {@link DeviceListResult}
 * instances that {@link com.github.auties00.cobalt.device.DeviceService} consumes.
 */
@WhatsAppWebModule(moduleName = "WAWebUsync")
@WhatsAppWebModule(moduleName = "WAWebUsyncDevice")
@WhatsAppWebModule(moduleName = "WAWebHandleAdvForUsyncApi")
@WhatsAppWebModule(moduleName = "WAWebUsyncUsername")
public final class DeviceUSyncResponseParser {
    /**
     * Logger for diagnostic messages during USync response parsing.
     */
    private static final System.Logger LOGGER = System.getLogger(DeviceUSyncResponseParser.class.getName());

    /**
     * ADV validator service for key index validation and hosted device gating.
     */
    private final DeviceADVValidator advValidatorService;

    /**
     * Creates a new USync response parser.
     * @param advValidatorService the ADV validator service for key index validation and
     *                            hosted device gating
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvForUsyncApi",
            exports = "handleADVSyncResultSync",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public DeviceUSyncResponseParser(DeviceADVValidator advValidatorService) {
        this.advValidatorService = Objects.requireNonNull(advValidatorService, "advValidatorService cannot be null");
    }

    /**
     * Parses a USync response into device list results.
     *
     * <p>The USync response has two main sections under the {@code <usync>} node:
     * <ul>
     *   <li>{@code <result>} - protocol-level status with error/refresh per protocol</li>
     *   <li>{@code <list>} - per-user data with protocol-specific children</li>
     * </ul>
     * @param responseNode the IQ response node
     * @return list of device list results (full, omitted, or error)
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "usyncParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    public List<DeviceListResult> parse(Node responseNode) {
        var usyncNode = responseNode.getChild("usync");
        if (usyncNode.isEmpty()) {
            return List.of();
        }

        var usync = usyncNode.get();

        // A global error is fatal and aborts the entire batch.
        var globalErrorNode = usync.getChild("error");
        if (globalErrorNode.isPresent()) {
            var error = globalErrorNode.get();
            var errorCode = error.getAttributeAsInt("code", 0);
            var errorText = error.getAttributeAsString("text", "Unknown error");
            LOGGER.log(System.Logger.Level.WARNING,
                    "USync global error: code={0}, text={1}", errorCode, errorText);
            var errorResult = new DeviceListResult.Error(null, errorCode, errorText, true);
            return List.of(errorResult);
        }

        var usernameMap = parseUsernameMap(usync);

        // A protocol-level devices error is non-fatal and only affects matching users.
        var protocolErrorStream = usync.streamChild("result")
                .flatMap(result -> result.streamChild("devices"))
                .flatMap(devices -> devices.streamChild("error"))
                .map(error -> {
                    var errorCode = error.getAttributeAsInt("code", 0);
                    var errorText = error.getAttributeAsString("text", "Unknown error");
                    LOGGER.log(System.Logger.Level.WARNING, "USync devices error: code={0}, text={1}", errorCode, errorText);
                    return (DeviceListResult) new DeviceListResult.Error(null, errorCode, errorText, false);
                });

        var listNode = usync.getChild("list");
        if (listNode.isEmpty()) {
            return protocolErrorStream.toList();
        }

        var userResults = listNode.get().streamChildren("user")
                .flatMap(userNode -> parseUserDevices(userNode, usernameMap));
        return Stream.concat(protocolErrorStream, userResults)
                .toList();
    }

    /**
     * Parses the username map from the USync list.
     * @param usync the usync node
     * @return map of user JID to username
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUsername",
            exports = "usernameParser",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Map<Jid, String> parseUsernameMap(Node usync) {
        return usync.streamChild("list")
                .flatMap(list -> list.streamChildren("user"))
                .flatMap(this::parseUsernameEntry)
                .collect(Collectors.toUnmodifiableMap(UsernameEntry::userJid, UsernameEntry::username));
    }

    /**
     * Parses device results for a single user from the USync list.
     *
     * <p>The user node may contain a {@code <devices>} child (from the device protocol)
     * which itself contains {@code <device-list>}, {@code <key-index-list>}, and optionally
     * an {@code <error>} element.
     * @param userNode    the user node from {@code usync > list > user}
     * @param usernameMap the username map for correlating usernames
     * @return stream of device list results for this user
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncDevice",
            exports = "deviceParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Stream<DeviceListResult> parseUserDevices(Node userNode, Map<Jid, String> usernameMap) {
        var jidAttr = userNode.getAttributeAsJid("jid");
        if (jidAttr.isEmpty()) {
            return Stream.empty();
        }

        var userJid = jidAttr.get().toUserJid();

        var devicesNode = userNode.getChild("devices");
        if (devicesNode.isEmpty()) {
            return Stream.empty();
        }

        var devices = devicesNode.get();

        var errorChild = devices.getChild("error");
        if (errorChild.isPresent()) {
            var error = errorChild.get();
            var errorCode = error.getAttributeAsInt("code", 0);
            var errorText = error.getAttributeAsString("text", "Unknown error");
            LOGGER.log(System.Logger.Level.WARNING,
                    "USync user devices error for {0}: code={1}, text={2}", userJid, errorCode, errorText);
            return Stream.of(new DeviceListResult.Error(userJid, errorCode, errorText, false));
        }

        var keyIndexListNode = devices.getChild("key-index-list", null);
        var deviceListNode = devices.getChild("device-list").orElse(null);

        var signedKeyIndexBytes = keyIndexListNode != null
                ? keyIndexListNode.toContentBytes().orElse(null)
                : null;

        if (signedKeyIndexBytes == null) {
            return parseOmittedResult(userJid, deviceListNode, keyIndexListNode);
        }

        if (deviceListNode == null) {
            return Stream.empty();
        }

        var username = usernameMap.get(userJid);
        return parseFullResult(userJid, username, deviceListNode, keyIndexListNode, signedKeyIndexBytes);
    }

    /**
     * Parses an omitted device list result when no signed key index bytes are present.
     *
     * <p>If the device-list contains companion devices (non-primary), the result is dropped
     * because a companion device list without a signed key index is invalid.
     * @param userJid          the user JID
     * @param deviceListNode   the device-list node, or {@code null}
     * @param keyIndexListNode the key-index-list node, or {@code null}
     * @return stream containing the omitted result, or empty if invalid
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvForUsyncApi",
            exports = "handleADVSyncResultSync",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Stream<DeviceListResult> parseOmittedResult(Jid userJid, Node deviceListNode, Node keyIndexListNode) {
        // A device-list carrying companion devices without a signed key index is invalid.
        if (deviceListNode != null && hasCompanionDevices(deviceListNode)) {
            return Stream.empty();
        }

        if (keyIndexListNode == null) {
            return Stream.of(new DeviceListResult.Omitted(userJid, null, null, true));
        }

        var ts = keyIndexListNode.getAttributeAsLong("ts", null);
        var timestamp = ts != null ? Instant.ofEpochSecond(ts) : null;

        var expTs = keyIndexListNode.getAttributeAsLong("expected_ts", null);
        var expectedTs = expTs != null ? Instant.ofEpochSecond(expTs) : null;

        return Stream.of(new DeviceListResult.Omitted(userJid, timestamp, expectedTs, true));
    }

    /**
     * Checks if the device-list contains companion (non-primary) devices.
     * @param deviceListNode the device-list node
     * @return {@code true} if any device has a non-primary device ID
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvForUsyncApi",
            exports = "handleADVSyncResultSync",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean hasCompanionDevices(Node deviceListNode) {
        return deviceListNode.streamChildren("device")
                .anyMatch(device -> !device.hasAttribute("id", DeviceConstants.PRIMARY_DEVICE_ID));
    }

    /**
     * Parses a full device list result with signed key index validation.
     *
     * <p>Validates the signed key index list, builds a key index map, parses device entries,
     * and constructs a full device list result. The verification path mirrors WA Web's
     * {@code handleKeyIndexResultSync}: when hosted-business gating is on and the
     * device-list advertises at least one hosted device the embedded
     * {@code accountSignatureKey} is used; otherwise the user's locally-stored primary
     * identity is used and any embedded key is ignored.
     * @param userJid            the user JID
     * @param username           the username from username protocol, or {@code null}
     * @param deviceListNode     the device-list node
     * @param keyIndexListNode   the key-index-list node
     * @param signedKeyIndexBytes the raw signed key index list bytes
     * @return stream containing the full result, or empty if validation fails
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvKeyIndexResultApi",
            exports = "handleKeyIndexResultSync",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Stream<DeviceListResult> parseFullResult(
            Jid userJid,
            String username,
            Node deviceListNode,
            Node keyIndexListNode,
            byte[] signedKeyIndexBytes
    ) {
        var expectedTsSeconds = keyIndexListNode.getAttributeAsLong("expected_ts", 0);
        var expectedTs = expectedTsSeconds != 0  ? Instant.ofEpochSecond(expectedTsSeconds) : null;

        var useHostedPath = advValidatorService.isBizHostedDevicesEnabled()
                && hasHostedDeviceAttribute(deviceListNode);
        var validatedInfo = useHostedPath
                ? advValidatorService.verifySKeyIndexWithAccSigKey(signedKeyIndexBytes)
                : advValidatorService.decodeSignedKeyIndexBytes(userJid, signedKeyIndexBytes);
        if (validatedInfo.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Key index list signature verification failed for {0}, skipping", userJid);
            return Stream.empty();
        }

        var info = validatedInfo.get();

        var keyIndexMap = keyIndexListNode.streamChildren("device")
                .flatMap(this::parseKeyIndexEntry)
                .collect(Collectors.toUnmodifiableMap(KeyIndexEntry::deviceId, KeyIndexEntry::keyIndex));

        var devices = deviceListNode.streamChildren("device")
                .flatMap(deviceNode -> parseDeviceEntry(deviceNode, keyIndexMap, info.validIndexes()))
                .toList();

        var deviceList = new DeviceListBuilder()
                .userJid(userJid)
                .devices(devices)
                .timestamp(info.timestamp())
                .rawId(String.valueOf(info.rawId()))
                .advAccountType(info.accountType())
                .expectedTimestamp(expectedTs)
                .currentIndex(info.currentIndex())
                .validIndexes(info.validIndexes())
                .build();

        return Stream.of(new DeviceListResult.Full(deviceList, info.accountSignatureKey().orElse(null), username));
    }

    /**
     * Checks if the device-list contains a device with {@code is_hosted="true"}.
     * @param deviceListNode the device-list node
     * @return {@code true} if any device advertises the hosted attribute
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvKeyIndexResultApi",
            exports = "handleKeyIndexResultSync",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean hasHostedDeviceAttribute(Node deviceListNode) {
        return deviceListNode.streamChildren("device")
                .anyMatch(device -> device.hasAttribute("is_hosted", true));
    }

    /**
     * Parses a key index entry from a device node in the key-index-list.
     * @param deviceNode the device node from key-index-list
     * @return stream containing the key index entry, or empty if required attributes are missing
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvKeyIndexResultApi",
            exports = "handleKeyIndexResultSync",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Stream<KeyIndexEntry> parseKeyIndexEntry(Node deviceNode) {
        var jid = deviceNode.getAttributeAsJid("jid");
        var keyIndex = deviceNode.getAttributeAsInt("key-index");
        if (jid.isEmpty() || keyIndex.isEmpty()) {
            return Stream.empty();
        }
        return Stream.of(new KeyIndexEntry(jid.get().device(), keyIndex.getAsInt()));
    }

    /**
     * Parses a single device entry from the device-list.
     *
     * <p>Extracts the device ID, resolves the key index from the key-index-list map, validates
     * against valid indexes, and conditionally sets the hosted flag based on the
     * {@code is_hosted} attribute.
     * @param deviceNode   the device node from device-list
     * @param keyIndexMap  map of device ID to key index (from key-index-list)
     * @param validIndexes the set of valid key indexes, or {@code null} if not available
     * @return stream containing the parsed device info, or empty if invalid
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncDevice",
            exports = "deviceParser",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Stream<DeviceInfo> parseDeviceEntry(
            Node deviceNode,
            Map<Integer, Integer> keyIndexMap,
            SequencedSet<Integer> validIndexes
    ) {
        var id = deviceNode.getAttributeAsInt("id");
        if (id.isEmpty()) {
            return Stream.empty();
        }

        var deviceId = id.getAsInt();
        var keyIndex = keyIndexMap.getOrDefault(deviceId, 0);

        // The primary device (keyIndex 0) is always accepted. An empty validIndexes set
        // skips validation and allows every device.
        if (validIndexes != null && !validIndexes.isEmpty() && keyIndex != 0 && !validIndexes.contains(keyIndex)) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Device {0} has keyIndex {1} not in validIndexes {2}, excluding",
                    deviceId, keyIndex, validIndexes);
            return Stream.empty();
        }

        // The is_hosted attribute is read with strict string equality, matching WA Web.
        var bizHostedDevicesEnabled = advValidatorService.isBizHostedDevicesEnabled();
        var isHosted = bizHostedDevicesEnabled
                && "true".equals(deviceNode.getAttributeAsString("is_hosted").orElse(null));

        if (deviceId == DeviceConstants.HOSTED_DEVICE_ID && bizHostedDevicesEnabled) {
            return Stream.of(DeviceInfo.ofHosted(keyIndex));
        } else {
            return Stream.of(DeviceInfo.ofE2EE(deviceId, keyIndex, isHosted));
        }
    }

    /**
     * Parses a username entry from a user node.
     *
     * <p>Extracts the username from the {@code <username>} child of a user node,
     * skipping entries with errors or empty content.
     * @param userNode the user node from {@code usync > list > user}
     * @return stream containing the username entry, or empty if not available
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncUsername",
            exports = "usernameParser",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Stream<UsernameEntry> parseUsernameEntry(Node userNode) {
        var jid = userNode.getAttributeAsJid("jid");
        if (jid.isEmpty()) {
            return Stream.empty();
        }

        var usernameNode = userNode.getChild("username");
        if (usernameNode.isEmpty()) {
            return Stream.empty();
        }

        var node = usernameNode.get();

        // Per-user username errors are dropped because Cobalt has no downstream consumer
        // that observes them.
        if (node.getChild("error").isPresent()) {
            return Stream.empty();
        }

        var usernameContent = node.toContentString();
        if (usernameContent.isEmpty() || usernameContent.get().isEmpty()) {
            return Stream.empty();
        }

        return Stream.of(new UsernameEntry(jid.get().toUserJid(), usernameContent.get()));
    }

    /**
     * Internal record for holding parsed key index entries.
     * @param deviceId the device ID
     * @param keyIndex the key index value
     */
    private record KeyIndexEntry(int deviceId, int keyIndex) {}

    /**
     * Internal record for holding parsed username entries.
     * @param userJid  the user JID
     * @param username the username string
     */
    private record UsernameEntry(Jid userJid, String username) {}
}
