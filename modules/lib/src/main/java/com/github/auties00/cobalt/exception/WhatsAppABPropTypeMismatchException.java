package com.github.auties00.cobalt.exception;

import java.util.Objects;

/**
 * Thrown when an A/B configuration property cannot be decoded as the type
 * the caller asked for.
 *
 * @apiNote
 * WhatsApp ships A/B test ("AB prop") values from the server to drive
 * feature flags, rate limits, and rollout percentages. Each property is
 * keyed by a numeric configuration code and read from the client by
 * specifying the expected Java type ({@link Boolean}, {@link Integer},
 * {@link Long}, {@link Double}, {@link String}). When the raw string the
 * server delivered cannot be parsed as the expected type, this exception
 * is raised so the caller can fall back to a default. Equivalent to a
 * type-coercion failure inside WA Web's {@code getABPropConfigValue}
 * pipeline; WA Web handles the conversion inline and silently returns the
 * default, whereas Cobalt surfaces the mismatch so the embedder can
 * choose between logging it or falling back.
 *
 * @implNote
 * This implementation is non-fatal: {@link #isFatal()} always returns
 * {@code false} because an AB prop lookup miss never invalidates the
 * Noise session.
 */
public final class WhatsAppABPropTypeMismatchException extends WhatsAppException {

    /**
     * The numeric configuration code identifying the AB prop that could
     * not be decoded.
     */
    private final int configCode;

    /**
     * The Java type the caller asked the AB prop to be decoded as.
     */
    private final Class<?> expectedType;

    /**
     * The raw string value delivered by the server, exactly as received.
     */
    private final String actualValue;

    /**
     * Constructs a new AB prop type mismatch exception.
     *
     * @param configCode   the numeric configuration code identifying the AB prop
     * @param expectedType the type that was expected but could not be obtained
     * @param actualValue  the raw string value that could not be converted
     * @throws NullPointerException if {@code expectedType} or {@code actualValue} is {@code null}
     */
    public WhatsAppABPropTypeMismatchException(int configCode, Class<?> expectedType, String actualValue) {
        super(String.format(
                "AB prop type mismatch: code=%d, expected=%s, actualValue='%s'",
                configCode,
                Objects.requireNonNull(expectedType, "expectedType cannot be null").getSimpleName(),
                Objects.requireNonNull(actualValue, "actualValue cannot be null")
        ));
        this.configCode = configCode;
        this.expectedType = expectedType;
        this.actualValue = actualValue;
    }

    /**
     * Returns the numeric configuration code of the AB prop whose value
     * could not be decoded.
     *
     * @return the configuration code
     */
    public int configCode() {
        return configCode;
    }

    /**
     * Returns the Java type the caller requested when reading the AB prop.
     *
     * @return the expected type, never {@code null}
     */
    public Class<?> expectedType() {
        return expectedType;
    }

    /**
     * Returns the raw string value the server delivered for this AB prop.
     *
     * @return the actual value, never {@code null}
     */
    public String actualValue() {
        return actualValue;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always returns {@code false}: an AB prop type
     * mismatch is local to a single configuration lookup and the caller
     * can fall back to a default value.
     */
    @Override
    public boolean isFatal() {
        return false;
    }
}
