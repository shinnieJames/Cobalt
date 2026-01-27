package com.github.auties00.cobalt.device.usync;

import com.github.auties00.cobalt.device.info.DeviceInfo;
import com.github.auties00.cobalt.device.info.DeviceList;
import com.github.auties00.cobalt.node.Node;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Parses USync IQ responses into DeviceList objects.
 */
public final class DeviceUSyncResponseParser {
    private static final int HOSTED_DEVICE_ID = 99;

    private DeviceUSyncResponseParser() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Parses a USync response into a map of device lists.
     *
     * @param responseNode the IQ response node
     * @return list of device lists
     */
    public static List<DeviceList> parse(Node responseNode) {
        return responseNode.streamChild("usync")
                .flatMap(usync -> usync.streamChild("result"))
                .flatMap(result -> result.streamChild("devices"))
                .flatMap(devices -> devices.streamChildren("user"))
                .flatMap(DeviceUSyncResponseParser::parseDevices)
                .toList();
    }

    private static Stream<DeviceList> parseDevices(Node userNode) {
        var jid = userNode.getAttributeAsJid("jid");
        if(jid.isEmpty()) {
            return Stream.empty();
        }

        var deviceList = userNode.getChild("device-list");
        if(deviceList.isEmpty()) {
            return Stream.empty();
        }

        var keyIndexMap = buildKeyIndexMap(userNode);
        var devices = deviceList.get()
                .streamChildren("device")
                .flatMap(deviceNode -> parseDeviceEntry(deviceNode, keyIndexMap))
                .toList();
        var result = DeviceList.of(jid.get().toUserJid(), devices);
        return Stream.of(result);
    }

    private static Map<Integer, Integer> buildKeyIndexMap(Node userNode) {
        return userNode.streamChild("key-index-list")
                .flatMap(keyIndexList -> keyIndexList.streamChildren("device"))
                .flatMap(DeviceUSyncResponseParser::parseKeyIndexEntry)
                .collect(Collectors.toUnmodifiableMap(KeyIndexEntry::deviceId, KeyIndexEntry::keyIndex));
    }

    private static Stream<KeyIndexEntry> parseKeyIndexEntry(Node deviceNode) {
        var jid = deviceNode.getAttributeAsJid("jid");
        if(jid.isEmpty()) {
            return Stream.empty();
        }

        var keyIndex = (int) deviceNode.getAttributeAsLong("key-index", -1);
        if(keyIndex == -1) {
            return Stream.empty();
        }

        var result = new KeyIndexEntry(jid.get().device(), keyIndex);
        return Stream.of(result);
    }

    private static Stream<DeviceInfo> parseDeviceEntry(Node deviceNode, Map<Integer, Integer> keyIndexMap) {
        var id = (int) deviceNode.getAttributeAsLong("id", -1);
        if(id == -1) {
            return Stream.empty();
        }

        var keyIndex = keyIndexMap.getOrDefault(id, 0);
        var accountType = id == HOSTED_DEVICE_ID
                ? DeviceInfo.Type.HOSTED
                : DeviceInfo.Type.E2EE;
        var result = new DeviceInfo(id, keyIndex, accountType);
        return Stream.of(result);
    }

    private record KeyIndexEntry(int deviceId, int keyIndex) {

    }
}
