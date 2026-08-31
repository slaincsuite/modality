package cn.slainc.storage.local;

import cn.slainc.storage.ExtendedFileStorage;
import cn.slainc.storage.StorageException;
import cn.slainc.storage.model.PartInfo;
import cn.slainc.storage.model.StorageMetadata;
import cn.slainc.storage.model.StorageObject;
import cn.slainc.storage.support.ContentTypes;
import cn.slainc.storage.support.StoragePathValidator;
import cn.slainc.utils.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

public class LocalFileStorage implements ExtendedFileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

    private static final String MULTIPART_ROOT = ".multipart";

    private static final String LOCK_ROOT = ".locks";

    private final String backend;

    private final Path root;

    private final String baseUrl;

    public LocalFileStorage(Path root) {
        this("local", root, null);
    }

    public LocalFileStorage(String backend, Path root, String baseUrl) {
        this.backend = backend;
        this.root = root.toAbsolutePath().normalize();
        this.baseUrl = baseUrl;
        try {
            Files.createDirectories(this.root);
        } catch (IOException ex) {
            throw new StorageException("初始化本地存储根目录失败：" + this.root, ex);
        }
    }

    @Override
    public String backend() {
        return backend;
    }

    public Path root() {
        return root;
    }

    @Override
    public void upload(String key, InputStream data, long size, String contentType) {
        Path target = safeResolve(key);
        Path parent = target.getParent();
        Path temp = parent.resolve("." + target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.createDirectories(parent);
            StoragePathValidator.assertNoSymbolicLink(root, parent);
            long written = Files.copy(data, temp, StandardCopyOption.REPLACE_EXISTING);
            if (size >= 0 && written != size) {
                Files.deleteIfExists(temp);
                throw new StorageException("上传内容长度与声明不一致，期望 " + size + " 字节，实际 " + written + " 字节");
            }
            move(temp, target);
        } catch (IOException ex) {
            quietDelete(temp);
            throw new StorageException("写入本地存储失败：" + key, ex);
        }
    }

    @Override
    public InputStream download(String key) {
        Path target = safeResolve(key);
        if (!Files.isRegularFile(target)) {
            throw new StorageException("文件不存在：" + key);
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException ex) {
            throw new StorageException("读取本地存储失败：" + key, ex);
        }
    }

    @Override
    public void download(String key, OutputStream target) {
        Path path = safeResolve(key);
        if (!Files.isRegularFile(path)) {
            throw new StorageException("文件不存在：" + key);
        }
        try (InputStream input = Files.newInputStream(path)) {
            input.transferTo(target);
        } catch (IOException ex) {
            throw new StorageException("读取本地存储失败：" + key, ex);
        }
    }

    @Override
    public boolean delete(String key) {
        Path target = safeResolve(key);
        try {
            boolean deleted = Files.deleteIfExists(target);
            if (deleted) {
                cleanupEmptyParents(target.getParent());
            }
            return deleted;
        } catch (IOException ex) {
            throw new StorageException("删除本地存储失败：" + key, ex);
        }
    }

    @Override
    public boolean exists(String key) {
        Path target = safeResolve(key);
        return Files.isRegularFile(target);
    }

    @Override
    public StorageMetadata metadata(String key) {
        Path target = safeResolve(key);
        if (!Files.isRegularFile(target)) {
            throw new StorageException("文件不存在：" + key);
        }
        try {
            FileTime created = (FileTime) Files.getAttribute(target, "creationTime");
            return StorageMetadata.builder(key)
                    .size(Files.size(target))
                    .contentType(ContentTypes.detect(key))
                    .md5(FileUtils.md5(target))
                    .etag(FileUtils.md5(target))
                    .createdAt(created.toInstant())
                    .lastModified(Files.getLastModifiedTime(target).toInstant())
                    .build();
        } catch (IOException ex) {
            throw new StorageException("读取本地存储元数据失败：" + key, ex);
        }
    }

    @Override
    public List<StorageObject> list(String prefix) {
        String normalized = StoragePathValidator.normalizePrefix(prefix);
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> !StoragePathValidator.isUnsafeSymbolicLink(root, path))
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .filter(key -> !key.startsWith(MULTIPART_ROOT + "/") && !key.startsWith(LOCK_ROOT + "/"))
                    .filter(key -> normalized.isEmpty() || key.startsWith(normalized))
                    .sorted()
                    .map(key -> {
                        Path path = root.resolve(key);
                        return new StorageObject(key, FileUtils.size(path), ContentTypes.detect(key), null,
                                lastModified(path));
                    })
                    .toList();
        } catch (IOException ex) {
            throw new StorageException("列出本地存储对象失败：" + prefix, ex);
        }
    }

    @Override
    public boolean supportsMultipart() {
        return true;
    }

    @Override
    public String initMultipart(String key, String contentType) {
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        Path directory = multipartDirectory(uploadId);
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("contentType"), contentType == null ? "" : contentType);
            Files.writeString(directory.resolve("key"), key);
        } catch (IOException ex) {
            throw new StorageException("初始化本地分片上传失败：" + key, ex);
        }
        return uploadId;
    }

    @Override
    public PartInfo uploadPart(String key, String uploadId, int partNumber, InputStream data, long size) {
        if (partNumber < 1) {
            throw new StorageException("分片序号必须从 1 开始，当前为：" + partNumber);
        }
        Path directory = multipartDirectory(uploadId);
        if (!Files.isDirectory(directory)) {
            throw new StorageException("分片上传任务不存在或已过期：" + uploadId);
        }
        Path part = directory.resolve(partFileName(partNumber));
        Path temp = directory.resolve(partFileName(partNumber) + ".tmp");
        try {
            long written = Files.copy(data, temp, StandardCopyOption.REPLACE_EXISTING);
            if (size >= 0 && written != size) {
                Files.deleteIfExists(temp);
                throw new StorageException("分片长度与声明不一致，期望 " + size + " 字节，实际 " + written + " 字节");
            }
            move(temp, part);
            return new PartInfo(partNumber, written, FileUtils.md5(part));
        } catch (IOException ex) {
            quietDelete(temp);
            throw new StorageException("写入本地分片失败：" + key + " 第 " + partNumber + " 片", ex);
        }
    }

    @Override
    public List<PartInfo> listParts(String key, String uploadId) {
        Path directory = multipartDirectory(uploadId);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".part"))
                    .map(path -> {
                        int number = Integer.parseInt(path.getFileName().toString().replace(".part", ""));
                        return new PartInfo(number, FileUtils.size(path), FileUtils.md5(path));
                    })
                    .sorted(Comparator.comparingInt(PartInfo::partNumber))
                    .toList();
        } catch (IOException ex) {
            throw new StorageException("读取本地已上传分片失败：" + key, ex);
        }
    }

    @Override
    public void completeMultipart(String key, String uploadId, List<PartInfo> parts) {
        List<PartInfo> ordered = parts == null || parts.isEmpty() ? listParts(key, uploadId) : new ArrayList<>(parts);
        if (ordered.isEmpty()) {
            throw new StorageException("没有可合并的分片：" + key);
        }
        ordered.sort(Comparator.comparingInt(PartInfo::partNumber));
        Path directory = multipartDirectory(uploadId);
        Path target = safeResolve(key);
        Path parent = target.getParent();
        Path temp = parent.resolve("." + target.getFileName() + "." + UUID.randomUUID() + ".merge");
        try {
            Files.createDirectories(parent);
            try (OutputStream output = Files.newOutputStream(temp)) {
                for (PartInfo part : ordered) {
                    Path partPath = directory.resolve(partFileName(part.partNumber()));
                    if (!Files.isRegularFile(partPath)) {
                        throw new StorageException("分片缺失，无法合并：" + key + " 第 " + part.partNumber() + " 片");
                    }
                    try (InputStream input = Files.newInputStream(partPath)) {
                        input.transferTo(output);
                    }
                }
            }
            move(temp, target);
            deleteDirectory(directory);
        } catch (IOException ex) {
            quietDelete(temp);
            throw new StorageException("合并本地分片失败：" + key, ex);
        }
    }

    @Override
    public void abortMultipart(String key, String uploadId) {
        deleteDirectory(multipartDirectory(uploadId));
    }

    @Override
    public boolean supportsSignedUrl() {
        return false;
    }

    @Override
    public String presignedUploadUrl(String key, Duration expiry) {
        throw new StorageException("本地存储后端不支持预签名直传地址，请改用 SignedUrlService 生成自签名地址");
    }

    @Override
    public String presignedDownloadUrl(String key, Duration expiry) {
        throw new StorageException("本地存储后端不支持预签名下载地址，请改用 SignedUrlService 生成自签名地址");
    }

    @Override
    public boolean supportsBucket() {
        return true;
    }

    @Override
    public boolean bucketExists(String bucket) {
        Path path = bucketPath(bucket);
        return Files.isDirectory(path);
    }

    @Override
    public void createBucket(String bucket) {
        Path path = bucketPath(bucket);
        try {
            Files.createDirectories(path);
        } catch (IOException ex) {
            throw new StorageException("创建本地桶目录失败：" + bucket, ex);
        }
    }

    @Override
    public void deleteBucket(String bucket) {
        Path path = bucketPath(bucket);
        if (!Files.isDirectory(path)) {
            return;
        }
        try (Stream<Path> stream = Files.list(path)) {
            if (stream.findAny().isPresent()) {
                throw new StorageException("桶非空，拒绝删除：" + bucket);
            }
        } catch (IOException ex) {
            throw new StorageException("检查本地桶目录失败：" + bucket, ex);
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new StorageException("删除本地桶目录失败：" + bucket, ex);
        }
    }

    @Override
    public List<String> listBuckets() {
        try (Stream<Path> stream = Files.list(root)) {
            return stream.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new StorageException("列出本地桶目录失败", ex);
        }
    }

    public long totalSize() {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).mapToLong(FileUtils::size).sum();
        } catch (IOException ex) {
            throw new StorageException("统计本地存储占用失败", ex);
        }
    }

    private Path safeResolve(String key) {
        Path target = StoragePathValidator.resolve(root, key);
        StoragePathValidator.assertNoSymbolicLink(root, target);
        return target;
    }

    private Path bucketPath(String bucket) {
        return StoragePathValidator.resolve(root, bucket);
    }

    private Path multipartDirectory(String uploadId) {
        if (uploadId == null || !uploadId.matches("[A-Za-z0-9]{8,64}")) {
            throw new StorageException("非法的分片上传标识：" + uploadId);
        }
        return root.resolve(MULTIPART_ROOT).resolve(uploadId);
    }

    private static String partFileName(int partNumber) {
        return String.format(Locale.ROOT, "%06d.part", partNumber);
    }

    private static Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException ex) {
            return Instant.now();
        }
    }

    private void cleanupEmptyParents(Path directory) {
        Path current = directory;
        while (current != null && !current.equals(root) && current.startsWith(root)) {
            try (Stream<Path> stream = Files.list(current)) {
                if (stream.findAny().isPresent()) {
                    return;
                }
            } catch (IOException ex) {
                return;
            }
            try {
                Files.deleteIfExists(current);
            } catch (IOException ex) {
                return;
            }
            current = current.getParent();
        }
    }

    private void deleteDirectory(Path directory) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    log.warn("删除本地分片临时文件失败：{}", path, ex);
                }
            });
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void quietDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("清理本地临时文件失败：{}", path, ex);
        }
    }
}
