package com.github.auties00.cobalt.sync.key;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.message.send.id.MessageIdGenerator;
import com.github.auties00.cobalt.message.send.id.MessageIdVersion;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.device.sync.MissingDeviceSyncKeyBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainerBuilder;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessageBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyIdBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyRequestBuilder;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for requesting missing sync keys from companion devices.
 * <p>
 * Per WhatsApp Web WAWebKeyManagementSendKeyRequestApi: when a sync key is missing,
 * the client sends an AppStateSyncKeyRequest protocol message to all companion devices.
 * Companion devices that have the key respond with AppStateSyncKeyShare.
 */
public final class MissingSyncKeyRequestService {
    private static final System.Logger LOGGER = System.getLogger(MissingSyncKeyRequestService.class.getName());

    private final WhatsAppClient client;
    private final WhatsAppStore store;

    public MissingSyncKeyRequestService(WhatsAppClient client) {
        this.client = client;
        this.store = client.store();
    }

    /**
     * Requests missing sync keys from companion devices.
     *
     * @param keyIds the IDs of the missing keys
     */
    public void requestMissingKeys(Collection<byte[]> keyIds) {
        requestMissingKeysInternal(keyIds, false);
    }

    /**
     * Requests a single missing sync key from companion devices.
     *
     * @param keyId the ID of the missing key
     */
    public void requestMissingKey(byte[] keyId) {
        requestMissingKeys(List.of(keyId));
    }

    /**
     * Re-requests missing sync keys that are already being tracked.
     *
     * <p>Per WhatsApp Web {@code requestAllSyncdMissingKeysJob}, periodic retries
     * must re-send requests for tracked keys instead of filtering them out as duplicates.
     *
     * @param keyIds the IDs of the missing keys to re-request
     */
    public void reRequestMissingKeys(Collection<byte[]> keyIds) {
        requestMissingKeysInternal(keyIds, true);
    }

    /**
     * Gets the list of companion devices that can be asked for missing keys.
     * <p>
     * Per WhatsApp Web WAWebKeyManagementUtils.getPeerDevices:
     * Returns all devices from our own device list except the current device.
     */
    @SuppressWarnings("OptionalIsPresent")
    private List<Jid> getCompanionDevices() {
        var myJid = store.jid()
                .orElse(null);
        if (myJid == null) {
            return List.of();
        }

        var myDeviceList = store.findDeviceList(myJid.toUserJid());
        if (myDeviceList.isEmpty()) {
            return List.of();
        }

        return myDeviceList.get()
                .devices()
                .stream()
                .filter(device -> device.id() != myJid.device()) // Exclude our own device
                .map(device -> device.toDeviceJid(myJid.user(), myJid.server()))
                .toList();
    }

    /**
     * Sends a key request message to a specific device.
     */
    private void requestMissingKeysInternal(Collection<byte[]> keyIds, boolean includeTracked) {
        if (keyIds.isEmpty()) {
            return;
        }

        var requestedKeyIds = keyIds.stream()
                .filter(Objects::nonNull)
                .map(id -> Arrays.copyOf(id, id.length))
                .filter(id -> includeTracked || store.findMissingSyncKey(id).isEmpty())
                .toList();
        if (requestedKeyIds.isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG, "All requested keys are already tracked, skipping duplicate request");
            return;
        }

        var companionDevices = getCompanionDevices();
        var requestedDeviceIds = companionDevices.stream()
                .map(Jid::device)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        var keyIdHexes = requestedKeyIds.stream()
                .map(id -> HexFormat.of().formatHex(id))
                .toList();
        LOGGER.log(System.Logger.Level.INFO, "Requesting missing sync keys {0} from companion devices {1}",
                keyIdHexes, requestedDeviceIds);

        if (companionDevices.isEmpty()) {
            trackMissingKeys(requestedKeyIds, Set.of());
            LOGGER.log(System.Logger.Level.WARNING, "No companion devices available to request missing keys from");
            return;
        }

        var keyIdList = requestedKeyIds.stream()
                .map(id -> new AppStateSyncKeyIdBuilder()
                        .keyId(id)
                        .build())
                .toList();
        var keyRequest = new AppStateSyncKeyRequestBuilder()
                .keyIds(keyIdList)
                .build();

        var successfulDeviceIds = new LinkedHashSet<Integer>();
        for (var device : companionDevices) {
            if (sendKeyRequest(device, keyRequest)) {
                successfulDeviceIds.add(device.device());
            }
        }

        trackMissingKeys(requestedKeyIds, successfulDeviceIds);
    }

    private void trackMissingKeys(Collection<byte[]> keyIds, Set<Integer> successfulDeviceIds) {
        for (var keyId : keyIds) {
            var missingKey = store.findMissingSyncKey(keyId)
                    .orElseGet(() -> new MissingDeviceSyncKeyBuilder()
                            .keyId(keyId)
                            .timestamp(Instant.now())
                            .askedDevices(Set.of())
                            .build());
            successfulDeviceIds.forEach(missingKey::markDeviceAsked);
            store.addMissingSyncKey(missingKey);
        }
    }

    private boolean sendKeyRequest(
            Jid device,
            com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyRequest keyRequest
    ) {
        try {
            var self = store.jid().orElse(null);
            if (self == null) {
                return false;
            }

            var protocolMessage = new ProtocolMessageBuilder()
                    .type(ProtocolMessage.Type.APP_STATE_SYNC_KEY_REQUEST)
                    .appStateSyncKeyRequest(keyRequest)
                    .build();
            var messageContainer = new MessageContainerBuilder()
                    .protocolMessage(protocolMessage)
                    .build();
            var messageKey = new MessageKeyBuilder()
                    .id(MessageIdGenerator.generate(MessageIdVersion.V2, self))
                    .parentJid(self)
                    .fromMe(true)
                    .senderJid(self)
                    .build();
            var messageInfo = new ChatMessageInfoBuilder()
                    .key(messageKey)
                    .message(messageContainer)
                    .timestamp(Instant.now())
                    .senderJid(self)
                    .build();
            client.sendPeerMessage(device, messageInfo);
            return true;
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to send key request to device {0}: {1}",
                    device, e.getMessage());
            return false;
        }
    }
}
