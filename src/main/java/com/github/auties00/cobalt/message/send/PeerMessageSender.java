package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.message.send.ack.AckParser;
import com.github.auties00.cobalt.message.send.ack.AckResult;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
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
    private static final System.Logger LOGGER = System.getLogger(PeerMessageSender.class.getName());

    private final MessageEncryption encryption;
    private final DeviceService deviceService;

    PeerMessageSender(
            WhatsAppClient client,
            MessageEncryption encryption,
            DeviceService deviceService
    ) {
        super(client);
        this.encryption = Objects.requireNonNull(encryption, "encryption");
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService");
    }

    @Override
    AckResult send(Jid targetDevice, ChatMessageInfo messageInfo) {
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

        var stanza = new NodeBuilder()
                .description("message")
                .attribute("id", messageInfo.key().id().orElseThrow())
                .attribute("to", targetDevice)
                .attribute("type", resolvePeerStanzaType(container))
                .attribute("subtype", resolvePeerStanzaSubtype(container))
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

    private String resolvePeerStanzaType(com.github.auties00.cobalt.model.message.MessageContainer container) {
        return container.content() instanceof ProtocolMessage ? "protocol" : resolveStanzaType(container);
    }

    private String resolvePeerStanzaSubtype(com.github.auties00.cobalt.model.message.MessageContainer container) {
        if (!(container.content() instanceof ProtocolMessage protocolMessage)) {
            return null;
        }

        var type = protocolMessage.type()
                .orElse(null);
        if(type == null) {
            return null;
        }

        return switch (type) {
            case APP_STATE_SYNC_KEY_SHARE -> "app_state_sync_key_share";
            case APP_STATE_SYNC_KEY_REQUEST -> "app_state_sync_key_request";
            case APP_STATE_FATAL_EXCEPTION_NOTIFICATION -> "app_state_fatal_exception_notification";
            case PEER_DATA_OPERATION_REQUEST_MESSAGE -> "peer_data_operation_request_message";
            case PEER_DATA_OPERATION_REQUEST_RESPONSE_MESSAGE -> "peer_data_operation_request_response_message";
            default -> null;
        };
    }
}
