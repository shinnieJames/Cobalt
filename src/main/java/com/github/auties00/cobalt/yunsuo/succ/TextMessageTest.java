package com.github.auties00.cobalt.yunsuo.succ;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidCompanion;

import java.io.IOException;
import java.net.URI;
import java.util.Scanner;

public class TextMessageTest {

    public static void main(String[] args) throws IOException {
        // 首次会话会触发 pre-key message；mobile primary 用本地自签 ADV identity（对齐 whalibmob 做法）
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
        var targetPhone = 60102619686L;

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
                    try {
                        var info = api.sendMessage(Jid.of(targetPhone), "hi");
                        System.out.println("Text message sent successfully: " + info.id());
                    } catch (Throwable error) {
                        System.err.println("Failed to send Text message: " + error.getMessage());
                        error.printStackTrace();
                    }
                })
                .connect()
                .waitForDisconnection();
    }
}
