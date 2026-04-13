package com.github.auties00.cobalt.device.fanout;

import com.github.auties00.cobalt.model.jid.Jid;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Result of group message fanout calculation containing target devices and participant hash.
 *
 * <p>Combines the device fanout list produced by
 * {@link DeviceFanoutCalculator#calculate(Jid, Set, Jid)} with the participant hash
 * computed by {@code DevicePhashCalculator}. The participant hash is sent to the server
 * alongside the encrypted message so the server can verify message delivery integrity.
 *
 * @implNote WAWebDBDeviceListFanout.getFanOutList: returns the device list.
 * WAWebPhashUtils.phashV2: calculates the participant hash.
 */
public final class DeviceGroupFanoutResult {

    /**
     * The set of device JIDs to send the message to.
     *
     * @implNote WAWebDBDeviceListFanout.getFanOutList: the filtered device list from
     * fanout calculation.
     */
    private final Set<Jid> devices;

    /**
     * The participant hash for server-side message delivery verification.
     *
     * @implNote WAWebPhashUtils.phashV2: hash of sorted participant JIDs used by the
     * server to verify that the client has an up-to-date view of the group.
     */
    private final String phash;

    /**
     * Creates a new device group fanout result.
     *
     * @param devices the collection of device JIDs to send to
     * @param phash   the calculated participant hash for server verification
     * @implNote ADAPTED: WAWebDBDeviceListFanout.getFanOutList: in WA Web, the device
     * list and phash are computed separately and combined at the call site. Cobalt
     * groups them into this result object for type safety.
     */
    public DeviceGroupFanoutResult(Set<Jid> devices, String phash) {
        this.devices = Objects.requireNonNull(devices, "devices cannot be null");
        this.phash = Objects.requireNonNull(phash, "phash cannot be null");
    }

    /**
     * Returns the set of device JIDs to send to.
     *
     * @return an unmodifiable view of the set of device JIDs
     * @implNote WAWebDBDeviceListFanout.getFanOutList: the return value of the fanout
     * calculation after identity change filtering.
     */
    public Set<Jid> devices() {
        return Collections.unmodifiableSet(devices);
    }

    /**
     * Returns the calculated participant hash for server verification.
     *
     * @return the participant hash string
     * @implNote WAWebPhashUtils.phashV2: the computed hash value used in the message
     * stanza's {@code phash} attribute.
     */
    public String phash() {
        return phash;
    }
}
