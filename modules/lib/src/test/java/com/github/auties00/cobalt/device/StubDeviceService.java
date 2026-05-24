package com.github.auties00.cobalt.device;

import com.github.auties00.cobalt.device.fanout.DeviceGroupFanoutResult;
import com.github.auties00.cobalt.device.icdc.HostedIcdcResult;
import com.github.auties00.cobalt.device.icdc.IcdcResult;
import com.github.auties00.cobalt.model.device.DeviceListMetadata;
import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;
import com.github.auties00.cobalt.model.device.identity.ADVSignedDeviceIdentity;
import com.github.auties00.cobalt.model.device.info.DeviceList;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Test-only {@link DeviceService} that throws
 * {@link UnsupportedOperationException} from every method by default, with a
 * small fluent surface for stubbing the handful of methods sender / receiver
 * tests actually call.
 *
 * @apiNote
 * Used by tests in adjacent packages that need a {@link DeviceService}
 * dependency without the full {@link DefaultDeviceService} collaborator graph.
 * Defaults are deliberately loud: any unexpected device-service call fails the
 * test instead of silently returning {@code null} or empty. Each
 * {@code withXxx} setter overrides exactly one method and returns the stub so
 * calls can be chained.
 *
 * @implNote
 * This implementation stores one {@link Function} per stub-able method;
 * non-stubbed methods raise an {@link UnsupportedOperationException} carrying
 * the method name so the test report points directly at the missing stub.
 * {@link #ensureSessions(Collection)} and {@link #computeIcdc(Jid)} default to
 * the harmless return rather than throwing, since several sender tests use
 * them as no-op participants.
 */
public final class StubDeviceService implements DeviceService {

    private Function<Jid, Collection<Jid>> userFanout;
    private BiFunction<Jid, Jid, DeviceGroupFanoutResult> groupFanout;
    private Function<Collection<Jid>, DeviceGroupFanoutResult> broadcastFanout;
    private Function<Collection<Jid>, Integer> ensureSessions;
    private Function<Jid, Optional<IcdcResult>> computeIcdc;

    /**
     * Returns a new stub with every method throwing by default.
     *
     * @apiNote
     * Entry point for every consumer; do not call the constructor directly.
     *
     * @return the new stub
     */
    public static StubDeviceService create() {
        return new StubDeviceService();
    }

    /**
     * Installs the handler used by {@link #getUserFanout(Jid, String)}.
     *
     * @apiNote
     * The handler receives the chat JID; the {@code expectedPhash} argument
     * passed by the production caller is discarded by the stub.
     *
     * @param handler the handler, keyed on chat JID
     * @return this stub for chaining
     */
    public StubDeviceService withUserFanout(Function<Jid, Collection<Jid>> handler) {
        this.userFanout = handler;
        return this;
    }

    /**
     * Installs the handler used by {@link #getGroupFanout(Jid, Jid)}.
     *
     * @apiNote
     * The handler receives the group JID and the sender device JID.
     *
     * @param handler the handler, keyed on group JID and sender device JID
     * @return this stub for chaining
     */
    public StubDeviceService withGroupFanout(BiFunction<Jid, Jid, DeviceGroupFanoutResult> handler) {
        this.groupFanout = handler;
        return this;
    }

    /**
     * Installs the handler used by
     * {@link #getBroadcastFanout(Jid, Jid, Collection)}.
     *
     * @apiNote
     * The handler receives only the recipient user JIDs; the broadcast JID
     * and sender device JID are discarded by the stub. Mirrors the broadcast
     * fanout's client-only audience model where the recipient roster is the
     * load-bearing input.
     *
     * @param handler the handler, keyed on recipient user JIDs
     * @return this stub for chaining
     */
    public StubDeviceService withBroadcastFanout(Function<Collection<Jid>, DeviceGroupFanoutResult> handler) {
        this.broadcastFanout = handler;
        return this;
    }

    /**
     * Installs the handler used by {@link #ensureSessions(Collection)}.
     *
     * @apiNote
     * The handler receives the device JIDs to ensure and returns the number of
     * sessions established. When no handler is installed the stub returns
     * zero (treated as "all sessions already exist").
     *
     * @param handler the handler
     * @return this stub for chaining
     */
    public StubDeviceService withEnsureSessions(Function<Collection<Jid>, Integer> handler) {
        this.ensureSessions = handler;
        return this;
    }

    /**
     * Installs the handler used by {@link #computeIcdc(Jid)}.
     *
     * @apiNote
     * The handler receives the user JID and returns the ICDC result. When no
     * handler is installed the stub returns {@link Optional#empty()}.
     *
     * @param handler the handler, keyed on user JID
     * @return this stub for chaining
     */
    public StubDeviceService withComputeIcdc(Function<Jid, Optional<IcdcResult>> handler) {
        this.computeIcdc = handler;
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; no stub setter exists.
     */
    @Override
    public Set<DeviceList> getDeviceLists(Collection<Jid> userJids, String context, String expectedPhash, boolean shouldMergeAltDevices) {
        throw unsupported("getDeviceLists");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; no stub setter exists.
     */
    @Override
    public Optional<Instant> lastAdvCheckTime() {
        throw unsupported("lastAdvCheckTime");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; no stub setter exists.
     */
    @Override
    public void updateAdvCheckTime() {
        throw unsupported("updateAdvCheckTime");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; no stub setter exists.
     */
    @Override
    public void startAdvCheckScheduler() {
        throw unsupported("startAdvCheckScheduler");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; no stub setter exists.
     */
    @Override
    public void stopAdvCheckScheduler() {
        throw unsupported("stopAdvCheckScheduler");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; no stub setter exists.
     */
    @Override
    public void retryPendingSyncs() {
        throw unsupported("retryPendingSyncs");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; no stub setter exists.
     */
    @Override
    public void updateMissingKeyDevices() {
        throw unsupported("updateMissingKeyDevices");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation delegates to the handler installed by
     * {@link #withUserFanout}; throws when no handler is installed.
     */
    @Override
    public Collection<Jid> getUserFanout(Jid chatJid, String expectedPhash) {
        if (userFanout == null) {
            throw unsupported("getUserFanout: install via withUserFanout(...)");
        }
        return userFanout.apply(chatJid);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation delegates to the handler installed by
     * {@link #withGroupFanout}; throws when no handler is installed.
     */
    @Override
    public DeviceGroupFanoutResult getGroupFanout(Jid groupJid, Jid senderDeviceJid) {
        if (groupFanout == null) {
            throw unsupported("getGroupFanout: install via withGroupFanout(...)");
        }
        return groupFanout.apply(groupJid, senderDeviceJid);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation delegates to the handler installed by
     * {@link #withBroadcastFanout}, passing only the recipient user JIDs;
     * throws when no handler is installed.
     */
    @Override
    public DeviceGroupFanoutResult getBroadcastFanout(Jid broadcastJid, Jid senderDeviceJid, Collection<Jid> recipientUserJids) {
        if (broadcastFanout == null) {
            throw unsupported("getBroadcastFanout: install via withBroadcastFanout(...)");
        }
        return broadcastFanout.apply(recipientUserJids);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation delegates to the handler installed by
     * {@link #withComputeIcdc}; returns {@link Optional#empty()} when no
     * handler is installed so tests that do not care about ICDC can rely on
     * the silent default.
     */
    @Override
    public Optional<IcdcResult> computeIcdc(Jid userJid) {
        if (computeIcdc == null) {
            return Optional.empty();
        }
        return computeIcdc.apply(userJid);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation delegates to the handler installed by
     * {@link #withEnsureSessions}; returns zero (no-op) when no handler is
     * installed so tests that do not care about session establishment can
     * rely on the silent default.
     */
    @Override
    public int ensureSessions(Collection<Jid> deviceJids) {
        if (ensureSessions == null) {
            return 0;
        }
        return ensureSessions.apply(deviceJids);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; no stub setter exists.
     */
    @Override
    public void handleDeviceNotification(Node node, String action, Jid userJid) {
        throw unsupported("handleDeviceNotification");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; no stub setter exists.
     */
    @Override
    public void syncMyDeviceList() {
        throw unsupported("syncMyDeviceList");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; no stub setter exists.
     */
    @Override
    public List<DeviceList> syncAndGetDeviceList(Collection<Jid> userJids) {
        throw unsupported("syncAndGetDeviceList");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; no stub setter exists.
     */
    @Override
    public Optional<DeviceList> getDeviceRecord(Jid userJid) {
        throw unsupported("getDeviceRecord");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; no stub setter exists.
     */
    @Override
    public List<DeviceList> bulkGetDeviceRecord(Collection<Jid> userJids) {
        throw unsupported("bulkGetDeviceRecord");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; no stub setter exists.
     */
    @Override
    public void createOrReplaceDeviceRecord(DeviceList record) {
        throw unsupported("createOrReplaceDeviceRecord");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; no stub setter exists.
     */
    @Override
    public void bulkCreateOrReplaceDeviceRecord(Collection<DeviceList> records) {
        throw unsupported("bulkCreateOrReplaceDeviceRecord");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; no stub setter exists.
     */
    @Override
    public List<DeviceList> getDeviceIds(Collection<Jid> userJids, boolean shouldMergeAltDevices) {
        throw unsupported("getDeviceIds");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; no stub setter exists.
     */
    @Override
    public List<DeviceList> getDeviceInfoForSync(Collection<Jid> userJids) {
        throw unsupported("getDeviceInfoForSync");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; no stub setter exists.
     */
    @Override
    public boolean hasDevice(Jid userJid, int deviceId) {
        throw unsupported("hasDevice");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; no stub setter exists.
     */
    @Override
    public DeviceList getMyDeviceList() {
        throw unsupported("getMyDeviceList");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; no stub setter exists.
     */
    @Override
    public Collection<DeviceList> getAllDeviceLists() {
        throw unsupported("getAllDeviceLists");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; no stub setter exists.
     */
    @Override
    public void handleICDCData(Jid senderJid, Jid recipientUserJid, DeviceListMetadata metadata) {
        throw unsupported("handleICDCData");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; no stub setter exists.
     */
    @Override
    public HostedIcdcResult handleHostedIcdcMetadataInline(Jid chatJid, Jid authorJid, DeviceListMetadata metadata) {
        throw unsupported("handleHostedIcdcMetadataInline");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; no stub setter exists.
     */
    @Override
    public void handleADVDeviceUpdateForMessage(Jid deviceJid, String rawId, long timestamp, int keyIndex, byte[] identityKey, ADVEncryptionType accountType) {
        throw unsupported("handleADVDeviceUpdateForMessage");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; no stub setter exists.
     */
    @Override
    public Optional<ADVSignedDeviceIdentity> extractAndValidateLocalSignedDeviceIdentity(Node deviceIdentityNode) {
        throw unsupported("extractAndValidateLocalSignedDeviceIdentity");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; no stub setter exists.
     */
    @Override
    public void persistLocalDeviceIdentityFromPairSuccess(Jid deviceJid, byte[] accountSignatureKey) {
        throw unsupported("persistLocalDeviceIdentityFromPairSuccess");
    }

    /**
     * Builds the failure thrown by every unstubbed method.
     *
     * @apiNote
     * Internal helper; the method name is embedded in the message so the test
     * report points directly at the missing stub.
     *
     * @param method the method name
     * @return the configured exception
     */
    private static UnsupportedOperationException unsupported(String method) {
        return new UnsupportedOperationException("StubDeviceService." + method + " is not stubbed");
    }
}
