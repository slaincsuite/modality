package cn.slainc.modules.infra.store;

public record ChunkSession(String uploadId, String key, String filename, String contentType, String folder,
                           int partSize, long totalSize, String owner) {
}
