package com.github.auties00.cobalt.model.location;

import com.github.auties00.cobalt.model.message.interactive.InteractiveAction;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * A geographic location that can be associated with an
 * {@link com.github.auties00.cobalt.model.message.interactive.InteractiveAnnotation InteractiveAnnotation}
 * on image and video messages.
 *
 * <p>When a user annotates a media message with a location pin, the WhatsApp
 * client creates an {@code InteractiveAnnotation} whose action is set to an
 * instance of this class. The annotation also carries a polygon of
 * {@link Point} vertices that defines the tappable region on top of the
 * media; tapping inside that region opens this location in the device's map
 * application.
 *
 * <p>In the WhatsApp Web client, the location name that appears in the
 * annotation overlay is derived from this class. When a
 * {@code LocationMessage} is parsed by the {@code WAWebParseLocationMessageProto}
 * module, the {@link #name()} and the address of the location (carried by
 * the enclosing {@code LocationMessage}, not by this class) are combined
 * into a single display string separated by a newline character, which is
 * then shown as the annotation label.
 *
 * <p>This class should not be confused with the top-level
 * {@code LocationMessage} or {@code LiveLocationMessage} protobuf types,
 * which represent standalone location-sharing messages. This class is a
 * lightweight protobuf type that appears only as a nested action inside
 * {@code InteractiveAnnotation}.
 *
 * @see com.github.auties00.cobalt.model.message.interactive.InteractiveAnnotation
 * @see Point
 */
@ProtobufMessage(name = "Location")
public final class Location implements InteractiveAction {
    /**
     * The latitude of this location expressed in decimal degrees. Positive
     * values indicate positions north of the equator; negative values
     * indicate positions south of the equator.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.DOUBLE)
    Double degreesLatitude;

    /**
     * The longitude of this location expressed in decimal degrees. Positive
     * values indicate positions east of the prime meridian; negative values
     * indicate positions west of the prime meridian.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.DOUBLE)
    Double degreesLongitude;

    /**
     * The human-readable display name of this location, such as a venue or
     * landmark name, or {@code null} if no name was provided.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String name;

    /**
     * Constructs a new {@code Location} with the specified latitude,
     * longitude, and display name.
     *
     * @param degreesLatitude  the latitude in decimal degrees, or
     *                         {@code null} if not available
     * @param degreesLongitude the longitude in decimal degrees, or
     *                         {@code null} if not available
     * @param name             the display name of the location, or
     *                         {@code null} if not available
     */
    Location(Double degreesLatitude, Double degreesLongitude, String name) {
        this.degreesLatitude = degreesLatitude;
        this.degreesLongitude = degreesLongitude;
        this.name = name;
    }

    /**
     * Returns the latitude of this location in decimal degrees, if present.
     *
     * @return an {@code OptionalDouble} containing the latitude, or empty
     *         if the latitude was not set
     */
    public OptionalDouble degreesLatitude() {
        return degreesLatitude == null ? OptionalDouble.empty() : OptionalDouble.of(degreesLatitude);
    }

    /**
     * Returns the longitude of this location in decimal degrees, if present.
     *
     * @return an {@code OptionalDouble} containing the longitude, or empty
     *         if the longitude was not set
     */
    public OptionalDouble degreesLongitude() {
        return degreesLongitude == null ? OptionalDouble.empty() : OptionalDouble.of(degreesLongitude);
    }

    /**
     * Returns the human-readable display name of this location, if present.
     *
     * @return an {@code Optional} containing the location name, or empty if
     *         no name was provided
     */
    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    /**
     * Sets the latitude of this location in decimal degrees.
     *
     * @param degreesLatitude the latitude, or {@code null} to clear the
     *                        value
     */
    public void setDegreesLatitude(Double degreesLatitude) {
        this.degreesLatitude = degreesLatitude;
    }

    /**
     * Sets the longitude of this location in decimal degrees.
     *
     * @param degreesLongitude the longitude, or {@code null} to clear the
     *                         value
     */
    public void setDegreesLongitude(Double degreesLongitude) {
        this.degreesLongitude = degreesLongitude;
    }

    /**
     * Sets the human-readable display name of this location.
     *
     * @param name the location name, or {@code null} to clear the value
     */
    public void setName(String name) {
        this.name = name;
    }
}
