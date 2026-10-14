package cn.slainc.storage.spi;

public record PartInfo(int partNumber, long size, String etag) {

    public PartInfo(int partNumber, long size) {
        this(partNumber, size, null);
    }
}
