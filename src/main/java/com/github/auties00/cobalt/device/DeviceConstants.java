package com.github.auties00.cobalt.device;

import com.github.auties00.cobalt.model.device.info.DeviceInfo;

/**
 * Device identification constants for WhatsApp's multi-device architecture.
 *
 * @implNote WAJids: defines device ID constants used across the multi-device architecture.
 * @see DeviceInfo
 */
public final class DeviceConstants {

    /**
     * Prevents instantiation of this utility class.
     *
     * @implNote NO_WA_BASIS: Java-specific utility class pattern.
     */
    private DeviceConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Device ID for the primary (phone) device.
     *
     * @implNote WAJids.DEFAULT_DEVICE_ID: the primary device (phone) is always device 0.
     */
    public static final int PRIMARY_DEVICE_ID = 0;

    /**
     * Device ID reserved for hosted (business API) devices.
     *
     * @implNote WAWebUsyncDevice.parseDeviceNode: hosted devices use ID 99 or
     * {@code is_hosted="true"}.
     * WAWebBizCoexGatingUtils.bizHostedDevicesEnabled: controls whether hosted devices
     * are accepted.
     */
    public static final int HOSTED_DEVICE_ID = 99;
}
