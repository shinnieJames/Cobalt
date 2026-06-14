package com.github.auties00.cobalt.call.internal;

import com.github.auties00.cobalt.ack.AckParser;
import com.github.auties00.cobalt.ack.AckResult;
import com.github.auties00.cobalt.ack.CallAck;
import com.github.auties00.cobalt.call.LiveCallService;
import com.github.auties00.cobalt.call.signaling.CallStanza;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.wam.WamService;

import java.util.List;

/**
 * Test-only factory that constructs a {@link LiveCallService} backed by a stub
 * {@link MessageService} whose {@link MessageService#sendCall} builds the minimal offer stanza
 * and routes it through {@code client.sendNode} so test harness {@code sendNodeHandler}s capture
 * the outbound call, and whose {@link MessageService#processCall} returns the ciphertext
 * unchanged.
 *
 * <p>The stub skips the real Signal encryption and device-list sync; tests that exercise call
 * wiring (registry, stanza shape, receiver, listener fan-out) don't need them.
 */
public final class TestLiveCallServiceFactory {
    private TestLiveCallServiceFactory() {
        throw new AssertionError("TestLiveCallServiceFactory is not instantiable");
    }

    /**
     * Constructs a {@link LiveCallService} for the given client.
     *
     * @param client     the test client
     * @param wamService the WAM telemetry service (may be {@code null})
     * @return the live call service
     */
    public static LiveCallService create(LinkedWhatsAppClient client, WamService wamService) {
        return new LiveCallService(client, wamService, new StubMessageService(client));
    }

    /**
     * Constructs the stub {@link MessageService} used by {@link #create(LinkedWhatsAppClient, WamService)},
     * for tests that build a {@code CallReceiver} directly and need to supply the same message service.
     *
     * @param client the test client
     * @return the stub message service
     */
    public static MessageService messageService(LinkedWhatsAppClient client) {
        return new StubMessageService(client);
    }

    /**
     * Stub {@link MessageService} that builds the minimal {@code <call><offer>} stanza (no real
     * Signal encryption, empty destination block) and dispatches via {@code client.sendNode} so
     * test capture handlers see the call.
     */
    private static final class StubMessageService implements MessageService {
        private final LinkedWhatsAppClient client;

        StubMessageService(LinkedWhatsAppClient client) {
            this.client = client;
        }

        @Override
        public AckResult send(Jid chatJid, MessageContainer container) {
            throw new UnsupportedOperationException("StubMessageService.send not stubbed");
        }

        @Override
        public AckResult send(MessageInfo messageInfo) {
            throw new UnsupportedOperationException("StubMessageService.send not stubbed");
        }

        @Override
        public AckResult sendPeer(Jid targetDevice, ChatMessageInfo messageInfo) {
            throw new UnsupportedOperationException("StubMessageService.sendPeer not stubbed");
        }

        @Override
        public MessageInfo process(Node node) {
            throw new UnsupportedOperationException("StubMessageService.process not stubbed");
        }

        @Override
        public CallAck sendCall(Jid peer, String callId, byte[] callKey, boolean video) {
            var selfJid = client.store().accountStore().jid().orElseThrow();
            var ackNode = client.sendNode(CallStanza.offer(
                    peer, selfJid, callId, video,
                    new byte[0], null, List.of(), new byte[0], null, null));
            var parsed = AckParser.parse(ackNode);
            if (parsed instanceof CallAck callAck) {
                return callAck;
            }
            // Test sendNode handlers often return a bare <ack/> without class="call". Synthesize
            // an empty CallAck so the call layer doesn't NPE on the cast.
            var synthetic = new NodeBuilder()
                    .description("ack")
                    .attribute("class", "call")
                    .attribute("type", "offer")
                    .build();
            return (CallAck) AckParser.parse(synthetic);
        }

        @Override
        public CallAck sendGroupCall(Jid group, java.util.Collection<Jid> participants, String callId,
                                     byte[] callKey, boolean video) {
            return sendCall(group, callId, callKey, video);
        }

        @Override
        public byte[] processCall(Jid senderJid, MessageEncryptionType encType, byte[] ciphertext) {
            return ciphertext;
        }

        @Override
        public void clearPendingMessages() {
        }
    }
}
