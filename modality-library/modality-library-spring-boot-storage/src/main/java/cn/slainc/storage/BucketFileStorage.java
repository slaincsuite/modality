package cn.slainc.storage;

import java.util.List;

public interface BucketFileStorage {

    boolean supportsBucket();

    boolean bucketExists(String bucket);

    void createBucket(String bucket);

    void deleteBucket(String bucket);

    List<String> listBuckets();
}
