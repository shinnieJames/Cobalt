package com.github.auties00.cobalt.yunsuo.test;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.model.button.template.hydrated.HydratedFourRowTemplateSimpleBuilder;
import com.github.auties00.cobalt.model.button.template.hydrated.HydratedFourRowTemplateTextTitle;
import com.github.auties00.cobalt.model.button.template.hydrated.HydratedTemplateButton;
import com.github.auties00.cobalt.model.button.template.hydrated.HydratedURLButtonBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidCompanion;
import com.github.auties00.cobalt.model.message.button.TemplateMessageSimpleBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * B 方案：参考 Baileys 常见 CTA 思路的模板 URL 按钮消息。
 * <p>
 * 设计原则：
 * - 使用强类型 HydratedURLButton 生成真正的 URL 按钮
 * - 正文中同时保留 raw URL，作为客户端兼容性兜底
 * - 继续沿用 ImageTextCardTest 的营销文案内容
 */
public class BaileysTemplateUrlButtonMarketingTest {

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
        String url = "https://djy.dagzbhsauad.com?ch=91289";

        AtomicBoolean send = new AtomicBoolean(false);

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
                        String bodyText = "❤️Olá 😊, sou o gerente da plataforma TT700 PG, quero convidar você a entrar em nossa plataforma para ganhar dinheiro.\n"
                                + "\n"
                                + "🎁Cadastre uma conta e deposite 10 e você receberá imediatamente 100/10R$ de graça.\n"
                                + "\n"
                                + "💵Quer aproveitar esta oportunidade para ganhar dinheiro?\n"
                                + "\n"
                                + "Site oficial:\n"
                                + url;

                        var urlButton = new HydratedURLButtonBuilder()
                                .text("👇 Clique aqui para participar")
                                .url(url)
                                .build();

                        var templateButton = HydratedTemplateButton.of(urlButton);

                        var template = new HydratedFourRowTemplateSimpleBuilder()
                                .title(HydratedFourRowTemplateTextTitle.of("TT700 PG - Plataforma Oficial"))
                                .body(bodyText)
                                .footer("Link alternativo disponível no corpo da mensagem")
                                .buttons(List.of(templateButton))
                                .build();

                        var message = new TemplateMessageSimpleBuilder()
                                .content(template)
                                .build();

                        try {
                            var info = api.sendMessage(Jid.of(targetPhone), message);
                            System.out.println("BaileysTemplateUrlButtonMarketingTest sent successfully: " + info.id());
                        } catch (Throwable error) {
                            System.err.println("Failed to send BaileysTemplateUrlButtonMarketingTest: " + error.getMessage());
                            error.printStackTrace();
                        }
                    }
                })
                .connect()
                .waitForDisconnection();
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
