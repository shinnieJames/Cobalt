package com.github.auties00.cobalt.store.db;

import com.github.auties00.cobalt.client.WhatsAppClientType;
import com.github.auties00.cobalt.model.jid.JidDeviceSpec;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.store.WhatsAppStoreFactory;
import com.github.auties00.libsignal.key.SignalIdentityKeyPairSpec;
import com.github.auties00.libsignal.key.SignalSignedKeyPairSpec;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.JDBCUtils;

import java.io.IOException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class DatabaseWhatsAppStoreFactory implements WhatsAppStoreFactory {
    private final String jdbcUrl;

    public DatabaseWhatsAppStoreFactory(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    @Override
    public WhatsAppStore loadOrCreateStore(WhatsAppClientType clientType, UUID uuid) throws IOException {
        Objects.requireNonNull(clientType, "clientType cannot be null");
        Objects.requireNonNull(jdbcUrl, "jdbcUrl cannot be null");
        try {
            var conn = DriverManager.getConnection(jdbcUrl);
            var dialect = JDBCUtils.dialect(jdbcUrl);
            var tempDb = DSL.using(conn, dialect);

            // Check if session_props table exists by attempting to read from it
            var uuidStr = getString(tempDb, "uuid", null);
            if (uuidStr == null) { conn.close(); return Optional.empty(); }
            var loadedUuid = UUID.fromString(uuidStr);
            if (uuid != null && !loadedUuid.equals(uuid)) { conn.close(); return Optional.empty(); }

            var clientTypeStr = getString(tempDb, "clientType", null);
            if (clientTypeStr == null) { conn.close(); return Optional.empty(); }
            var loadedClientType = WhatsAppClientType.valueOf(clientTypeStr);

            var initTs = getLong(tempDb, "initializationTimeStamp");
            var regId = getInt(tempDb, "registrationId");
            var noiseKp = getProto(tempDb, "noiseKeyPair", SignalIdentityKeyPairSpec::decode);
            var identityKp = getProto(tempDb, "identityKeyPair", SignalIdentityKeyPairSpec::decode);
            var signedKp = getProto(tempDb, "signedKeyPair", SignalSignedKeyPairSpec::decode);
            var fdidStr = getString(tempDb, "fdid", null);
            var loadedDeviceId = getBytes(tempDb, "deviceId");
            var advIdStr = getString(tempDb, "advertisingId", null);
            var loadedIdentityId = getBytes(tempDb, "identityId");
            var loadedBackupToken = getBytes(tempDb, "backupToken");
            var loadedDevice = getProto(tempDb, "device", JidDeviceSpec::decode);

            conn.close();

            if (initTs == null || regId == null || noiseKp == null || identityKp == null
                || signedKp == null || loadedDeviceId == null || loadedIdentityId == null
                || loadedBackupToken == null || loadedDevice == null
                || fdidStr == null || advIdStr == null) {
                return Optional.empty();
            }

            var store = new DatabaseWhatsAppStore(
                    jdbcUrl,
                    loadedUuid,
                    loadedClientType,
                    initTs,
                    regId,
                    noiseKp,
                    identityKp,
                    signedKp,
                    UUID.fromString(fdidStr),
                    loadedDeviceId,
                    UUID.fromString(advIdStr),
                    loadedIdentityId,
                    loadedBackupToken,
                    loadedDevice
            );
            store.loadMutableProps();
            store.loadSignalCaches();
            store.loadLidMappings();
            store.loadDeviceIdentityRanges();
            store.loadPropertiesMap();
            return Optional.of(store);
        } catch (SQLException | RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public WhatsAppStore loadOrCreateStore(WhatsAppClientType clientType, long phoneNumber) throws IOException {
        return null;
    }
}
