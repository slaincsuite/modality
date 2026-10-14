package cn.slainc.storage.spi;

import java.util.Collections;
import java.util.Map;

public record ImageMetadata(int width, int height, String format, Map<String, String> exif) {

    public ImageMetadata {
        exif = exif == null ? Map.of() : Collections.unmodifiableMap(exif);
    }
}
