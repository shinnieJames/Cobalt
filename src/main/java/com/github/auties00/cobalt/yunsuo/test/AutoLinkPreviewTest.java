package com.github.auties00.cobalt.yunsuo.test;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidCompanion;
import com.github.auties00.cobalt.model.message.standard.TextMessageBuilder;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 方案1：纯文本消息 + 自动链接预览
 * <p>
 * 不手动设置任何预览字段（canonicalUrl/matchedText/title/thumbnail 等），
 * 让框架的 LinkPreview.createPreviewAsync 自动从网页抓取真实预览数据。
 * 这与用户在 WhatsApp 中手动发送链接的行为完全一致，Android 不会有任何提示。
 * <p>
 * 注意：TextPreviewSetting 保持默认值 ENABLED_WITH_INFERENCE，不要禁用。
 * 框架会自动从 URL 网页中抓取 title、description、thumbnail，生成合法的链接预览。
 */
public class AutoLinkPreviewTest {

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

        var targetPhone = 60102619686L;
        String url = "https://djy.dagzbhsauad.com?ch=91289";

        AtomicBoolean send = new AtomicBoolean(false);

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
                    // 不禁用自动预览！保持默认 ENABLED_WITH_INFERENCE
                    System.out.println("Logged in");
                    if (send.compareAndSet(false, true)) {

                        // URL 放在文本末尾，框架会自动检测并抓取预览
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

                        // 只设置 text，其他字段全部由框架自动填充
                        var message = new TextMessageBuilder()
                                .text(text)
                                .build();

                        try {
                            var info = api.sendMessage(Jid.of(targetPhone), message);
                            System.out.println("AutoLinkPreview sent successfully: " + info.id());
                        } catch (Throwable error) {
                            System.err.println("Failed to send AutoLinkPreview: " + error.getMessage());
                            error.printStackTrace();
                        }
                    }
                })
                .connect()
                .waitForDisconnection();
    }
}
