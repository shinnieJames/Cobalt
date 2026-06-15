package com.github.auties00.cobalt.yunsuo;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidCompanion;
import com.github.auties00.cobalt.model.message.model.MessageStatus;
import com.github.auties00.cobalt.node.Node;

import java.io.IOException;
import java.net.URI;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 纯文本链接发送测试：
 * <p>
 * 只发送普通文本 + 完整 HTTPS 链接，
 * 用于验证接收端是否能稳定收到消息并点击链接，
 * 不依赖 preview / externalAdReply / 模板卡片。
 */
public class PlainTextLinkTest {
    public static void main(String[] args) throws IOException {
        System.out.println("Enter the six parts segment: ");
        var scanner = new Scanner(System.in);
        var sixParts = scanner.nextLine().trim();
        System.out.println("Select if the account is business or personal:\n(1) Business (2) Personal");
        var business = switch (scanner.nextInt()) {
            case 1 -> true;
            case 2 -> false;
            default -> throw new IllegalStateException("Unexpected value: " + scanner.nextInt());
        };

        var proxyUri = URI.create("socks5://cfchgwfs:rc97cfzd5e42@92.113.231.117:7202");
        var targetPhone = 60102619686L;
//        var targetPhone = 85254849927L;
        var recipient = Jid.of(targetPhone);
        var url = "https://djy.dagzbhsauad.com?ch=91289";
        var text = "hello link test 20260612\n"
                + "Please open the official website:\n"
                + url;

        var send = new AtomicBoolean(false);
        var sentMessageId = new AtomicReference<String>();

        WhatsAppClient.builder()
                .mobileClient()
                .loadConnection(WhatsAppClientSixPartsKeys.of(sixParts))
                .proxy(proxyUri)
                .device(JidCompanion.ios(business))
                .name("yunsuo")
                .registered()
                .orElseThrow()
                .addNodeReceivedListener((ignored, incoming) -> {
                    System.out.printf("Received node %s%n", abbreviateNode(incoming));
                    logOutgoingMessageState(incoming, sentMessageId.get());
                })
                .addNodeSentListener((ignored, outgoing) -> System.out.printf("Sent node %s%n", abbreviateNode(outgoing)))
                .addMessageStatusListener((ignored, info) -> {
                    var currentMessageId = sentMessageId.get();
                    if (currentMessageId != null && currentMessageId.equals(info.id())) {
                        System.out.printf("Message status update: id=%s, status=%s%n", info.id(), info.status());
                        if (info.status() == MessageStatus.ERROR) {
                            System.err.printf("Message %s was rejected after send%n", info.id());
                        }
                    }
                })
                .addDisconnectedListener((ignored, reason) -> System.out.println("Disconnected: " + reason))
                .addLoggedInListener(api -> {
                    System.out.println("Logged in");
                    if (send.compareAndSet(false, true)) {
                        Thread.startVirtualThread(() -> sendPlainTextLink(api, recipient, text, sentMessageId));
                    }
                })
                .connect()
                .waitForDisconnection();
    }

    private static void sendPlainTextLink(
            WhatsAppClient api,
            Jid recipient,
            String text,
            AtomicReference<String> sentMessageId
    ) {
        try {
            Thread.sleep(5000);
            var info = api.sendMessage(recipient, text);
            sentMessageId.set(info.id());
            System.out.println("Queued plain text link message: " + info.id());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            System.err.println("Plain text link send interrupted: " + interruptedException.getMessage());
        } catch (Throwable error) {
            System.err.println("Failed to queue plain text link message: " + error.getMessage());
            error.printStackTrace();
        }
    }

    private static void logOutgoingMessageState(Node incoming, String sentMessageId) {
        if (sentMessageId == null) {
            return;
        }

        if (incoming.hasDescription("ack") && incoming.hasAttribute("class", "message")) {
            var ackId = incoming.getAttributeAsString("id").orElse(null);
            if (!sentMessageId.equals(ackId)) {
                return;
            }

            var error = incoming.getAttributeAsLong("error");
            if (error.isPresent()) {
                System.err.printf("Server rejected message %s with error %d%n", ackId, error.getAsLong());
            } else {
                System.out.printf("Server acked message %s%n", ackId);
            }
            return;
        }

        if (incoming.hasDescription("receipt")) {
            var receiptId = incoming.getAttributeAsString("id").orElse(null);
            if (!sentMessageId.equals(receiptId)) {
                return;
            }

            var from = incoming.getAttributeAsJid("from")
                    .map(Object::toString)
                    .orElse("unknown");
            var type = incoming.getAttributeAsString("type")
                    .orElse("delivered");
            System.out.printf("Message receipt: id=%s, from=%s, type=%s%n", receiptId, from, type);
        }
    }

    private static String abbreviateNode(Object node) {
        var value = String.valueOf(node);
        var maxLength = 1200;
        if (value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength) + " ...[truncated " + (value.length() - maxLength) + " chars]";
    }
}
