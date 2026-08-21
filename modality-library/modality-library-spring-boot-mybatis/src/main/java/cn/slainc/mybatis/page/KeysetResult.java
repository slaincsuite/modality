package cn.slainc.mybatis.page;

import java.util.List;

public record KeysetResult<T>(List<T> records, boolean hasNext, Object nextCursor) {

    public static <T> KeysetResult<T> empty() {
        return new KeysetResult<>(List.of(), false, null);
    }
}
