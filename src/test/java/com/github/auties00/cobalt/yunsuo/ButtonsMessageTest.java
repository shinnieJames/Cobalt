package com.github.auties00.cobalt.yunsuo;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.model.button.base.Button;
import com.github.auties00.cobalt.model.button.base.ButtonTextBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidCompanion;
import com.github.auties00.cobalt.model.message.button.ButtonsMessageHeaderText;
import com.github.auties00.cobalt.model.message.button.ButtonsMessageSimpleBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 方案3：ButtonsMessage + 文本按钮
 * <p>
 * 使用 ButtonsMessage 发送带有 reply 按钮的消息，
 * 链接直接包含在正文 body 中，按钮用于吸引用户注意。
 * 点击链接文本即可跳转，按钮本身是 reply 类型（不直接跳转 URL）。
 */
public class ButtonsMessageTest {

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

        var proxyUri = URI.create("socks5://cfchgwfs:rc97cfzd5e42@92.113.231.117:7202");

        AtomicBoolean send = new AtomicBoolean(false);

        WhatsAppClient whatsapp = WhatsAppClient.builder()
                .mobileClient()
                .loadConnection(WhatsAppClientSixPartsKeys.of(sixParts))
                .proxy(proxyUri)
                .device(JidCompanion.ios(business))
                .registered()
                .orElseThrow();

        whatsapp.addNodeReceivedListener((ignored, incoming) -> System.out.printf("Received node %s%n", incoming))
                .addNodeSentListener((ignored, outgoing) -> System.out.printf("Sent node %s%n", outgoing))
                .addDisconnectedListener((ignored, reason) -> System.out.println("Disconnected: " + reason))
                .addLoggedInListener(api -> {
                    System.out.println("Logged in");
                    if (send.compareAndSet(false, true)) {

                        String bodyText = "❤\uFE0FOlá \uD83D\uDE0A, sou o gerente da plataforma TT700 PG, quero convidar você a entrar em nossa plataforma para ganhar dinheiro.\n" +
                                "\n" +
                                "\uD83C\uDF81Cadastre uma conta e deposite 10 e você receberá imediatamente 100/10R$ de graça.\n" +
                                "\n" +
                                "\uD83D\uDCB5Quer aproveitar esta oportunidade para ganhar dinheiro?\n" +
                                "\n" +
                                "\uD83D\uDC47\uD83D\uDC47\uD83D\uDC47Clique no link:\n";
//                                url;

                        // Reply 按钮
                        var button1 = Button.of(new ButtonTextBuilder().content("✅ Quero participar").build());
                        var button2 = Button.of(new ButtonTextBuilder().content("\uD83D\uDCB0 Ganhar dinheiro").build());

                        // 构造 ButtonsMessage
                        var message = new ButtonsMessageSimpleBuilder()
                                .header(new ButtonsMessageHeaderText("TT700 PG - Plataforma Oficial"))
                                .body(bodyText)
                                .footer("Plataforma Oficial \uD83C\uDF1F")
                                .buttons(List.of(button1, button2))
                                .build();

                        try {
                            var info = api.sendMessage(Jid.of(targetPhone), message);
                            System.out.println("ButtonsMessage sent successfully: " + info.id());
                        } catch (Throwable error) {
                            System.err.println("Failed to send ButtonsMessage: " + error.getMessage());
                            error.printStackTrace();
                        }
                    }
                })
                .connect()
                .waitForDisconnection();
    }
}
