package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.ack.AckParser;
import com.github.auties00.cobalt.ack.AckResult;
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
 * Sends peer protocol messages to one of the user's own devices.
 *
 * <p>Used for app-state-sync key shares and requests, fatal-exception
 * notifications, peer-data operation requests and responses, and
 * ephemeral-sync responses. Each send produces a single
 * {@code <message category="peer" push_priority="high">} stanza wrapping a
 * per-device Signal envelope with no {@code <participants>} layer.
 */
@WhatsAppWebModule(moduleName = "WAWebSendAppStateSyncMsgJob")
@WhatsAppWebModule(moduleName = "WAWebSendMsgCreateDeviceStanza")
final class PeerMessageSender extends MessageSender<ChatMessageInfo> {
    /**
     * The {@link System.Logger} used for peer-send diagnostics.
     */
    private static final System.Logger LOGGER = System.getLogger(PeerMessageSender.class.getName());

    /**
     * The {@link MessageEncryption} service used for per-device Signal
     * encryption.
     */
    private final MessageEncryption encryption;

    /**
     * The {@link DeviceService} used to ensure an E2E session is established
     * before encryption.
     */
    private final DeviceService deviceService;

    /**
     * Constructs a {@link PeerMessageSender} bound to the supplied
     * dependencies.
     *
     * @apiNote
     * Constructed once by {@link MessageSendingService}; embedders should
     * not instantiate directly.
     *
     * @param client         the {@link WhatsAppClient} used to dispatch
     *                       stanzas
     * @param encryption     the {@link MessageEncryption} service
     * @param deviceService  the {@link DeviceService} used to manage Signal
     *                       sessions
     * @param abPropsService the {@link ABPropsService} consulted by the base
     *                       sender
     * @param wamService     the {@link WamService} shared with the base
     *                       sender
     * @throws NullPointerException if any argument is {@code null}
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
     * {@inheritDoc}
     *
     * @apiNote
     * Encrypts the payload for the supplied {@code targetDevice}, wraps the
     * envelope in a {@code <message category="peer" push_priority="high">}
     * stanza alongside a {@code <meta appdata="default">} child and an
     * optional {@code <device-identity>} child (PKMSG only), and blocks
     * until the server returns the ack.
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
