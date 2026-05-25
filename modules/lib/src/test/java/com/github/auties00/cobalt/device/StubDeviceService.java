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
 * Test-only {@link DeviceService} double used by tests in adjacent packages that need a
 * {@link DeviceService} dependency without the full {@link DefaultDeviceService} collaborator graph.
 *
 * <p>Defaults are deliberately loud: every method raises an {@link UnsupportedOperationException}
 * carrying its own name (so the test report points at the missing stub) except
 * {@link #ensureSessions(Collection)} and {@link #computeIcdc(Jid)}, which return a harmless default
 * because several sender tests use them as no-op participants. Each {@code withXxx} setter installs
 * the handler for exactly one method and returns the stub so calls can be chained.
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
     * @return the new stub
     */
    public static StubDeviceService create() {
        return new StubDeviceService();
    }

    /**
     * Installs the handler used by {@link #getUserFanout(Jid, String)}; the handler receives only
     * the chat JID, and the {@code expectedPhash} argument is discarded.
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
     * @param handler the handler, keyed on group JID and sender device JID
     * @return this stub for chaining
     */
    public StubDeviceService withGroupFanout(BiFunction<Jid, Jid, DeviceGroupFanoutResult> handler) {
        this.groupFanout = handler;
        return this;
    }

    /**
     * Installs the handler used by {@link #getBroadcastFanout(Jid, Jid, Collection)}; the handler
     * receives only the recipient user JIDs, and the broadcast JID and sender device JID are
     * discarded.
     *
     * @param handler the handler, keyed on recipient user JIDs
     * @return this stub for chaining
     */
    public StubDeviceService withBroadcastFanout(Function<Collection<Jid>, DeviceGroupFanoutResult> handler) {
        this.broadcastFanout = handler;
        return this;
    }

    /**
     * Installs the handler used by {@link #ensureSessions(Collection)}, which returns the number of
     * sessions established; when no handler is installed the stub returns zero (treated as "all
     * sessions already exist").
     *
     * @param handler the handler
     * @return this stub for chaining
     */
    public StubDeviceService withEnsureSessions(Function<Collection<Jid>, Integer> handler) {
        this.ensureSessions = handler;
        return this;
    }

    /**
     * Installs the handler used by {@link #computeIcdc(Jid)}; when no handler is installed the stub
     * returns {@link Optional#empty()}.
     *
     * @param handler the handler, keyed on user JID
     * @return this stub for chaining
     */
    public StubDeviceService withComputeIcdc(Function<Jid, Optional<IcdcResult>> handler) {
        this.computeIcdc = handler;
        return this;
    }

    @Override
    public Set<DeviceList> getDeviceLists(Collection<Jid> userJids, String context, String expectedPhash, boolean shouldMergeAltDevices) {
        throw unsupported("getDeviceLists");
    }

    @Override
    public Optional<Instant> lastAdvCheckTime() {
        throw unsupported("lastAdvCheckTime");
    }

    @Override
    public void updateAdvCheckTime() {
        throw unsupported("updateAdvCheckTime");
    }

    @Override
    public void startAdvCheckScheduler() {
        throw unsupported("startAdvCheckScheduler");
    }

    @Override
    public void stopAdvCheckScheduler() {
        throw unsupported("stopAdvCheckScheduler");
    }

    @Override
    public void retryPendingSyncs() {
        throw unsupported("retryPendingSyncs");
    }

    @Override
    public void updateMissingKeyDevices() {
        throw unsupported("updateMissingKeyDevices");
    }

    @Override
    public Collection<Jid> getUserFanout(Jid chatJid, String expectedPhash) {
        if (userFanout == null) {
            throw unsupported("getUserFanout: install via withUserFanout(...)");
        }
        return userFanout.apply(chatJid);
    }

    @Override
    public DeviceGroupFanoutResult getGroupFanout(Jid groupJid, Jid senderDeviceJid) {
        if (groupFanout == null) {
            throw unsupported("getGroupFanout: install via withGroupFanout(...)");
        }
        return groupFanout.apply(groupJid, senderDeviceJid);
    }

    @Override
    public DeviceGroupFanoutResult getBroadcastFanout(Jid broadcastJid, Jid senderDeviceJid, Collection<Jid> recipientUserJids) {
        if (broadcastFanout == null) {
            throw unsupported("getBroadcastFanout: install via withBroadcastFanout(...)");
        }
        return broadcastFanout.apply(recipientUserJids);
    }

    @Override
    public Optional<IcdcResult> computeIcdc(Jid userJid) {
        if (computeIcdc == null) {
            return Optional.empty();
        }
        return computeIcdc.apply(userJid);
    }

    @Override
    public int ensureSessions(Collection<Jid> deviceJids) {
        if (ensureSessions == null) {
            return 0;
        }
        return ensureSessions.apply(deviceJids);
    }

    @Override
    public void handleDeviceNotification(Node node, String action, Jid userJid) {
        throw unsupported("handleDeviceNotification");
    }

    @Override
    public void syncMyDeviceList() {
        throw unsupported("syncMyDeviceList");
    }

    @Override
    public List<DeviceList> syncAndGetDeviceList(Collection<Jid> userJids) {
        throw unsupported("syncAndGetDeviceList");
    }

    @Override
    public Optional<DeviceList> getDeviceRecord(Jid userJid) {
        throw unsupported("getDeviceRecord");
    }

    @Override
    public List<DeviceList> bulkGetDeviceRecord(Collection<Jid> userJids) {
        throw unsupported("bulkGetDeviceRecord");
    }

    @Override
    public void createOrReplaceDeviceRecord(DeviceList record) {
        throw unsupported("createOrReplaceDeviceRecord");
    }

    @Override
    public void bulkCreateOrReplaceDeviceRecord(Collection<DeviceList> records) {
        throw unsupported("bulkCreateOrReplaceDeviceRecord");
    }

    @Override
    public List<DeviceList> getDeviceIds(Collection<Jid> userJids, boolean shouldMergeAltDevices) {
        throw unsupported("getDeviceIds");
    }

    @Override
    public List<DeviceList> getDeviceInfoForSync(Collection<Jid> userJids) {
        throw unsupported("getDeviceInfoForSync");
    }

    @Override
    public boolean hasDevice(Jid userJid, int deviceId) {
        throw unsupported("hasDevice");
    }

    @Override
    public DeviceList getMyDeviceList() {
        throw unsupported("getMyDeviceList");
    }

    @Override
    public Collection<DeviceList> getAllDeviceLists() {
        throw unsupported("getAllDeviceLists");
    }

    @Override
    public void handleICDCData(Jid senderJid, Jid recipientUserJid, DeviceListMetadata metadata) {
        throw unsupported("handleICDCData");
    }

    @Override
    public HostedIcdcResult handleHostedIcdcMetadataInline(Jid chatJid, Jid authorJid, DeviceListMetadata metadata) {
        throw unsupported("handleHostedIcdcMetadataInline");
    }

    @Override
    public void handleADVDeviceUpdateForMessage(Jid deviceJid, String rawId, long timestamp, int keyIndex, byte[] identityKey, ADVEncryptionType accountType) {
        throw unsupported("handleADVDeviceUpdateForMessage");
    }

    @Override
    public Optional<ADVSignedDeviceIdentity> extractAndValidateLocalSignedDeviceIdentity(Node deviceIdentityNode) {
        throw unsupported("extractAndValidateLocalSignedDeviceIdentity");
    }

    @Override
    public void persistLocalDeviceIdentityFromPairSuccess(Jid deviceJid, byte[] accountSignatureKey) {
        throw unsupported("persistLocalDeviceIdentityFromPairSuccess");
    }

    /**
     * Builds the failure thrown by every unstubbed method, embedding the method name in the message
     * so the test report points at the missing stub.
     *
     * @param method the method name
     * @return the configured exception
     */
    private static UnsupportedOperationException unsupported(String method) {
        return new UnsupportedOperationException("StubDeviceService." + method + " is not stubbed");
    }
}
