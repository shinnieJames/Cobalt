package com.github.auties00.cobalt.node.iq.biz;

import java.util.Objects;

/**
 * The typed service-area entry carried inside the {@code <service_areas/>} child of an {@link IqEditBusinessProfileRequest}.
 *
 * @apiNote
 * Use this entry to express one service area in the SMB profile editor: the free-text description names the region, the radius in meters defines the coverage circle, and the centre is the latitude and longitude of the circle's centre. The relay accepts multiple entries per profile so a merchant can express several disjoint coverage zones.
 *
 * @implNote
 * This implementation mirrors the wire shape produced by {@code WAWebBusinessProfileJob.editBusinessProfile}: the {@code <service_area/>} child stamps the description, the radius and a {@code <area_center>} pair carrying the latitude and longitude as decimal-string content.
 */
public final class IqEditBusinessProfileServiceArea {
    /**
     * The free-text area description emitted as the {@code <area_description/>} child content.
     */
    private final String areaDescription;

    /**
     * The coverage radius in meters emitted as the {@code <area_radius_meters/>} child content.
     */
    private final double radius;

    /**
     * The centre latitude emitted as the {@code <latitude/>} child content inside {@code <area_center/>}.
     */
    private final double latitude;

    /**
     * The centre longitude emitted as the {@code <longitude/>} child content inside {@code <area_center/>}.
     */
    private final double longitude;

    /**
     * Constructs a typed area.
     *
     * @apiNote
     * Call this constructor with the description and the geometry; the description must be non-{@code null} because the relay rejects a {@code <service_area/>} child without a description.
     *
     * @param areaDescription the free-text description; never {@code null}
     * @param radius          the coverage radius in meters
     * @param latitude        the centre latitude
     * @param longitude       the centre longitude
     * @throws NullPointerException if {@code areaDescription} is {@code null}
     */
    public IqEditBusinessProfileServiceArea(String areaDescription, double radius, double latitude, double longitude) {
        this.areaDescription = Objects.requireNonNull(areaDescription, "areaDescription cannot be null");
        this.radius = radius;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * Returns the free-text area description.
     *
     * @apiNote
     * Use this getter to render the per-area label in the SMB profile editor.
     *
     * @return the description; never {@code null}
     */
    public String areaDescription() {
        return areaDescription;
    }

    /**
     * Returns the coverage radius in meters.
     *
     * @apiNote
     * Use this getter to render the per-area radius in the SMB profile editor; the value is the circle radius centred on the latitude and longitude pair.
     *
     * @return the radius in meters
     */
    public double radius() {
        return radius;
    }

    /**
     * Returns the centre latitude.
     *
     * @apiNote
     * Use this getter to render the per-area centre on a map.
     *
     * @return the latitude
     */
    public double latitude() {
        return latitude;
    }

    /**
     * Returns the centre longitude.
     *
     * @apiNote
     * Use this getter to render the per-area centre on a map.
     *
     * @return the longitude
     */
    public double longitude() {
        return longitude;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqEditBusinessProfileServiceArea) obj;
        return Double.compare(this.radius, that.radius) == 0
                && Double.compare(this.latitude, that.latitude) == 0
                && Double.compare(this.longitude, that.longitude) == 0
                && Objects.equals(this.areaDescription, that.areaDescription);
    }

    @Override
    public int hashCode() {
        return Objects.hash(areaDescription, radius, latitude, longitude);
    }

    @Override
    public String toString() {
        return "IqEditBusinessProfileServiceArea[areaDescription=" + areaDescription
                + ", radius=" + radius + ", latitude=" + latitude + ", longitude=" + longitude + ']';
    }
}
