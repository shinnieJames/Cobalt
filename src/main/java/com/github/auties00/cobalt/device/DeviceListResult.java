package com.github.auties00.cobalt.device;

import com.github.auties00.cobalt.model.device.info.DeviceInfo;
import com.github.auties00.cobalt.model.device.info.DeviceList;
import com.github.auties00.cobalt.model.device.DeviceListBuilder;
import com.github.auties00.cobalt.model.jid.Jid;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of parsing a device list from a USync response.
 *
 * <p>Can be a full device list, an omitted result (delta update confirmation), or an error.
 *
 * @apiNote WAWebAdvHandlerApi: handles USync response processing and dispatches to
 * appropriate handlers based on whether the result is full, omitted, or error.
 */
public sealed interface DeviceListResult {

    /**
     * Returns the user JID this result is for.
     *
     * @return an optional containing the user JID, or empty for global errors
     */
    Optional<Jid> userJid();
    
    DeviceListResult withoutHostedDevices();

    /**
     * A full device list response from the server.
     *
     * @apiNote WAWebHandleAdvForUsyncApi: processes full device list responses,
     * validates key indexes, and updates the device table.
     */
    final class Full implements DeviceListResult {
        private final DeviceList deviceList;
        private final byte[] accountSignatureKey;
        private final String username;

        /**
         * Creates a new full device list result.
         *
         * @param deviceList          the complete device list
         * @param accountSignatureKey the account signature key from signed key index list, or {@code null}
         * @param username            the username if username protocol was included, or {@code null}
         */
        public Full(DeviceList deviceList, byte[] accountSignatureKey, String username) {
            this.deviceList = Objects.requireNonNull(deviceList, "deviceList cannot be null");
            this.accountSignatureKey = accountSignatureKey;
            this.username = username;
        }

        @Override
        public Optional<Jid> userJid() {
            return Optional.of(deviceList.userJid());
        }

        /**
         * Returns the complete device list.
         *
         * @return the device list
         */
        public DeviceList deviceList() {
            return deviceList;
        }

        /**
         * Returns the account signature key from signed key index list.
         *
         * @return an optional containing the account signature key, or empty if not present
         */
        public Optional<byte[]> accountSignatureKey() {
            return Optional.ofNullable(accountSignatureKey);
        }

        /**
         * Returns the username if username protocol was included.
         *
         * @return an optional containing the username, or empty if not present
         */
        public Optional<String> username() {
            return Optional.ofNullable(username);
        }

        /**
         * Returns {@code true} if this result contains a hosted device.
         *
         * @return {@code true} if a hosted device is present
         */
        public boolean hasHostedDevice() {
            return deviceList.devices().stream().anyMatch(DeviceInfo::isHosted);
        }

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
     * @apiNote WAWebHandleAdvOmittedResultApi.handleOmittedResult: processes omitted
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

        @Override
        public Optional<Jid> userJid() {
            return Optional.of(userJid);
        }

        /**
         * Returns the server's timestamp.
         *
         * @return an optional containing the timestamp, or empty if not present
         */
        public Optional<Instant> timestamp() {
            return Optional.ofNullable(timestamp);
        }

        /**
         * Returns the server's expected timestamp.
         *
         * @return an optional containing the expected timestamp, or empty if not present
         */
        public Optional<Instant> expectedTimestamp() {
            return Optional.ofNullable(expectedTimestamp);
        }

        /**
         * Returns the marker flag for detecting HOSTED to E2EE transitions.
         *
         * @return {@code true} if from handle omitted result
         */
        public boolean fromHandleOmittedResult() {
            return fromHandleOmittedResult;
        }

        @Override
        public DeviceListResult withoutHostedDevices() {
            return this;
        }
    }

    /**
     * An error result indicating the server returned an error for this user.
     *
     * @apiNote WAWebAdvHandlerApi: error.all is fatal and aborts processing,
     * error.devices is non-fatal and allows continuing with other users.
     */
    final class Error implements DeviceListResult {
        private final Jid userJid;
        private final int errorCode;
        private final String errorText;
        private final boolean fatal;

        /**
         * Creates a new error device list result.
         *
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

        @Override
        public Optional<Jid> userJid() {
            return Optional.ofNullable(userJid);
        }

        /**
         * Returns the error code from the server.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the error text from the server.
         *
         * @return the error text
         */
        public String errorText() {
            return errorText;
        }

        /**
         * Returns whether this is a fatal error.
         *
         * @return {@code true} for fatal errors (error.all), {@code false} for non-fatal (error.devices)
         */
        public boolean fatal() {
            return fatal;
        }

        @Override
        public DeviceListResult withoutHostedDevices() {
            return this;
        }
    }
}
