package cn.slainc.storage.multipart;

@FunctionalInterface
public interface UploadProgressListener {

    void onProgress(UploadProgress progress);

    UploadProgressListener NOOP = progress -> {
    };
}
