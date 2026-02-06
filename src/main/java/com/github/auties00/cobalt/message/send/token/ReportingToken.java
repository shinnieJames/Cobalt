package com.github.auties00.cobalt.message.send.token;

import com.github.auties00.cobalt.message.send.util.CryptoUtils;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.common.Message;
import com.github.auties00.cobalt.model.message.common.MessageContainer;
import com.github.auties00.cobalt.model.message.common.MessageContainerSpec;
import com.github.auties00.cobalt.model.message.standard.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Set;

/**
 * Generates reporting tokens for message accountability.
 * <p>
 * Reporting tokens are used for content moderation and abuse reporting.
 * They allow WhatsApp to verify message content without having access
 * to the plaintext, using a franking mechanism.
 *
 * @apiNote WAWebReportingTokenUtils.genReportingToken
 */
public final class ReportingToken {
    private static final System.Logger LOGGER = System.getLogger("ReportingToken");

    private static final int REPORTING_TOKEN_LENGTH = 16;
    private static final int REPORTING_TOKEN_KEY_SIZE = 32;
    private static final byte USE_CASE_SECRET_REPORT_TOKEN = 5;

    private static final Set<Message.Type> SUPPORTED_TYPES = Set.of(
            Message.Type.TEXT,
            Message.Type.IMAGE,
            Message.Type.VIDEO,
            Message.Type.AUDIO,
            Message.Type.DOCUMENT,
            Message.Type.STICKER,
            Message.Type.INTERACTIVE
    );

    /**
     * Generates a reporting token for a message.
     *
     * @param messageId     the message ID
     * @param message       the message container
     * @param messageSecret the message secret bytes
     * @param senderJid     the sender JID
     * @param remoteJid     the remote JID
     * @return the reporting token bytes, or null if not applicable
     *
     * @apiNote WAWebReportingTokenUtils.genReportingToken
     */
    public byte[] generate(
            String messageId,
            MessageContainer message,
            byte[] messageSecret,
            Jid senderJid,
            Jid remoteJid
    ) {
        if (!isReportingTokenCompatible(message)) {
            return null;
        }

        if (messageSecret == null || messageSecret.length == 0) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Cannot generate reporting token: missing message secret");
            return null;
        }

        try {
            var tokenKey = deriveReportingTokenKey(
                    messageSecret,
                    messageId,
                    senderJid.toUserJid(),
                    remoteJid
            );

            var reportingContent = getReportingTokenContent(message);
            if (reportingContent == null || reportingContent.length == 0) {
                return null;
            }

            return CryptoUtils.hmacSha256Truncated(tokenKey, reportingContent, REPORTING_TOKEN_LENGTH);
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to generate reporting token: {0}", e.getMessage());
            return null;
        }
    }

    private boolean isReportingTokenCompatible(MessageContainer message) {
        var unwrapped = message.unbox();
        var type = unwrapped.type();
        return SUPPORTED_TYPES.contains(type);
    }

    private byte[] deriveReportingTokenKey(
            byte[] messageSecret,
            String messageId,
            Jid senderJid,
            Jid remoteJid
    ) throws Exception {
        var info = buildBinaryInfo(
                messageId,
                senderJid.toString(),
                remoteJid.toString(),
                USE_CASE_SECRET_REPORT_TOKEN
        );

        return CryptoUtils.hkdfExtractAndExpand(messageSecret, info, REPORTING_TOKEN_KEY_SIZE);
    }

    private byte[] buildBinaryInfo(String stanzaId, String senderJid, String remoteJid, byte useCaseType) {
        try {
            var baos = new ByteArrayOutputStream();
            baos.write(stanzaId.getBytes(StandardCharsets.UTF_8));
            baos.write(senderJid.getBytes(StandardCharsets.UTF_8));
            baos.write(remoteJid.getBytes(StandardCharsets.UTF_8));
            baos.write(useCaseType);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to build binary info", e);
        }
    }

    private byte[] getReportingTokenContent(MessageContainer message) {
        var unwrapped = message.unbox();
        var content = unwrapped.content();

        try {
            return extractReportingContentV1(content);
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to extract reporting content, falling back to full message: {0}", e.getMessage());
            return MessageContainerSpec.encode(message);
        }
    }

    private byte[] extractReportingContentV1(Message content) throws IOException {
        var baos = new ByteArrayOutputStream();

        switch (content) {
            case TextMessage text -> {
                var body = text.text();
                if (body != null) {
                    baos.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            case ImageMessage image -> {
                writeMediaReportingContent(baos, image.mediaEncryptedSha256().orElse(null), image.caption().orElse(null));
            }
            case VideoOrGifMessage video -> {
                writeMediaReportingContent(baos, video.mediaEncryptedSha256().orElse(null), video.caption().orElse(null));
            }
            case AudioMessage audio -> {
                var hash = audio.mediaEncryptedSha256().orElse(null);
                if (hash != null) {
                    baos.write(hash);
                }
            }
            case DocumentMessage document -> {
                writeMediaReportingContent(baos, document.mediaEncryptedSha256().orElse(null), document.caption().orElse(null));
            }
            case StickerMessage sticker -> {
                var hash = sticker.mediaEncryptedSha256().orElse(null);
                if (hash != null) {
                    baos.write(hash);
                }
            }
            case ContactMessage contact -> {
                var vcard = contact.vcard().toVcard();
                if (vcard != null) {
                    baos.write(vcard.getBytes(StandardCharsets.UTF_8));
                }
            }
            case ContactsMessage contacts -> {
                for (var entry : contacts.contacts()) {
                    var vcard = entry.vcard().toVcard();
                    if (vcard != null) {
                        baos.write(vcard.getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
            case LocationMessage location -> {
                baos.write(Double.toString(location.latitude()).getBytes(StandardCharsets.UTF_8));
                baos.write(Double.toString(location.longitude()).getBytes(StandardCharsets.UTF_8));
            }
            case LiveLocationMessage liveLocation -> {
                baos.write(Double.toString(liveLocation.latitude()).getBytes(StandardCharsets.UTF_8));
                baos.write(Double.toString(liveLocation.longitude()).getBytes(StandardCharsets.UTF_8));
            }
            default -> {
                return new byte[0];
            }
        }

        return baos.toByteArray();
    }

    private void writeMediaReportingContent(ByteArrayOutputStream baos, byte[] encHash, String caption) throws IOException {
        var totalLength = (encHash != null ? encHash.length : 0) +
                          (caption != null && !caption.isEmpty() ? caption.getBytes(StandardCharsets.UTF_8).length : 0);

        if (totalLength > 0) {
            var randomBytes = new byte[totalLength];
            try {
                SecureRandom.getInstanceStrong().nextBytes(randomBytes);
            } catch (NoSuchAlgorithmException e) {
                new SecureRandom().nextBytes(randomBytes);
            }
            baos.write(randomBytes);
        }
    }
}
