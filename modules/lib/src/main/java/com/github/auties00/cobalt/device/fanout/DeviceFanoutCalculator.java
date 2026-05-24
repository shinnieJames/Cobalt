package com.github.auties00.cobalt.device.fanout;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.device.info.DeviceList;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Resolves the set of recipient device {@link Jid}s for a given outgoing WhatsApp send.
 *
 * @apiNote
 * Drives the per-send recipient selection that {@link com.github.auties00.cobalt.device.DeviceService#getUserFanout(Jid, String)}
 * and {@link com.github.auties00.cobalt.device.DeviceService#getGroupFanout(Jid, Jid)} expose: given
 * the resolved {@link DeviceList} for each addressee, the calculator drops the sender's own
 * device, applies the hosted-device gating rule used for business coexistence, falls back to the
 * primary user JID when a device list is empty, and filters out devices whose identity key has
 * rotated without the user acknowledging the change. Call this directly only when bypassing
 * {@code DeviceService} (for example in tests); production senders go through
 * {@code DeviceService}.
 */
@WhatsAppWebModule(moduleName = "WAWebDBDeviceListFanout")
public final class DeviceFanoutCalculator {

    /**
     * Logger used to mirror the WA Web {@code [getFanOutList] no device for ...} diagnostic.
     */
    private static final System.Logger LOGGER = System.getLogger(DeviceFanoutCalculator.class.getName());

    /**
     * The {@link ABPropsService} consulted for the hosted-device gating flag.
     */
    private final ABPropsService abPropsService;

    /**
     * Constructs a calculator bound to the given AB props service.
     *
     * @apiNote
     * Embedders rarely instantiate this directly; it is wired by
     * {@link com.github.auties00.cobalt.device.DeviceService} and shared across all sends from
     * a single client.
     *
     * @param abPropsService the AB props service used by {@link #isBizHostedDevicesEnabled()}
     * @throws NullPointerException if {@code abPropsService} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebDBDeviceListFanout",
            exports = "getFanOutList",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public DeviceFanoutCalculator(ABPropsService abPropsService) {
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService cannot be null");
    }

    /**
     * Computes the fanout {@link Jid} set for the given resolved device lists.
     *
     * @apiNote
     * Callers that drive a 1:1 send pass the chat JID as {@code includeHostedForOneToOneChatJid}
     * so hosted-device entries (device id {@code 99} or {@code isHosted}) are included for that
     * peer when {@link #isBizHostedDevicesEnabled()} is on. Group sends pass {@code null} and so
     * never include hosted devices. The returned set always omits the sender's own PN and LID
     * device JIDs; when a user has no resolved device list the primary user JID is added unless
     * it belongs to the sender's own account (PN or LID side).
     *
     * @implNote
     * This implementation logs at most three fallback JIDs to {@code DEBUG} via the
     * {@code [getFanOutList] no device for ...} message, matching WA Web's WALogger output.
     * Resolution of {@code DeviceList} entries (calling {@code WAWebApiDeviceList.getDeviceIds})
     * happens upstream in {@link com.github.auties00.cobalt.device.DeviceService}; this method
     * is purely a filter over the already-resolved lists.
     *
     * @param senderPnDeviceJid               the sender's PN device JID, or {@code null} if not signed in via PN
     * @param senderLidDeviceJid              the sender's LID device JID, or {@code null} if not signed in via LID
     * @param deviceLists                     the per-user resolved device lists to fan out across
     * @param includeHostedForOneToOneChatJid the chat JID of the targeted 1:1 conversation when hosted devices may participate, or {@code null} for group sends
     * @return an unmodifiable set of recipient device JIDs
     */
    @WhatsAppWebExport(moduleName = "WAWebDBDeviceListFanout",
            exports = "getFanOutList",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Set<Jid> calculate(
            Jid senderPnDeviceJid,
            Jid senderLidDeviceJid,
            Set<DeviceList> deviceLists,
            Jid includeHostedForOneToOneChatJid
    ) {
        var results = new HashSet<Jid>();
        var includeHosted = isBizHostedDevicesEnabled()
                && includeHostedForOneToOneChatJid != null
                && isUserJid(includeHostedForOneToOneChatJid);
        var fallbackWids = new ArrayList<String>();

        for (var deviceList : deviceLists) {
            var userJid = deviceList.userJid();

            if (deviceList.devices().isEmpty()) {
                var primaryJid = userJid.toUserJid();
                if (fallbackWids.size() < 3) {
                    fallbackWids.add(primaryJid.toString());
                }
                if (!isMeAccount(primaryJid, senderPnDeviceJid, senderLidDeviceJid)) {
                    results.add(primaryJid);
                }
                continue;
            }

            for (var device : deviceList.devices()) {
                if (device.isHosted() && !includeHosted) {
                    continue;
                }
                var deviceJid = device.toDeviceJid(userJid.user(), userJid.server());
                if (isMeDevice(deviceJid, senderPnDeviceJid, senderLidDeviceJid)) {
                    continue;
                }
                results.add(deviceJid);
            }
        }

        if (!fallbackWids.isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "[getFanOutList] no device for {0} wids => primary {1}",
                    fallbackWids.size(),
                    fallbackWids);
        }

        return Collections.unmodifiableSet(results);
    }

    /**
     * Returns whether hosted devices may be included in a 1:1 fanout.
     *
     * @apiNote
     * Gates the inclusion of {@code isHosted} device records (and the legacy {@code id == 99}
     * hosted device id) for business-coexistence sends. Off by default; flipped on by the
     * {@link ABProp#ADV_ACCEPT_HOSTED_DEVICES} AB prop.
     *
     * @implNote
     * This implementation reads {@link ABProp#ADV_ACCEPT_HOSTED_DEVICES} directly; WA Web's
     * {@code WAWebBizCoexGatingUtils.bizHostedDevicesEnabled} is the exact wire-level
     * counterpart.
     *
     * @return {@code true} when {@link ABProp#ADV_ACCEPT_HOSTED_DEVICES} is set
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCoexGatingUtils",
            exports = "bizHostedDevicesEnabled",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean isBizHostedDevicesEnabled() {
        return abPropsService.getBool(ABProp.ADV_ACCEPT_HOSTED_DEVICES);
    }

    /**
     * Returns whether the given {@link Jid} addresses an individual user account.
     *
     * @apiNote
     * Used by {@link #calculate} to decide whether the 1:1 chat target is a user (and so
     * eligible for hosted-device inclusion); not part of any public Cobalt API.
     *
     * @implNote
     * This implementation accepts the same five server suffixes as WA Web's
     * {@code WAWebWid.isUser}: {@code c.us}, {@code lid}, {@code bot}, {@code hosted}, and
     * {@code hosted.lid}.
     *
     * @param jid the JID under test
     * @return {@code true} when the JID's server denotes one of the user-shaped servers
     */
    @WhatsAppWebExport(moduleName = "WAWebWid",
            exports = "isUser",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static boolean isUserJid(Jid jid) {
        return jid.hasUserServer()
                || jid.hasLidServer()
                || jid.hasBotServer()
                || jid.hasHostedServer()
                || jid.hasHostedLidServer();
    }

    /**
     * Returns whether the candidate device {@link Jid} is one of the sender's own devices.
     *
     * @apiNote
     * Used by {@link #calculate} to drop self-addressed entries before fanning out; equivalent
     * to WA Web's {@code WAWebUserPrefsMeUser.isMeDevice}, which checks against the cached PN
     * and LID device JIDs of the signed-in user.
     *
     * @implNote
     * This implementation is the disjunction
     * {@code candidate.equals(senderPnDeviceJid) || candidate.equals(senderLidDeviceJid)}.
     * A {@code null} sender JID compares unequal.
     *
     * @param candidate          the device JID under test
     * @param senderPnDeviceJid  the sender's PN device JID, or {@code null}
     * @param senderLidDeviceJid the sender's LID device JID, or {@code null}
     * @return {@code true} when the candidate equals either sender device JID
     */
    @WhatsAppWebExport(moduleName = "WAWebUserPrefsMeUser",
            exports = "isMeDevice",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static boolean isMeDevice(Jid candidate, Jid senderPnDeviceJid, Jid senderLidDeviceJid) {
        return Objects.equals(candidate, senderPnDeviceJid)
                || Objects.equals(candidate, senderLidDeviceJid);
    }

    /**
     * Returns whether the candidate user-level {@link Jid} belongs to the sender's own account.
     *
     * @apiNote
     * Used by {@link #calculate}'s primary-fallback branch to avoid silently fanning out to the
     * sender's own bare user JID when the resolved {@link DeviceList} for that user is empty;
     * matches WA Web's {@code WAWebUserPrefsMeUser.isMeAccount}.
     *
     * @implNote
     * This implementation compares {@link Jid#toUserJid()} of the candidate against the
     * normalised user form of each sender device JID, so {@code hosted} maps to {@code c.us}
     * and {@code hosted.lid} maps to {@code lid} before equality.
     *
     * @param candidate          the user-level JID under test
     * @param senderPnDeviceJid  the sender's PN device JID, or {@code null}
     * @param senderLidDeviceJid the sender's LID device JID, or {@code null}
     * @return {@code true} when the candidate normalises to either sender account
     */
    @WhatsAppWebExport(moduleName = "WAWebUserPrefsMeUser",
            exports = "isMeAccount",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static boolean isMeAccount(Jid candidate, Jid senderPnDeviceJid, Jid senderLidDeviceJid) {
        if (candidate == null) {
            return false;
        }
        var candidateUser = candidate.toUserJid();
        if (senderPnDeviceJid != null
                && candidateUser.equals(senderPnDeviceJid.toUserJid())) {
            return true;
        }
        return senderLidDeviceJid != null
                && candidateUser.equals(senderLidDeviceJid.toUserJid());
    }

    /**
     * Removes from the given fanout the devices whose identity key has rotated without
     * acknowledgement.
     *
     * @apiNote
     * Called on the resend path to honour the rule that Cobalt never silently re-encrypts to
     * a peer whose Signal identity key has changed since the last verified state; mirrors WA
     * Web's {@code WAWebSendMsgCommonApi.filterDeviceWithChangedIdentity} pruning step.
     *
     * @implNote
     * This implementation short-circuits when {@code changedIdentities} is empty and returns
     * the input set unchanged; otherwise a fresh unmodifiable set is built via stream
     * collection.
     *
     * @param devices           the candidate device JIDs
     * @param changedIdentities the device JIDs flagged as having a pending identity change
     * @return the {@code devices} subset whose identity key has not been flagged
     */
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCommonApi",
            exports = "filterDeviceWithChangedIdentity",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Set<Jid> filterIdentityChanges(Set<Jid> devices, Set<Jid> changedIdentities) {
        if (changedIdentities.isEmpty()) {
            return devices;
        }

        return devices.stream()
                .filter(jid -> !changedIdentities.contains(jid))
                .collect(Collectors.toUnmodifiableSet());
    }
}
