package com.github.auties00.cobalt.yunsuo.sender;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;

/**
 * 图文卡片消息发送器：大图 + 下方正文文字，点击大图跳转 URL，无安全提示。
 */
public class ImageTextCardSender {

    private static final long TIMEOUT_SECONDS = 55;

    public static void main(String[] args) {
        if (args == null || args.length < 9) {
            System.err.println("Usage: java ImageTextCardSender <sender> <recipient> <text> <picPath> <url> <title> <body> <isIos> <isBusiness>");
            System.exit(SendResult.SEND_FAILED.code());
        }

        String sender = args[0];
        Long recipient = Long.parseLong(args[1]);
        String text = args[2];
        String picPath = args[3];
        String url = args[4];
        String title = args[5];
        String body = args[6];
        boolean ios = Boolean.parseBoolean(args[7]);
        boolean business = Boolean.parseBoolean(args[8]);

        SendResult result;
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<SendResult> future = executor.submit(() -> {
                try {
                    return sendMsg(sender, recipient, text, picPath, url, title, body, ios, business);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            try {
                result = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                System.err.println("ImageTextCardSender timeout after " + TIMEOUT_SECONDS + " seconds, exiting.");
                future.cancel(true);
                result = SendResult.TIMEOUT;
            } catch (ExecutionException e) {
                e.printStackTrace();
                result = SendResult.SEND_FAILED;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                result = SendResult.SEND_FAILED;
            }
        }

        System.out.println("Result: " + result + " (code=" + result.code() + ")");
    }

    private static SendResult sendMsg(String sender, Long recipient, String text, String picPath, String url,
                                      String title, String body, boolean ios, boolean business) throws IOException {
        System.out.println("Sender: " + sender);
        System.out.println("Recipient: " + recipient);
        System.out.println("Url: " + url);

        byte[] imageData = Files.readAllBytes(Path.of(picPath));

        WhatsAppClient whatsapp = WhatsAppClient.builder()
                .mobileClient()
                .loadConnection(WhatsAppClientSixPartsKeys.of(sender))
                .device(ios ? JidCompanion.ios(business) : JidCompanion.android(business))
                .name("yunsuo")
                .registered()
                .orElseThrow();

        return SenderSupport.run(whatsapp, callback -> {
            var externalAdReply = new ExternalAdReplyInfoBuilder()
                    .title(title)
                    .body(body)
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

            var message = new TextMessageBuilder()
                    .text(text)
                    .matchedText("")
                    .contextInfo(contextInfo)
                    .previewType(TextMessage.PreviewType.NONE)
                    .build();

            try {
                var info = whatsapp.sendMessage(Jid.of(recipient), message);
                callback.success(info.id());
            } catch (Throwable error) {
                callback.failed(error);
            }
        });
    }
}
