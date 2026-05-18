package com.github.auties00.cobalt.yunsuo.sender;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidCompanion;

import java.util.concurrent.*;

public class TextMsgSender {

    private static final long TIMEOUT_SECONDS = 55;

    public static void main(String[] args) {
        if (args == null || args.length < 5) {
            System.err.println("Usage: java TextMsgSender <sender> <recipient> <content> <isIos> <isBusiness>");
            System.exit(SendResult.SEND_FAILED.code());
        }

        String sender = args[0];
        Long recipient = Long.parseLong(args[1]);
        String content = args[2];
        boolean ios = Boolean.parseBoolean(args[3]);
        boolean business = Boolean.parseBoolean(args[4]);

        SendResult result;
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<SendResult> future = executor.submit(() -> sendMsg(sender, recipient, content, ios, business));
            try {
                result = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                System.err.println("TextMsgSender timeout after " + TIMEOUT_SECONDS + " seconds, exiting.");
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

    private static SendResult sendMsg(String sender, Long recipient, String content, boolean ios, boolean business) {
        System.out.println("Sender: " + sender);
        System.out.println("Recipient: " + recipient);
        System.out.println("Content: " + content);

        WhatsAppClient whatsapp = WhatsAppClient.builder()
                .mobileClient()
                .loadConnection(WhatsAppClientSixPartsKeys.of(sender))
                .device(ios ? JidCompanion.ios(business) : JidCompanion.android(business))
                .name("yunsuo")
                .registered()
                .orElseThrow();

        return SenderSupport.run(whatsapp, callback -> {
            try {
                var info = whatsapp.sendMessage(Jid.of(recipient), content);
                callback.success(info.id());
            } catch (Throwable error) {
                callback.failed(error);
            }
        });
    }
}
