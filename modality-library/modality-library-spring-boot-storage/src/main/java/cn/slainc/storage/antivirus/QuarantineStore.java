package cn.slainc.storage.antivirus;

import cn.slainc.storage.StorageException;
import cn.slainc.utils.json.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class QuarantineStore {

    private static final Logger log = LoggerFactory.getLogger(QuarantineStore.class);

    private final Path directory;

    public QuarantineStore(Path directory) {
        this.directory = directory;
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new StorageException("初始化隔离区目录失败：" + directory, ex);
        }
    }

    public Path directory() {
        return directory;
    }

    public Path quarantine(String key, InputStream data, VirusScanResult result) {
        String name = key.replace('/', '_') + "." + System.nanoTime() + ".quarantine";
        Path target = directory.resolve(name);
        try (InputStream input = data) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(directory.resolve(name + ".json"), JsonUtils.toJson(Map.of(
                    "key", key,
                    "signature", result.signature() == null ? "" : result.signature(),
                    "detail", result.detail() == null ? "" : result.detail(),
                    "quarantinedAt", Instant.now().toString())));
            log.warn("文件已隔离：{}，命中特征：{}", key, result.signature());
            return target;
        } catch (IOException ex) {
            throw new StorageException("隔离感染文件失败：" + key, ex);
        }
    }

    public List<Path> list() {
        try (var stream = Files.list(directory)) {
            List<Path> files = new ArrayList<>();
            stream.filter(path -> path.getFileName().toString().endsWith(".quarantine")).forEach(files::add);
            return files;
        } catch (IOException ex) {
            throw new StorageException("列出隔离区文件失败：" + directory, ex);
        }
    }

    public boolean release(String name) {
        try {
            return Files.deleteIfExists(directory.resolve(name));
        } catch (IOException ex) {
            throw new StorageException("删除隔离文件失败：" + name, ex);
        }
    }
}
