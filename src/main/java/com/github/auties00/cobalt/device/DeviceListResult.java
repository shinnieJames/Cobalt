package com.github.auties00.cobalt.device;

import com.github.auties00.cobalt.model.device.info.DeviceInfo;
import com.github.auties00.cobalt.model.device.info.DeviceList;
import com.github.auties00.cobalt.model.device.info.DeviceListBuilder;
import com.github.auties00.cobalt.model.jid.Jid;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of parsing a device list from a USync response.
 *
 * <p>Can be a full device list, an omitted result (delta update confirmation), or an error.
 *
 * @implNote WAWebAdvHandlerApi: handles USync response processing and dispatches to
 * appropriate handlers based on whether the result is full, omitted, or error.
 */
public sealed interface DeviceListResult {

    /**
     * Returns the user JID this result is for.
     *
     * @implNote WAWebAdvHandlerApi: each result is associated with a user JID, except
     * global errors which have no specific user.
     * @return an optional containing the user JID, or empty for global errors
     */
    Optional<Jid> userJid();
    
    /**
     * Returns a copy of this result with hosted devices removed.
     *
     * <p>For full results, filters out devices with hosted device ID.
     * For omitted and error results, returns the same instance unchanged.
     *
     * @implNote WAWebAdvHandlerApi.handleADVDeviceSyncResult: when bizHostedDevicesEnabled
     * is false, filters devices with {@code id !== HOSTED_DEVICE_ID} from each result.
     * @return a result without hosted devices
     */
    DeviceListResult withoutHostedDevices();

    /**
     * A full device list response from the server.
     *
     * @implNote WAWebHandleAdvForUsyncApi: processes full device list responses,
     * validates key indexes, and updates the device table.
     */
    final class Full implements DeviceListResult {
        private final DeviceList deviceList;
        private final byte[] accountSignatureKey;
        private final String username;

        /**
         * Creates a new full device list result.
         *
         * @implNote WAWebHandleAdvKeyIndexResultApi.handleKeyIndexResultSync: constructs the full
         * result with device list, account signature key from the validated key index list,
         * and optional username from the username protocol.
         * @param deviceList          the complete device list
         * @param accountSignatureKey the account signature key from signed key index list, or {@code null}
         * @param username            the username if username protocol was included, or {@code null}
         */
        public Full(DeviceList deviceList, byte[] accountSignatureKey, String username) {
            this.deviceList = Objects.requireNonNull(deviceList, "deviceList cannot be null");
            this.accountSignatureKey = accountSignatureKey;
            this.username = username;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote WAWebAdvHandlerApi: the user JID comes from the device list's user JID field.
         */
        @Override
        public Optional<Jid> userJid() {
            return Optional.of(deviceList.userJid());
        }

        /**
         * Returns the complete device list.
         *
         * @implNote WAWebHandleAdvKeyIndexResultApi: the device list contains validated devices,
         * timestamps, raw ID, account type, and key index information.
         * @return the device list
         */
        public DeviceList deviceList() {
            return deviceList;
        }

        /**
         * Returns the account signature key from signed key index list.
         *
         * @implNote WAWebHandleAdvDeviceNotificationUtils.verifySKeyIndexWithAccSigKey: extracts
         * the accountSignatureKey from the validated signed key index list protobuf.
         * @return an optional containing the account signature key, or empty if not present
         */
        public Optional<byte[]> accountSignatureKey() {
            return Optional.ofNullable(accountSignatureKey);
        }

        /**
         * Returns the username if username protocol was included.
         *
         * @implNote WAWebUsyncUsername.usernameParser: extracts username from the username
         * protocol child of the user node.
         * @return an optional containing the username, or empty if not present
         */
        public Optional<String> username() {
            return Optional.ofNullable(username);
        }

        /**
         * Returns {@code true} if this result contains a hosted device.
         *
         * @implNote WAWebBizCoexUtils: checks whether any device in the list is hosted,
         * either by device ID 99 or by the {@code isHosted} flag.
         * @return {@code true} if a hosted device is present
         */
        public boolean hasHostedDevice() {
            return deviceList.devices().stream().anyMatch(DeviceInfo::isHosted);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote WAWebAdvHandlerApi.handleADVDeviceSyncResult: filters out devices with
         * {@code id === HOSTED_DEVICE_ID} (99) when {@code bizHostedDevicesEnabled} is false.
         */
        @Override
        public DeviceListResult withoutHostedDevices() {
            var filteredDevices = deviceList.devices()
                    .stream()
                    .filter(device -> device.id() != DeviceConstants.HOSTED_DEVICE_ID)
                    .toList();
            if (filteredDevices.size() == deviceList.devices().size()) {
                return this;
            }

            var filteredList = new DeviceListBuilder()
                    .userJid(deviceList.userJid())
                    .devices(filteredDevices)
                    .timestamp(deviceList.timestamp())
                    .rawId(deviceList.rawId())
                    .deleted(deviceList.deleted())
                    .deletedChangedToHost(deviceList.deletedChangedToHost())
                    .advAccountType(deviceList.advAccountType())
                    .expectedTimestamp(deviceList.expectedTimestamp())
                    .expectedTimestampLastDeviceJobTimestamp(deviceList.expectedTimestampLastDeviceJobTimestamp())
                    .expectedTimestampUpdateTimestamp(deviceList.expectedTimestampUpdateTimestamp())
                    .currentIndex(deviceList.currentIndex())
                    .validIndexes(deviceList.validIndexes())
                    .build();
            return new DeviceListResult.Full(filteredList, accountSignatureKey, username);
        }
    }

    /**
     * An omitted result indicating the server confirmed the dhash matches.
     *
     * <p>The cached device list should be retained with updated timestamp.
     *
     * @implNote WAWebHandleAdvOmittedResultApi.handleOmittedResult: processes omitted
     * results by resetting to primary-only device list and detecting account type transitions.
     */
    final class Omitted implements DeviceListResult {
        private final Jid userJid;
        private final Instant timestamp;
        private final Instant expectedTimestamp;
        private final boolean fromHandleOmittedResult;

        /**
         * Creates a new omitted device list result.
         *
         * @implNote WAWebHandleAdvOmittedResultApi.handleOmittedResult: constructs the omitted
         * result with timestamp and expected timestamp from the key-index-list node attributes.
         * @param userJid                 the user JID
         * @param timestamp               the server's timestamp, or {@code null}
         * @param expectedTimestamp       the server's expected timestamp, or {@code null}
         * @param fromHandleOmittedResult marker flag for detecting HOSTED to E2EE transitions
         */
        public Omitted(Jid userJid, Instant timestamp, Instant expectedTimestamp, boolean fromHandleOmittedResult) {
            this.userJid = Objects.requireNonNull(userJid, "userJid cannot be null");
            this.timestamp = timestamp;
            this.expectedTimestamp = expectedTimestamp;
            this.fromHandleOmittedResult = fromHandleOmittedResult;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote WAWebHandleAdvOmittedResultApi: the user JID is always present for omitted results.
         */
        @Override
        public Optional<Jid> userJid() {
            return Optional.of(userJid);
        }

        /**
         * Returns the server's timestamp.
         *
         * @implNote WAWebUsyncDevice.deviceParser: extracted from the key-index-list node's
         * {@code ts} attribute via {@code attrTime("ts")}.
         * @return an optional containing the timestamp, or empty if not present
         */
        public Optional<Instant> timestamp() {
            return Optional.ofNullable(timestamp);
        }

        /**
         * Returns the server's expected timestamp.
         *
         * @implNote WAWebUsyncDevice.deviceParser: extracted from the key-index-list node's
         * {@code expected_ts} attribute via {@code maybeAttrTime("expected_ts")}.
         * @return an optional containing the expected timestamp, or empty if not present
         */
        public Optional<Instant> expectedTimestamp() {
            return Optional.ofNullable(expectedTimestamp);
        }

        /**
         * Returns the marker flag for detecting HOSTED to E2EE transitions.
         *
         * @implNote WAWebHandleAdvOmittedResultApi.handleOmittedResult: sets
         * {@code fromHandleOmittedResult: true} in the returned update object.
         * WAWebAdvHandlerApi.handleADVDeviceSyncResult: checks this flag to detect
         * HOSTED to E2EE transitions when the existing record was HOSTED.
         * @return {@code true} if from handle omitted result
         */
        public boolean fromHandleOmittedResult() {
            return fromHandleOmittedResult;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote ADAPTED: omitted results have no device list, so filtering is a no-op.
         */
        @Override
        public DeviceListResult withoutHostedDevices() {
            return this;
        }
    }

    /**
     * An error result indicating the server returned an error for this user.
     *
     * @implNote WAWebAdvHandlerApi: error.all is fatal and aborts processing,
     * error.devices is non-fatal and allows continuing with other users.
     * WAWebUsyncDevice.deviceParser: per-user device errors return {@code {errorCode, errorText}}.
     */
    final class Error implements DeviceListResult {
        private final Jid userJid;
        private final int errorCode;
        private final String errorText;
        private final boolean fatal;

        /**
         * Creates a new error device list result.
         *
         * @implNote WAWebUsyncDevice.deviceParser: per-user device errors extract errorCode
         * and errorText from the error child node. WAWebUsync.usyncParser: protocol-level
         * errors also extract errorBackoff (not used in Cobalt).
         * @param userJid   the user JID, or {@code null} for global errors
         * @param errorCode the error code from the server
         * @param errorText the error text from the server
         * @param fatal     {@code true} for fatal errors (error.all), {@code false} for non-fatal (error.devices)
         */
        public Error(Jid userJid, int errorCode, String errorText, boolean fatal) {
            this.userJid = userJid;
            this.errorCode = errorCode;
            this.errorText = Objects.requireNonNull(errorText, "errorText cannot be null");
            this.fatal = fatal;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote WAWebAdvHandlerApi: global errors (error.all) have no user JID.
         * Per-user device errors include the user JID.
         */
        @Override
        public Optional<Jid> userJid() {
            return Optional.ofNullable(userJid);
        }

        /**
         * Returns the error code from the server.
         *
         * @implNote WAWebUsyncDevice.deviceParser: {@code n.attrInt("code")} extracts the
         * error code from the error node.
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the error text from the server.
         *
         * @implNote WAWebUsyncDevice.deviceParser: {@code n.attrString("text")} extracts the
         * error text from the error node.
         * @return the error text
         */
        public String errorText() {
            return errorText;
        }

        /**
         * Returns whether this is a fatal error.
         *
         * @implNote WAWebAdvHandlerApi: error.all is fatal and aborts the entire USync request.
         * WAWebUsyncDevice.deviceParser errors and protocol-level errors are non-fatal.
         * @return {@code true} for fatal errors (error.all), {@code false} for non-fatal (error.devices)
         */
        public boolean fatal() {
            return fatal;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote ADAPTED: error results have no device list, so filtering is a no-op.
         */
        @Override
        public DeviceListResult withoutHostedDevices() {
            return this;
        }
    }
}
