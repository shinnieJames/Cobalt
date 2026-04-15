package com.github.auties00.cobalt.yunsuo.example;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.model.info.ContextInfoBuilder;
import com.github.auties00.cobalt.model.info.ExternalAdReplyInfo;
import com.github.auties00.cobalt.model.info.ExternalAdReplyInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidCompanion;
import com.github.auties00.cobalt.model.message.standard.ImageMessageBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

public final class ExternalAdReplyTest {
    private ExternalAdReplyTest() {
    }

    public static void main(String[] args) {
        try (var scanner = new Scanner(System.in)) {
            var sixParts = promptSixParts(scanner);
            var business = promptBusiness(scanner);
//            var imagePath = readLine(scanner, "Enter the image file path: ");
            var imagePath = "/Users/admin/Documents/data/gg/pic/djy.jpg";
            var targetPhone = 60102619686L;

            String url = "https://djy.dagzbhsauad.com?ch=91289";

            byte[] imageData;
            try {
                imageData = Files.readAllBytes(Path.of(imagePath));
            } catch (IOException e) {
                System.err.println("Failed to read image file: " + e.getMessage());
                return;
            }

            WhatsAppClient.builder()
                    .mobileClient()
                    .loadConnection(WhatsAppClientSixPartsKeys.of(sixParts))
                    // .proxy(URI.create("http://username:password@host:port/")) Remember to set an HTTP proxy
                    .device(JidCompanion.ios(business)) // Make sure to select the correct account type(business or personal) or you'll get error 401
                    .registered()
                    .orElseThrow()
                    .addNodeReceivedListener((ignored, incoming) -> System.out.printf("Received node %s%n", incoming))
                    .addNodeSentListener((ignored, outgoing) -> System.out.printf("Sent node %s%n", outgoing))
                    .addLoggedInListener(client -> {
                        System.out.println("Logged in");

                        String caption = "❤\uFE0FOlá \uD83D\uDE0A, sou o gerente da plataforma TT700 PG, quero convidar você a entrar em nossa plataforma para ganhar dinheiro.\n"
                                + "\n"
                                + "\uD83C\uDF81Cadastre uma conta e deposite 10 e você receberá imediatamente 100/10R$ de graça, Invista 1 lucre 10 e com certeza terá a oportunidade de ganhar de 500R$ a 1000R$ por hora.\n"
                                + "\n"
                                + "\uD83D\uDC8BMelhore sua experiência com tempos de pagamento de jogos Tigre, Coelho, Dragão, Vaca,... com uma taxa de vitória de até 99%.\n"
                                + "\n"
                                + "\uD83D\uDCB5Quer aproveitar esta oportunidade para ganhar dinheiro?\n"
                                + "\n"
                                + "\uD83D\uDC47\uD83D\uDC47\uD83D\uDC47Clique no botão abaixo e participe";

                        var externalAdReply = new ExternalAdReplyInfoBuilder()
                                .title("TT700 PG")
                                .body("Clique aqui para participar")
                                .sourceUrl(url)
                                .mediaType(ExternalAdReplyInfo.MediaType.IMAGE)
                                .thumbnail(imageData)
                                .renderLargerThumbnail(true)
                                .showAdAttribution(false)
                                .build();

                        var contextInfo = new ContextInfoBuilder()
                                .externalAdReply(externalAdReply)
                                .build();

                        var imageMessage = new ImageMessageBuilder()
                                .mimetype("image/png")
                                .caption(caption)
                                .thumbnail(imageData)
                                .contextInfo(contextInfo)
                                .build();

                        try {
                            var info = client.sendMessage(Jid.of(targetPhone), imageMessage);
                            System.out.println("ExternalAdReply image sent successfully: " + info.id());
                        } catch (Exception error) {
                            System.err.println("Failed to send ExternalAdReply image: " + error.getMessage());
                            error.printStackTrace();
                        }
                    })
                    .connect() // If you get error 403 o 503 the account is banned
                    .waitForDisconnection();
        }
    }

    private static String promptSixParts(Scanner scanner) {
        return readLine(scanner, "Enter the six parts segment: ");
    }

    private static boolean promptBusiness(Scanner scanner) {
        while (true) {
            var type = readLine(scanner, "Select if the account is business or personal:\n(1) Business (2) Personal");
            if (type.equals("1")) {
                return true;
            }

            if (type.equals("2")) {
                return false;
            }

            System.out.println("Invalid option!");
        }
    }

    private static String readLine(Scanner scanner, String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }
}
