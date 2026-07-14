package com.github.auties00.cobalt.yunsuo.succ;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidCompanion;
import com.github.auties00.cobalt.model.message.standard.TextMessage;
import com.github.auties00.cobalt.model.message.standard.TextMessageBuilder;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标准链接预览消息测试：优先验证会话内可见 + 点击链接可跳浏览器
 * <p>
 * 设计原则：
 * - 参考 Baileys 的常规 extendedTextMessage 链接预览字段布局
 * - 关键预览字段放在 TextMessage 本体：matchedText/title/description/thumbnail
 * - 避免依赖 externalAdReply 作为主要展示层，防止通知可见但会话内不渲染
 * - 文本中保留裸 URL，确保客户端能把这条消息识别成正常的链接预览消息
 */
public class ImageTextCardTest {
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

    public static void main(String[] args) throws IOException {
        // Baileys 行为拟态：
        // 1) 默认 generateHighQualityLinkPreview=false，因此关闭 Cobalt 的 link preview 自动上传，只发 inline 预览字段
        System.setProperty("cobalt.textLinkPreviewUpload", "false");
        // 2) 首次会话会触发 pre-key message，Baileys 用登录/配对期就绪的 account 附带 device-identity；
        //    mobile six-part 恢复没有 ADV identity，这里允许本地回退自签，避免首条消息因缺少 device-identity 失败
        System.setProperty("cobalt.mobileAdvFallback", "true");

        System.out.println("Enter the six parts segment: ");
        var scanner = new Scanner(System.in);
        var sixParts = scanner.nextLine().trim();
        System.out.println("Enter proxy in the format Region|Host|Port|Username|Password|ExpireTime: ");
        var proxyInfo = scanner.nextLine().trim().split("\\|");
        if (proxyInfo.length != 6) {
            throw new IllegalStateException("Unexpected proxy format: " + proxyInfo.length);
        }
        System.out.println("Select if the account is business or personal:\n(1) Business (2) Personal");
        var business = switch (scanner.nextInt()) {
            case 1 -> true;
            case 2 -> false;
            default -> throw new IllegalStateException("Unexpected value: " + scanner.nextInt());
        };

        var proxyUri = URI.create("socks5://%s:%s@%s:%s".formatted(proxyInfo[3], proxyInfo[4], proxyInfo[1], proxyInfo[2]));
        var imagePath = "/Users/admin/Documents/data/gg/pic/djy.jpg";
        var targetPhone = 556181290316L;
//        var targetPhone = 85254849927L;

        String url = "https://baidu.com";

        AtomicBoolean send = new AtomicBoolean(false);
        byte[] imageData = createJpegThumbnail(Files.readAllBytes(Path.of(imagePath)));

        WhatsAppClient whatsapp = WhatsAppClient.builder()
                .mobileClient()
                .loadConnection(WhatsAppClientSixPartsKeys.of(sixParts))
                .proxy(proxyUri)
                .device(JidCompanion.ios(business))
                .name("yunsuo")
                .registered()
                .orElseThrow();

        whatsapp.addNodeReceivedListener((ignored, incoming) -> System.out.printf("Received node %s%n", incoming))
                .addNodeSentListener((ignored, outgoing) -> System.out.printf("Sent node %s%n", abbreviateNode(outgoing)))
                .addDisconnectedListener((ignored, reason) -> System.out.println("Disconnected: " + reason))
                .addLoggedInListener(api -> {
                    System.out.println("Logged in");
                    if (send.compareAndSet(false, true)) {
                        String text = "this is baidu\n" + url;

                        Matcher matcher = URL_PATTERN.matcher(text);
                        String matchedText = matcher.find() ? matcher.group() : url;

                        var message = new TextMessageBuilder()
                                .text(text)
                                .matchedText(matchedText)
                                .title("Bai Du")
                                .description("This is a website!")
                                .thumbnail(imageData)
                                .previewType(TextMessage.PreviewType.NONE)
                                .build();

                        try {
                            var info = api.sendMessage(Jid.of(targetPhone), message);
                            System.out.println("Link preview message sent successfully: " + info.id());
                        } catch (Throwable error) {
                            System.err.println("Failed to send link preview message: " + error.getMessage());
                            error.printStackTrace();
                        }
                    }
                })
                .connect()
                .waitForDisconnection();
    }

    private static byte[] createJpegThumbnail(byte[] imageData) throws IOException {
        var image = ImageIO.read(new java.io.ByteArrayInputStream(imageData));
        if (image == null) {
            return imageData;
        }

        var maxWidth = 640;
        var maxHeight = 640;
        var scale = Math.min(1D, Math.min((double) maxWidth / image.getWidth(), (double) maxHeight / image.getHeight()));
        var width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        var height = Math.max(1, (int) Math.round(image.getHeight() * scale));

        var resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(image.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null);
        } finally {
            graphics.dispose();
        }

        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(resized, "jpg", output);
            return output.toByteArray();
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
