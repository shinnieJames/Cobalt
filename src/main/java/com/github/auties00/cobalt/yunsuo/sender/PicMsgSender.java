package com.github.auties00.cobalt.yunsuo.sender;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidCompanion;
import com.github.auties00.cobalt.model.message.standard.ImageMessageBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;

public class PicMsgSender {

    private static final long TIMEOUT_SECONDS = 55;

    public static void main(String[] args) {
        if (args == null || args.length < 6) {
            System.err.println("Usage: java PicMsgSender <sender> <recipient> <content> <picPath> <isIos> <isBusiness>");
            System.exit(SendResult.SEND_FAILED.code());
        }

        String sender = args[0];
        Long recipient = Long.parseLong(args[1]);
        String content = args[2];
        String picPath = args[3];
        boolean ios = Boolean.parseBoolean(args[4]);
        boolean business = Boolean.parseBoolean(args[5]);

        SendResult result;
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<SendResult> future = executor.submit(() -> {
                try {
                    return sendMsg(sender, recipient, content, picPath, ios, business);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            try {
                result = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                System.err.println("PicMsgSender timeout after " + TIMEOUT_SECONDS + " seconds, exiting.");
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

    private static SendResult sendMsg(String sender, Long recipient, String content, String picPath, boolean ios, boolean business) throws IOException {
        System.out.println("Sender: " + sender);
        System.out.println("Recipient: " + recipient);
        System.out.println("Content: " + content);

        byte[] imageData = Files.readAllBytes(Path.of(picPath));

        WhatsAppClient whatsapp = WhatsAppClient.builder()
                .mobileClient()
                .loadConnection(WhatsAppClientSixPartsKeys.of(sender))
                .device(ios ? JidCompanion.ios(business) : JidCompanion.android(business))
                .name("yunsuo")
                .registered()
                .orElseThrow();

        return SenderSupport.run(whatsapp, callback -> {
            var imageMessage = new ImageMessageBuilder()
                    .mimetype("image/png")
                    .caption(content)
                    .build();
            try {
                var info = whatsapp.sendMessage(Jid.of(recipient), imageMessage);
                callback.success(info.id());
            } catch (Throwable error) {
                callback.failed(error);
            }
        });
    }
}
