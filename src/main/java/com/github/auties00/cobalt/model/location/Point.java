package com.github.auties00.cobalt.model.location;

import com.github.auties00.cobalt.model.message.interactive.InteractiveAnnotation;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * A two-dimensional coordinate used as a vertex in the polygon that defines
 * the tappable region of an
 * {@link InteractiveAnnotation InteractiveAnnotation}
 * on image and video messages.
 *
 * <p>Interactive annotations allow the WhatsApp client to overlay a tappable
 * area on top of a media message. The boundary of that area is described by
 * a list of {@code Point} instances stored in
 * {@link InteractiveAnnotation#polygonVertices() InteractiveAnnotation.polygonVertices()}.
 * The vertices are connected in order to form a closed polygon; tapping
 * inside the polygon triggers the annotation's associated action (for
 * example, opening a {@link Location} on a map).
 *
 * <p>Originally, the coordinate system used integer values exposed through
 * the {@link #xDeprecated()} and {@link #yDeprecated()} accessors. The
 * protocol has since moved to double-precision floating-point coordinates
 * accessible through {@link #x()} and {@link #y()}. New code should use
 * the double-precision accessors exclusively.
 *
 * @see InteractiveAnnotation
 */
@ProtobufMessage(name = "Point")
public final class Point {
    /**
     * The deprecated integer x-coordinate of this vertex.
     *
     * <p>This field has been superseded by the double-precision {@link #x}
     * field and is retained only for backward compatibility with older
     * protocol versions.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.INT32)
    Integer xDeprecated;

    /**
     * The deprecated integer y-coordinate of this vertex.
     *
     * <p>This field has been superseded by the double-precision {@link #y}
     * field and is retained only for backward compatibility with older
     * protocol versions.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.INT32)
    Integer yDeprecated;

    /**
     * The double-precision x-coordinate of this vertex within the
     * annotation polygon.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.DOUBLE)
    Double x;

    /**
     * The double-precision y-coordinate of this vertex within the
     * annotation polygon.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.DOUBLE)
    Double y;

    /**
     * Constructs a new {@code Point} with the specified deprecated integer
     * coordinates and double-precision coordinates.
     *
     * @param xDeprecated the deprecated integer x-coordinate, or
     *                    {@code null} if not available
     * @param yDeprecated the deprecated integer y-coordinate, or
     *                    {@code null} if not available
     * @param x           the double-precision x-coordinate, or
     *                    {@code null} if not available
     * @param y           the double-precision y-coordinate, or
     *                    {@code null} if not available
     */
    Point(Integer xDeprecated, Integer yDeprecated, Double x, Double y) {
        this.xDeprecated = xDeprecated;
        this.yDeprecated = yDeprecated;
        this.x = x;
        this.y = y;
    }

    /**
     * Returns the deprecated integer x-coordinate of this vertex, if
     * present. New code should use {@link #x()} instead.
     *
     * @return an {@code OptionalInt} containing the deprecated x-coordinate,
     *         or empty if the value was not set
     * @deprecated Use {@link #x()} for the double-precision coordinate.
     */
    @Deprecated
    public OptionalInt xDeprecated() {
        return xDeprecated == null ? OptionalInt.empty() : OptionalInt.of(xDeprecated);
    }

    /**
     * Returns the deprecated integer y-coordinate of this vertex, if
     * present. New code should use {@link #y()} instead.
     *
     * @return an {@code OptionalInt} containing the deprecated y-coordinate,
     *         or empty if the value was not set
     * @deprecated Use {@link #y()} for the double-precision coordinate.
     */
    @Deprecated
    public OptionalInt yDeprecated() {
        return yDeprecated == null ? OptionalInt.empty() : OptionalInt.of(yDeprecated);
    }

    /**
     * Returns the double-precision x-coordinate of this vertex within the
     * annotation polygon, if present.
     *
     * @return an {@code OptionalDouble} containing the x-coordinate, or
     *         empty if the value was not set
     */
    public OptionalDouble x() {
        return x == null ? OptionalDouble.empty() : OptionalDouble.of(x);
    }

    /**
     * Returns the double-precision y-coordinate of this vertex within the
     * annotation polygon, if present.
     *
     * @return an {@code OptionalDouble} containing the y-coordinate, or
     *         empty if the value was not set
     */
    public OptionalDouble y() {
        return y == null ? OptionalDouble.empty() : OptionalDouble.of(y);
    }

    /**
     * Sets the deprecated integer x-coordinate of this vertex.
     *
     * @param xDeprecated the deprecated x-coordinate, or {@code null} to
     *                    clear the value
     * @deprecated Use {@link #setX(Double)} instead.
     */
    @Deprecated
    public void setXDeprecated(Integer xDeprecated) {
        this.xDeprecated = xDeprecated;
    }

    /**
     * Sets the deprecated integer y-coordinate of this vertex.
     *
     * @param yDeprecated the deprecated y-coordinate, or {@code null} to
     *                    clear the value
     * @deprecated Use {@link #setY(Double)} instead.
     */
    @Deprecated
    public void setYDeprecated(Integer yDeprecated) {
        this.yDeprecated = yDeprecated;
    }

    /**
     * Sets the double-precision x-coordinate of this vertex.
     *
     * @param x the x-coordinate, or {@code null} to clear the value
     */
    public void setX(Double x) {
        this.x = x;
    }

    /**
     * Sets the double-precision y-coordinate of this vertex.
     *
     * @param y the y-coordinate, or {@code null} to clear the value
     */
    public void setY(Double y) {
        this.y = y;
    }
}
