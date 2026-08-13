package cn.slainc.utils.io;

import cn.slainc.utils.digest.DigestUtils;
import cn.slainc.utils.math.NumberUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class FileUtils {

    private static final Map<String, String> MIME_TYPES = new LinkedHashMap<>();

    static {
        MIME_TYPES.put("txt", "text/plain");
        MIME_TYPES.put("html", "text/html");
        MIME_TYPES.put("htm", "text/html");
        MIME_TYPES.put("css", "text/css");
        MIME_TYPES.put("csv", "text/csv");
        MIME_TYPES.put("js", "application/javascript");
        MIME_TYPES.put("json", "application/json");
        MIME_TYPES.put("xml", "application/xml");
        MIME_TYPES.put("pdf", "application/pdf");
        MIME_TYPES.put("zip", "application/zip");
        MIME_TYPES.put("gz", "application/gzip");
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("gif", "image/gif");
        MIME_TYPES.put("bmp", "image/bmp");
        MIME_TYPES.put("webp", "image/webp");
        MIME_TYPES.put("svg", "image/svg+xml");
        MIME_TYPES.put("mp3", "audio/mpeg");
        MIME_TYPES.put("mp4", "video/mp4");
        MIME_TYPES.put("doc", "application/msword");
        MIME_TYPES.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        MIME_TYPES.put("xls", "application/vnd.ms-excel");
        MIME_TYPES.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        MIME_TYPES.put("ppt", "application/vnd.ms-powerpoint");
        MIME_TYPES.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
    }

    private FileUtils() {
    }

    public static String readText(String path) {
        return readText(Path.of(path), StandardCharsets.UTF_8);
    }

    public static String readText(String path, Charset charset) {
        return readText(Path.of(path), charset);
    }

    public static String readText(Path path) {
        return readText(path, StandardCharsets.UTF_8);
    }

    public static String readText(Path path, Charset charset) {
        try {
            return Files.readString(path, charset == null ? StandardCharsets.UTF_8 : charset);
        } catch (IOException ex) {
            throw new IllegalStateException("读取文件失败: " + path, ex);
        }
    }

    public static byte[] readBytes(String path) {
        return readBytes(Path.of(path));
    }

    public static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new IllegalStateException("读取文件失败: " + path, ex);
        }
    }

    public static void writeText(String path, String content) {
        writeText(Path.of(path), content, StandardCharsets.UTF_8);
    }

    public static void writeText(Path path, String content) {
        writeText(path, content, StandardCharsets.UTF_8);
    }

    public static void writeText(Path path, String content, Charset charset) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content == null ? "" : content, charset == null ? StandardCharsets.UTF_8 : charset);
        } catch (IOException ex) {
            throw new IllegalStateException("写入文件失败: " + path, ex);
        }
    }

    public static void writeBytes(String path, byte[] content) {
        writeBytes(Path.of(path), content);
    }

    public static void writeBytes(Path path, byte[] content) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(path, content == null ? new byte[0] : content);
        } catch (IOException ex) {
            throw new IllegalStateException("写入文件失败: " + path, ex);
        }
    }

    public static Path copy(Path source, Path target) {
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            return Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("复制文件失败", ex);
        }
    }

    public static Path move(Path source, Path target) {
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            return Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("移动文件失败", ex);
        }
    }

    public static boolean delete(Path path) {
        if (path == null || !Files.exists(path)) {
            return false;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(current -> {
                try {
                    Files.deleteIfExists(current);
                } catch (IOException ex) {
                    throw new IllegalStateException("删除失败: " + current, ex);
                }
            });
            return true;
        } catch (IOException ex) {
            throw new IllegalStateException("删除失败: " + path, ex);
        }
    }

    public static boolean deleteQuietly(Path path) {
        try {
            return delete(path);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public static void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException ex) {
            throw new IllegalStateException("创建目录失败: " + path, ex);
        }
    }

    public static String extension(String filename) {
        if (filename == null) {
            return null;
        }
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        int dot = filename.lastIndexOf('.');
        return dot > slash && dot < filename.length() - 1 ? filename.substring(dot + 1) : "";
    }

    public static String baseName(String filename) {
        if (filename == null) {
            return null;
        }
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        int dot = filename.lastIndexOf('.');
        if (dot <= slash) {
            return slash >= 0 ? filename.substring(slash + 1) : filename;
        }
        return filename.substring(slash + 1, dot);
    }

    public static boolean hasExtension(String filename, String... extensions) {
        String actual = extension(filename);
        if (actual == null || extensions == null) {
            return false;
        }
        for (String candidate : extensions) {
            if (actual.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    public static String humanReadableSize(long bytes) {
        return NumberUtils.humanReadableSize(bytes);
    }

    public static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ex) {
            throw new IllegalStateException("读取文件大小失败: " + path, ex);
        }
    }

    public static String mimeTypeByExtension(String filename) {
        String extension = extension(filename);
        return extension == null ? null : MIME_TYPES.get(extension.toLowerCase(Locale.ROOT));
    }

    public static String mimeTypeByMagicNumber(byte[] header) {
        if (header == null || header.length < 4) {
            return null;
        }
        int b0 = header[0] & 0xFF;
        int b1 = header[1] & 0xFF;
        int b2 = header[2] & 0xFF;
        int b3 = header[3] & 0xFF;
        if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) {
            return "image/png";
        }
        if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) {
            return "image/jpeg";
        }
        if (b0 == 0x47 && b1 == 0x49 && b2 == 0x46) {
            return "image/gif";
        }
        if (b0 == 0x25 && b1 == 0x50 && b2 == 0x44 && b3 == 0x46) {
            return "application/pdf";
        }
        if (b0 == 0x50 && b1 == 0x4B && b2 == 0x03 && b3 == 0x04) {
            return "application/zip";
        }
        if (b0 == 0x1F && b1 == 0x8B) {
            return "application/gzip";
        }
        return null;
    }

    public static String mimeType(byte[] header, String filename) {
        String byMagic = mimeTypeByMagicNumber(header);
        return byMagic != null ? byMagic : mimeTypeByExtension(filename);
    }

    public static Path createTempFile(String prefix, String suffix) {
        try {
            return Files.createTempFile(prefix, suffix);
        } catch (IOException ex) {
            throw new IllegalStateException("创建临时文件失败", ex);
        }
    }

    public static Path createTempDirectory(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException ex) {
            throw new IllegalStateException("创建临时目录失败", ex);
        }
    }

    public static long copy(InputStream inputStream, OutputStream outputStream) {
        return IoUtils.copy(inputStream, outputStream);
    }

    public static String md5(Path path) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return DigestUtils.md5Stream(inputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("文件摘要计算失败: " + path, ex);
        }
    }

    public static String sha256(Path path) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return DigestUtils.sha256Stream(inputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("文件摘要计算失败: " + path, ex);
        }
    }
}
