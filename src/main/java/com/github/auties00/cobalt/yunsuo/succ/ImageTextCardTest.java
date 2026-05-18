package com.github.auties00.cobalt.yunsuo.succ;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.model.info.ContextInfoBuilder;
import com.github.auties00.cobalt.model.info.ExternalAdReplyInfo;
import com.github.auties00.cobalt.model.info.ExternalAdReplyInfoBuilder;
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

/**
 * 图文卡片消息测试：模拟图文消息效果，无安全提示
 * <p>
 * 实现思路：
 * 使用 TextMessage + ExternalAdReplyInfo 组合：
 * - ExternalAdReplyInfo 的 thumbnail + renderLargerThumbnail=true 渲染为顶部大图
 * - TextMessage 的 text 渲染为图片下方的正文文字（不含裸 URL，不触发安全提示）
 * - 链接通过 ExternalAdReplyInfo 的 sourceUrl + mediaUrl 嵌入，点击图片区域直接跳转
 * - matchedText 设为空字符串，使 WhatsApp 无法在 text 中定位链接，彻底消除安全提示
 * - 注意：canonicalUrl 在最新 WhatsApp 协议中已移除（参考 whatsmeow 项目），不再使用
 * <p>
 * 渲染效果：大图(上，可点击跳转链接) + 文字(下)
 * iOS:     点击大图 → sourceUrl → Safari 打开     ✅ 无安全提示
 * Android: 点击大图 → sourceUrl/mediaUrl → 浏览器打开   ✅ 无安全提示
 */
public class ImageTextCardTest {

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

        var proxyUri = URI.create("socks5://3221:3221@s10.sgp6.dns.2jj.net:50488");
        var imagePath = "/Users/admin/Documents/data/gg/pic/djy.jpg";
        var targetPhone = 60102619686L;

        String url = "https://djy.dagzbhsauad.com?ch=91289";
//        String url = "https://www.baidu.com";

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
                .addNodeSentListener((ignored, outgoing) -> System.out.printf("Sent node %s%n", outgoing))
                .addDisconnectedListener((ignored, reason) -> System.out.println("Disconnected: " + reason))
                .addLoggedInListener(api -> {
                    System.out.println("Logged in");
                    if (send.compareAndSet(false, true)) {

                        // === 图片下方的正文文字（不含裸 URL，不触发安全提示） ===
                        String text = "❤\uFE0FOlá \uD83D\uDE0A, sou o gerente da plataforma TT700 PG, quero convidar você a entrar em nossa plataforma para ganhar dinheiro.\n" +
                                "\n" +
                                "\uD83C\uDF81Cadastre uma conta e deposite 10 e você receberá imediatamente 100/10R$ de graça, Invista 1 lucre 10 e com certeza terá a oportunidade de ganhar de 500R$ a 1000R$ por hora.\n" +
                                "\n" +
                                "\uD83D\uDC8BMelhore sua experiência com tempos de pagamento de jogos Tigre, Coelho, Dragão, Vaca,... com uma taxa de vitória de até 99%.\n" +
                                "\n" +
                                "\uD83D\uDCB5Quer aproveitar esta oportunidade para ganhar dinheiro?\n" +
                                "\n" +
                                "\uD83D\uDC47\uD83D\uDC47\uD83D\uDC47Clique na imagem acima e participe";

                        // === 顶部大图 + 链接（通过 ExternalAdReplyInfo 渲染，iOS/Android 均走 sourceUrl 跳转） ===
                        var externalAdReply = new ExternalAdReplyInfoBuilder()
                                .title("TT700 PG")
                                .body("Clique aqui para participar")
                                .sourceUrl(url)
                                .mediaUrl(url)
                                .thumbnailUrl(url)
                                .mediaType(ExternalAdReplyInfo.MediaType.IMAGE)
                                .thumbnail(imageData)
                                .renderLargerThumbnail(true)
                                .showAdAttribution(false)
                                .build();

                        var contextInfo = new ContextInfoBuilder()
                                .externalAdReply(externalAdReply)
                                .build();

                        // === 构建图文消息：大图卡片(上，可点击) + 正文文字(下) ===
                        // 不设置 canonicalUrl（最新协议已移除该字段，Android 不再识别）
                        // 跳转完全依赖 ExternalAdReplyInfo 的 sourceUrl/mediaUrl
                        // textPreviewSetting=DISABLED：阻止框架用网络抓取结果覆盖手动设置的值
                        var message = new TextMessageBuilder()
                                .text(text)
                                .matchedText("")
                                .contextInfo(contextInfo)
                                .previewType(TextMessage.PreviewType.NONE)
                                .build();

                        try {
                            var info = api.sendMessage(Jid.of(targetPhone), message);
                            System.out.println("Image-text card sent successfully: " + info.id());
                        } catch (Throwable error) {
                            System.err.println("Failed to send image-text card: " + error.getMessage());
                            error.printStackTrace();
                        }
                    }
                })
                .connect()
                .waitForDisconnection();
    }
}
