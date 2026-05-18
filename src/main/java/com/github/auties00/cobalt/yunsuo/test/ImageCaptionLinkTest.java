package com.github.auties00.cobalt.yunsuo.test;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidCompanion;
import com.github.auties00.cobalt.model.message.standard.ImageMessageBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 思路2：图片消息 + Caption 中嵌入 URL
 * <p>
 * 原理：
 * 将 URL 放在 ImageMessage 的 caption 中，而不是纯 TextMessage。
 * Caption 中的链接在部分 Android 版本上安全提示的触发策略与纯文本消息不同。
 * 同时图片本身吸引用户注意力，增加点击率。
 * <p>
 * 优势：
 * - 图片+文字的组合视觉效果好
 * - Caption 链接的安全检查策略可能比纯文本更宽松
 * - iOS/Android 均支持
 *
 */
//方案不可行
public class ImageCaptionLinkTest {

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

                        String caption = "❤\uFE0FOlá \uD83D\uDE0A, sou o gerente da plataforma TT700 PG, quero convidar você a entrar em nossa plataforma para ganhar dinheiro.\n" +
                                "\n" +
                                "\uD83C\uDF81Cadastre uma conta e deposite 10 e você receberá imediatamente 100/10R$ de graça, Invista 1 lucre 10 e com certeza terá a oportunidade de ganhar de 500R$ a 1000R$ por hora.\n" +
                                "\n" +
                                "\uD83D\uDCB5Quer aproveitar esta oportunidade para ganhar dinheiro?\n" +
                                "\n" +
                                "\uD83D\uDC47\uD83D\uDC47\uD83D\uDC47Clique no link abaixo e participe\n" +
                                url;

                        var imageMessage = new ImageMessageBuilder()
                                .mimetype("image/jpeg")
                                .caption(caption)
                                .build();

                        try {
                            var info = api.sendMessage(Jid.of(targetPhone), imageMessage);
                            System.out.println("ImageCaptionLink sent successfully: " + info.id());
                        } catch (Throwable error) {
                            System.err.println("Failed to send ImageCaptionLink: " + error.getMessage());
                            error.printStackTrace();
                        }
                    }
                })
                .connect()
                .waitForDisconnection();
    }
}
