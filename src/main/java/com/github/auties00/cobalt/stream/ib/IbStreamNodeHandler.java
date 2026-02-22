package com.github.auties00.cobalt.stream.ib;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientOfflineResumeState;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.stream.SocketStream;

public final class IbStreamNodeHandler extends SocketStream.Handler {
    private static final System.Logger LOGGER = System.getLogger(IbStreamNodeHandler.class.getName());

    private final DeviceService deviceService;

    public IbStreamNodeHandler(WhatsAppClient whatsapp, DeviceService deviceService) {
        super(whatsapp, "ib");
        this.deviceService = deviceService;
    }

    @Override
    public void handle(Node node) {
        var child = node.getChild();
        if(child.isEmpty()) {
            return;
        }

        switch(child.get().description()) {
            case "dirty" -> handleIbDirty(child.get());
            case "offline_preview" -> handleIbOfflinePreview(child.get());
            case "offline" -> handleIbOfflineComplete(child.get());
        }
    }

    private void handleIbDirty(Node dirty) {
        var type = dirty.getRequiredAttributeAsString("type");
        // TODO: Support other types
        switch (type) {
            case "account_sync" -> handleAccountSync(dirty, type);
        }
    }

    private void handleAccountSync(Node dirty, String type) {
        var timestamp = dirty.getRequiredAttributeAsLong("timestamp");
        var queryBody = new NodeBuilder()
                .description("clean")
                .attribute("type", type)
                .attribute("timestamp", timestamp)
                .build();
        var queryRequest = new NodeBuilder()
                .description("iq")
                .attribute("to", JidServer.user())
                .attribute("type", "set")
                .attribute("xmlns", "urn:xmpp:whatsapp:dirty")
                .content(queryBody);
        whatsapp.sendNode(queryRequest);
    }

    /**
     * Handles the offline_preview IB which indicates the start of offline message delivery.
     * Per WhatsApp Web: this triggers the RESUME_ON_RESTART state.
     */
    private void handleIbOfflinePreview(Node offlinePreview) {
        var count = offlinePreview.getAttributeAsLong("count", 0);
        LOGGER.log(System.Logger.Level.DEBUG, "Received offline_preview with {0} messages", count);

        // Transition to RESUME_ON_RESTART state
        whatsapp.store().setOfflineResumeState(WhatsAppClientOfflineResumeState.RESUME_ON_RESTART);

        // Request offline messages
        var ibBody = new NodeBuilder()
                .description("offline_batch")
                .attribute("count", count)
                .build();
        var ibRequest = new NodeBuilder()
                .description("ib")
                .content(ibBody)
                .build();
        whatsapp.sendNodeWithNoResponse(ibRequest);
    }

    /**
     * Handles the offline IB which indicates offline message delivery is complete.
     * Per WhatsApp Web WAWebBlockingOfflineResumeManager.processOfflineSessionComplete():
     * this triggers the COMPLETE state.
     */
    private void handleIbOfflineComplete(Node offline) {
        LOGGER.log(System.Logger.Level.DEBUG, "Received offline completion marker");

        // Transition to COMPLETE state
        whatsapp.store().setOfflineResumeState(WhatsAppClientOfflineResumeState.COMPLETE);

        // Per WhatsApp Web: trigger pending device syncs after offline resume completes
        deviceService.retryPendingSyncs();
    }
}
