package com.github.auties00.cobalt.device.store;

import com.github.auties00.cobalt.device.info.DeviceList;
import com.github.auties00.cobalt.model.jid.Jid;

import java.util.List;
import java.util.Optional;

/**
 * Persistent storage interface for device lists.
 */
public interface DeviceListStore {
    /**
     * Loads a device list from storage.
     *
     * @param userJid the user JID
     * @return the device list, or empty if not found
     */
    Optional<DeviceList> load(Jid userJid);

    /**
     * Stores a device list.
     *
     * @param deviceList the device list to store
     */
    void store(DeviceList deviceList);

    /**
     * Deletes a device list from storage.
     *
     * @param userJid the user JID
     * @return true if an entry was deleted
     */
    boolean delete(Jid userJid);

    /**
     * Returns all expired device lists.
     *
     * @return list of expired device lists
     */
    List<DeviceList> getExpired();

    /**
     * Deletes all expired device lists.
     *
     * @return number of entries deleted
     */
    int deleteExpired();

    /**
     * Clears all stored device lists.
     */
    void clear();
}
