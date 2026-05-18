package com.github.auties00.cobalt.yunsuo.test;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidCompanion;
import com.github.auties00.cobalt.model.message.standard.TextMessage;
import com.github.auties00.cobalt.model.message.standard.TextMessageBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 方案：使用 TextMessage 原生链接预览字段（对应 protobuf ExtendedTextMessage）
 * <p>
 * 原理：
 * - text 中包含 URL，matchedText 精确匹配该 URL
 * - canonicalUrl 指向目标链接
 * - title/description/thumbnail 提供预览卡片内容
 * - previewType=VIDEO 渲染大缩略图，previewType=NONE 渲染小缩略图
 * <p>
 * 这与用户手动在 WhatsApp 中发送链接的行为完全一致，
 * WhatsApp 客户端将其视为标准链接预览，iOS/Android 点击均直接跳转浏览器，
 * 不会触发安全提示或外部跳转确认。
 */
//方案不可行
public class NativeLinkPreviewTest {

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

        var imagePath = "/Users/admin/Documents/data/gg/pic/djy.jpg";
        var targetPhone = 60102619686L;
        String url = "https://www.baidu.com";

        AtomicBoolean send = new AtomicBoolean(false);

        byte[] imageData = Files.readAllBytes(Path.of(imagePath));

        WhatsAppClient whatsapp = WhatsAppClient.builder()
                .mobileClient()
                .loadConnection(WhatsAppClientSixPartsKeys.of(sixParts))
                .device(JidCompanion.ios(business))
                .name("yunsuo")
                .registered()
                .orElseThrow();

        whatsapp.addNodeReceivedListener((ignored, incoming) -> System.out.printf("Received node %s%n", incoming))
                .addNodeSentListener((ignored, outgoing) -> System.out.printf("Sent node %s%n", outgoing))
                .addDisconnectedListener((ignored, reason) -> System.out.println("Disconnected: " + reason))
                .addLoggedInListener(api -> {
                    System.out.println("Logged in");
                    if (send.compareAndSet(false, true)) {

                        // 正文文字，URL 放在末尾（matchedText 必须能在 text 中找到）
                        String text = "❤\uFE0FOlá \uD83D\uDE0A, sou o gerente da plataforma TT700 PG, quero convidar você a entrar em nossa plataforma para ganhar dinheiro.\n" +
                                "\n" +
                                "\uD83C\uDF81Cadastre uma conta e deposite 10 e você receberá imediatamente 100/10R$ de graça, Invista 1 lucre 10 e com certeza terá a oportunidade de ganhar de 500R$ a 1000R$ por hora.\n" +
                                "\n" +
                                "\uD83D\uDC8BMelhore sua experiência com tempos de pagamento de jogos Tigre, Coelho, Dragão, Vaca,... com uma taxa de vitória de até 99%.\n" +
                                "\n" +
                                "\uD83D\uDCB5Quer aproveitar esta oportunidade para ganhar dinheiro?\n" +
                                "\n" +
                                "\uD83D\uDC47\uD83D\uDC47\uD83D\uDC47Clique no link abaixo e participe\n" +
                                url;

                        // 使用 TextMessage 原生字段构建标准链接预览
                        // previewType=VIDEO 渲染大缩略图，NONE 渲染小缩略图
                        var message = new TextMessageBuilder()
                                .text(text)
                                .matchedText(url)
                                .canonicalUrl(url)
                                .title("TT700 PG - Plataforma Oficial")
                                .description("Clique aqui para participar e ganhar dinheiro")
                                .thumbnail(imageData)
                                .previewType(TextMessage.PreviewType.VIDEO)
                                .build();

                        try {
                            var info = api.sendMessage(Jid.of(targetPhone), message);
                            System.out.println("NativeLinkPreview sent successfully: " + info.id());
                        } catch (Throwable error) {
                            System.err.println("Failed to send NativeLinkPreview: " + error.getMessage());
                            error.printStackTrace();
                        }
                    }
                })
                .connect()
                .waitForDisconnection();
    }
}
