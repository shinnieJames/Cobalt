package com.github.auties00.cobalt.yunsuo;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.model.contact.ContactCard;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidCompanion;
import com.github.auties00.cobalt.model.message.standard.ContactMessageBuilder;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 思路1：vCard 联系人卡片嵌入 URL
 * <p>
 * 原理：
 * 将目标 URL 嵌入 vCard 的 URL 字段中，以联系人卡片形式发送。
 * 用户点击联系人卡片 → 查看联系人详情 → 点击网址字段 → 浏览器直接打开。
 * <p>
 * 优势：
 * - URL 属于"联系人信息"，不走消息文本链接的安全检查
 * - iOS/Android 均支持 vCard URL 字段
 * - 不依赖 WhatsApp 链接预览机制
 */
//方案不可行
public class VCardLinkTest {

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
        String url = "https://www.baidu.com";

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

                        // 手动构造含 URL 字段的 vCard 字符串
                        // 使用 ofRaw 绕过 ezvcard 解析，保留 URL/ORG/NOTE 等字段
                        String vcardStr = "BEGIN:VCARD\r\n" +
                                "VERSION:3.0\r\n" +
                                "FN:TT700 PG - Plataforma Oficial\r\n" +
                                "N:TT700 PG;;;;\r\n" +
                                "ORG:TT700 PG\r\n" +
                                "TEL;type=CELL;type=VOICE;waid=5511999999999:+55 11 99999-9999\r\n" +
                                "URL:" + url + "\r\n" +
                                "NOTE:Clique no link do site para participar e ganhar dinheiro!\r\n" +
                                "END:VCARD";

                        var contactCard = ContactCard.of(vcardStr);

                        var message = new ContactMessageBuilder()
                                .name("TT700 PG - Plataforma Oficial")
                                .vcard(contactCard)
                                .build();

                        try {
                            var info = api.sendMessage(Jid.of(targetPhone), message);
                            System.out.println("VCardLink sent successfully: " + info.id());
                        } catch (Throwable error) {
                            System.err.println("Failed to send VCardLink: " + error.getMessage());
                            error.printStackTrace();
                        }
                    }
                })
                .connect()
                .waitForDisconnection();
    }
}
