package cn.slainc.storage.multipart;

import cn.slainc.storage.spi.PartInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MultipartSession {

    private final String key;

    private final String uploadId;

    private final long totalSize;

    private final int partSize;

    private final String contentType;

    private final Map<Integer, PartInfo> uploaded = new ConcurrentHashMap<>();

    private volatile boolean completed;

    public MultipartSession(String key, String uploadId, long totalSize, int partSize, String contentType) {
        this.key = key;
        this.uploadId = uploadId;
        this.totalSize = totalSize;
        this.partSize = partSize;
        this.contentType = contentType;
    }

    public String key() {
        return key;
    }

    public String uploadId() {
        return uploadId;
    }

    public long totalSize() {
        return totalSize;
    }

    public int partSize() {
        return partSize;
    }

    public String contentType() {
        return contentType;
    }

    public boolean completed() {
        return completed;
    }

    public void markCompleted() {
        this.completed = true;
    }

    public void markUploaded(PartInfo part) {
        uploaded.put(part.partNumber(), part);
    }

    public void restore(Collection<PartInfo> parts) {
        if (parts != null) {
            parts.forEach(this::markUploaded);
        }
    }

    public List<PartInfo> uploadedParts() {
        List<PartInfo> parts = new ArrayList<>(uploaded.values());
        parts.sort(Comparator.comparingInt(PartInfo::partNumber));
        return parts;
    }

    public int uploadedPartCount() {
        return uploaded.size();
    }

    public int totalParts() {
        if (partSize <= 0 || totalSize <= 0) {
            return 0;
        }
        return (int) ((totalSize + partSize - 1) / partSize);
    }

    public List<Integer> pendingPartNumbers() {
        List<Integer> pending = new ArrayList<>();
        for (int number = 1; number <= totalParts(); number++) {
            if (!uploaded.containsKey(number)) {
                pending.add(number);
            }
        }
        return pending;
    }

    public long uploadedBytes() {
        return uploaded.values().stream().mapToLong(PartInfo::size).sum();
    }

    public long partLength(int partNumber) {
        long offset = (long) (partNumber - 1) * partSize;
        return Math.min(partSize, totalSize - offset);
    }

    public long partOffset(int partNumber) {
        return (long) (partNumber - 1) * partSize;
    }
}
