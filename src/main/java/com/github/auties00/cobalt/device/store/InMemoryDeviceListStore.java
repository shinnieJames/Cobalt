package com.github.auties00.cobalt.device.store;

import com.github.auties00.cobalt.device.info.DeviceList;
import com.github.auties00.cobalt.model.jid.Jid;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory implementation of DeviceListStore.
 * <p>
 * This implementation is thread-safe and suitable for use as a
 * secondary cache tier or for testing purposes.
 */
public final class InMemoryDeviceListStore implements DeviceListStore {
    private final ConcurrentMap<String, DeviceList> storage;

    public InMemoryDeviceListStore() {
        this.storage = new ConcurrentHashMap<>();
    }

    @Override
    public Optional<DeviceList> load(Jid userJid) {
        var key = toKey(userJid);
        var entry = storage.get(key);
        if (entry == null || entry.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    @Override
    public void store(DeviceList deviceList) {
        var key = toKey(deviceList.userJid());
        storage.put(key, deviceList);
    }

    @Override
    public boolean delete(Jid userJid) {
        var key = toKey(userJid);
        return storage.remove(key) != null;
    }

    @Override
    public List<DeviceList> getExpired() {
        var expired = new ArrayList<DeviceList>();
        for (var entry : storage.values()) {
            if (entry.isExpired()) {
                expired.add(entry);
            }
        }
        return expired;
    }

    @Override
    public int deleteExpired() {
        var initialSize = storage.size();
        storage.entrySet().removeIf(entry -> entry.getValue().isExpired());
        return initialSize - storage.size();
    }

    @Override
    public void clear() {
        storage.clear();
    }

    private String toKey(Jid jid) {
        return jid.toUserJid().toString();
    }
}
