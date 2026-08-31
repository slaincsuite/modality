package cn.slainc.storage.model;

public record VideoMetadata(double durationSeconds, int width, int height, String codec, String format, long bitRate) {
}
