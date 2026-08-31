package cn.slainc.storage.model;

public record PartInfo(int partNumber, long size, String etag) {

    public PartInfo(int partNumber, long size) {
        this(partNumber, size, null);
    }
}
