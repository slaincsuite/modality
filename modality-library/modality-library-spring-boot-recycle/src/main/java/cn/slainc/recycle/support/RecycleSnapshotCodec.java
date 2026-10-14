package cn.slainc.recycle.support;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.utils.string.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

public class RecycleSnapshotCodec {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecycleSnapshotCodec.class);

    private final ObjectMapper objectMapper;

    private final int maxSnapshotBytes;

    public RecycleSnapshotCodec(ObjectMapper objectMapper, int maxSnapshotBytes) {
        this.objectMapper = objectMapper == null ? JsonMapper.builder().build() : objectMapper;
        this.maxSnapshotBytes = maxSnapshotBytes;
    }

    public String serialize(Object entity) {
        if (entity == null) {
            return null;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(entity);
        } catch (RuntimeException e) {
            throw new BizException(ResultCode.BUSINESS_ERROR, "回收站快照序列化失败", e);
        }
        if (json == null) {
            return null;
        }
        int bytes = json.getBytes(StandardCharsets.UTF_8).length;
        if (maxSnapshotBytes > 0 && bytes > maxSnapshotBytes) {
            LOGGER.warn("回收站快照超出字节上限，实体 {} 序列化后 {} 字节，上限 {} 字节，已截断",
                    entity.getClass().getName(), bytes, maxSnapshotBytes);
            return truncate(json, maxSnapshotBytes);
        }
        return json;
    }

    public <T> T deserialize(String snapshot, Class<T> entityClass) {
        if (StringUtils.isBlank(snapshot) || entityClass == null) {
            return null;
        }
        try {
            return objectMapper.readValue(snapshot, entityClass);
        } catch (RuntimeException e) {
            throw new BizException(ResultCode.BUSINESS_ERROR, "回收站快照解析失败", e);
        }
    }

    static String truncate(String text, int maxBytes) {
        if (text == null || maxBytes <= 0) {
            return text;
        }
        StringBuilder builder = new StringBuilder(Math.min(text.length(), Math.max(maxBytes, 16)));
        int bytes = 0;
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            int size = utf8Length(codePoint);
            if (bytes + size > maxBytes) {
                break;
            }
            builder.appendCodePoint(codePoint);
            bytes += size;
            index += Character.charCount(codePoint);
        }
        return builder.toString();
    }

    private static int utf8Length(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        return codePoint < 0x10000 ? 3 : 4;
    }
}
