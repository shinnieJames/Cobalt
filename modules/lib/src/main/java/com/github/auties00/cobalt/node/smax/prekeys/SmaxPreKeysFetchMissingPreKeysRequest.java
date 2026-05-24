package com.github.auties00.cobalt.node.smax.prekeys;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <iq xmlns="encrypt" type="get">} stanza that asks the
 * relay to re-issue stale per-device pre-key bundles.
 *
 * @apiNote
 * Built by Cobalt's Signal recovery path, the counterpart of WA Web's
 * {@code WAWebFetchResendMissingKeyJob.fetchResendMissingKeys}. The
 * caller submits the device-level registration ids it currently has and
 * the relay surfaces fresh bundles for any device whose recorded
 * registration id changed (e.g., after a device re-pair); this lets
 * Cobalt re-establish Signal sessions after a "missing prekey" decryption
 * failure.
 *
 * @implNote
 * This implementation collapses the {@code WASmaxOutPreKeysClientRequestMixin}
 * envelope shaping and the per-user/per-device payload construction into
 * a single {@link #toNode()} pass; the JS layer routes the same data
 * through multiple mixin functions.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutPreKeysFetchMissingPreKeysRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutPreKeysClientRequestMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutPreKeysRegistrationIDMixin")
public final class SmaxPreKeysFetchMissingPreKeysRequest implements SmaxOperation.Request {
    /**
     * The per-user fetch entries that will appear as {@code <user>}
     * children of the request.
     */
    private final List<UserKeyFetchRequest> users;

    /**
     * Constructs a request for the given list of users.
     *
     * @apiNote
     * Used directly by Cobalt's Signal recovery path; the relay rejects
     * empty requests, so the constructor refuses an empty list early.
     *
     * @param users the per-user fetch entries
     * @throws NullPointerException     if {@code users} is {@code null}
     * @throws IllegalArgumentException if {@code users} is empty
     */
    public SmaxPreKeysFetchMissingPreKeysRequest(List<UserKeyFetchRequest> users) {
        Objects.requireNonNull(users, "users cannot be null");
        if (users.isEmpty()) {
            throw new IllegalArgumentException("users cannot be empty");
        }
        this.users = List.copyOf(users);
    }

    /**
     * Returns the list of users carried by this request.
     *
     * @apiNote
     * Exposed for test and audit code; the list is unmodifiable so
     * callers cannot mutate it after construction.
     *
     * @return an unmodifiable {@link List} of {@link UserKeyFetchRequest}
     */
    public List<UserKeyFetchRequest> users() {
        return users;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation hard-codes {@code xmlns="encrypt"},
     * {@code type="get"}, and {@code to=s.whatsapp.net} per the
     * {@code WASmaxOutPreKeysFetchMissingPreKeysRequest.makeFetchMissingPreKeysRequest}
     * fixture, then nests one {@code <user>} per entry under a single
     * {@code <key_fetch>} child, each carrying one
     * {@code <device id><registration/></device>} grandchild per device.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutPreKeysFetchMissingPreKeysRequest",
            exports = "makeFetchMissingPreKeysRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var userNodes = new ArrayList<Node>(users.size());
        for (var user : users) {
            var deviceNodes = new ArrayList<Node>(user.devices().size());
            for (var device : user.devices()) {
                var registrationNode = new NodeBuilder()
                        .description("registration")
                        .content(device.registrationId())
                        .build();
                var deviceNode = new NodeBuilder()
                        .description("device")
                        .attribute("id", device.deviceId())
                        .content(registrationNode)
                        .build();
                deviceNodes.add(deviceNode);
            }
            var userBuilder = new NodeBuilder()
                    .description("user")
                    .attribute("jid", user.userJid());
            if (user.hasUserReasonIdentity()) {
                userBuilder.attribute("reason", "identity");
            }
            userBuilder.content(deviceNodes);
            userNodes.add(userBuilder.build());
        }
        var keyFetchNode = new NodeBuilder()
                .description("key_fetch")
                .content(userNodes)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "encrypt")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(keyFetchNode);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation compares the carried {@link #users} list.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxPreKeysFetchMissingPreKeysRequest) obj;
        return Objects.equals(this.users, that.users);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation hashes the carried {@link #users} list to
     * stay consistent with {@link #equals(Object)}.
     */
    @Override
    public int hashCode() {
        return Objects.hash(users);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation mirrors the record-like rendering used across
     * the {@code Smax*} stanza family.
     */
    @Override
    public String toString() {
        return "SmaxPreKeysFetchMissingPreKeysRequest[users=" + users + ']';
    }

    /**
     * Per-user entry in the outbound {@code <key_fetch>} payload.
     *
     * @apiNote
     * Pairs a target user {@link Jid} with a list of per-device fetch
     * entries plus the optional {@code reason="identity"} hint.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutPreKeysFetchMissingPreKeysRequest")
    public static final class UserKeyFetchRequest {
        /**
         * The target user {@link Jid} whose stale pre-keys are being
         * re-fetched.
         */
        private final Jid userJid;

        /**
         * Whether to set {@code reason="identity"} on the
         * {@code <user>} child.
         */
        private final boolean hasUserReasonIdentity;

        /**
         * The per-device fetch entries (zero to one hundred per the
         * {@code REPEATED_CHILD(0, 100)} schema).
         */
        private final List<DeviceKeyFetchRequest> devices;

        /**
         * Constructs a per-user request entry.
         *
         * @apiNote
         * Callers should set {@code hasUserReasonIdentity=true} when
         * they need the relay to attach the device-identity attestation
         * for any re-fetched device. The {@code devices} list may be
         * empty when the caller only needs to refresh the user-level
         * attestation.
         *
         * @param userJid               the target user {@link Jid}
         * @param hasUserReasonIdentity whether to set the identity-reason
         *                              hint
         * @param devices               the per-device entries; defaults
         *                              to an empty list when
         *                              {@code null}
         * @throws NullPointerException if {@code userJid} is {@code null}
         */
        public UserKeyFetchRequest(Jid userJid, boolean hasUserReasonIdentity, List<DeviceKeyFetchRequest> devices) {
            this.userJid = Objects.requireNonNull(userJid, "userJid cannot be null");
            this.hasUserReasonIdentity = hasUserReasonIdentity;
            this.devices = List.copyOf(Objects.requireNonNullElse(devices, List.of()));
        }

        /**
         * Returns the target user {@link Jid}.
         *
         * @apiNote
         * Used by {@link SmaxPreKeysFetchMissingPreKeysRequest#toNode()}
         * to populate the {@code jid} attribute.
         *
         * @return the user {@link Jid}
         */
        public Jid userJid() {
            return userJid;
        }

        /**
         * Returns whether the identity-reason hint is set.
         *
         * @apiNote
         * Used by {@link SmaxPreKeysFetchMissingPreKeysRequest#toNode()}
         * to decide whether to emit the {@code reason="identity"}
         * attribute.
         *
         * @return {@code true} when the hint is set
         */
        public boolean hasUserReasonIdentity() {
            return hasUserReasonIdentity;
        }

        /**
         * Returns the per-device fetch entries.
         *
         * @apiNote
         * Each entry feeds one {@code <device id><registration/></device>}
         * grandchild of the outbound stanza.
         *
         * @return an unmodifiable {@link List} of
         *         {@link DeviceKeyFetchRequest}
         */
        public List<DeviceKeyFetchRequest> devices() {
            return devices;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation compares the JID, the identity-reason
         * flag, and the devices list.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (UserKeyFetchRequest) obj;
            return this.hasUserReasonIdentity == that.hasUserReasonIdentity
                    && Objects.equals(this.userJid, that.userJid)
                    && Objects.equals(this.devices, that.devices);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation hashes all three fields via
         * {@link Objects#hash(Object...)}.
         */
        @Override
        public int hashCode() {
            return Objects.hash(userJid, hasUserReasonIdentity, devices);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation mirrors the record-like rendering used
         * across the {@code Smax*} stanza family.
         */
        @Override
        public String toString() {
            return "SmaxPreKeysFetchMissingPreKeysRequest.UserKeyFetchRequest[userJid=" + userJid
                    + ", hasUserReasonIdentity=" + hasUserReasonIdentity
                    + ", devices=" + devices + ']';
        }
    }

    /**
     * Per-device entry inside a {@link UserKeyFetchRequest}.
     *
     * @apiNote
     * Pairs a numeric device id with the 4-byte registration id that
     * Cobalt currently has cached for that device; the relay compares the
     * supplied id with its own record and only surfaces a fresh bundle
     * when the two disagree.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutPreKeysFetchMissingPreKeysRequest")
    @WhatsAppWebModule(moduleName = "WASmaxOutPreKeysRegistrationIDMixin")
    public static final class DeviceKeyFetchRequest {
        /**
         * The numeric device id in the {@code [0, 99]} range.
         */
        private final int deviceId;

        /**
         * The 4-byte registration id (raw bytes, big-endian) whose
         * freshness needs validating.
         */
        private final byte[] registrationId;

        /**
         * Constructs a per-device fetch entry.
         *
         * @apiNote
         * Used directly by Cobalt's recovery path; the {@code deviceId}
         * is the device-list slot of the addressee account.
         *
         * @param deviceId       the device id
         * @param registrationId the 4-byte registration id
         * @throws NullPointerException if {@code registrationId} is
         *                              {@code null}
         */
        public DeviceKeyFetchRequest(int deviceId, byte[] registrationId) {
            this.deviceId = deviceId;
            this.registrationId = Objects.requireNonNull(registrationId, "registrationId cannot be null");
        }

        /**
         * Returns the numeric device id.
         *
         * @apiNote
         * Used by {@link SmaxPreKeysFetchMissingPreKeysRequest#toNode()}
         * to populate the {@code id} attribute of each {@code <device>}
         * grandchild.
         *
         * @return the device id
         */
        public int deviceId() {
            return deviceId;
        }

        /**
         * Returns the 4-byte registration id.
         *
         * @apiNote
         * Used by {@link SmaxPreKeysFetchMissingPreKeysRequest#toNode()}
         * as the content of the {@code <registration>} grandchild.
         *
         * @return the raw bytes
         */
        public byte[] registrationId() {
            return registrationId;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation compares the device id and the
         * registration id via {@link Arrays#equals(byte[], byte[])}.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (DeviceKeyFetchRequest) obj;
            return this.deviceId == that.deviceId
                    && Arrays.equals(this.registrationId, that.registrationId);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation combines {@link Integer#hashCode(int)} and
         * {@link Arrays#hashCode(byte[])} to stay consistent with
         * {@link #equals(Object)}.
         */
        @Override
        public int hashCode() {
            var result = Integer.hashCode(deviceId);
            result = 31 * result + Arrays.hashCode(registrationId);
            return result;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation renders the registration id as a
         * length-only summary to avoid leaking key material into logs.
         */
        @Override
        public String toString() {
            return "SmaxPreKeysFetchMissingPreKeysRequest.DeviceKeyFetchRequest[deviceId=" + deviceId
                    + ", registrationId=" + (registrationId != null ? registrationId.length + " bytes" : "null") + ']';
        }
    }
}
