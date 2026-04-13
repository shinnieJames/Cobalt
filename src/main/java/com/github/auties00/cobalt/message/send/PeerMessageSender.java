package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.message.send.ack.AckParser;
import com.github.auties00.cobalt.message.send.ack.AckResult;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.util.List;
import java.util.Objects;

/**
 * Sends peer protocol messages to the user's own devices.
 *
 * <p>Peer messages are protocol-level messages exchanged between the
 * user's own devices for state synchronisation.  They include:
 * <ul>
 *   <li>App state sync key shares and requests</li>
 *   <li>Fatal exception notifications</li>
 *   <li>Peer data operation requests/responses</li>
 *   <li>Ephemeral sync responses</li>
 * </ul>
 *
 * <p>These messages are encrypted per-device via the Signal session
 * cipher and tagged with {@code category="peer"} to distinguish them
 * from regular messages.  They use {@code push_priority="high"} to
 * ensure prompt delivery.
 *
 * @apiNote WAWebSendAppStateSyncMsgJob.encryptAndSendKeyMsg: sends peer
 * messages via createUserDeviceMsgStanza with category="peer" and
 * push_priority="high".
 * WAWebSendMsgCreateDeviceStanza.createUserDeviceMsgStanza: builds the
 * single-device stanza for peer messages.
 */
final class PeerMessageSender extends MessageSender<ChatMessageInfo> {
    /**
     * Logger for peer message sending diagnostics.
     *
     * @implNote ADAPTED: WAWebSendAppStateSyncMsgJob uses WALogger;
     * Cobalt uses {@link System.Logger}.
     */
    private static final System.Logger LOGGER = System.getLogger(PeerMessageSender.class.getName());

    /**
     * The encryption service for per-device Signal encryption.
     *
     * @implNote WAWebSendMsgCreateDeviceStanza.createUserDeviceMsgStanza:
     * delegates to WAWebEncryptMsgProtobuf.encryptMsgProtobuf.
     */
    private final MessageEncryption encryption;

    /**
     * The device service for ensuring E2E sessions before encryption.
     *
     * @implNote WAWebSendMsgCreateDeviceStanza.createUserDeviceMsgStanza:
     * calls WAWebManageE2ESessionsJob.ensureE2ESessions.
     */
    private final DeviceService deviceService;

    /**
     * Creates a new peer message sender.
     *
     * @param client        the WhatsApp client for sending stanzas
     * @param encryption    the message encryption service
     * @param deviceService the device service for session management
     *
     * @implNote ADAPTED: WAWebSendAppStateSyncMsgJob uses module-level
     * imports; Cobalt uses constructor-based DI instead.
     */
    PeerMessageSender(
            WhatsAppClient client,
            MessageEncryption encryption,
            DeviceService deviceService
    ) {
        super(client);
        this.encryption = Objects.requireNonNull(encryption, "encryption");
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService");
    }

    /**
     * Sends a peer protocol message to a single target device.
     *
     * <p>Encrypts the message container using the Signal session cipher
     * for the target device, wraps it in a {@code <message>} stanza with
     * {@code category="peer"} and {@code push_priority="high"}, and
     * waits for the server acknowledgement.
     *
     * @param targetDevice the target device JID (typically the user's
     *                     own primary device)
     * @param messageInfo  the outgoing peer protocol message
     * @return the server ack result
     *
     * @implNote WAWebSendAppStateSyncMsgJob.encryptAndSendKeyMsg:
     * waits for offline delivery end, calls createPeerMsgProtobuf,
     * then delegates to createUserDeviceMsgStanza with
     * {@code MsgType.AppStateSync}, which sets {@code category="peer"},
     * {@code push_priority="high"}, and {@code appdata="default"} on
     * the meta node. Finally sends via deprecatedSendStanzaAndWaitForAck.
     */
    @Override
    AckResult send(Jid targetDevice, ChatMessageInfo messageInfo) {
        // WAWebSendAppStateSyncMsgJob: yield waitForOfflineDeliveryEnd()
        waitForOfflineDelivery();

        var container = messageInfo.message();
        var plaintext = MessageContainerSpec.encode(container);

        // WAWebSendMsgCreateDeviceStanza: ensureE2ESessions then encrypt
        deviceService.ensureSessions(List.of(targetDevice));
        var payload = encryption.encryptForDevice(targetDevice, plaintext);

        // WAWebSendMsgCreateDeviceStanza: identity node when pkmsg
        var identityNode = payload.isPreKeyMessage()
                ? buildIdentityNode() : null;

        // WAWebSendMsgCreateDeviceStanza: build the stanza
        // category="peer" distinguishes from regular messages
        // push_priority="high" ensures prompt delivery
        var encNode = new NodeBuilder()
                .description("enc")
                .attribute("v", String.valueOf(MessageEncryption.CIPHERTEXT_VERSION))
                .attribute("type", payload.type().protocolValue())
                .content(payload.ciphertext())
                .build();

        // WAWebSendMsgCreateDeviceStanza: build meta node with appdata="default"
        // for peer messages (isCategoryPeerMessage=true)
        var metaNode = new NodeBuilder()
                .description("meta")
                .attribute("appdata", "default")
                .build();

        // WAWebSendMsgCreateDeviceStanza: the edit attribute comes from
        // editAttribute(n, d.subtype) — returns DROP_ATTR for peer messages
        // since none of the edit/revoke conditions apply.
        // There is NO "subtype" attribute on the wire stanza.
        // WAWebE2EProtoUtils.typeAttributeFromProtobuf: protocolMessage → "text"
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
