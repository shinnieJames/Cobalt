package com.github.auties00.cobalt.stream.control;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.migration.InactiveGroupLidMigrationService;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.sync.WebAppStateService;
import com.github.auties00.cobalt.wam.WamService;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SuccessStreamHandler implements SocketStream.Handler {
    private final WhatsAppClient whatsapp;
    private final ABPropsService abPropsService;
    private final DeviceService deviceService;
    private final LidMigrationService lidMigrationService;
    private final InactiveGroupLidMigrationService inactiveGroupLidMigrationService;
    private final WamService wamService;
    private final WebAppStateService webAppStateService;
    private final AtomicBoolean started;

    public SuccessStreamHandler(
            WhatsAppClient whatsapp,
            ABPropsService abPropsService,
            DeviceService deviceService,
            LidMigrationService lidMigrationService,
            InactiveGroupLidMigrationService inactiveGroupLidMigrationService,
            WamService wamService,
            WebAppStateService webAppStateService
    ) {
        this.whatsapp = whatsapp;
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService cannot be null");
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService cannot be null");
        this.lidMigrationService = Objects.requireNonNull(lidMigrationService, "lidMigrationService cannot be null");
        this.inactiveGroupLidMigrationService = Objects.requireNonNull(inactiveGroupLidMigrationService, "inactiveGroupLidMigrationService cannot be null");
        this.wamService = Objects.requireNonNull(wamService, "wamService cannot be null");
        this.webAppStateService = Objects.requireNonNull(webAppStateService, "webAppStateService cannot be null");
        this.started = new AtomicBoolean();
    }

    @Override
    public void handle(Node node) {
        if (started.compareAndSet(false, true)) {
            bootstrap(node);
        }
    }

    @Override
    public void reset() {
        started.set(false);
    }

    private void bootstrap(Node node) {
        var store = whatsapp.store();
        store.setOnline(true);
        store.setRegistered(true);
        node.getAttributeAsJid("lid").ifPresent(store::setLid);

        var displayName = node.getAttributeAsString("display_name", null);
        if (displayName != null && !displayName.isBlank()) {
            var oldName = store.name();
            store.setName(displayName);
            if (!java.util.Objects.equals(oldName, displayName)) {
                for (var listener : store.listeners()) {
                    Thread.startVirtualThread(() -> listener.onNameChanged(whatsapp, oldName, displayName));
                }
            }
        }

        lidMigrationService.initialize();
        var abPropsSynced = abPropsService.sync();
        if (abPropsSynced && abPropsService.getBool(ABProp.LID_ONE_ON_ONE_MIGRATION_ENABLED)) {
            lidMigrationService.enableMigration();
        } else {
            lidMigrationService.disableMigration();
        }

        wamService.initialize();
        deviceService.startAdvCheckScheduler();
        deviceService.retryPendingSyncs();
        deviceService.updateMissingKeyDevices();
        inactiveGroupLidMigrationService.start();
        webAppStateService.resumeAfterRestart();
        webAppStateService.startPeriodicSyncJob();

        for (var listener : store.listeners()) {
            Thread.startVirtualThread(() -> listener.onLoggedIn(whatsapp));
        }

        try {
            store.save();
        } catch (Exception ignored) {
        }
    }
}
