package cn.slainc.storage.support;

import cn.slainc.storage.StorageException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.regex.Pattern;

public final class StoragePathValidator {

    private static final int MAX_LENGTH = 1024;

    private static final Pattern DRIVE_LETTER = Pattern.compile("^[A-Za-z]:");

    private static final Pattern PERCENT_ESCAPE = Pattern.compile("%[0-9A-Fa-f]{2}");

    private StoragePathValidator() {
    }

    public static String normalize(String key) {
        if (key == null || key.isBlank()) {
            throw new StorageException("存储路径不能为空");
        }
        if (key.length() > MAX_LENGTH) {
            throw new StorageException("存储路径长度超过上限：" + MAX_LENGTH);
        }
        inspect(key, false);
        if (PERCENT_ESCAPE.matcher(key).find()) {
            inspect(decode(key), true);
        }
        return key;
    }

    public static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank() || "/".equals(prefix)) {
            return "";
        }
        String trimmed = prefix;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? "" : normalize(trimmed);
    }

    public static Path resolve(Path root, String key) {
        String normalized = normalize(key);
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path target = normalizedRoot.resolve(normalized).normalize();
        if (!target.startsWith(normalizedRoot) || target.equals(normalizedRoot)) {
            throw new StorageException("存储路径越出根目录，已拒绝：" + key);
        }
        return target;
    }

    public static Path resolveRoot(Path root, String key) {
        String normalized = normalize(key);
        return root.toAbsolutePath().normalize().resolve(normalized).normalize();
    }

    public static void assertNoSymbolicLink(Path root, Path target) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedRoot)) {
            throw new StorageException("存储路径越出根目录，已拒绝：" + target);
        }
        Path current = normalizedRoot;
        for (Path segment : normalizedRoot.relativize(normalizedTarget)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new StorageException("存储路径包含符号链接，已拒绝访问：" + normalizedTarget);
            }
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                break;
            }
        }
    }

    public static boolean isUnsafeSymbolicLink(Path root, Path target) {
        try {
            assertNoSymbolicLink(root, target);
            return false;
        } catch (StorageException ex) {
            return true;
        }
    }

    private static void inspect(String key, boolean decoded) {
        String label = decoded ? "解码后的存储路径" : "存储路径";
        if (key.indexOf('\0') >= 0) {
            throw new StorageException(label + "包含空字节，已拒绝");
        }
        if (key.indexOf('\\') >= 0) {
            throw new StorageException(label + "包含反斜杠，已拒绝");
        }
        if (key.startsWith("/") || key.startsWith("//")) {
            throw new StorageException(label + "不允许使用绝对路径，已拒绝：" + key);
        }
        if (DRIVE_LETTER.matcher(key).find()) {
            throw new StorageException(label + "不允许使用 Windows 盘符路径，已拒绝：" + key);
        }
        String[] segments = key.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty()) {
                throw new StorageException(label + "包含空路径段，已拒绝：" + key);
            }
            if ("..".equals(segment)) {
                throw new StorageException(label + "包含上级目录引用（..），已拒绝：" + key);
            }
            if (".".equals(segment)) {
                throw new StorageException(label + "包含当前目录引用（.），已拒绝：" + key);
            }
        }
    }

    private static String decode(String key) {
        try {
            return URLDecoder.decode(key, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new StorageException("存储路径包含非法的百分号转义，已拒绝：" + key);
        }
    }
}
