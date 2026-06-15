package com.github.auties00.cobalt.yunsuo;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.model.button.template.hydrated.HydratedFourRowTemplateSimpleBuilder;
import com.github.auties00.cobalt.model.button.template.hydrated.HydratedTemplateButton;
import com.github.auties00.cobalt.model.button.template.hydrated.HydratedURLButtonBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidCompanion;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.message.button.TemplateMessage;
import com.github.auties00.cobalt.model.message.button.TemplateMessageSimpleBuilder;
import com.github.auties00.cobalt.model.message.model.MessageContainer;
import com.github.auties00.cobalt.model.message.model.MessageStatus;
import com.github.auties00.cobalt.model.message.standard.ImageMessage;
import com.github.auties00.cobalt.model.message.standard.ImageMessageBuilder;
import com.github.auties00.cobalt.node.Node;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 图片模板卡片消息测试：
 * <p>
 * 相比 externalAdReply 的 ExtendedTextMessage 方案，这里改成更接近 whalibmob/常见客户端实现的
 * “上传图片 + hydrated template + URL button” 发送方式，便于排查是否是卡片协议形态导致的服务端拒绝。
 */
public class ImageTemplateCardTest {
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
        var imagePath = "/Users/admin/Documents/data/gg/pic/djy.jpg";
        var targetPhone = 60102619686L;
//        var targetPhone = 85254849927L;
        var recipient = Jid.of(targetPhone);
        var buttonUrl = "https://djy.dagzbhsauad.com?ch=91289";
        var bodyText = "❤️Olá 😊, sou o gerente da plataforma TT700 PG, quero convidar você a entrar em nossa plataforma para ganhar dinheiro.\n"
                + "\n"
                + "🎁Cadastre uma conta e deposite 10 e você receberá imediatamente 100/10R$ de graça, Invista 1 lucre 10 e com certeza terá a oportunidade de ganhar de 500R$ a 1000R$ por hora.\n"
                + "\n"
                + "💋Melhore sua experiência com tempos de pagamento de jogos Tigre, Coelho, Dragão, Vaca,... com uma taxa de vitória de até 99%.\n"
                + "\n"
                + "💵Quer aproveitar esta oportunidade para ganhar dinheiro?";
        var buttonText = "Clique aqui para participar";

        var send = new AtomicBoolean(false);
        var sentMessageId = new AtomicReference<String>();
        var sendAttemptId = new AtomicReference<String>();
        var imageData = Files.readAllBytes(Path.of(imagePath));
        var startedAt = System.currentTimeMillis();

        WhatsAppClient.builder()
                .mobileClient()
                .loadConnection(WhatsAppClientSixPartsKeys.of(sixParts))
                .proxy(proxyUri)
                .device(JidCompanion.ios(business))
                .name("yunsuo")
                .registered()
                .orElseThrow()
                .addNodeReceivedListener((ignored, incoming) -> {
                    if (shouldLogIncomingNode(incoming, sentMessageId.get())) {
                        logPhase(startedAt, sendAttemptId.get(), "node-received", summarizeNode(incoming));
                    }
                    logOutgoingMessageState(startedAt, sendAttemptId.get(), incoming, sentMessageId.get());
                })
                .addNodeSentListener((ignored, outgoing) -> {
                    if (shouldLogOutgoingNode(outgoing, sentMessageId.get())) {
                        logPhase(startedAt, sendAttemptId.get(), "node-sent", summarizeNode(outgoing));
                    }
                })
                .addMessageStatusListener((ignored, info) -> {
                    var currentMessageId = sentMessageId.get();
                    if (currentMessageId != null && currentMessageId.equals(info.id())) {
                        logPhase(startedAt, sendAttemptId.get(), "message-status", "id=%s, status=%s".formatted(info.id(), info.status()));
                        if (info.status() == MessageStatus.ERROR) {
                            logError(startedAt, sendAttemptId.get(), "message-rejected", "id=%s was rejected after send".formatted(info.id()));
                        }
                    }
                })
                .addDisconnectedListener((ignored, reason) -> logError(startedAt, sendAttemptId.get(), "disconnected", "reason=%s".formatted(reason)))
                .addLoggedInListener(api -> {
                    logPhase(startedAt, sendAttemptId.get(), "login", "connected=%s".formatted(api.isConnected()));
                    if (send.compareAndSet(false, true)) {
                        var attemptId = "attempt-" + Long.toUnsignedString(System.nanoTime(), 36);
                        sendAttemptId.set(attemptId);
                        Thread.startVirtualThread(() -> sendTemplateMessage(
                                api,
                                recipient,
                                bodyText,
                                buttonText,
                                buttonUrl,
                                imageData,
                                sentMessageId,
                                attemptId,
                                startedAt
                        ));
                    }
                })
                .connect()
                .waitForDisconnection();
    }

    private static void sendTemplateMessage(
            WhatsAppClient api,
            Jid recipient,
            String bodyText,
            String buttonText,
            String buttonUrl,
            byte[] imageData,
            AtomicReference<String> sentMessageId,
            String attemptId,
            long startedAt
    ) {
        try {
            logPhase(startedAt, attemptId, "send-thread-start", "recipient=%s, imageBytes=%d".formatted(maskJid(recipient), imageData.length));
            Thread.sleep(5000);

            var message = createUploadedImageUrlTemplateMessage(api, bodyText, buttonText, buttonUrl, imageData, attemptId, startedAt);
            logPhase(startedAt, attemptId, "before-send", "recipient=%s".formatted(maskJid(recipient)));
            var info = api.sendChatMessage(recipient, MessageContainer.of(message), false);
            sentMessageId.set(info.id());
            logPhase(startedAt, attemptId, "after-send", "messageId=%s, status=%s".formatted(info.id(), info.status()));
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            logError(startedAt, attemptId, "interrupted", interruptedException.getMessage());
        } catch (Throwable error) {
            logError(startedAt, attemptId, "failed", error.getMessage());
            error.printStackTrace();
        }
    }

    private static TemplateMessage createUploadedImageUrlTemplateMessage(
            WhatsAppClient client,
            String bodyText,
            String buttonText,
            String buttonUrl,
            byte[] imageData,
            String attemptId,
            long startedAt
    ) {
        var titleImage = new ImageMessageBuilder()
                .mimetype("image/jpeg")
                .caption(bodyText)
                .thumbnail(imageData)
                .build();
        uploadTitleImage(client, titleImage, imageData, attemptId, startedAt);

        var urlButton = new HydratedURLButtonBuilder()
                .text(buttonText)
                .url(buttonUrl)
                .build();

        var template = new HydratedFourRowTemplateSimpleBuilder()
                .title(titleImage)
                .body(bodyText)
                .buttons(List.of(HydratedTemplateButton.of(urlButton)))
                .build();

        return new TemplateMessageSimpleBuilder()
                .content(template)
                .format(template)
                .build();
    }

    private static void uploadTitleImage(WhatsAppClient client, ImageMessage titleImage, byte[] imageData, String attemptId, long startedAt) {
        try (var inputStream = new ByteArrayInputStream(imageData)) {
            logPhase(startedAt, attemptId, "before-media-wait", "imageBytes=%d".formatted(imageData.length));
            var mediaConnection = client.store()
                    .waitForMediaConnection();
            logPhase(startedAt, attemptId, "after-media-wait", "hosts=%d, ttl=%d".formatted(mediaConnection.hosts().size(), mediaConnection.ttl()));
            logPhase(startedAt, attemptId, "before-upload", "mediaPath=%s".formatted(titleImage.mediaPath().path().orElse("unavailable")));
            var uploaded = mediaConnection.upload(titleImage, inputStream);
            logPhase(startedAt, attemptId, "after-upload", "uploaded=%s, mediaUrl=%s".formatted(uploaded, titleImage.mediaUrl().map(ImageTemplateCardTest::maskUrl).orElse("missing")));
            if (!uploaded) {
                throw new IllegalStateException("Image upload was rejected because the message does not expose an uploadable media path");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot upload template image to WhatsApp media servers", exception);
        }
    }

    private static void logOutgoingMessageState(long startedAt, String attemptId, Node incoming, String sentMessageId) {
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
                logError(startedAt, attemptId, "message-ack", "id=%s, error=%d".formatted(ackId, error.getAsLong()));
            } else {
                logPhase(startedAt, attemptId, "message-ack", "id=%s".formatted(ackId));
            }
            return;
        }

        if (incoming.hasDescription("receipt")) {
            var receiptId = incoming.getAttributeAsString("id").orElse(null);
            if (!sentMessageId.equals(receiptId)) {
                return;
            }

            var from = incoming.getAttributeAsJid("from")
                    .map(ImageTemplateCardTest::maskJid)
                    .orElse("unknown");
            var type = incoming.getAttributeAsString("type")
                    .orElse("delivered");
            logPhase(startedAt, attemptId, "message-receipt", "id=%s, from=%s, type=%s".formatted(receiptId, from, type));
        }
    }

    private static boolean shouldLogIncomingNode(Node node, String sentMessageId) {
        return switch (node.description()) {
            case "stream:error", "xmlstreamend", "notification" -> true;
            case "ack", "receipt" -> sentMessageId != null && sentMessageId.equals(node.getAttributeAsString("id").orElse(null));
            case "message" -> node.getAttributeAsJid("from")
                    .map(from -> from.hasServer(JidServer.broadcast()))
                    .orElse(false);
            default -> false;
        };
    }

    private static boolean shouldLogOutgoingNode(Node node, String sentMessageId) {
        return switch (node.description()) {
            case "ack" -> node.getAttributeAsJid("to")
                    .map(to -> to.hasServer(JidServer.broadcast()) || to.hasServer(JidServer.user()))
                    .orElse(false);
            case "message" -> sentMessageId == null || sentMessageId.equals(node.getAttributeAsString("id").orElse(null));
            case "receipt" -> sentMessageId != null && sentMessageId.equals(node.getAttributeAsString("id").orElse(null));
            default -> false;
        };
    }

    private static String summarizeNode(Node node) {
        var summary = new StringJoiner(", ", node.description() + "[", "]");
        node.getAttributeAsString("id").ifPresent(id -> summary.add("id=" + id));
        node.getAttributeAsJid("from").map(ImageTemplateCardTest::maskJid).ifPresent(from -> summary.add("from=" + from));
        node.getAttributeAsJid("to").map(ImageTemplateCardTest::maskJid).ifPresent(to -> summary.add("to=" + to));
        node.getAttributeAsString("type").ifPresent(type -> summary.add("type=" + type));
        node.getAttributeAsString("class").ifPresent(messageClass -> summary.add("class=" + messageClass));
        node.getAttributeAsJid("participant").map(ImageTemplateCardTest::maskJid).ifPresent(participant -> summary.add("participant=" + participant));
        node.getAttributeAsJid("recipient").map(ImageTemplateCardTest::maskJid).ifPresent(recipient -> summary.add("recipient=" + recipient));
        var childDescriptions = node.children().stream()
                .limit(3)
                .map(Node::description)
                .toList();
        if (!childDescriptions.isEmpty()) {
            summary.add("children=" + childDescriptions);
        }
        return summary.toString();
    }

    private static void logPhase(long startedAt, String attemptId, String phase, String detail) {
        System.out.printf("[ImageTemplateCardTest][%s][+%dms] %s%s%n",
                attemptId == null ? "no-attempt" : attemptId,
                System.currentTimeMillis() - startedAt,
                phase,
                detail == null || detail.isBlank() ? "" : " - " + detail);
    }

    private static void logError(long startedAt, String attemptId, String phase, String detail) {
        System.err.printf("[ImageTemplateCardTest][%s][+%dms] %s%s%n",
                attemptId == null ? "no-attempt" : attemptId,
                System.currentTimeMillis() - startedAt,
                phase,
                detail == null || detail.isBlank() ? "" : " - " + detail);
    }

    private static String maskJid(Jid jid) {
        var user = jid.user();
        var visibleSuffix = user == null || user.length() <= 4 ? user : user.substring(user.length() - 4);
        var maskedUser = visibleSuffix == null || visibleSuffix.isBlank() ? "unknown" : "***" + visibleSuffix;
        var device = jid.device() == 0 ? "" : ":" + jid.device();
        return maskedUser + "@" + jid.server() + device;
    }

    private static String maskUrl(String url) {
        return URI.create(url)
                .getHost();
    }
}
