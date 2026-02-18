package com.github.auties00.cobalt.device;

import com.github.auties00.cobalt.model.device.info.DeviceInfo;

/**
 * Device identification constants for WhatsApp's multi-device architecture.
 *
 * @see DeviceInfo
 */
public final class DeviceConstants {

    private DeviceConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Device ID for the primary (phone) device.
     *
     * @apiNote WAJids.DEFAULT_DEVICE_ID: the primary device (phone) is always device 0
     */
    public static final int PRIMARY_DEVICE_ID = 0;

    /**
     * Device ID reserved for hosted (business API) devices.
     *
     * @apiNote WAWebUsyncDevice.parseDeviceNode: hosted devices use ID 99 or is_hosted="true".
     * WAWebBizCoexGatingUtils.bizHostedDevicesEnabled: controls whether hosted devices are accepted.
     */
    public static final int HOSTED_DEVICE_ID = 99;
}
