package cn.slainc.storage.spi;

public record VideoMetadata(double durationSeconds, int width, int height, String codec, String format, long bitRate) {
}
