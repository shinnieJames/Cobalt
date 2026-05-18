package com.github.auties00.cobalt.yunsuo;

import com.github.auties00.cobalt.yunsuo.sender.ImageTextCardSender;
import com.github.auties00.cobalt.yunsuo.sender.PicMsgSender;
import com.github.auties00.cobalt.yunsuo.sender.TextMsgSender;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 统一调用入口，根据消息类型分发到具体的 Sender。
 * <p>
 * 用法：java -jar yunsuo_sender.jar &lt;type&gt; &lt;sender args...&gt;
 * <ul>
 *   <li>text: &lt;sender&gt; &lt;recipient&gt; &lt;content&gt; &lt;isIos&gt; &lt;isBusiness&gt; [outputDir]</li>
 *   <li>pic:  &lt;sender&gt; &lt;recipient&gt; &lt;content&gt; &lt;picPath&gt; &lt;isIos&gt; &lt;isBusiness&gt; [outputDir]</li>
 *   <li>card: &lt;sender&gt; &lt;recipient&gt; &lt;text&gt; &lt;picPath&gt; &lt;url&gt; &lt;title&gt; &lt;body&gt; &lt;isIos&gt; &lt;isBusiness&gt; [outputDir]</li>
 * </ul>
 */
public class SenderEntry {

    private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final long AUTO_EXIT_SECONDS = 60;

    public static void main(String[] args) {
        scheduleAutoExit();
        if (args == null || args.length < 1) {
            printUsage();
            System.exit(1);
        }

        String type = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        int expectedArgCount = expectedArgs(type);
        if (expectedArgCount < 0) {
            System.err.println("Unknown message type: " + type);
            printUsage();
            System.exit(1);
        }

        String outputDir = null;
        String[] senderArgs = rest;
        if (rest.length == expectedArgCount + 1) {
            outputDir = rest[rest.length - 1];
            senderArgs = Arrays.copyOf(rest, rest.length - 1);
        }

        if (senderArgs.length < expectedArgCount) {
            printUsage();
            System.exit(1);
        }

        if (outputDir != null && !outputDir.isBlank()) {
            setupAppendLog(outputDir.trim(), type, senderArgs);
        }

        switch (type.toLowerCase()) {
            case "text" -> TextMsgSender.main(senderArgs);
            case "pic" -> PicMsgSender.main(senderArgs);
            case "card" -> ImageTextCardSender.main(senderArgs);
            default -> {
                System.err.println("Unknown message type: " + type);
                printUsage();
                System.exit(1);
            }
        }
    }

    private static void scheduleAutoExit() {
        var scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "sender-auto-exit"));
        scheduler.schedule(() -> {
            System.out.println("SenderEntry auto exit after " + AUTO_EXIT_SECONDS + " seconds");
            System.exit(0);
        }, AUTO_EXIT_SECONDS, TimeUnit.SECONDS);
    }

    private static int expectedArgs(String type) {
        return switch (type.toLowerCase()) {
            case "text" -> 5;
            case "pic" -> 6;
            case "card" -> 9;
            default -> -1;
        };
    }

    private static void setupAppendLog(String outputDir, String type, String[] senderArgs) {
        try {
            Path dir = Path.of(outputDir);
            Files.createDirectories(dir);
            Path logFile = dir.resolve(resolveLogFileName(senderArgs));
            OutputStream fileOut = Files.newOutputStream(logFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);

            PrintStream originalOut = System.out;
            PrintStream originalErr = System.err;
            PrintStream merged = new PrintStream(new TeeOutputStream(originalOut, fileOut), true);
            PrintStream mergedErr = new PrintStream(new TeeOutputStream(originalErr, fileOut), true);
            System.setOut(merged);
            System.setErr(mergedErr);
            System.out.printf("%n[%s] SenderEntry start, type=%s, args=%s%n",
                    LocalDateTime.now().format(TS_FORMATTER), type, Arrays.toString(senderArgs));
        } catch (IOException e) {
            System.err.println("Failed to initialize sender.txt appender: " + e.getMessage());
        }
    }

    /**
     * sender 参数格式：phone,publicNoiseKey,privateNoiseKey,publicIdentityKey,privateIdentityKey,identityId
     * 日志文件名规则：按逗号拆分取第一段作为文件名。
     */
    private static String resolveLogFileName(String[] senderArgs) {
        if (senderArgs == null || senderArgs.length == 0 || senderArgs[0] == null || senderArgs[0].isBlank()) {
            return "sender.txt";
        }
        String sender = senderArgs[0];
        String firstSegment = sender.split(",", 2)[0].trim();
        if (firstSegment.isEmpty()) {
            return "sender.txt";
        }
        // 轻量兜底：替换文件名非法字符，避免跨平台写文件异常
        String safe = firstSegment.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safe.isBlank()) {
            return "sender.txt";
        }
        return safe + ".txt";
    }

    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream a;
        private final OutputStream b;

        private TeeOutputStream(OutputStream a, OutputStream b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public void write(int i) throws IOException {
            a.write(i);
            b.write(i);
        }

        @Override
        public void write(byte[] bytes, int off, int len) throws IOException {
            a.write(bytes, off, len);
            b.write(bytes, off, len);
        }

        @Override
        public void flush() throws IOException {
            a.flush();
            b.flush();
        }
    }

    private static void printUsage() {
        System.err.println("Usage: java -jar yunsuo_sender.jar <type> <args...>");
        System.err.println("  text <sender> <recipient> <content> <isIos> <isBusiness> [outputDir]");
        System.err.println("  pic  <sender> <recipient> <content> <picPath> <isIos> <isBusiness> [outputDir]");
        System.err.println("  card <sender> <recipient> <text> <picPath> <url> <title> <body> <isIos> <isBusiness> [outputDir]");
    }
}
