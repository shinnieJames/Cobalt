package com.github.auties00.cobalt.model.device.info;

import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;

import static com.github.auties00.cobalt.device.DeviceConstants.HOSTED_DEVICE_ID;
import static com.github.auties00.cobalt.device.DeviceConstants.PRIMARY_DEVICE_ID;

/**
 * Represents information about a single device in a user's device list.
 *
 */
@ProtobufMessage
public final class DeviceInfo {
    @ProtobufProperty(index = 1, type = ProtobufType.INT32)
    final int id;

    @ProtobufProperty(index = 2, type = ProtobufType.INT32)
    final int keyIndex;

    @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
    final ADVEncryptionType type;

    @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
    final boolean hostedFlag;

    DeviceInfo(int id, int keyIndex, ADVEncryptionType type, boolean hostedFlag) {
        this.id = id;
        this.keyIndex = keyIndex;
        this.type = type;
        this.hostedFlag = hostedFlag;
    }

    /**
     * Creates a DeviceInfo with E2EE account type.
     */
    public static DeviceInfo ofE2EE(int id, int keyIndex) {
        return new DeviceInfo(id, keyIndex, ADVEncryptionType.E2EE, false);
    }

    /**
     * Creates a DeviceInfo with E2EE account type and explicit hosted flag.
     *
     * @apiNote WAWebUsyncDevice.parseDeviceNode: parses is_hosted attribute separately from device ID.
     */
    public static DeviceInfo ofE2EE(int id, int keyIndex, boolean hostedFlag) {
        return new DeviceInfo(id, keyIndex, ADVEncryptionType.E2EE, hostedFlag);
    }

    /**
     * Creates a DeviceInfo for a hosted device.
     */
    public static DeviceInfo ofHosted(int keyIndex) {
        return new DeviceInfo(HOSTED_DEVICE_ID, keyIndex, ADVEncryptionType.HOSTED, true);
    }

    /**
     * Returns true if this is a hosted device.
     *
     * @apiNote WAWebDBDeviceListFanout.getFanOutList: checks {@code e.id === 99 || e.isHosted === true}.
     * A device is considered hosted if either the ID is 99 OR the hostedFlag is explicitly set.
     */
    public boolean isHosted() {
        return id == HOSTED_DEVICE_ID || hostedFlag;
    }

    /**
     * Returns the explicit hosted flag value.
     */
    public boolean hostedFlag() {
        return hostedFlag;
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

    public int id() {
        return id;
    }

    public int keyIndex() {
        return keyIndex;
    }

    public ADVEncryptionType type() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof DeviceInfo that
                            && id == that.id
                            && keyIndex == that.keyIndex
                            && type == that.type
                            && hostedFlag == that.hostedFlag;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, keyIndex, type, hostedFlag);
    }

    @Override
    public String toString() {
        return "DeviceInfo[" +
               "id=" + id + ", " +
               "keyIndex=" + keyIndex + ", " +
               "type=" + type + ", " +
               "hostedFlag=" + hostedFlag + ']';
    }


}
