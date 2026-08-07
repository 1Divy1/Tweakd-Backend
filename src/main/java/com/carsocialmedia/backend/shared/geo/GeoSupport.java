package com.carsocialmedia.backend.shared.geo;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * Helpers for converting between plain latitude/longitude doubles (the wire format
 * used by the API) and JTS {@link Point}s stored in the {@code geography(Point,4326)}
 * columns.
 *
 * <p>PostGIS / WGS84 coordinate order is <strong>(longitude, latitude)</strong>, i.e.
 * {@code Point.getX()} is the longitude and {@code Point.getY()} is the latitude.
 */
public final class GeoSupport {

    /** WGS84 spatial reference id — the SRID used by every geography column. */
    static final int SRID_WGS84 = 4326;

    private static final GeometryFactory FACTORY =
            new GeometryFactory(new PrecisionModel(), SRID_WGS84);

    private GeoSupport() {
    }

    /**
     * Builds a WGS84 point from latitude/longitude. Returns {@code null} if either
     * coordinate is {@code null} (so optional locations stay unset).
     */
    public static Point point(Double lat, Double lng) {
        if (lat == null || lng == null) {
            return null;
        }
        return FACTORY.createPoint(new Coordinate(lng, lat));
    }

    public static Double latOf(Point point) {
        return point == null ? null : point.getY();
    }

    public static Double lngOf(Point point) {
        return point == null ? null : point.getX();
    }
}