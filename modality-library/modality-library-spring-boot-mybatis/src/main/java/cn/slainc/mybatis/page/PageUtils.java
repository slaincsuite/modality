package cn.slainc.mybatis.page;

import cn.slainc.core.page.PageParam;
import cn.slainc.core.result.PageResult;
import cn.slainc.utils.reflect.ReflectUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.ArrayList;
import java.util.List;

public final class PageUtils {

    private PageUtils() {
    }

    public static <T> PageResult<T> toResult(IPage<T> page) {
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public static <T> Page<T> toPage(PageQuery query) {
        return query == null ? new Page<>(PageQuery.DEFAULT_PAGE_NUM, PageParam.DEFAULT_SIZE) : query.toPage();
    }

    public static <T> List<T> selectPage(BaseMapper<T> mapper, PageQuery query,
                                         com.baomidou.mybatisplus.core.conditions.Wrapper<T> wrapper) {
        return mapper.selectPage(toPage(query), wrapper).getRecords();
    }

    public static <T> KeysetResult<T> keyset(BaseMapper<T> mapper, QueryWrapper<T> wrapper, String keyColumn,
                                             Object lastKey, boolean asc, int size) {
        if (size <= 0) {
            return KeysetResult.empty();
        }
        if (lastKey != null) {
            if (asc) {
                wrapper.gt(keyColumn, lastKey);
            } else {
                wrapper.lt(keyColumn, lastKey);
            }
        }
        wrapper.orderBy(true, asc, keyColumn);
        Page<T> page = new Page<>(1, size + 1L, false);
        page.setSearchCount(false);
        List<T> records = mapper.selectPage(page, wrapper).getRecords();
        boolean hasNext = records.size() > size;
        List<T> limited = hasNext ? new ArrayList<>(records.subList(0, size)) : records;
        Object nextCursor = limited.isEmpty() ? null
                : ReflectUtils.getFieldValue(limited.get(limited.size() - 1), keyColumn);
        return new KeysetResult<>(limited, hasNext, nextCursor);
    }

    public static <T> KeysetResult<T> keyset(BaseMapper<T> mapper, String keyColumn, Object lastKey,
                                             boolean asc, int size) {
        return keyset(mapper, new QueryWrapper<>(), keyColumn, lastKey, asc, size);
    }

    public static <T> PageResult<T> memoryPage(List<T> source, PageQuery query) {
        List<T> data = source == null ? List.of() : source;
        long total = data.size();
        if (query == null) {
            return PageResult.of(data, total, PageQuery.DEFAULT_PAGE_NUM, data.size());
        }
        long pageNum = query.normalizedPageNum();
        long pageSize = query.normalizedPageSize();
        int from = (int) Math.min((pageNum - 1) * pageSize, total);
        int to = (int) Math.min(from + pageSize, total);
        return PageResult.of(new ArrayList<>(data.subList(from, to)), total, pageNum, pageSize);
    }
}
