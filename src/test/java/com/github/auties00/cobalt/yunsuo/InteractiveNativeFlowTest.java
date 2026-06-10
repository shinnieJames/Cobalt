package com.github.auties00.cobalt.yunsuo;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.model.button.interactive.*;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidCompanion;
import com.github.auties00.cobalt.model.message.button.InteractiveMessageSimpleBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * InteractiveMessage + NativeFlow CTA URL 按钮测试
 * <p>
 * 原理：
 * 使用 InteractiveMessage 的 NativeFlow 内容类型，通过 cta_url 按钮实现点击跳转。
 * 这是 WhatsApp Business API 新一代交互式消息格式，替代已废弃的 ButtonsMessage。
 * <p>
 * NativeFlowButton:
 * - name="cta_url" 表示 CTA (Call To Action) URL 按钮
 * - parameters 为按钮级 JSON，包含 display_text 和 url，对应 Baileys/buttonParamsJson 语义
 * <p>
 * 注意：此方案主要面向 Business API (BSP) 账号，非官方账号发送的渲染效果可能不稳定。
 */
public class InteractiveNativeFlowTest {

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

                        // CTA URL 按钮：name=cta_url，parameters 为 JSON
                        var ctaButton = new InteractiveButtonBuilder()
                                .name("cta_url")
                                .parameters("{\"display_text\":\"\\uD83D\\uDC47 Clique aqui para participar\",\"url\":\"" + url + "\"}")
                                .build();

                        // NativeFlow 内容：按钮级参数保留在 cta_url buttonParamsJson，
                        // 未使用的消息级 messageParamsJson 不发送，避免和 Baileys 空值语义漂移。
                        var nativeFlow = new InteractiveNativeFlowBuilder()
                                .buttons(List.of(ctaButton))
                                .version(1)
                                .build();

                        // Header
                        var header = new InteractiveHeaderSimpleBuilder()
                                .title("TT700 PG - Plataforma Oficial")
                                .build();

                        // 构造 InteractiveMessage
                        var message = new InteractiveMessageSimpleBuilder()
                                .header(header)
                                .body(bodyText)
                                .footer("Plataforma Oficial \uD83C\uDF1F")
                                .content(nativeFlow)
                                .build();

                        try {
                            var info = api.sendMessage(Jid.of(targetPhone), message);
                            System.out.println("InteractiveNativeFlow sent successfully: " + info.id());
                        } catch (Throwable error) {
                            System.err.println("Failed to send InteractiveNativeFlow: " + error.getMessage());
                            error.printStackTrace();
                        }
                    }
                })
                .connect()
                .waitForDisconnection();
    }
}
