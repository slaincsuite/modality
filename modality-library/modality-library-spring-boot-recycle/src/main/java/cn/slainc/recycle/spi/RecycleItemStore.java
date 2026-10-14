package cn.slainc.recycle.spi;

import cn.slainc.core.result.PageResult;

import java.time.LocalDateTime;

public interface RecycleItemStore {

    Long insert(RecycleItemRecord item);

    RecycleItemRecord findById(Long id);

    PageResult<RecycleItemRecord> page(RecycleItemQuery query);

    void markRestored(RecycleItemRecord item);

    boolean deleteById(Long id);

    int deleteExpired(LocalDateTime now);
}
