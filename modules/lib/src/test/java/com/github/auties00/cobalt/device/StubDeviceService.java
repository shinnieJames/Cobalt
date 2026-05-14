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
 * {@link UnsupportedOperationException} from every method by default,
 * with a small fluent surface for stubbing the handful of methods
 * sender / receiver tests actually call.
 *
 * <p>Defaults are deliberately loud: any unexpected device-service call
 * fails the test instead of silently returning {@code null} or empty.
 * Each {@code withXxx} setter overrides exactly one method and returns
 * the stub so calls can be chained.
 */
public final class StubDeviceService implements DeviceService {

    private Function<Jid, Collection<Jid>> userFanout;
    private BiFunction<Jid, Jid, DeviceGroupFanoutResult> groupFanout;
    private Function<Collection<Jid>, Integer> ensureSessions;
    private Function<Jid, Optional<IcdcResult>> computeIcdc;

    /** Returns a new stub with every method throwing by default. */
    public static StubDeviceService create() {
        return new StubDeviceService();
    }

    /** Installs the supplier used by {@link #getUserFanout(Jid, String)}. */
    public StubDeviceService withUserFanout(Function<Jid, Collection<Jid>> handler) {
        this.userFanout = handler;
        return this;
    }

    /** Installs the supplier used by {@link #getGroupFanout(Jid, Jid)}. */
    public StubDeviceService withGroupFanout(BiFunction<Jid, Jid, DeviceGroupFanoutResult> handler) {
        this.groupFanout = handler;
        return this;
    }

    /** Installs the supplier used by {@link #ensureSessions(Collection)}. */
    public StubDeviceService withEnsureSessions(Function<Collection<Jid>, Integer> handler) {
        this.ensureSessions = handler;
        return this;
    }

    /** Installs the supplier used by {@link #computeIcdc(Jid)}. */
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
            throw unsupported("getUserFanout — install via withUserFanout(...)");
        }
        return userFanout.apply(chatJid);
    }

    @Override
    public DeviceGroupFanoutResult getGroupFanout(Jid groupJid, Jid senderDeviceJid) {
        if (groupFanout == null) {
            throw unsupported("getGroupFanout — install via withGroupFanout(...)");
        }
        return groupFanout.apply(groupJid, senderDeviceJid);
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
            // Default to "all sessions already exist" (no-op) so simple
            // tests don't need to wire anything up.
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
     * Builds the loud failure that every unstubbed method raises.
     *
     * @param method the method name
     * @return the configured exception
     */
    private static UnsupportedOperationException unsupported(String method) {
        return new UnsupportedOperationException("StubDeviceService." + method + " is not stubbed");
    }
}
