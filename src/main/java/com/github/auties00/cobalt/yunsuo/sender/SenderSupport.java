package com.github.auties00.cobalt.yunsuo.sender;

import com.github.auties00.cobalt.client.WhatsAppClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 封装消息发送的通用流程：监听登录、node 失败、发送结果，并以 {@link SendResult} 返回状态。
 * <ul>
 *   <li>收到 {@code failure} 节点且 reason=401/403 → {@link SendResult#ACCOUNT_BANNED}</li>
 *   <li>{@code sendMessage} 成功回调拿到消息 id → {@link SendResult#SUCCESS}</li>
 * </ul>
 */
final class SenderSupport {

    private SenderSupport() {
    }

    /**
     * @param whatsapp 已构建好的 WhatsAppClient 实例
     * @param onLogin  登录完成后要执行的发送动作；实现内部应调用
     *                 {@code api.sendMessage(...).thenAccept(info -> callback.success(info.id()))}
     */
    static SendResult run(WhatsAppClient whatsapp, Consumer<SendCallback> onLogin) {
        AtomicReference<SendResult> result = new AtomicReference<>();
        CompletableFuture<SendResult> done = new CompletableFuture<>();

        Consumer<Throwable> markFailed = error -> {
            if (error != null) {
                System.err.println("Send failed: " + error.getMessage());
                error.printStackTrace();
            } else {
                System.err.println("Send failed: unknown");
            }
            markResult(result, done, SendResult.SEND_FAILED);
        };

        SendCallback callback = new SendCallback() {
            @Override
            public void success(String messageId) {
                System.out.println("Send success, id=" + messageId);
                markResult(result, done, SendResult.SUCCESS);
            }

            @Override
            public void failed(Throwable error) {
                markFailed.accept(error);
            }
        };

        try {
            whatsapp.addNodeReceivedListener((ignored, incoming) -> {
                        System.out.printf("Received node %s%n", incoming);
                        if ("failure".equals(incoming.description())) {
                            var reasonAttribute = incoming.attributes().get("reason");
                            String reason = reasonAttribute != null ? reasonAttribute.toString() : null;
                            if ("401".equals(reason) || "403".equals(reason)) {
                                System.err.println("Account banned, reason=" + reason);
                                markResult(result, done, SendResult.ACCOUNT_BANNED);
                            }
                        }
                    })
                    .addNodeSentListener((ignored, outgoing) -> System.out.printf("Sent node %s%n", outgoing))
                    .addDisconnectedListener((ignored, reason) -> {
                        System.out.println("Disconnected: " + reason);
                        if (result.get() == null) {
                            if ("BANNED".equals(String.valueOf(reason))) {
                                markResult(result, done, SendResult.ACCOUNT_BANNED);
                            } else {
                                markResult(result, done, SendResult.SEND_FAILED);
                            }
                        }
                    })
                    .addLoggedInListener(api -> {
                        System.out.println("Logged in");
                        try {
                            onLogin.accept(callback);
                        } catch (Throwable t) {
                            markFailed.accept(t);
                        }
                    })
                    .connect();
            return done.join();
        } catch (Throwable t) {
            markFailed.accept(t);
        }

        return result.get() == null ? SendResult.SEND_FAILED : result.get();
    }

    private static void markResult(AtomicReference<SendResult> result, CompletableFuture<SendResult> done, SendResult target) {
        if (result.compareAndSet(null, target)) {
            done.complete(target);
        }
    }

    interface SendCallback {
        void success(String messageId);

        void failed(Throwable error);
    }
}
