package cn.slainc.storage.preview;

import cn.slainc.core.constant.MimeTypes;
import cn.slainc.storage.FileStorage;
import cn.slainc.storage.StorageException;
import cn.slainc.storage.command.CommandResult;
import cn.slainc.storage.command.ExternalCommandRunner;
import cn.slainc.storage.support.ContentTypes;
import cn.slainc.utils.io.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public class FilePreviewService {

    private static final Logger log = LoggerFactory.getLogger(FilePreviewService.class);

    private static final int MAX_TEXT_LENGTH = 200_000;

    private final FileStorage storage;

    private final ExternalCommandRunner runner;

    private final String libreOfficeExecutable;

    private final Duration conversionTimeout;

    public FilePreviewService(FileStorage storage, ExternalCommandRunner runner, String libreOfficeExecutable,
                              Duration conversionTimeout) {
        this.storage = storage;
        this.runner = runner;
        this.libreOfficeExecutable = libreOfficeExecutable == null || libreOfficeExecutable.isBlank()
                ? "soffice" : libreOfficeExecutable;
        this.conversionTimeout = conversionTimeout == null ? Duration.ofMinutes(2) : conversionTimeout;
    }

    public PreviewResult preview(String key, String contentType) {
        String type = contentType == null ? ContentTypes.detect(key) : contentType;
        if (ContentTypes.isImage(type)) {
            return PreviewResult.inline(readBytes(key), type);
        }
        if (MimeTypes.PDF.equals(type)) {
            return PreviewResult.inline(readBytes(key), type);
        }
        if (ContentTypes.isText(type)) {
            return PreviewResult.text(readText(key));
        }
        Path converted = convertToPdf(key);
        if (converted == null) {
            return PreviewResult.unavailable("暂不支持预览该类型：" + type + "，且未能通过 LibreOffice 转换");
        }
        try {
            return PreviewResult.inline(Files.readAllBytes(converted), MimeTypes.PDF);
        } catch (IOException ex) {
            throw new StorageException("读取转换后的预览文件失败：" + key, ex);
        } finally {
            try {
                Files.deleteIfExists(converted);
            } catch (IOException ex) {
                converted.toFile().deleteOnExit();
            }
        }
    }

    public Path convertToPdf(String key) {
        if (runner.which(libreOfficeExecutable) == null) {
            log.warn("未检测到 LibreOffice（{}），Office 文档预览已降级为不可用；如需在线预览请安装 libreoffice",
                    libreOfficeExecutable);
            return null;
        }
        Path source = null;
        Path outputDirectory = null;
        try {
            source = Files.createTempFile("storage-preview-", suffix(key));
            try (InputStream input = storage.download(key)) {
                Files.copy(input, source, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            outputDirectory = Files.createTempDirectory("storage-preview-out-");
            CommandResult result = runner.run(List.of(libreOfficeExecutable, "--headless", "--convert-to", "pdf",
                    "--outdir", outputDirectory.toString(), source.toString()), conversionTimeout);
            if (!result.success()) {
                log.warn("LibreOffice 转换失败，预览降级：{}，原因：{}", key, result.message());
                return null;
            }
            String baseName = source.getFileName().toString();
            int dot = baseName.lastIndexOf('.');
            Path pdf = outputDirectory.resolve((dot < 0 ? baseName : baseName.substring(0, dot)) + ".pdf");
            if (!Files.isRegularFile(pdf)) {
                log.warn("LibreOffice 未生成预期的 PDF：{}", pdf);
                return null;
            }
            Path moved = Files.createTempFile("storage-preview-pdf-", ".pdf");
            Files.move(pdf, moved, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return moved;
        } catch (IOException ex) {
            log.warn("Office 文档预览转换失败，已降级：{}，原因：{}", key, ex.getMessage());
            return null;
        } finally {
            deleteQuietly(source);
            deleteDirectory(outputDirectory);
        }
    }

    public boolean officePreviewAvailable() {
        return runner.which(libreOfficeExecutable) != null;
    }

    private byte[] readBytes(String key) {
        try (InputStream input = storage.download(key)) {
            return IoUtils.readBytes(input);
        } catch (IOException ex) {
            throw new StorageException("读取预览内容失败：" + key, ex);
        }
    }

    private String readText(String key) {
        String text = new String(readBytes(key), StandardCharsets.UTF_8);
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) + "...（内容过长已截断）" : text;
    }

    private static String suffix(String key) {
        int dot = key.lastIndexOf('.');
        return dot < 0 ? ".bin" : key.substring(dot);
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            path.toFile().deleteOnExit();
        }
    }

    private static void deleteDirectory(Path path) {
        if (path == null || !Files.isDirectory(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(FilePreviewService::deleteQuietly);
        } catch (IOException ex) {
            log.warn("清理预览临时目录失败：{}", path, ex);
        }
    }

    public record PreviewResult(Kind kind, byte[] binary, String text, String contentType, String reason) {

        public enum Kind {
            INLINE,
            TEXT,
            UNAVAILABLE
        }

        public static PreviewResult inline(byte[] data, String contentType) {
            return new PreviewResult(Kind.INLINE, data, null, contentType, null);
        }

        public static PreviewResult text(String text) {
            return new PreviewResult(Kind.TEXT, null, text, MimeTypes.TEXT, null);
        }

        public static PreviewResult unavailable(String reason) {
            return new PreviewResult(Kind.UNAVAILABLE, null, null, null, reason);
        }

        public boolean available() {
            return kind != Kind.UNAVAILABLE;
        }
    }
}
