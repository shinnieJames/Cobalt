package com.github.auties00.cobalt.yunsuo.test;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidCompanion;
import com.github.auties00.cobalt.model.message.standard.TextMessage;
import com.github.auties00.cobalt.model.message.standard.TextMessageBuilder;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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
 * A 方案：参考 ImageTextCardTest 的 Baileys / WhatsApp Web 风格链接预览消息。
 * <p>
 * 设计原则：
 * - 正文中显式保留 raw URL，确保接收方能直接看到并点击链接
 * - 把 matchedText / canonicalUrl / title / description / thumbnail 放在 TextMessage 本体
 * - 使用压缩后的小尺寸缩略图，降低消息体积并提高会话内渲染稳定性
 */
public class BaileysTextLinkPreviewMarketingTest {
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    private static final int PREVIEW_IMAGE_MAX_WIDTH = 640;
    private static final int PREVIEW_IMAGE_MAX_HEIGHT = 640;
    private static final float PREVIEW_IMAGE_QUALITY = 0.72f;

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
        var url = "https://djy.dagzbhsauad.com?ch=91289";

        AtomicBoolean send = new AtomicBoolean(false);
        var previewImage = createPreviewImage(Files.readAllBytes(Path.of(imagePath)));

        var mobileBuilder = WhatsAppClient.builder()
                .mobileClient()
                .loadConnection(WhatsAppClientSixPartsKeys.of(sixParts))
                .proxy(proxyUri)
                .device(JidCompanion.ios(business))
                .name("yunsuo");
        mobileBuilder.messagePreviewHandler(_ -> {});

        WhatsAppClient whatsapp = mobileBuilder
                .registered()
                .orElseThrow();

        whatsapp.addNodeReceivedListener((ignored, incoming) -> System.out.printf("Received node %s%n", incoming))
                .addNodeSentListener((ignored, outgoing) -> System.out.printf("Sent node %s%n", abbreviateNode(outgoing)))
                .addDisconnectedListener((ignored, reason) -> System.out.println("Disconnected: " + reason))
                .addLoggedInListener(api -> {
                    System.out.println("Logged in");
                    if (send.compareAndSet(false, true)) {
                        String text = "❤️Olá 😊, sou o gerente da plataforma TT700 PG, quero convidar você a entrar em nossa plataforma para ganhar dinheiro.\n"
                                + "\n"
                                + "🎁Cadastre uma conta e deposite 10 e você receberá imediatamente 100/10R$ de graça, Invista 1 lucre 10 e com certeza terá a oportunidade de ganhar de 500R$ a 1000R$ por hora.\n"
                                + "\n"
                                + "💋Melhore sua experiência com tempos de pagamento de jogos Tigre, Coelho, Dragão, Vaca,... com uma taxa de vitória de até 99%.\n"
                                + "\n"
                                + "💵Quer aproveitar esta oportunidade para ganhar dinheiro?\n"
                                + "\n"
                                + "👇👇👇Clique no link abaixo e participe\n"
                                + url;

                        Matcher matcher = URL_PATTERN.matcher(text);
                        String matchedText = matcher.find() ? matcher.group() : url;

                        var message = new TextMessageBuilder()
                                .text(text)
                                .matchedText(matchedText)
                                .canonicalUrl(url)
                                .title("TT700 PG")
                                .description("Clique aqui para participar")
                                .thumbnail(previewImage.data())
                                .thumbnailWidth(previewImage.width())
                                .thumbnailHeight(previewImage.height())
                                .previewType(TextMessage.PreviewType.NONE)
                                .build();

                        try {
                            var info = api.sendMessage(Jid.of(targetPhone), message);
                            System.out.println("BaileysTextLinkPreviewMarketingTest sent successfully: " + info.id());
                        } catch (Throwable error) {
                            System.err.println("Failed to send BaileysTextLinkPreviewMarketingTest: " + error.getMessage());
                            error.printStackTrace();
                        }
                    }
                })
                .connect()
                .waitForDisconnection();
    }

    private static PreviewImage createPreviewImage(byte[] imageData) throws IOException {
        try (var inputStream = new ByteArrayInputStream(imageData)) {
            var inputImage = ImageIO.read(inputStream);
            if (inputImage == null) {
                return new PreviewImage(imageData, PREVIEW_IMAGE_MAX_WIDTH, PREVIEW_IMAGE_MAX_HEIGHT);
            }

            var scale = Math.min(
                    1d,
                    Math.min(
                            (double) PREVIEW_IMAGE_MAX_WIDTH / inputImage.getWidth(),
                            (double) PREVIEW_IMAGE_MAX_HEIGHT / inputImage.getHeight()
                    )
            );
            var scaledWidth = Math.max(1, (int) Math.round(inputImage.getWidth() * scale));
            var scaledHeight = Math.max(1, (int) Math.round(inputImage.getHeight() * scale));
            var outputImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
            var graphics = outputImage.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.drawImage(inputImage.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH), 0, 0, null);
            } finally {
                graphics.dispose();
            }

            var writers = ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext()) {
                return new PreviewImage(imageData, scaledWidth, scaledHeight);
            }

            ImageWriter writer = writers.next();
            try (var outputStream = new ByteArrayOutputStream()) {
                var writeParam = writer.getDefaultWriteParam();
                if (writeParam.canWriteCompressed()) {
                    writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    writeParam.setCompressionQuality(PREVIEW_IMAGE_QUALITY);
                }
                writer.setOutput(ImageIO.createImageOutputStream(outputStream));
                writer.write(null, new IIOImage(outputImage, null, null), writeParam);
                return new PreviewImage(outputStream.toByteArray(), scaledWidth, scaledHeight);
            } finally {
                writer.dispose();
            }
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

    private record PreviewImage(byte[] data, int width, int height) {
    }
}
