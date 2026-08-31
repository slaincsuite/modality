package cn.slainc.storage.support;

import cn.slainc.core.context.TenantContext;
import cn.slainc.utils.io.FileUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class StorageKeyGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private StorageKeyGenerator() {
    }

    public static String generate(String filename) {
        return generate(filename, TenantContext.getOrDefault(), LocalDate.now());
    }

    public static String generate(String filename, Long tenantId, LocalDate date) {
        String extension = FileUtils.extension(filename);
        String name = UUID.randomUUID().toString().replace("-", "");
        String tenant = tenantId == null ? "public" : String.valueOf(tenantId);
        String datePart = (date == null ? LocalDate.now() : date).format(DATE_FORMATTER);
        return extension == null || extension.isEmpty()
                ? tenant + "/" + datePart + "/" + name
                : tenant + "/" + datePart + "/" + name + "." + extension.toLowerCase();
    }
}
