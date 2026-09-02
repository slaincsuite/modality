package cn.slainc.logger.constant;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public final class CriticalMarker {

    public static final String NAME = "CRITICAL";

    public static final Marker INSTANCE = MarkerFactory.getMarker(NAME);

    public static boolean isCritical(Marker marker) {
        if (marker == null) {
            return false;
        }
        if (NAME.equals(marker.getName())) {
            return true;
        }
        return marker.iterator().hasNext() && marker.contains(INSTANCE);
    }

    private CriticalMarker() {
    }
}
