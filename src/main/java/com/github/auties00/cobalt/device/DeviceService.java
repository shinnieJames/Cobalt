package com.github.auties00.cobalt.device;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.fanout.DeviceFanoutCalculator;
import com.github.auties00.cobalt.device.fanout.DeviceFanoutOptions;
import com.github.auties00.cobalt.device.fanout.DeviceGroupFanoutResult;
import com.github.auties00.cobalt.device.info.DeviceList;
import com.github.auties00.cobalt.device.phash.DevicePhashCalculator;
import com.github.auties00.cobalt.device.usync.DeviceUSyncQueryBuilder;
import com.github.auties00.cobalt.device.usync.DeviceUSyncResponseParser;
import com.github.auties00.cobalt.model.chat.ChatParticipant;
import com.github.auties00.cobalt.model.jid.Jid;

import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.StructuredTaskScope.Subtask;

/**
 * Service for managing device lists and calculating fanout for messages.
 */
public final class DeviceService {
    private static final Joiner<List<DeviceList>, List<DeviceList>> JOINER = new Joiner<>() {
        private final List<Subtask<? extends List<DeviceList>>> subtasks = new ArrayList<>();

        @Override
        public boolean onFork(Subtask<? extends List<DeviceList>> subtask) {
            Objects.requireNonNull(subtask, "subtask cannot be null");

            if(subtask.state() == Subtask.State.UNAVAILABLE) {
                throw new IllegalStateException("Subtask is not available");
            }

            subtasks.add(subtask);
            return false;
        }

        @Override
        public boolean onComplete(Subtask<? extends List<DeviceList>> subtask) {
            Objects.requireNonNull(subtask, "subtask cannot be null");

            return switch (subtask.state()) {
                case UNAVAILABLE -> throw new IllegalStateException("Subtask is not completed");
                case SUCCESS -> true;
                case FAILED -> false;
            };
        }

        @Override
        public List<DeviceList> result() {
            return subtasks.stream()
                    .flatMap(subtask -> subtask.get().stream())
                    .toList();
        }
    };

    private final WhatsAppClient client;

    public DeviceService(WhatsAppClient client) {
        this.client = client;
    }

    /**
     * Gets device lists for multiple users, batching where possible.
     *
     * @param userJids the user JIDs
     * @return list of devices
     */
    public List<DeviceList> getDeviceLists(Collection<Jid> userJids) {
        var batches = DeviceUSyncQueryBuilder.build(userJids);
        try (var scope = StructuredTaskScope.open(JOINER)) {
            for (var batch : batches) {
                scope.fork(() -> {
                    var response = client.sendNode(batch);
                    return DeviceUSyncResponseParser.parse(response);
                });
            }
            return scope.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while fetching device lists", e);
        }
    }

    /**
     * Gets the complete fanout for a group message.
     *
     * @param groupJid    the group JID
     * @param myDeviceJid the current device's JID
     * @return the fanout result
     */
    public DeviceGroupFanoutResult getGroupFanout(Jid groupJid, Jid myDeviceJid) {
        var metadata = client.queryGroupOrCommunityMetadata(groupJid);
        var participants = metadata.participants()
                .stream()
                .map(ChatParticipant::jid)
                .toList();
        return getGroupFanout(participants, myDeviceJid);
    }

    /**
     * Gets the complete fanout for a group message given participants.
     *
     * @param participants the group participants
     * @param myDeviceJid  the current device's JID
     * @return the fanout result
     */
    public DeviceGroupFanoutResult getGroupFanout(Collection<Jid> participants, Jid myDeviceJid) {
        try {
            var deviceLists = getDeviceLists(participants);
            var options = DeviceFanoutOptions.of(myDeviceJid);
            var fanoutDevices = DeviceFanoutCalculator.calculateMultiple(deviceLists, options);
            var phash = DevicePhashCalculator.calculateV2(fanoutDevices);
            return new DeviceGroupFanoutResult(fanoutDevices, phash, deviceLists);
        }catch (NoSuchAlgorithmException exception) {
            throw new InternalError("Missing SHA-256 implementation", exception);
        }
    }
}
