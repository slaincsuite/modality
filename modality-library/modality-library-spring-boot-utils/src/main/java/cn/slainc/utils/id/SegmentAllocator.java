package cn.slainc.utils.id;

public interface SegmentAllocator {

    long nextMaxId(String key, int step);
}
