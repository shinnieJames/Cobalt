package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles device capabilities actions.
 *
 * <p>Index format: ["device_capabilities", ...]
 */
public final class DeviceCapabilitiesHandler implements WebAppStateActionHandler {
    public static final DeviceCapabilitiesHandler INSTANCE = new DeviceCapabilitiesHandler();

    private DeviceCapabilitiesHandler() {

    }

    @Override
    public String actionName() {
        return "device_capabilities";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR_LOW;
    }

    @Override
    public int version() {
        return 7;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        return true;
    }
}
