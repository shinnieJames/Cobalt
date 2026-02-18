package com.github.auties00.cobalt.device.stanza;

import com.github.auties00.cobalt.device.adv.DeviceADVValidator;
import com.github.auties00.cobalt.device.DeviceListResult;
import com.github.auties00.cobalt.device.DeviceConstants;
import com.github.auties00.cobalt.model.device.info.DeviceInfo;
import com.github.auties00.cobalt.model.device.DeviceListBuilder;
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
 * @apiNote WAWebUsyncDevice.deviceParser: parses device lists, key indices, and hosting
 * status from USync response elements.
 */
public final class DeviceUSyncResponseParser {
    private static final System.Logger LOGGER = System.getLogger(DeviceUSyncResponseParser.class.getName());

    private final DeviceADVValidator advValidatorService;

    /**
     * Creates a new USync response parser.
     *
     * @param advValidatorService the ADV validator service for key index validation
     */
    public DeviceUSyncResponseParser(DeviceADVValidator advValidatorService) {
        this.advValidatorService = Objects.requireNonNull(advValidatorService, "advValidatorService cannot be null");
    }

    /**
     * Parses a USync response into device list results.
     *
     * @param responseNode the IQ response node
     * @return list of device list results (full, omitted, or error)
     *
     * @apiNote WAWebAdvHandlerApi: handles two error types - error.all (fatal) aborts
     * processing, error.devices (non-fatal) allows continuing with other users.
     */
    public List<DeviceListResult> parse(Node responseNode) {
        var usyncNode = responseNode.getChild("usync");
        if (usyncNode.isEmpty()) {
            return List.of();
        }

        var usync = usyncNode.get();

        // WAWebAdvHandlerApi: error.all is fatal and aborts entire request
        var errorNode = usync.getChild("error");
        if (errorNode.isPresent()) {
            var error = errorNode.get();
            var errorCode = error.getAttributeAsInt("code", 0);
            var errorText = error.getAttributeAsString("text", "Unknown error");
            LOGGER.log(System.Logger.Level.WARNING,
                    "USync global error: code={0}, text={1}", errorCode, errorText);
            var errorResult = new DeviceListResult.Error(null, errorCode, errorText, true);
            return List.of(errorResult);
        }

        // WAWebUsyncUsername.usernameParser: extracts username from usync/list/user nodes
        var usernameMap = parseUsernameMap(usync);

        // WAWebAdvHandlerApi: error.devices is non-fatal, allows continuing with other users
        var resultNode = usync.getChild("result");
        if (resultNode.isEmpty()) {
            return List.of();
        }

        var devicesNode = resultNode.get().getChild("devices");
        if (devicesNode.isEmpty()) {
            return List.of();
        }

        var devices = devicesNode.get();

        // Collect results: first check for error, then parse user devices
        var errorResult = devices.streamChild("error").map(error -> {
            var errorCode = error.getAttributeAsInt("code", 0);
            var errorText = error.getAttributeAsString("text", "Unknown error");
            LOGGER.log(System.Logger.Level.WARNING, "USync devices error: code={0}, text={1}", errorCode, errorText);
            return new DeviceListResult.Error(null, errorCode, errorText, false);
        });
        var userResults = devices.streamChildren("user")
                .flatMap(userNode -> parseUserDevices(userNode, usernameMap));
        return Stream.concat(errorResult, userResults)
                .toList();
    }

    private Map<Jid, String> parseUsernameMap(Node usync) {
        return usync.streamChild("list")
                .flatMap(list -> list.streamChildren("user"))
                .flatMap(this::parseUsernameEntry)
                .collect(Collectors.toUnmodifiableMap(UsernameEntry::userJid, UsernameEntry::username));
    }

    private Stream<DeviceListResult> parseUserDevices(Node userNode, Map<Jid, String> usernameMap) {
        var jidAttr = userNode.getAttributeAsJid("jid");
        if (jidAttr.isEmpty()) {
            return Stream.empty();
        }

        var userJid = jidAttr.get().toUserJid();
        var deviceListNode = userNode.getChild("device-list").orElse(null);
        var keyIndexListNode = userNode.getChild("key-index-list", null);

        // WAWebHandleAdvOmittedResultApi: parse signed content once for reuse
        var signedKeyIndexBytes = keyIndexListNode != null
                ? keyIndexListNode.toContentBytes().orElse(null)
                : null;

        // WAWebHandleAdvOmittedResultApi: omitted result when no signed content
        if (signedKeyIndexBytes == null) {
            return parseOmittedResult(userJid, deviceListNode, keyIndexListNode);
        }

        // WAWebHandleAdvForUsyncApi: full device list response requires device-list
        if (deviceListNode == null) {
            return Stream.empty();
        }

        var username = usernameMap.get(userJid);
        return parseFullResult(userJid, username, deviceListNode, keyIndexListNode, signedKeyIndexBytes);
    }

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

    private boolean hasCompanionDevices(Node deviceListNode) {
        return deviceListNode.streamChildren("device")
                .anyMatch(device -> !device.hasAttribute("id", DeviceConstants.PRIMARY_DEVICE_ID));
    }

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

    private Stream<KeyIndexEntry> parseKeyIndexEntry(Node deviceNode) {
        var jid = deviceNode.getAttributeAsJid("jid");
        var keyIndex = deviceNode.getAttributeAsInt("key-index");
        if (jid.isEmpty() || keyIndex.isEmpty()) {
            return Stream.empty();
        }
        return Stream.of(new KeyIndexEntry(jid.get().device(), keyIndex.getAsInt()));
    }

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

        // WAWebUsyncDevice.deviceParser: check device ID 99 or is_hosted="true"
        // WAWebDBDeviceListFanout.getFanOutList: checks both e.id === 99 || e.isHosted === true
        // Preserve the is_hosted attribute separately from device ID
        var isHostedAttribute = deviceNode.getAttributeAsBool("is_hosted", false);

        if (deviceId == DeviceConstants.HOSTED_DEVICE_ID) {
            // Device ID 99 is always hosted
            return Stream.of(DeviceInfo.ofHosted(keyIndex));
        } else {
            // Preserve the is_hosted flag even for non-99 device IDs
            return Stream.of(DeviceInfo.ofE2EE(deviceId, keyIndex, isHostedAttribute));
        }
    }

    /**
     * Parses a username entry from a user node.
     *
     * @apiNote WAWebUsyncUsername.usernameParser: extracts username string or error details.
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

    private record KeyIndexEntry(int deviceId, int keyIndex) {}

    private record UsernameEntry(Jid userJid, String username) {}
}
