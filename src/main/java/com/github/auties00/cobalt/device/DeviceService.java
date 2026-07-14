package com.github.auties00.cobalt.device;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.adv.DeviceADVValidator;
import com.github.auties00.cobalt.exception.NodeTimeoutException;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.util.SecureBytes;
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.groups.SignalGroupCipher;
import com.github.auties00.libsignal.groups.SignalSenderKeyName;
import com.github.auties00.libsignal.key.SignalIdentityPublicKey;
import com.github.auties00.libsignal.protocol.SignalSenderKeyDistributionMessage;
import com.github.auties00.libsignal.state.SignalPreKeyBundleBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class DeviceService {
    private final WhatsAppClient client;
    private final SignalSessionCipher sessionCipher;
    private final SignalGroupCipher groupCipher;

    public DeviceService(WhatsAppClient client, SignalSessionCipher sessionCipher, SignalGroupCipher groupCipher) {
        this.client = client;
        this.sessionCipher = sessionCipher;
        this.groupCipher = groupCipher;
    }

    /**
     * Queries the device list for message sending and ensures Signal sessions exist.
     * This includes querying the device list via usync and fetching pre-keys for devices
     * that messages should be encrypted for.
     *
     * @param jids the list of user JIDs to query devices for
     * @return the swr of device JIDs that should receive the encrypted message
     */
    public Set<? extends Jid> queryDevices(Collection<? extends Jid> jids) {
        if (jids == null) {
            return Set.of();
        }

        var normalizedJids = jids.stream()
                .filter(Objects::nonNull)
                .flatMap(this::expandIdentityCandidates)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalizedJids.isEmpty()) {
            return Set.of();
        }

        var cachedDevices = getCachedDevices(normalizedJids);
        var missingJids = normalizedJids.stream()
                .filter(jid -> client.store().findDeviceList(jid.toUserJid()).isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        var freshDevices = missingJids.isEmpty()
                ? Set.<Jid>of()
                : queryDevicesForJids(missingJids);

        var devices = new LinkedHashSet<Jid>();
        devices.addAll(cachedDevices);
        devices.addAll(freshDevices);
        if (devices.isEmpty()) {
            return Set.of();
        }

        // Ensure sessions exist for all devices
        var devicesNeedingSessions = devices.stream()
                .filter(device -> client.store()
                        .findSessionByAddress(device.toSignalAddress()).isEmpty())
                .collect(Collectors.toUnmodifiableSet());

        if (!devicesNeedingSessions.isEmpty()) {
            fetchPreKeysAndCreateSessions(devicesNeedingSessions);
        }

        return devices;
    }

    private Stream<Jid> expandIdentityCandidates(Jid jid) {
        var normalized = jid.toUserJid();
        if (normalized.hasServer(JidServer.lid())) {
            var phone = client.store().findPhoneByLid(normalized);
            return phone.<Stream<Jid>>map(mappedPhone -> Stream.of(normalized, mappedPhone.toUserJid()))
                    .orElseGet(() -> Stream.of(normalized));
        }

        if (normalized.hasServer(JidServer.user()) || normalized.hasServer(JidServer.legacyUser())) {
            var lid = client.store().findLidByPhone(normalized);
            return lid.<Stream<Jid>>map(mappedLid -> Stream.of(normalized, mappedLid.toUserJid()))
                    .orElseGet(() -> Stream.of(normalized));
        }

        return Stream.of(normalized);
    }

    private Set<? extends Jid> queryDevicesForJids(Collection<? extends Jid> jids) {
        var userNodes = jids.stream()
                .distinct()
                .map(this::buildUserNode)
                .toList();

        var devicesNode = new NodeBuilder()
                .description("devices")
                .attribute("version", "2")
                .build();
        var lidNode = new NodeBuilder()
                .description("lid")
                .build();
        var queryNode = new NodeBuilder()
                .description("query")
                .content(devicesNode, lidNode)
                .build();
        var listNode = new NodeBuilder()
                .description("list")
                .content(userNodes.toArray(Node[]::new))
                .build();
        var sideListNode = new NodeBuilder()
                .description("side_list")
                .build();
        var syncNode = new NodeBuilder()
                .description("usync")
                .attribute("sid", SecureBytes.randomSid())
                .attribute("mode", "query")
                .attribute("last", "true")
                .attribute("index", "0")
                .attribute("context", "message")
                .content(queryNode, listNode, sideListNode)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "usync")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(syncNode);

        try {
            var response = client.sendNode(iqNode);
            var users = response.streamChildren("usync")
                    .flatMap(node -> node.streamChild("list"))
                    .flatMap(node -> node.streamChildren("user"))
                    .toList();
            users.forEach(this::parseAndCacheLidMapping);
            var devices = users.stream()
                    .flatMap(this::parseDevice)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            cacheDevices(devices);
            return devices;
        } catch (NodeTimeoutException exception) {
            var cachedDevices = getCachedDevices(jids);
            if (!cachedDevices.isEmpty()) {
                return cachedDevices;
            }
            throw exception;
        }
    }

    private void cacheDevices(Collection<? extends Jid> devices) {
        devices.stream()
                .collect(Collectors.groupingBy(Jid::toUserJid, LinkedHashMap::new, Collectors.toCollection(ArrayList::new)))
                .forEach((userJid, userDevices) -> client.store().addDeviceList(userJid, new ArrayList<>(userDevices)));
    }

    private Set<? extends Jid> getCachedDevices(Collection<? extends Jid> jids) {
        return jids.stream()
                .map(Jid::toUserJid)
                .map(client.store()::findDeviceList)
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Stream<Jid> parseDevice(Node user) {
        var userJid = user.getAttributeAsJid("jid");
        if (userJid.isEmpty()) {
            return Stream.empty();
        } else {
            return user.streamChild("devices")
                    .flatMap(devices -> devices.streamChild("device-list"))
                    .flatMap(deviceList -> deviceList.streamChildren("device"))
                    .map(device -> {
                        var deviceId = (int) device.getAttributeAsLong("id", 0L);
                        return userJid.get().withDevice(deviceId);
                    });
        }
    }

    private void parseAndCacheLidMapping(Node user) {
        var userJid = user.getAttributeAsJid("jid").orElse(null);
        var lidJid = parseLid(user).orElse(null);
        if (userJid == null
                || lidJid == null
                || !userJid.hasServer(JidServer.user())
                || !lidJid.hasServer(JidServer.lid())) {
            return;
        }

        client.store()
                .registerLidMapping(userJid, lidJid);
    }

    private java.util.Optional<Jid> parseLid(Node user) {
        return user.getChild("lid")
                .flatMap(lid -> lid.getAttributeAsJid("val")
                        .or(() -> lid.getAttributeAsJid("jid")));
    }

    private void fetchPreKeysAndCreateSessions(Set<? extends Jid> devices) {
        if (devices.isEmpty()) {
            return;
        }

        var keyNodes = devices.stream()
                .map(this::buildUserNode)
                .toArray(Node[]::new);
        var keyNode = new NodeBuilder()
                .description("key")
                .content(keyNodes)
                .build();
        var iqNode = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "encrypt")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(keyNode);

        client.sendNode(iqNode)
                .streamChild("list")
                .flatMap(list -> list.streamChildren("user"))
                .forEach(this::processPreKeyResponse);
    }

    private Node buildUserNode(Jid jid) {
        return new NodeBuilder()
                .description("user")
                .attribute("jid", jid)
                .build();
    }

    private void processPreKeyResponse(Node userNode) {
        var localJid = client.store()
                .jid()
                .orElseThrow(() -> new IllegalStateException("Local jid is not available"));
        var remoteJid = userNode.getRequiredAttributeAsJid("jid");
        var registrationId = userNode.getChild("registration")
                .flatMap(Node::toContentBytes)
                .map(bytes -> SecureBytes.bytesToInt(bytes, Math.min(bytes.length, 4)))
                .orElse(0);
        var identityKeyBytes = userNode.getChild("identity")
                .flatMap(Node::toContentBytes)
                .orElse(null);
        var identityKey = identityKeyBytes != null
                ? SignalIdentityPublicKey.ofDirect(identityKeyBytes)
                : null;

        var signedPreKey = userNode.getChild("skey", null);
        var preKey = userNode.getChild("key", null);
        if (identityKey == null || signedPreKey == null) {
            return;
        }

        client.store()
                .companionIdentity()
                .ifPresent(companionIdentity -> DeviceADVValidator.extractAndValidateRemoteSignedDeviceIdentity(localJid, remoteJid, companionIdentity, userNode, identityKeyBytes));

        var signedPreKeyId = signedPreKey.getChild("id")
                .flatMap(Node::toContentBytes)
                .map(bytes -> SecureBytes.bytesToInt(bytes, Math.min(bytes.length, 4)))
                .orElse(0);
        var signedPreKeyPublic = signedPreKey.getChild("value")
                .flatMap(Node::toContentBytes)
                .map(SignalIdentityPublicKey::ofDirect)
                .orElse(null);
        var signedPreKeySignature = signedPreKey.getChild("signature")
                .flatMap(Node::toContentBytes)
                .orElse(null);
        if (signedPreKeyPublic == null || signedPreKeySignature == null) {
            return;
        }

        int preKeyId = 0;
        SignalIdentityPublicKey preKeyPublic = null;
        if (preKey != null) {
            preKeyId = preKey.getChild("id")
                    .flatMap(Node::toContentBytes)
                    .map(bytes -> SecureBytes.bytesToInt(bytes, Math.min(bytes.length, 4)))
                    .orElse(0);
            preKeyPublic = preKey.getChild("value")
                    .flatMap(Node::toContentBytes)
                    .map(SignalIdentityPublicKey::ofDirect)
                    .orElse(null);
        }

        var bundle = new SignalPreKeyBundleBuilder()
                .registrationId(registrationId)
                .deviceId(remoteJid.device())
                .preKeyId(preKeyId)
                .preKeyPublic(preKeyPublic)
                .signedPreKeyId(signedPreKeyId)
                .signedPreKeyPublic(signedPreKeyPublic)
                .signedPreKeySignature(signedPreKeySignature)
                .identityKey(identityKey)
                .build();

        sessionCipher.process(remoteJid.toSignalAddress(), bundle);
    }

    public void processDistributionMessage(SignalSenderKeyName groupName, SignalSenderKeyDistributionMessage signalDistributionMessage) {
        groupCipher.process(groupName, signalDistributionMessage);
    }
}
