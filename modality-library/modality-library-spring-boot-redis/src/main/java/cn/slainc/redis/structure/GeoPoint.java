package cn.slainc.redis.structure;

public class GeoPoint {

    private final String member;

    private final double longitude;

    private final double latitude;

    private final Double distanceMeters;

    public GeoPoint(String member, double longitude, double latitude, Double distanceMeters) {
        this.member = member;
        this.longitude = longitude;
        this.latitude = latitude;
        this.distanceMeters = distanceMeters;
    }

    public String getMember() {
        return member;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public Double getDistanceMeters() {
        return distanceMeters;
    }

    @Override
    public String toString() {
        return "地理坐标[成员=" + member + ", 经度=" + longitude + ", 纬度=" + latitude
                + ", 距离米=" + distanceMeters + "]";
    }
}
