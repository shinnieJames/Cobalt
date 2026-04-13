package com.github.auties00.cobalt.device.stanza;

import com.github.auties00.cobalt.device.DeviceConstants;
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
 * Parses USync IQ responses into device list results.
 *
 * <p>Combines the behavior of several WA Web modules to parse and validate the USync
 * response: {@code WAWebUsync.usyncParser} for response structure, {@code WAWebUsyncDevice.deviceParser}
 * for per-user device data, and {@code WAWebHandleAdvForUsyncApi} for key index validation
 * and omitted result handling.
 *
 * @implNote WAWebUsyncDevice.deviceParser: parses device lists, key indices, and hosting
 * status from USync response elements.
 * WAWebUsync.usyncParser: parses the USync IQ response envelope.
 * WAWebHandleAdvForUsyncApi.handleADVSyncResultSync: routes results to full or omitted handlers.
 */
public final class DeviceUSyncResponseParser {
    /**
     * Logger for diagnostic messages during USync response parsing.
     *
     * @implNote NO_WA_BASIS: Java-specific logging infrastructure.
     */
    private static final System.Logger LOGGER = System.getLogger(DeviceUSyncResponseParser.class.getName());

    /**
     * ADV validator service for key index validation and hosted device gating.
     *
     * @implNote WAWebHandleAdvDeviceNotificationUtils: used for verifying signed key index lists.
     * WAWebBizCoexGatingUtils: used for hosted device feature gating.
     */
    private final DeviceADVValidator advValidatorService;

    /**
     * Creates a new USync response parser.
     *
     * @implNote WAWebHandleAdvForUsyncApi: the handler uses WAWebHandleAdvDeviceNotificationUtils
     * for key index validation, which is provided here via the ADV validator service.
     * @param advValidatorService the ADV validator service for key index validation and
     *                            hosted device gating
     */
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
     *
     * @implNote WAWebUsync.usyncParser: parses the USync IQ response, extracting protocol-level
     * errors from {@code usync > result > devices > error}, and per-user device data from
     * {@code usync > list > user > devices} via WAWebUsyncDevice.deviceParser.
     * @param responseNode the IQ response node
     * @return list of device list results (full, omitted, or error)
     */
    public List<DeviceListResult> parse(Node responseNode) {
        // WAWebUsync.usyncParser: r = t.child("usync")
        var usyncNode = responseNode.getChild("usync");
        if (usyncNode.isEmpty()) {
            return List.of();
        }

        var usync = usyncNode.get();

        // WAWebAdvHandlerApi: error.all is fatal and aborts entire request
        // WAWebUsync.USyncQuery.execute: if IQ fails, returns error.all
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

        // WAWebUsyncUsername.usernameParser: extracts username from usync/list/user nodes
        var usernameMap = parseUsernameMap(usync);

        // WAWebUsync.usyncParser: check protocol-level devices error from result > devices > error
        // WAWebAdvHandlerApi: error.devices is non-fatal, allows continuing with other users
        var protocolErrorStream = usync.streamChild("result")
                .flatMap(result -> result.streamChild("devices"))
                .flatMap(devices -> devices.streamChild("error"))
                .map(error -> {
                    var errorCode = error.getAttributeAsInt("code", 0);
                    var errorText = error.getAttributeAsString("text", "Unknown error");
                    LOGGER.log(System.Logger.Level.WARNING, "USync devices error: code={0}, text={1}", errorCode, errorText);
                    return (DeviceListResult) new DeviceListResult.Error(null, errorCode, errorText, false);
                });

        // WAWebUsync.usyncParser.m: per-user data is in usync > list > user
        // WAWebUsyncDevice.deviceParser: each user has a devices child with device-list and key-index-list
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
     *
     * @implNote WAWebUsyncUsername.usernameParser: extracts username strings from
     * {@code usync > list > user > username} nodes for each user.
     * @param usync the usync node
     * @return map of user JID to username
     */
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
     *
     * @implNote WAWebUsync.usyncParser.m: for each user in the list, extracts the devices child
     * and passes it to WAWebUsyncDevice.deviceParser for parsing. The deviceParser checks for
     * error, then extracts device-list and key-index-list from the devices node.
     * @param userNode    the user node from {@code usync > list > user}
     * @param usernameMap the username map for correlating usernames
     * @return stream of device list results for this user
     */
    private Stream<DeviceListResult> parseUserDevices(Node userNode, Map<Jid, String> usernameMap) {
        var jidAttr = userNode.getAttributeAsJid("jid");
        if (jidAttr.isEmpty()) {
            return Stream.empty();
        }

        var userJid = jidAttr.get().toUserJid();

        // WAWebUsyncDevice.deviceParser: operates on the <devices> child of the user node
        var devicesNode = userNode.getChild("devices");
        if (devicesNode.isEmpty()) {
            return Stream.empty();
        }

        var devices = devicesNode.get();

        // WAWebUsyncDevice.deviceParser: e.assertTag("devices"), then check for error child
        var errorChild = devices.getChild("error");
        if (errorChild.isPresent()) {
            var error = errorChild.get();
            var errorCode = error.getAttributeAsInt("code", 0);
            var errorText = error.getAttributeAsString("text", "Unknown error");
            LOGGER.log(System.Logger.Level.WARNING,
                    "USync user devices error for {0}: code={1}, text={2}", userJid, errorCode, errorText);
            return Stream.of(new DeviceListResult.Error(userJid, errorCode, errorText, false));
        }

        // WAWebUsyncDevice.deviceParser: extract key-index-list and device-list
        var keyIndexListNode = devices.getChild("key-index-list", null);
        var deviceListNode = devices.getChild("device-list").orElse(null);

        // WAWebHandleAdvForUsyncApi.handleADVSyncResultSync: parse signed content once for reuse
        var signedKeyIndexBytes = keyIndexListNode != null
                ? keyIndexListNode.toContentBytes().orElse(null)
                : null;

        // WAWebHandleAdvForUsyncApi.handleADVSyncResultSync: omitted result when no signed content
        if (signedKeyIndexBytes == null) {
            return parseOmittedResult(userJid, deviceListNode, keyIndexListNode);
        }

        // WAWebHandleAdvForUsyncApi.handleADVSyncResultSync: full device list response
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
     *
     * @implNote WAWebHandleAdvForUsyncApi.handleADVSyncResultSync: when keyIndex is null or
     * signedKeyIndexBytes is null, delegates to WAWebHandleAdvOmittedResultApi.handleOmittedResult
     * after checking for companion devices.
     * @param userJid          the user JID
     * @param deviceListNode   the device-list node, or {@code null}
     * @param keyIndexListNode the key-index-list node, or {@code null}
     * @return stream containing the omitted result, or empty if invalid
     */
    private Stream<DeviceListResult> parseOmittedResult(Jid userJid, Node deviceListNode, Node keyIndexListNode) {
        // WAWebHandleAdvOmittedResultApi: reject if device-list has companion devices
        if (deviceListNode != null && hasCompanionDevices(deviceListNode)) {
            return Stream.empty();
        }

        if (keyIndexListNode == null) {
            var result = new DeviceListResult.Omitted(userJid, null, null, true);
            return Stream.of(result);
        }

        var ts = keyIndexListNode.getAttributeAsLong("ts", null);
        var timestamp = ts != null ? Instant.ofEpochSecond(ts) : null;

        var expTs = keyIndexListNode.getAttributeAsLong("expected_ts", null);
        var expectedTs = expTs != null ? Instant.ofEpochSecond(expTs) : null;

        var result = new DeviceListResult.Omitted(userJid, timestamp, expectedTs, true);
        return Stream.of(result);
    }

    /**
     * Checks if the device-list contains companion (non-primary) devices.
     *
     * @implNote WAWebHandleAdvForUsyncApi.handleADVSyncResultSync: checks if
     * {@code deviceList.some(e => e.id !== DEFAULT_DEVICE_ID)} to detect companion devices.
     * @param deviceListNode the device-list node
     * @return {@code true} if any device has a non-primary device ID
     */
    private boolean hasCompanionDevices(Node deviceListNode) {
        return deviceListNode.streamChildren("device")
                .anyMatch(device -> !device.hasAttribute("id", DeviceConstants.PRIMARY_DEVICE_ID));
    }

    /**
     * Parses a full device list result with signed key index validation.
     *
     * <p>Validates the signed key index list, builds a key index map, parses device entries,
     * and constructs a full device list result.
     *
     * @implNote WAWebHandleAdvKeyIndexResultApi.handleKeyIndexResultSync: validates the signed
     * key index list via WAWebHandleAdvDeviceNotificationUtils.verifySKeyIndexWithAccSigKey,
     * extracts device information, and builds the update record.
     * @param userJid            the user JID
     * @param username           the username from username protocol, or {@code null}
     * @param deviceListNode     the device-list node
     * @param keyIndexListNode   the key-index-list node
     * @param signedKeyIndexBytes the raw signed key index list bytes
     * @return stream containing the full result, or empty if validation fails
     */
    private Stream<DeviceListResult> parseFullResult(
            Jid userJid,
            String username,
            Node deviceListNode,
            Node keyIndexListNode,
            byte[] signedKeyIndexBytes
    ) {
        // Extract expected timestamp
        var expectedTsSeconds = keyIndexListNode.getAttributeAsLong("expected_ts", 0);
        var expectedTs = expectedTsSeconds != 0  ? Instant.ofEpochSecond(expectedTsSeconds) : null;

        // WAWebHandleAdvDeviceNotificationUtils: validate signed key index
        // WAWebHandleAdvKeyIndexResultApi: if validation fails, return null (skip this user)
        var validatedInfo = advValidatorService.validateAndDecodeSignedKeyIndexList(signedKeyIndexBytes);
        if (validatedInfo.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Key index list signature verification failed for {0}, skipping", userJid);
            return Stream.empty();
        }

        var info = validatedInfo.get();

        // Build key index map from key-index-list/device nodes
        var keyIndexMap = keyIndexListNode.streamChildren("device")
                .flatMap(this::parseKeyIndexEntry)
                .collect(Collectors.toUnmodifiableMap(KeyIndexEntry::deviceId, KeyIndexEntry::keyIndex));

        // WAWebUsyncDevice.deviceParser: parse device nodes with validation
        var devices = deviceListNode.streamChildren("device")
                .flatMap(deviceNode -> parseDeviceEntry(deviceNode, keyIndexMap, info.validIndexes()))
                .toList();

        // Extract metadata from validated info
        var rawId = String.valueOf(info.rawId());
        var advAccountType = info.accountType();
        var accountSignatureKey = info.accountSignatureKey();
        var currentIndex = info.currentIndex();

        // WAWebHandleAdvKeyIndexResultApi: use server timestamp from signed key index list
        var deviceList = new DeviceListBuilder()
                .userJid(userJid)
                .devices(devices)
                .timestamp(info.timestamp())
                .rawId(rawId)
                .advAccountType(advAccountType)
                .expectedTimestamp(expectedTs)
                .currentIndex(currentIndex)
                .validIndexes(info.validIndexes())
                .build();

        var result = new DeviceListResult.Full(deviceList, accountSignatureKey, username);
        return Stream.of(result);
    }

    /**
     * Parses a key index entry from a device node in the key-index-list.
     *
     * @implNote WAWebHandleAdvKeyIndexResultApi.handleKeyIndexResultSync: extracts device JID and
     * key-index from key-index-list/device nodes to build the key index map. Note that
     * WAWebUsyncDevice.deviceParser extracts keyIndex from device-list device nodes directly
     * via {@code maybeAttrInt("key-index")}, but the key-index-list/device nodes provide
     * an equivalent mapping used for ADV validation.
     * @param deviceNode the device node from key-index-list
     * @return stream containing the key index entry, or empty if required attributes are missing
     */
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
     *
     * @implNote WAWebUsyncDevice.deviceParser: maps each device child to
     * {@code {id, keyIndex, isHosted?}}. The {@code isHosted} flag is only set when
     * {@code bizHostedDevicesEnabled()} is true and {@code is_hosted="true"} attribute is present.
     * WAWebHandleAdvKeyIndexResultApi.handleKeyIndexResultSync: filters devices whose keyIndex
     * is not in validIndexes.
     * @param deviceNode   the device node from device-list
     * @param keyIndexMap  map of device ID to key index (from key-index-list)
     * @param validIndexes the set of valid key indexes, or {@code null} if not available
     * @return stream containing the parsed device info, or empty if invalid
     */
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

        // WAWebHandleAdvKeyIndexResultApi: validate keyIndex for server response devices
        // Primary device (keyIndex 0) is always accepted
        // If validIndexes is null or empty, skip validation (allow all devices)
        if (validIndexes != null && !validIndexes.isEmpty() && keyIndex != 0 && !validIndexes.contains(keyIndex)) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Device {0} has keyIndex {1} not in validIndexes {2}, excluding",
                    deviceId, keyIndex, validIndexes);
            return Stream.empty();
        }

        // WAWebUsyncDevice.deviceParser: isHosted is only set when bizHostedDevicesEnabled()
        // AND the is_hosted attribute is present and equals "true"
        var isHosted = advValidatorService.isBizHostedDevicesEnabled()
                && deviceNode.hasAttribute("is_hosted")
                && deviceNode.getAttributeAsBool("is_hosted", false);

        // WAWebHandleAdvKeyIndexResultApi: device with id === 99 is always treated as hosted
        // when bizHostedDevicesEnabled is true
        if (deviceId == DeviceConstants.HOSTED_DEVICE_ID) {
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
     *
     * @implNote WAWebUsyncUsername.usernameParser: extracts username string from the username
     * protocol child, returning error details if present.
     * @param userNode the user node from {@code usync > list > user}
     * @return stream containing the username entry, or empty if not available
     */
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

        // WAWebUsyncUsername.usernameParser: skip if error present
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
     *
     * @implNote WAWebHandleAdvKeyIndexResultApi: intermediate data structure for building
     * the key index map from key-index-list device nodes.
     * @param deviceId the device ID
     * @param keyIndex the key index value
     */
    private record KeyIndexEntry(int deviceId, int keyIndex) {}

    /**
     * Internal record for holding parsed username entries.
     *
     * @implNote WAWebUsyncUsername.usernameParser: intermediate data structure for correlating
     * usernames with user JIDs from the USync list.
     * @param userJid  the user JID
     * @param username the username string
     */
    private record UsernameEntry(Jid userJid, String username) {}
}
