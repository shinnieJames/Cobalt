package com.github.auties00.cobalt.util;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates IDs in the same format as WhatsApp Web's WAWap.generateId().
 * <p>
 * Per WhatsApp Web: the format is "{prefix}-{counter}" where:
 * - prefix: two random 16-bit numbers formatted as "num1.num2"
 * - counter: incrementing integer starting from 1
 * <p>
 * Example: "12345.67890-1", "12345.67890-2", etc.
 */
public final class WhatsAppIdGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Session-scoped prefix (generated once per instance).
     */
    private final String prefix;

    /**
     * Incrementing counter for generating unique IDs.
     */
    private final AtomicLong counter;

    /**
     * Creates a new ID generator with a random prefix.
     */
    public WhatsAppIdGenerator() {
        // Generate two random 16-bit numbers (0-65535)
        var num1 = RANDOM.nextInt(65536);
        var num2 = RANDOM.nextInt(65536);
        this.prefix = num1 + "." + num2 + "-";
        this.counter = new AtomicLong(1);
    }

    /**
     * Generates a new unique ID.
     *
     * @return the generated ID in WhatsApp format
     */
    public String generateId() {
        return prefix + counter.getAndIncrement();
    }

    /**
     * Creates a new ID generator and immediately generates an ID.
     * <p>
     * This is a convenience method for one-off ID generation.
     * For repeated ID generation, prefer creating a single instance
     * and calling {@link #generateId()} multiple times.
     *
     * @return a new unique ID
     */
    public static String newId() {
        // Generate two random 16-bit numbers (0-65535)
        var num1 = RANDOM.nextInt(65536);
        var num2 = RANDOM.nextInt(65536);
        return num1 + "." + num2 + "-1";
    }

    public static String generateSid() {
        return Instant.now().getEpochSecond()
               + "-" + ThreadLocalRandom.current().nextLong(1_000_000_000, 9_999_999_999L)
               + "-" + ThreadLocalRandom.current().nextInt(0, 1000);
    }
}
