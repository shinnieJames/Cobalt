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
//        var proxyUri = URI.create("socks5://3221:3221@s10.sgp6.dns.2jj.net:50488");
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
                        System.out.println("Image-text card sent successfully: " + info.id());
                    } catch (Throwable error) {
                        System.err.println("Failed to send image-text card: " + error.getMessage());
                        error.printStackTrace();
                    }
                })
                .connect()
                .waitForDisconnection();
    }
}
