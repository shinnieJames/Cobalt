package com.github.auties00.cobalt.yunsuo;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.model.button.template.hydrated.HydratedFourRowTemplateSimpleBuilder;
import com.github.auties00.cobalt.model.button.template.hydrated.HydratedTemplateButton;
import com.github.auties00.cobalt.model.button.template.hydrated.HydratedURLButtonBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidCompanion;
import com.github.auties00.cobalt.model.message.button.TemplateMessage;
import com.github.auties00.cobalt.model.message.button.TemplateMessageSimpleBuilder;
import com.github.auties00.cobalt.model.message.model.MessageContainer;
import com.github.auties00.cobalt.model.message.standard.ImageMessage;
import com.github.auties00.cobalt.model.message.standard.ImageMessageBuilder;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.List;

public class MobileLoginTest {
    private static final String IPHONE_16_MODEL = "iPhone 16";
    private static final String IPHONE_16_OS_VERSION = "18.3.2";
    private static final String IPHONE_16_OS_BUILD = "22D82";
    private static final String IPHONE_16_MODEL_ID = "iPhone17,3";

    public static void main(String[] args) {
        var sixParts = promptSixParts();
        var business = promptBusiness();
        var recipient = Jid.of(60102619686L);
        var messageType = promptMessageType();
        var headerImageUrl = "https://picsum.photos/900/500.jpg";
        var bodyText = "this is text";
        var buttonText = "this is a button🚀";
        var buttonLinkUrl = "https://www.baidu.com/";
        WhatsAppClient.builder()
                .mobileClient()
                .loadConnection(WhatsAppClientSixPartsKeys.of(sixParts))
                // .proxy(URI.create("http://username:password@host:port/")) Remember to set an HTTP proxy
                .device(createIphone16(business))
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
                    MessageContainer message;
                    if (messageType == 1) {
                        var templateMessage = createUploadedImageUrlTemplateMessage(
                                client,
                                headerImageUrl,
                                bodyText,
                                buttonText,
                                buttonLinkUrl
                        );
                        message = MessageContainer.of(templateMessage);
                    } else {
                        message = MessageContainer.of("hi");
                    }
                    var sentInfo = client.sendChatMessage(recipient, message, false);
                    System.out.printf("Sent message: id=%s, status=%s%n", sentInfo.id(), sentInfo.status());

                    Thread.startVirtualThread(() -> {
                        try {
                            Thread.sleep(20000);
                            client.disconnect();
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                        }
                    });
                })
                .connect()
                .waitForDisconnection();
    }

    private static JidCompanion createIphone16(boolean business) {
        return JidCompanion.ios(
                IPHONE_16_MODEL,
                IPHONE_16_OS_VERSION,
                IPHONE_16_OS_BUILD,
                IPHONE_16_MODEL_ID,
                business
        );
    }

    private static TemplateMessage createUploadedImageUrlTemplateMessage(
            WhatsAppClient client,
            String headerImageUrl,
            String bodyText,
            String buttonText,
            String buttonLinkUrl
    ) {
        var imageBytes = downloadImage(headerImageUrl);
        var titleImage = new ImageMessageBuilder()
                .mimetype("image/jpeg")
                .caption(bodyText)
                .thumbnail(imageBytes)
                .build();
        uploadTitleImage(client, titleImage, imageBytes);

        var urlButton = new HydratedURLButtonBuilder()
                .text(buttonText)
                .url(buttonLinkUrl)
                .build();

        var template = new HydratedFourRowTemplateSimpleBuilder()
                .title(titleImage)
                .body(bodyText)
                .buttons(List.of(HydratedTemplateButton.of(urlButton)))
                .build();

        return new TemplateMessageSimpleBuilder()
                .content(template)
                .format(template)
                .build();
    }

    private static void uploadTitleImage(WhatsAppClient client, ImageMessage titleImage, byte[] imageBytes) {
        try (var inputStream = new ByteArrayInputStream(imageBytes)) {
            var mediaConnection = client.store()
                    .waitForMediaConnection();
            var uploaded = mediaConnection.upload(titleImage, inputStream);
            if (!uploaded) {
                throw new IllegalStateException("Image upload was rejected because the message does not expose an uploadable media path");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot upload template image to WhatsApp media servers", exception);
        }
    }

    private static byte[] downloadImage(String headerImageUrl) {
        try {
            return URI.create(headerImageUrl)
                    .toURL()
                    .openStream()
                    .readAllBytes();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot download header image from " + headerImageUrl, exception);
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

    private static int promptMessageType() {
        while (true) {
            var type = IO.readln("Select message type:\n(1) Image+Button template (2) Text message \"hi\"")
                    .trim();
            if (type.equals("1")) {
                return 1;
            } else if (type.equals("2")) {
                return 2;
            } else {
                IO.println("Invalid option!");
            }
        }
    }

    private static Jid promptRecipient() {
        return Jid.of(IO.readln("Enter the recipient phone number(with country code): ")
                .trim());
    }

    private static String promptHeaderImageUrl() {
        return IO.readln("Enter the image url: ")
                .trim();
    }

    private static String promptBodyText() {
        return IO.readln("Enter the message text: ")
                .trim();
    }

    private static String promptButtonText() {
        return IO.readln("Enter the button text: ")
                .trim();
    }

    private static String promptButtonLinkUrl() {
        return IO.readln("Enter the button target url: ")
                .trim();
    }
}
