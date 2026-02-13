package com.github.auties00.cobalt.yunsuo;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.model.button.base.Button;
import com.github.auties00.cobalt.model.info.NativeFlowInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidCompanion;
import com.github.auties00.cobalt.model.message.model.MessageContainer;
import com.github.auties00.cobalt.model.message.button.ButtonsMessage;
import com.github.auties00.cobalt.model.message.button.ButtonsMessageSimpleBuilder;
import com.github.auties00.cobalt.model.message.standard.ImageMessageBuilder;

import java.net.URI;
import java.util.List;

public class MobileLoginTest {
    private static final Jid RECIPIENT = Jid.of(60102619686L);
    private static final String HEADER_IMAGE_URL = "https://picsum.photos/900/500.jpg";
    private static final String BUTTON_LINK_URL = "https://www.baidu.com/";
    private static final String BODY_TEXT = "this is text";
    private static final String BUTTON_TEXT = "this is a button";

    static void main() {
        var sixParts = promptSixParts();
        var business = promptBusiness();
        WhatsAppClient.builder()
                .mobileClient()
                .loadConnection(WhatsAppClientSixPartsKeys.of(sixParts))
                // .proxy(URI.create("http://username:password@host:port/")) Remember to set an HTTP proxy
                .device(JidCompanion.ios(business)) // Make sure to select the correct account type(business or personal) or you'll get error 401
                .registered()
                .orElseThrow()
                .addNodeReceivedListener((_, incoming) -> System.out.printf("Received node %s%n", incoming))
                .addNodeSentListener((_, outgoing) -> System.out.printf("Sent node %s%n", outgoing))
                .addMessageStatusListener((_, info) -> System.out.printf(
                        "Status update: id=%s, status=%s%n",
                        info.id(),
                        info.status()
                ))
                .addLoggedInListener(client -> {
                    System.out.println("Logged in");
                    var textInfo = client.sendChatMessage(RECIPIENT, MessageContainer.of("hi"), false);
                    System.out.printf("Sent text message: id=%s, status=%s%n", textInfo.id(), textInfo.status());

                    var buttonsInfo = client.sendChatMessage(RECIPIENT, MessageContainer.of(createThreePartButtonsMessage()), false);
                    System.out.printf("Sent buttons message: id=%s, status=%s%n", buttonsInfo.id(), buttonsInfo.status());

                    Thread.startVirtualThread(() -> {
                        try {
                            Thread.sleep(20000);
                            client.disconnect();
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                        }
                    });
                })
                .connect() // If you get error 403 o 503 the account is banned
                .waitForDisconnection();
    }

    private static ButtonsMessage createThreePartButtonsMessage() {
        var header = new ImageMessageBuilder()
                .mimetype("image/jpeg")
                .thumbnail(downloadHeaderImage())
                .build();
        var button = Button.of(new NativeFlowInfoBuilder()
                .name("cta_url")
                .parameters("{\"display_text\":\"" + BUTTON_TEXT + "\",\"url\":\"" + BUTTON_LINK_URL + "\"}")
                .build());
        return new ButtonsMessageSimpleBuilder()
                .header(header)
                .body(BODY_TEXT)
                .buttons(List.of(button))
                .build();
    }

    private static byte[] downloadHeaderImage() {
        try {
            return URI.create(HEADER_IMAGE_URL)
                    .toURL()
                    .openStream()
                    .readAllBytes();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot download header image from " + HEADER_IMAGE_URL, exception);
        }
    }

    private static String promptSixParts() {
        return IO.readln("Enter the six parts segment: ")
                .trim();
    }

    private static boolean promptBusiness() {
        while (true) {
            var type = IO.readln("Select if the account is business or personal:\n(1) Business (2) Personal")
                    .trim();
            if (type.equals("1")) {
                return true;
            } else if (type.equals("2")) {
                return false;
            } else {
                IO.println("Invalid option!");
            }
        }
    }

}
