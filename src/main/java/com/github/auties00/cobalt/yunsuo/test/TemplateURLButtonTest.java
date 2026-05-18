package com.github.auties00.cobalt.yunsuo.test;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.model.button.template.hydrated.*;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidCompanion;
import com.github.auties00.cobalt.model.message.button.TemplateMessageSimpleBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 方案2：TemplateMessage + HydratedURLButton
 * <p>
 * 使用 WhatsApp Business 官方支持的模板消息类型，
 * 通过 HydratedFourRowTemplate 构造带有 URL 按钮的消息。
 * 用户点击按钮直接跳转浏览器，这是 Business API 标准的 CTA 按钮方式。
 */
public class TemplateURLButtonTest {

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
                    System.out.println("Logged in");
                    if (send.compareAndSet(false, true)) {

                        String bodyText = "❤\uFE0FOlá \uD83D\uDE0A, sou o gerente da plataforma TT700 PG, quero convidar você a entrar em nossa plataforma para ganhar dinheiro.\n" +
                                "\n" +
                                "\uD83C\uDF81Cadastre uma conta e deposite 10 e você receberá imediatamente 100/10R$ de graça.\n" +
                                "\n" +
                                "\uD83D\uDCB5Quer aproveitar esta oportunidade para ganhar dinheiro?";

                        // URL 按钮：点击直接跳转浏览器
                        var urlButton = new HydratedURLButtonBuilder()
                                .text("\uD83D\uDC47 Clique aqui para participar")
                                .url(url)
                                .build();

                        var templateButton = HydratedTemplateButton.of(urlButton);

                        // 构造 HydratedFourRowTemplate
                        var template = new HydratedFourRowTemplateSimpleBuilder()
                                .title(HydratedFourRowTemplateTextTitle.of("TT700 PG - Plataforma Oficial"))
                                .body(bodyText)
                                .footer("Plataforma Oficial \uD83C\uDF1F")
                                .buttons(List.of(templateButton))
                                .build();

                        // 构造 TemplateMessage
                        var message = new TemplateMessageSimpleBuilder()
                                .content(template)
                                .format(template)
                                .build();

                        try {
                            var info = api.sendMessage(Jid.of(targetPhone), message);
                            System.out.println("TemplateURLButton sent successfully: " + info.id());
                        } catch (Throwable error) {
                            System.err.println("Failed to send TemplateURLButton: " + error.getMessage());
                            error.printStackTrace();
                        }
                    }
                })
                .connect()
                .waitForDisconnection();
    }
}
