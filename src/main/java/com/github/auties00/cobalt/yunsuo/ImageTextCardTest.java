package com.github.auties00.cobalt.yunsuo;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientMessagePreviewHandler;
import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.model.info.ContextInfoBuilder;
import com.github.auties00.cobalt.model.info.ExternalAdReplyInfo;
import com.github.auties00.cobalt.model.info.ExternalAdReplyInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidCompanion;
import com.github.auties00.cobalt.model.message.model.MessageContainer;
import com.github.auties00.cobalt.model.message.model.MessageStatus;
import com.github.auties00.cobalt.model.media.ThumbnailLinkMediaProvider;
import com.github.auties00.cobalt.model.message.standard.ImageMessageBuilder;
import com.github.auties00.cobalt.model.message.standard.TextMessage;
import com.github.auties00.cobalt.model.message.standard.TextMessageBuilder;
import com.github.auties00.cobalt.node.Node;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 链接卡片消息测试：
 * <p>
 * 支持多种发送形态做对照：
 * 1) 纯文本
 * 2) 文本+URL
 * 3) 标准 preview（更接近 Baileys extendedText preview）
 * 4) externalAdReply
 * 5) real preview soft（复用现有 preview handler 生成真实 metadata）
 * 6) real preview HQ（在 soft 基础上上传 preview thumbnail 并补齐 HQ 元数据）
 * 7) real preview HQ thumbnail-link（使用 thumbnail-link carrier 做 A/B 对照）
 */
public class ImageTextCardTest {
    private static final String CARD_TITLE = "TT700 PG";
    private static final String CARD_DESCRIPTION = "Clique aqui para participar";

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
        System.out.println("Select message mode:\n(1) Plain text (2) Text + URL (3) Standard preview (4) ExternalAdReply (5) Real preview soft (6) Real preview HQ (7) Real preview HQ thumbnail-link");
        var mode = switch (scanner.nextInt()) {
            case 1 -> MessageMode.PLAIN_TEXT;
            case 2 -> MessageMode.TEXT_WITH_URL;
            case 3 -> MessageMode.STANDARD_PREVIEW;
            case 4 -> MessageMode.EXTERNAL_AD_REPLY;
            case 5 -> MessageMode.REAL_PREVIEW_SOFT;
            case 6 -> MessageMode.REAL_PREVIEW_HQ;
            case 7 -> MessageMode.REAL_PREVIEW_HQ_THUMBNAIL_LINK;
            default -> throw new IllegalStateException("Unexpected value: " + scanner.nextInt());
        };
        scanner.nextLine();
        System.out.println("Enter the proxy address (format: " + YunsuoProxyParser.INPUT_EXAMPLE + "): ");
        var proxyUri = YunsuoProxyParser.parse(scanner.nextLine().trim());

        var imagePath = "/Users/admin/Documents/data/gg/pic/djy.jpg";
        var targetPhone = 60102619686L;
//        var targetPhone = 85254849927L;
        var recipient = Jid.of(targetPhone);
        var url = "https://djy.dagzbhsauad.com?ch=91289";

        var send = new AtomicBoolean(false);
        var sentMessageId = new AtomicReference<String>();
        var imageData = Files.readAllBytes(Path.of(imagePath));

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
                        Thread.startVirtualThread(() -> sendLinkPreviewMessage(api, recipient, url, imageData, sentMessageId, mode));
                    }
                })
                .connect()
                .waitForDisconnection();
    }

    private static void sendLinkPreviewMessage(
            WhatsAppClient api,
            Jid recipient,
            String url,
            byte[] imageData,
            AtomicReference<String> sentMessageId,
            MessageMode mode
    ) {
        try {
            Thread.sleep(5000);

            var plainText = "hello test case-1 20260611";
            var textWithUrl = "hello test case-2 20260611\n" + url;
            var promoText = "hello test case-3 20260611\n"
                    + "This is a preview rendering test.\n"
                    + url;

            var message = switch (mode) {
                case PLAIN_TEXT -> TextMessage.of(plainText);
                case TEXT_WITH_URL -> TextMessage.of(textWithUrl);
                case STANDARD_PREVIEW -> createStandardLinkPreviewMessage(promoText, url, imageData);
                case EXTERNAL_AD_REPLY -> createExternalAdReplyMessage(promoText, url, imageData);
                case REAL_PREVIEW_SOFT -> createRealPreviewMessage(api, promoText, url, imageData, PreviewUploadMode.NONE);
                case REAL_PREVIEW_HQ -> createRealPreviewMessage(api, promoText, url, imageData, PreviewUploadMode.IMAGE_CARRIER);
                case REAL_PREVIEW_HQ_THUMBNAIL_LINK -> createRealPreviewMessage(api, promoText, url, imageData, PreviewUploadMode.THUMBNAIL_LINK_CARRIER);
            };
            logMessageShape(mode, message);
            var info = api.sendChatMessage(recipient, MessageContainer.of(message), false);
            sentMessageId.set(info.id());
            System.out.println("Queued " + mode + " message: " + info.id());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            System.err.println("Link preview send interrupted: " + interruptedException.getMessage());
        } catch (Throwable error) {
            System.err.println("Failed to queue link preview message: " + error.getMessage());
            error.printStackTrace();
        }
    }

    private static TextMessage createStandardLinkPreviewMessage(String text, String url, byte[] imageData) {
        return new TextMessageBuilder()
                .text(text)
                .matchedText(url)
                .canonicalUrl(url)
                .title(CARD_TITLE)
                .description(CARD_DESCRIPTION)
                .thumbnail(imageData)
                .previewType(TextMessage.PreviewType.NONE)
                .build();
    }

    private static TextMessage createExternalAdReplyMessage(String text, String url, byte[] imageData) {
        var externalAdReply = new ExternalAdReplyInfoBuilder()
                .title(CARD_TITLE)
                .body(CARD_DESCRIPTION)
                .sourceUrl(url)
                .mediaType(ExternalAdReplyInfo.MediaType.IMAGE)
                .thumbnail(imageData)
                .renderLargerThumbnail(true)
                .showAdAttribution(false)
                .build();
        var contextInfo = new ContextInfoBuilder()
                .externalAdReply(externalAdReply)
                .build();

        return new TextMessageBuilder()
                .text(text)
                .matchedText(url)
                .contextInfo(contextInfo)
                .previewType(TextMessage.PreviewType.NONE)
                .build();
    }

    private static TextMessage createRealPreviewMessage(WhatsAppClient api, String text, String url, byte[] fallbackImageData, PreviewUploadMode uploadMode) {
        var message = new TextMessageBuilder()
                .text(text)
                .build();
        WhatsAppClientMessagePreviewHandler.enabled(false)
                .attribute(message);
        message.setMatchedText(url);
        message.setCanonicalUrl(url);
        if (message.title().isEmpty()) {
            message.setTitle(CARD_TITLE);
        }
        if (message.description().isEmpty()) {
            message.setDescription(CARD_DESCRIPTION);
        }
        if (message.thumbnail().isEmpty()) {
            message.setThumbnail(fallbackImageData);
        }
        if (uploadMode == PreviewUploadMode.NONE) {
            message.setPreviewType(TextMessage.PreviewType.NONE);
            return message;
        }

        try {
            var thumbnailBytes = message.thumbnail().orElse(fallbackImageData);
            var previewWidth = message.thumbnailWidth().orElse(0);
            var previewHeight = message.thumbnailHeight().orElse(0);
            if ((previewWidth <= 0 || previewHeight <= 0) && thumbnailBytes.length > 0) {
                try (var input = new ByteArrayInputStream(thumbnailBytes)) {
                    var bufferedImage = ImageIO.read(input);
                    if (bufferedImage != null) {
                        previewWidth = bufferedImage.getWidth();
                        previewHeight = bufferedImage.getHeight();
                    }
                }
            }
            switch (uploadMode) {
                case IMAGE_CARRIER -> {
                    var carrier = new ImageMessageBuilder()
                            .mimetype("image/jpeg")
                            .width(previewWidth > 0 ? previewWidth : null)
                            .height(previewHeight > 0 ? previewHeight : null)
                            .thumbnail(thumbnailBytes)
                            .build();
                    try (var uploadStream = new ByteArrayInputStream(thumbnailBytes)) {
                        var uploaded = api.store()
                                .waitForMediaConnection()
                                .upload(carrier, uploadStream);
                        if (!uploaded) {
                            throw new IllegalStateException("Preview thumbnail upload was rejected");
                        }
                    }
                    carrier.mediaDirectPath().ifPresent(message::setThumbnailDirectPath);
                    carrier.mediaSha256().ifPresent(message::setThumbnailSha256);
                    carrier.mediaEncryptedSha256().ifPresent(message::setThumbnailEncSha256);
                    carrier.mediaKey().ifPresent(message::setMediaKey);
                    carrier.mediaKeyTimestamp().ifPresent(timestamp -> message.setMediaKeyTimestampSeconds(timestamp.toEpochSecond()));
                }
                case THUMBNAIL_LINK_CARRIER -> {
                    var carrier = new ThumbnailLinkMediaProvider();
                    try (var uploadStream = new ByteArrayInputStream(thumbnailBytes)) {
                        var uploaded = api.store()
                                .waitForMediaConnection()
                                .upload(carrier, uploadStream);
                        if (!uploaded) {
                            throw new IllegalStateException("Thumbnail-link preview upload was rejected");
                        }
                    }
                    carrier.mediaDirectPath().ifPresent(message::setThumbnailDirectPath);
                    carrier.mediaSha256().ifPresent(message::setThumbnailSha256);
                    carrier.mediaEncryptedSha256().ifPresent(message::setThumbnailEncSha256);
                    carrier.mediaKey().ifPresent(message::setMediaKey);
                    if (carrier.mediaKeyTimestampSeconds().isPresent()) {
                        message.setMediaKeyTimestampSeconds(carrier.mediaKeyTimestampSeconds().getAsLong());
                    }
                }
                case NONE -> throw new IllegalStateException("Unexpected upload mode: " + uploadMode);
            }
            if (previewWidth > 0) {
                message.setThumbnailWidth(previewWidth);
            }
            if (previewHeight > 0) {
                message.setThumbnailHeight(previewHeight);
            }
        } catch (Exception exception) {
            System.err.println("Falling back to REAL_PREVIEW_SOFT because HQ preview upload failed: " + exception.getMessage());
        }
        return message;
    }

    private static void logMessageShape(MessageMode mode, TextMessage message) {
        System.out.printf(
                "Preview payload [%s]: matchedText=%s, canonicalUrl=%s, title=%s, description=%s, previewType=%s, thumbnailBytes=%d, thumbnailDirectPath=%s, thumbnailSha256=%s, thumbnailEncSha256=%s, mediaKey=%s, mediaKeyTimestamp=%s, size=%sx%s%n",
                mode,
                message.matchedText().orElse("-"),
                message.canonicalUrl().orElse("-"),
                message.title().orElse("-"),
                message.description().orElse("-"),
                message.previewType().map(Enum::name).orElse("-"),
                message.thumbnail().map(bytes -> bytes.length).orElse(0),
                message.thumbnailDirectPath().orElse("-"),
                message.thumbnailSha256().map(HexFormat.of()::formatHex).map(value -> abbreviateHex(value, 16)).orElse("-"),
                message.thumbnailEncSha256().map(HexFormat.of()::formatHex).map(value -> abbreviateHex(value, 16)).orElse("-"),
                message.mediaKey().map(HexFormat.of()::formatHex).map(value -> abbreviateHex(value, 16)).orElse("-"),
                message.mediaKeyTimestampSeconds().isPresent() ? message.mediaKeyTimestampSeconds().getAsLong() : "-",
                message.thumbnailWidth().orElse(0),
                message.thumbnailHeight().orElse(0)
        );
    }

    private static String abbreviateHex(String value, int visibleChars) {
        if (value.length() <= visibleChars) {
            return value;
        }
        return value.substring(0, visibleChars) + "...";
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

    private enum MessageMode {
        PLAIN_TEXT,
        TEXT_WITH_URL,
        STANDARD_PREVIEW,
        EXTERNAL_AD_REPLY,
        REAL_PREVIEW_SOFT,
        REAL_PREVIEW_HQ,
        REAL_PREVIEW_HQ_THUMBNAIL_LINK
    }

    private enum PreviewUploadMode {
        NONE,
        IMAGE_CARRIER,
        THUMBNAIL_LINK_CARRIER
    }
}
