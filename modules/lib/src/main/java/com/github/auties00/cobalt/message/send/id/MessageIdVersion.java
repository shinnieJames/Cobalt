package com.github.auties00.cobalt.message.send.id;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

/**
 * Identifies the algorithm a {@link MessageIdGenerator#generate} call uses to
 * produce a WhatsApp stanza id.
 *
 * @apiNote
 * Embedders pick {@link #V2} for normal operation; {@link #V1} exists for the
 * SHA-256-unavailable fallback path inside {@link MessageIdGenerator#generate}
 * and is not normally selected directly.
 *
 * @see MessageIdGenerator
 */
@WhatsAppWebModule(moduleName = "WAWebMsgKey")
public enum MessageIdVersion {
    /**
     * The deprecated random-only algorithm.
     *
     * @apiNote
     * Mirrors WA Web's {@code newId_DEPRECATED}: the id is the prefix
     * {@value MessageIdGenerator#PREFIX} followed by 16 uppercase hex
     * characters drawn from 8 random bytes. Used by
     * {@link MessageIdGenerator#generate} as the fallback when
     * {@link #V2} is requested but SHA-256 is unavailable.
     */
    V1,

    /**
     * The current SHA-256 based algorithm.
     *
     * @apiNote
     * Mirrors WA Web's {@code newId}: the id is the prefix
     * {@value MessageIdGenerator#PREFIX} followed by 18 uppercase hex
     * characters taken from the first 9 bytes of
     * {@code SHA256(int64(unixTime) || utf8(senderJid) || random(16))}. The
     * sender JID is mixed into the pre-image so two accounts sending at the
     * same instant cannot collide on identical random bytes.
     */
    V2
}
