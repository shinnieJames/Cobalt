package com.github.auties00.cobalt.device.info;

import com.github.auties00.cobalt.model.jid.Jid;

import java.util.List;

/**
 * Report of changes between two device lists.
 *
 * @param addedDevices           devices that were added
 * @param removedDevices         devices that were removed
 * @param identityChangedDevices devices whose identity key changed
 */
public record DeviceChanges(
        List<Jid> addedDevices,
        List<Jid> removedDevices,
        List<Jid> identityChangedDevices
) {
    public boolean hasChanges() {
        return !addedDevices.isEmpty() || !removedDevices.isEmpty() || !identityChangedDevices.isEmpty();
    }

    public boolean hasIdentityChanges() {
        return !identityChangedDevices.isEmpty();
    }
}
