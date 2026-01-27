package com.github.auties00.cobalt.device.info;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;

import static com.github.auties00.cobalt.device.info.DeviceConstants.*;

/**
 * Represents information about a single device in a user's device list.
 *
 * @param id       the device number (0=primary, 1-4=companions, 99=hosted)
 * @param keyIndex the identity key index for change detection
 * @param type     the type of account (E2EE or HOSTED)
 */
public record DeviceInfo(int id, int keyIndex, Type type) {
    /**
     * Creates a DeviceInfo with E2EE account type.
     */
    public static DeviceInfo e2ee(int id, int keyIndex) {
        return new DeviceInfo(id, keyIndex, Type.E2EE);
    }

    /**
     * Creates a DeviceInfo for a hosted device.
     */
    public static DeviceInfo hosted(int keyIndex) {
        return new DeviceInfo(HOSTED_DEVICE_ID, keyIndex, Type.HOSTED);
    }

    /**
     * Returns true if this is a hosted device (device ID 99).
     */
    public boolean isHosted() {
        return id == HOSTED_DEVICE_ID;
    }

    /**
     * Returns true if this is the primary device (device ID 0).
     */
    public boolean isPrimary() {
        return id == PRIMARY_DEVICE_ID;
    }

    /**
     * Returns true if this is a companion device (device ID 1-4).
     */
    public boolean isCompanion() {
        return id > PRIMARY_DEVICE_ID && id < HOSTED_DEVICE_ID;
    }

    /**
     * Converts this device info to a full device JID.
     *
     * @param user   the user identifier
     * @param server the JID server
     * @return the device JID with device number
     */
    public Jid toDeviceJid(String user, JidServer server) {
        return Jid.of(user, server, id, 0);
    }

    /**
     * Account type for a device.
     */
    public enum Type {
        /**
         * End-to-end encrypted device.
         */
        E2EE,

        /**
         * Hosted/business API device.
         */
        HOSTED
    }
}
