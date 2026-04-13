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
 * <p>Retrieves device identifiers for specified users, filtering out the sender's
 * own device and applying hosted device logic based on {@code bizHostedDevicesEnabled}
 * and the chat type.
 *
 * @implNote WAWebDBDeviceListFanout.getFanOutList: retrieves device identifiers for
 * specified users, filtering out the sender's own device and applying hosted device logic.
 */
public final class DeviceFanoutCalculator {

    /**
     * Logger for device fanout operations.
     *
     * @implNote WAWebDBDeviceListFanout.getFanOutList: logs fallback-to-primary events
     * when no device record is found for a user.
     */
    private static final System.Logger LOGGER = System.getLogger(DeviceFanoutCalculator.class.getName());

    /**
     * The AB props service for checking feature flags.
     *
     * @implNote WAWebBizCoexGatingUtils.bizHostedDevicesEnabled: checks the
     * {@code adv_accept_hosted_devices} AB prop to determine whether hosted devices
     * should be included in the fanout.
     */
    private final ABPropsService abPropsService;

    /**
     * Creates a new device fanout calculator with the specified AB props service.
     *
     * @param abPropsService the AB props service for checking hosted device feature flags
     * @implNote WAWebDBDeviceListFanout: module-level dependency on WAWebBizCoexGatingUtils
     * for the {@code bizHostedDevicesEnabled} check.
     */
    public DeviceFanoutCalculator(ABPropsService abPropsService) {
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService cannot be null");
    }

    /**
     * Calculates the fanout list for the given device lists.
     *
     * <p>For each user's device list, iterates over all devices and includes them in
     * the fanout unless they are hosted (and hosted inclusion is not enabled) or they
     * represent the sender's own device. When no device list exists for a user, falls
     * back to the user's primary JID unless the user is the sender's own account.
     *
     * <p>Hosted devices are included only when all three conditions are met:
     * <ol>
     *   <li>{@code bizHostedDevicesEnabled()} returns {@code true}</li>
     *   <li>{@code includeHostedForOneToOneChatJid} is non-null</li>
     *   <li>{@code includeHostedForOneToOneChatJid} is a user-type JID</li>
     * </ol>
     *
     * @param senderJid                       the JID of the sender (exact device JID)
     * @param deviceLists                     the users' device lists
     * @param includeHostedForOneToOneChatJid JID for which hosted devices should be included, or {@code null}
     * @return unmodifiable set of device JIDs to send to
     * @implNote WAWebDBDeviceListFanout.getFanOutList: filters out sender's own device via
     * {@code isMeDevice}, skips hosted devices unless {@code bizHostedDevicesEnabled}
     * and the chat is a 1:1 user chat ({@code chatWidSetToIncludeHostedInFanoutOneToOneChatOnly.isUser()}).
     * Uses a {@code Map} keyed by {@code toString()} for deduplication; Cobalt uses a
     * {@code HashSet} which achieves the same deduplication via {@code equals}/{@code hashCode}.
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
                && isUserJid(includeHostedForOneToOneChatJid); // WAWebDBDeviceListFanout.getFanOutList

        // WAWebDBDeviceListFanout.getFanOutList: tracks fallback wids for logging (up to 3)
        var fallbackWids = new ArrayList<String>();

        for (var deviceList : deviceLists) {
            var userJid = deviceList.userJid();

            if (deviceList.devices().isEmpty()) {
                // WAWebDBDeviceListFanout.getFanOutList: fallback to primary device when no devices found
                // WA Web: var a = asUserWidOrThrow(wids[t])
                var primaryJid = userJid.toUserJid(); // WAWebWidFactory.asUserWidOrThrow

                // WAWebDBDeviceListFanout.getFanOutList: log up to 3 fallback wids
                if (fallbackWids.size() < 3) {
                    fallbackWids.add(primaryJid.toString());
                }

                // WAWebDBDeviceListFanout.getFanOutList: isMeAccount check - don't add self as fallback
                if (!isSameAccount(primaryJid, senderJid)) {
                    results.add(primaryJid);
                }
                continue;
            }

            for (var device : deviceList.devices()) {
                // WAWebDBDeviceListFanout.getFanOutList: skip hosted devices unless explicitly included
                // Checks: t.id === 99 || t.isHosted === true
                if (device.isHosted() && !includeHosted) {
                    continue;
                }

                // WAWebDBDeviceListFanout.getFanOutList: createDeviceWidFromDeviceListPk(e.id, t.id, t.isHosted)
                var deviceJid = device.toDeviceJid(userJid.user(), userJid.server());

                // WAWebDBDeviceListFanout.getFanOutList: exclude sender's own device
                // Uses isMeDevice which checks exact device JID equality
                if (isSameDevice(deviceJid, senderJid)) {
                    continue;
                }

                // WAWebDBDeviceListFanout.getFanOutList: uses toString() as Map key for deduplication
                results.add(deviceJid);
            }
        }

        // WAWebDBDeviceListFanout.getFanOutList: log fallback wids if any
        if (!fallbackWids.isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "[getFanOutList] no device for {0} wids => primary {1}",
                    fallbackWids.size(),
                    fallbackWids);
        }

        return Collections.unmodifiableSet(results);
    }

    /**
     * Checks if business hosted devices feature is enabled.
     *
     * @return {@code true} if hosted devices are enabled
     * @implNote WAWebBizCoexGatingUtils.bizHostedDevicesEnabled: returns true if the
     * {@code adv_accept_hosted_devices} AB prop is enabled.
     */
    public boolean isBizHostedDevicesEnabled() {
        return abPropsService.getBool(ABProp.ADV_ACCEPT_HOSTED_DEVICES);
    }

    /**
     * Checks if the JID is a user type (not group, broadcast, etc).
     *
     * @param jid the JID to check
     * @return {@code true} if the JID has a user-type server
     * @implNote WAWebWid.isUser: returns {@code true} for {@code c.us}, {@code lid},
     * {@code bot}, {@code hosted}, {@code hosted.lid} servers.
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
     * @param a the first JID
     * @param b the second JID
     * @return {@code true} if the JIDs are equal
     * @implNote WAWebUserPrefsMeUser.isMeDevice: checks exact device JID equality via
     * {@code equals()} against the logged-in device's PN and LID wids.
     */
    private static boolean isSameDevice(Jid a, Jid b) {
        return Objects.equals(a, b);
    }

    /**
     * Checks if two JIDs represent the same account (same user, ignoring device).
     *
     * <p>Handles hosted server mappings: {@code hosted} maps to {@code c.us} and
     * {@code hosted.lid} maps to {@code lid} when comparing via {@link Jid#toUserJid()}.
     *
     * @param a the first JID
     * @param b the second JID
     * @return {@code true} if the JIDs belong to the same account
     * @implNote WAWebUserPrefsMeUser.isMeAccount: uses {@code isSameAccountAndAddressingMode}
     * which compares user part and handles hosted server mappings (hosted-to-c.us,
     * hosted.lid-to-lid).
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
     * <p>Devices whose identity keys have changed but whose changes have not been
     * confirmed by the user are excluded from the fanout to prevent sending messages
     * to potentially compromised sessions.
     *
     * @param devices           the devices to filter
     * @param changedIdentities the set of devices with unconfirmed identity changes
     * @return filtered set excluding devices with identity changes
     * @implNote WAWebSendMsgCommonApi.filterDeviceWithChangedIdentity: excludes devices
     * with pending identity change confirmation from the fanout. In WA Web this is
     * called separately from {@code getFanOutList}; in Cobalt it is co-located in this
     * calculator for convenience.
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
