package cn.slainc.redis.cache;

import java.io.Serializable;

public class NullValueHolder implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final NullValueHolder INSTANCE = new NullValueHolder();

    private String marker = "空值占位";

    public String getMarker() {
        return marker;
    }

    public void setMarker(String marker) {
        this.marker = marker;
    }

    @Override
    public String toString() {
        return "空值占位";
    }
}
