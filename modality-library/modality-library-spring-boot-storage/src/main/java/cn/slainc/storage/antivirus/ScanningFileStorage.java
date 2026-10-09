package cn.slainc.storage.antivirus;

import cn.slainc.storage.ExtendedFileStorage;
import cn.slainc.storage.StorageException;
import cn.slainc.storage.support.DelegatingFileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ScanningFileStorage extends DelegatingFileStorage {

    private static final Logger log = LoggerFactory.getLogger(ScanningFileStorage.class);

    private final AntivirusService antivirusService;

    public ScanningFileStorage(ExtendedFileStorage delegate, AntivirusService antivirusService) {
        super(delegate);
        this.antivirusService = antivirusService;
    }

    @Override
    public void upload(String key, InputStream data, long size, String contentType) {
        Path temp;
        long actual;
        try {
            temp = Files.createTempFile("storage-scan-", ".tmp");
            actual = Files.copy(data, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new StorageException("病毒扫描前置处理失败：" + key, ex);
        }
        if (antivirusService.strategy() == AntivirusService.Strategy.SYNC) {
            scanBeforeUpload(key, temp, actual, contentType);
        } else {
            uploadThenScan(key, temp, actual, contentType);
        }
    }

    private void scanBeforeUpload(String key, Path temp, long size, String contentType) {
        try {
            try (InputStream input = Files.newInputStream(temp)) {
                VirusScanResult result = antivirusService.scan(input);
                if (result.infected()) {
                    quarantine(key, temp, result);
                    throw new StorageException("文件未通过病毒扫描，命中特征：" + result.signature());
                }
                if (result.unavailable() && antivirusService.failClosed()) {
                    throw new StorageException("病毒扫描服务不可用且已配置为失败即拒绝：" + result.detail());
                }
                if (result.unavailable()) {
                    log.warn("病毒扫描服务不可用，按放行策略继续上传：{}，原因：{}", key, result.detail());
                }
            }
            try (InputStream input = Files.newInputStream(temp)) {
                delegate().upload(key, input, size, contentType);
            }
        } catch (IOException ex) {
            throw new StorageException("病毒扫描前置处理失败：" + key, ex);
        } finally {
            deleteQuietly(temp);
        }
    }

    // 异步扫描无法在入库前拦截，先入库再由扫描回调负责清理临时文件与感染对象
    private void uploadThenScan(String key, Path temp, long size, String contentType) {
        boolean handedOff = false;
        try {
            try (InputStream input = Files.newInputStream(temp)) {
                delegate().upload(key, input, size, contentType);
            }
            antivirusService.scanAsync(key, temp, result -> afterScan(key, temp, result));
            handedOff = true;
        } catch (IOException ex) {
            throw new StorageException("病毒扫描前置处理失败：" + key, ex);
        } finally {
            if (!handedOff) {
                deleteQuietly(temp);
            }
        }
    }

    private void afterScan(String key, Path temp, VirusScanResult result) {
        try {
            if (result.infected()) {
                quarantine(key, temp, result);
                removeRejected(key, "命中特征：" + result.signature());
                return;
            }
            if (result.unavailable() && antivirusService.failClosed()) {
                removeRejected(key, "扫描服务不可用且已配置为失败即拒绝：" + result.detail());
            }
        } finally {
            deleteQuietly(temp);
        }
    }

    private void quarantine(String key, Path temp, VirusScanResult result) {
        try (InputStream input = Files.newInputStream(temp)) {
            antivirusService.quarantineStore().quarantine(key, input, result);
        } catch (IOException | RuntimeException ex) {
            log.warn("隔离感染文件失败：{}，原因：{}", key, ex.getMessage());
        }
    }

    private void removeRejected(String key, String reason) {
        try {
            delegate().delete(key);
            log.error("异步病毒扫描拒绝文件，已从存储中移除：{}，原因：{}", key, reason);
        } catch (RuntimeException ex) {
            log.error("异步病毒扫描拒绝文件，但移除失败：{}，原因：{}，异常：{}", key, reason, ex.getMessage());
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            path.toFile().deleteOnExit();
        }
    }
}
