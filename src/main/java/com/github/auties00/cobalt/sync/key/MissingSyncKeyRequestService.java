package com.github.auties00.cobalt.sync.key;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.device.sync.MissingDeviceSyncKeyBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerBuilder;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessageBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyIdBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyRequestBuilder;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
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
     * <p>
     * Per WhatsApp Web WAWebKeyManagementSendKeyRequestApi.sendAppStateSyncKeyRequest:
     * - Gets peer devices (companion devices)
     * - Creates a protocol message with APP_STATE_SYNC_KEY_REQUEST type
     * - Sends it to all peer devices
     *
     * @param keyIds the IDs of the missing keys
     */
    public void requestMissingKeys(Collection<byte[]> keyIds) {
        if (keyIds.isEmpty()) {
            return;
        }

        // Get companion devices to request keys from
        var companionDevices = getCompanionDevices();
        if (companionDevices.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING, "No companion devices available to request missing keys from");
            return;
        }

        // Create the key request
        var keyIdList = keyIds.stream()
                .map(id -> new AppStateSyncKeyIdBuilder()
                        .keyId(id)
                        .build())
                .toList();
        var keyRequest = new AppStateSyncKeyRequestBuilder()
                .keyIds(keyIdList)
                .build();

        // Create the protocol message
        var protocolMessage = new ProtocolMessageBuilder()
                .type(ProtocolMessage.Type.APP_STATE_SYNC_KEY_REQUEST)
                .appStateSyncKeyRequest(keyRequest)
                .build();

        var messageContainer = new MessageContainerBuilder()
                .protocolMessage(protocolMessage)
                .build();

        // Track which devices we're requesting from
        var deviceIds = companionDevices.stream()
                .map(Jid::device)
                .collect(Collectors.toSet());

        // Log the request
        var keyIdHexes = keyIds.stream()
                .map(id -> HexFormat.of().formatHex(id))
                .toList();
        LOGGER.log(System.Logger.Level.INFO, "Requesting missing sync keys {0} from companion devices {1}",
                keyIdHexes, deviceIds);

        // Add missing key entries to track responses
        for (var keyId : keyIds) {
            var existingKey = store.findMissingSyncKey(keyId);
            if (existingKey.isEmpty()) {
                var missingKey = new MissingDeviceSyncKeyBuilder()
                        .keyId(keyId)
                        .timestamp(Instant.now())
                        .askedDevices(deviceIds)
                        .build();
                store.addMissingSyncKey(missingKey);
            }
        }

        // Send to each companion device
        for (var device : companionDevices) {
            sendKeyRequest(device, messageContainer);
        }
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
    private void sendKeyRequest(Jid device, MessageContainer messageContainer) {
        try {
            // Send the protocol message to the companion device
            // This uses the peer message mechanism
            client.sendMessage(device, messageContainer);
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to send key request to device {0}: {1}",
                    device, e.getMessage());
        }
    }
}
