package com.github.auties00.cobalt.yunsuo.succ;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.model.info.ContextInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidCompanion;
import com.github.auties00.cobalt.model.message.standard.TextMessage;
import com.github.auties00.cobalt.model.message.standard.TextMessageBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标准链接预览消息测试：优先验证会话内可见 + 点击链接可跳浏览器
 * <p>
 * 设计原则：
 * - 参考 Baileys 的常规 extendedTextMessage 链接预览字段布局
 * - 关键预览字段放在 TextMessage 本体：matchedText/title/description/thumbnail
 * - 避免依赖 externalAdReply 作为主要展示层，防止通知可见但会话内不渲染
 * - 文本中保留裸 URL，确保客户端能把这条消息识别成正常的链接预览消息
 */
public class ImageTextCardTest {
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

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
        var imagePath = "/Users/admin/Documents/data/gg/pic/djy.jpg";
        var targetPhone = 60102619686L;
//        var targetPhone = 85254849927L;

        String url = "https://djy.dagzbhsauad.com?ch=91289";

        AtomicBoolean send = new AtomicBoolean(false);
        byte[] imageData = Files.readAllBytes(Path.of(imagePath));

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
                        String text = "❤️Olá 😊, sou o gerente da plataforma TT700 PG, quero convidar você a entrar em nossa plataforma para ganhar dinheiro.\n" +
                                "\n" +
                                "🎁Cadastre uma conta e deposite 10 e você receberá imediatamente 100/10R$ de graça, Invista 1 lucre 10 e com certeza terá a oportunidade de ganhar de 500R$ a 1000R$ por hora.\n" +
                                "\n" +
                                "💋Melhore sua experiência com tempos de pagamento de jogos Tigre, Coelho, Dragão, Vaca,... com uma taxa de vitória de até 99%.\n" +
                                "\n" +
                                "💵Quer aproveitar esta oportunidade para ganhar dinheiro?\n" +
                                "\n" +
                                "👇👇👇Clique no link abaixo e participe\n" + url;

                        Matcher matcher = URL_PATTERN.matcher(text);
                        String matchedText = matcher.find() ? matcher.group() : url;

                        var contextInfo = new ContextInfoBuilder().build();

                        var message = new TextMessageBuilder()
                                .text(text)
                                .matchedText(matchedText)
                                .canonicalUrl(url)
                                .title("TT700 PG")
                                .description("Clique aqui para participar")
                                .thumbnail(imageData)
                                .thumbnailWidth(1200)
                                .thumbnailHeight(630)
                                .contextInfo(contextInfo)
                                .previewType(TextMessage.PreviewType.NONE)
                                .build();

                        try {
                            var info = api.sendMessage(Jid.of(targetPhone), message);
                            System.out.println("Link preview message sent successfully: " + info.id());
                        } catch (Throwable error) {
                            System.err.println("Failed to send link preview message: " + error.getMessage());
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
