package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.message.send.ack.AckParser;
import com.github.auties00.cobalt.message.send.ack.AckResult;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryptedPayload;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.wam.WamService;

import java.util.List;
import java.util.Objects;

/**
 * Sends peer protocol messages to one of the user's own devices. These cover
 * app-state-sync key shares and requests, fatal-exception notifications, peer
 * data operation requests and responses, and ephemeral-sync responses.
 *
 * <p>Peer messages are encrypted per device using the Signal session cipher
 * and are tagged on the wire with {@code category="peer"} and
 * {@code push_priority="high"} so they are dispatched promptly.
 */
@WhatsAppWebModule(moduleName = "WAWebSendAppStateSyncMsgJob")
@WhatsAppWebModule(moduleName = "WAWebSendMsgCreateDeviceStanza")
final class PeerMessageSender extends MessageSender<ChatMessageInfo> {
    /**
     * Holds the logger used for peer-message diagnostics.
     */
    private static final System.Logger LOGGER = System.getLogger(PeerMessageSender.class.getName());

    /**
     * Holds the encryption service used for per-device Signal encryption.
     */
    private final MessageEncryption encryption;

    /**
     * Holds the device service used to ensure an E2E session before encryption.
     */
    private final DeviceService deviceService;

    /**
     * Constructs a peer-message sender bound to the given dependencies.
     *
     * @param client         the WhatsApp client used to dispatch stanzas
     * @param encryption     the message encryption service
     * @param deviceService  the device service used to manage Signal sessions
     * @param abPropsService the AB-props service consulted by the base sender
     * @param wamService     the WAM telemetry service shared with the base sender
     */
    @WhatsAppWebExport(moduleName = "WAWebSendAppStateSyncMsgJob", exports = "encryptAndSendKeyMsg",
            adaptation = WhatsAppAdaptation.ADAPTED)
    PeerMessageSender(
            WhatsAppClient client,
            MessageEncryption encryption,
            DeviceService deviceService,
            ABPropsService abPropsService,
            WamService wamService
    ) {
        super(client, abPropsService, wamService);
        this.encryption = Objects.requireNonNull(encryption, "encryption");
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService");
    }

    /**
     * Encrypts the given peer protocol message for the target device, builds a
     * {@code <message>} stanza tagged with {@code category="peer"} and
     * {@code push_priority="high"}, and waits for the server acknowledgement.
     *
     * @param targetDevice the target device JID, typically the user's primary device
     * @param messageInfo  the outgoing peer protocol message
     * @return the server ack result
     */
    @WhatsAppWebExport(moduleName = "WAWebSendAppStateSyncMsgJob", exports = "encryptAndSendKeyMsg",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebSendMsgCreateDeviceStanza", exports = "createUserDeviceMsgStanza",
            adaptation = WhatsAppAdaptation.DIRECT)
    @Override
    AckResult send(Jid targetDevice, ChatMessageInfo messageInfo) {
        waitForOfflineDelivery();

        var container = messageInfo.message();
        var plaintext = MessageContainerSpec.encode(container);

        deviceService.ensureSessions(List.of(targetDevice));
        MessageEncryptedPayload payload;
        try {
            payload = encryption.encryptForDevice(targetDevice, plaintext);
            emitE2eMessageSendEvent(targetDevice, container, true, payload.type(), 0);
        } catch (RuntimeException encryptionError) {
            emitE2eMessageSendEvent(targetDevice, container, false, null, 0);
            throw encryptionError;
        }

        var identityNode = payload.isPreKeyMessage()
                ? buildIdentityNode() : null;

        var encNode = new NodeBuilder()
                .description("enc")
                .attribute("v", String.valueOf(MessageEncryption.CIPHERTEXT_VERSION))
                .attribute("type", payload.type().protocolValue())
                .content(payload.ciphertext())
                .build();

        var metaNode = new NodeBuilder()
                .description("meta")
                .attribute("appdata", "default")
                .build();

        var stanza = new NodeBuilder()
                .description("message")
                .attribute("id", messageInfo.key().id().orElseThrow())
                .attribute("to", targetDevice)
                .attribute("type", resolveStanzaType(container))
                .attribute("category", "peer")
                .attribute("push_priority", "high")
                .content(
                        encNode,
                        identityNode,
                        metaNode
                );

        flushStore();
        var ackNode = client.sendNode(stanza);
        return AckParser.parse(ackNode);
    }

}
