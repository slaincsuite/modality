package cn.slainc.storage.multipart;

public record UploadProgress(String key, int partNumber, int completedParts, int totalParts, long uploadedBytes,
                             long totalBytes) {

    public double percent() {
        return totalBytes <= 0 ? 0d : Math.min(1d, (double) uploadedBytes / (double) totalBytes);
    }
}
