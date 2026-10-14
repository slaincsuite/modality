package cn.slainc.storage.metadata;

import cn.slainc.core.constant.MimeTypes;
import cn.slainc.storage.FileStorage;
import cn.slainc.storage.spi.StorageMetadata;
import cn.slainc.utils.json.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class SidecarMetadataStore implements MetadataStore {

    private static final Logger log = LoggerFactory.getLogger(SidecarMetadataStore.class);

    public static final String SUFFIX = ".meta.json";

    private final FileStorage storage;

    public SidecarMetadataStore(FileStorage storage) {
        this.storage = storage;
    }

    @Override
    public void save(String key, StorageMetadata metadata) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("key", key);
        values.put("size", metadata.size());
        values.put("contentType", metadata.contentType());
        values.put("md5", metadata.md5());
        values.put("tenantId", metadata.tenantId());
        values.put("uploaderId", metadata.uploaderId());
        values.put("uploaderName", metadata.uploaderName());
        values.put("createdAt", (metadata.createdAt() == null ? Instant.now() : metadata.createdAt()).toString());
        values.put("custom", metadata.custom());
        byte[] json = JsonUtils.toJson(values).getBytes(StandardCharsets.UTF_8);
        storage.upload(key + SUFFIX, new ByteArrayInputStream(json), json.length, MimeTypes.JSON);
    }

    @Override
    public Optional<StorageMetadata> load(String key) {
        String sidecar = key + SUFFIX;
        try {
            if (!storage.exists(sidecar)) {
                return Optional.empty();
            }
            byte[] json = cn.slainc.utils.io.IoUtils.readBytes(storage.download(sidecar));
            Map<String, Object> values = JsonUtils.parseMap(new String(json, StandardCharsets.UTF_8));
            StorageMetadata.Builder builder = StorageMetadata.builder(key)
                    .size(asLong(values.get("size")))
                    .contentType(asString(values.get("contentType")))
                    .md5(asString(values.get("md5")))
                    .tenantId(asLong(values.get("tenantId")))
                    .uploaderId(asLong(values.get("uploaderId")))
                    .uploaderName(asString(values.get("uploaderName")));
            Object createdAt = values.get("createdAt");
            if (createdAt != null) {
                builder.createdAt(Instant.parse(createdAt.toString()));
            }
            Object custom = values.get("custom");
            if (custom instanceof Map<?, ?> map) {
                map.forEach((name, value) -> builder.custom(String.valueOf(name),
                        value == null ? null : String.valueOf(value)));
            }
            return Optional.of(builder.build());
        } catch (RuntimeException ex) {
            log.warn("读取旁路元数据失败，已忽略：{}，原因：{}", sidecar, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        try {
            storage.delete(key + SUFFIX);
        } catch (RuntimeException ex) {
            log.debug("删除旁路元数据失败：{}，原因：{}", key, ex.getMessage());
        }
    }

    @Override
    public boolean internal(String key) {
        return key != null && key.endsWith(SUFFIX);
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }
}
