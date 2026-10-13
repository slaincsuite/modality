package cn.slainc.excel.format;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public enum ExcelFormat {

    XLSX("xlsx", "新版 Excel 工作簿"),
    XLS("xls", "旧版 Excel 工作簿"),
    CSV("csv", "分隔符文本"),
    UNKNOWN("", "未知格式");

    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] OLE2_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};

    private final String extension;
    private final String description;

    ExcelFormat(String extension, String description) {
        this.extension = extension;
        this.description = description;
    }

    public String extension() {
        return extension;
    }

    public String description() {
        return description;
    }

    public boolean isExcel() {
        return this == XLSX || this == XLS;
    }

    public static ExcelFormat fromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return UNKNOWN;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0 || dot == lower.length() - 1) {
            return UNKNOWN;
        }
        String extension = lower.substring(dot + 1);
        for (ExcelFormat format : values()) {
            if (format.extension.equals(extension)) {
                return format;
            }
        }
        return UNKNOWN;
    }

    public static ExcelFormat of(Path path) {
        if (path == null) {
            return UNKNOWN;
        }
        if (Files.isRegularFile(path)) {
            try (InputStream inputStream = Files.newInputStream(path)) {
                ExcelFormat detected = detect(inputStream.readNBytes(8));
                if (detected != UNKNOWN) {
                    return detected;
                }
            } catch (IOException ex) {
                throw new IllegalStateException("读取文件格式失败: " + path, ex);
            }
        }
        return fromFileName(path.getFileName() == null ? null : path.getFileName().toString());
    }

    public static ExcelFormat detect(byte[] head) {
        if (head == null || head.length == 0) {
            return UNKNOWN;
        }
        if (startsWith(head, ZIP_MAGIC)) {
            return XLSX;
        }
        if (startsWith(head, OLE2_MAGIC)) {
            return XLS;
        }
        for (byte value : head) {
            if (value == 0) {
                return UNKNOWN;
            }
        }
        return CSV;
    }

    private static boolean startsWith(byte[] source, byte[] prefix) {
        if (source.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (source[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
