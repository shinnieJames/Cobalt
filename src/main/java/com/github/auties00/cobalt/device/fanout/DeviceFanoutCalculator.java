package com.github.auties00.cobalt.device.fanout;

import com.github.auties00.cobalt.model.device.info.DeviceList;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service that calculates which devices should receive a message (fanout list).
 *
 * @apiNote WAWebDBDeviceListFanout.getFanOutList: retrieves device identifiers for
 * specified users, filtering out the sender's own device and applying hosted device logic.
 */
public final class DeviceFanoutCalculator {

    private static final System.Logger LOGGER = System.getLogger(DeviceFanoutCalculator.class.getName());

    private final ABPropsService abPropsService;

    public DeviceFanoutCalculator(ABPropsService abPropsService) {
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService cannot be null");
    }

    /**
     * Calculates the fanout list for the given device lists.
     *
     * @param senderJid                       the JID of the sender (exact device JID)
     * @param deviceLists                     the users' device lists
     * @param includeHostedForOneToOneChatJid JID for which hosted devices should be included, or {@code null}
     * @return unmodifiable set of device JIDs to send to
     *
     * @apiNote WAWebDBDeviceListFanout.getFanOutList: filters out sender's own device and
     * applies hosted device logic based on bizHostedDevicesEnabled and chat type.
     */
    public Set<Jid> calculate(
            Jid senderJid,
            Set<DeviceList> deviceLists,
            Jid includeHostedForOneToOneChatJid
    ) {
        // WAWebDBDeviceListFanout.getFanOutList: uses Map with toString() key for deduplication
        var results = new HashSet<Jid>();

        // WAWebDBDeviceListFanout.getFanOutList: hosted devices are included for 1:1 user chats
        // when bizHostedDevicesEnabled is true AND the chat JID is a user type (not group)
        // The check is global for all users in the fanout, not per-user
        var includeHosted = isBizHostedDevicesEnabled()
                && includeHostedForOneToOneChatJid != null
                && isUserJid(includeHostedForOneToOneChatJid);

        for (var deviceList : deviceLists) {
            var userJid = deviceList.userJid();

            if (deviceList.devices().isEmpty()) {
                // WAWebDBDeviceListFanout.getFanOutList: fallback to primary device when no devices found
                // "getFanOutList: no device is found for {}, just send to the primary device"
                LOGGER.log(System.Logger.Level.DEBUG,
                        "getFanOutList: no device is found for {0}, just send to the primary device",
                        userJid);

                // WAWebDBDeviceListFanout: isMeAccount check - don't add self as fallback
                if (!isSameAccount(userJid, senderJid)) {
                    var primaryJid = userJid.toUserJid();
                    results.add(primaryJid);
                }
                continue;
            }

            for (var device : deviceList.devices()) {
                // WAWebDBDeviceListFanout.getFanOutList: skip hosted devices unless explicitly included
                // Checks: e.id === 99 || e.isHosted === true
                if (device.isHosted() && !includeHosted) {
                    continue;
                }

                var deviceJid = device.toDeviceJid(userJid.user(), userJid.server());

                // WAWebDBDeviceListFanout.getFanOutList: exclude sender's own device
                // Uses isMeDevice which checks exact device JID equality
                if (isSameDevice(deviceJid, senderJid)) {
                    continue;
                }

                // WAWebDBDeviceListFanout: uses toString() as Map key for deduplication
                results.add(deviceJid);
            }
        }

        return Collections.unmodifiableSet(results);
    }

    /**
     * Checks if business hosted devices feature is enabled.
     *
     * @return {@code true} if hosted devices are enabled
     *
     * @apiNote WAWebBizCoexGatingUtils.bizHostedDevicesEnabled: returns true if the
     * ADV_ACCEPT_HOSTED_DEVICES AB prop is enabled.
     */
    public boolean isBizHostedDevicesEnabled() {
        return abPropsService.getBool(ABProp.ADV_ACCEPT_HOSTED_DEVICES);
    }

    /**
     * Checks if the JID is a user type (not group, broadcast, etc).
     *
     * @apiNote WAWebWid.isUser: returns true for c.us, lid, bot, hosted, hosted.lid servers
     */
    private static boolean isUserJid(Jid jid) {
        return jid.hasUserServer()
                || jid.hasLidServer()
                || jid.hasBotServer()
                || jid.hasHostedServer()
                || jid.hasHostedLidServer();
    }

    /**
     * Checks if two JIDs represent the same device (exact match).
     *
     * @apiNote WAWebUserPrefsMeUser.isMeDevice: uses equals() for exact device comparison
     */
    private static boolean isSameDevice(Jid a, Jid b) {
        return Objects.equals(a, b);
    }

    /**
     * Checks if two JIDs represent the same account (same user, ignoring device).
     *
     * @apiNote WAWebUserPrefsMeUser.isMeAccount: uses isSameAccountAndAddressingMode
     * which compares user part and handles hosted server mappings.
     */
    private static boolean isSameAccount(Jid a, Jid b) {
        if (a == null || b == null) {
            return false;
        }
        return Objects.equals(a.toUserJid(), b.toUserJid());
    }

    /**
     * Filters out devices with unconfirmed identity changes.
     *
     * @param devices           the devices to filter
     * @param changedIdentities the set of devices with unconfirmed identity changes
     * @return filtered set excluding devices with identity changes
     *
     * @apiNote WAWebIdentityChangeApi: devices with pending identity change confirmation
     * are excluded from fanout until the user confirms the change.
     */
    public Set<Jid> filterIdentityChanges(Set<Jid> devices, Set<Jid> changedIdentities) {
        if (changedIdentities.isEmpty()) {
            return devices;
        }

        return devices.stream()
                .filter(jid -> !changedIdentities.contains(jid))
                .collect(Collectors.toUnmodifiableSet());
    }
}
