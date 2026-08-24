package cn.slainc.redis.structure;

import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.List;

public class RedisGeo {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisGeo(RedisTemplate<String, Object> redisTemplate) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("redisTemplate 不能为空");
        }
        this.redisTemplate = redisTemplate;
    }

    public long add(String key, double longitude, double latitude, String member) {
        Long added = redisTemplate.opsForGeo().add(key, new Point(longitude, latitude), member);
        return added == null ? 0L : added;
    }

    public Double distanceMeters(String key, String member1, String member2) {
        Distance distance = redisTemplate.opsForGeo().distance(key, member1, member2, Metrics.NEUTRAL);
        return distance == null ? null : distance.getValue();
    }

    public List<GeoPoint> radius(String key, double longitude, double latitude, double radiusMeters, int limit) {
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                .newGeoRadiusArgs()
                .includeCoordinates()
                .includeDistance()
                .sortAscending();
        if (limit > 0) {
            args.limit(limit);
        }
        GeoResults<RedisGeoCommands.GeoLocation<Object>> results = redisTemplate.opsForGeo()
                .radius(key, new Circle(new Point(longitude, latitude), new Distance(radiusMeters, Metrics.NEUTRAL)), args);
        List<GeoPoint> points = new ArrayList<>();
        if (results == null) {
            return points;
        }
        results.forEach(result -> points.add(new GeoPoint(String.valueOf(result.getContent().getName()),
                result.getContent().getPoint().getX(), result.getContent().getPoint().getY(),
                result.getDistance() == null ? null : result.getDistance().getValue())));
        return points;
    }

    public List<String> hash(String key, String... members) {
        return redisTemplate.opsForGeo().hash(key, members);
    }

    public long remove(String key, String... members) {
        Long removed = redisTemplate.opsForGeo().remove(key, members);
        return removed == null ? 0L : removed;
    }
}
