package cn.slainc.storage.antivirus;

import cn.slainc.storage.ExtendedFileStorage;
import cn.slainc.storage.StorageException;
import cn.slainc.storage.support.DelegatingFileStorage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ScanningFileStorage extends DelegatingFileStorage {

    private final AntivirusService antivirusService;

    public ScanningFileStorage(ExtendedFileStorage delegate, AntivirusService antivirusService) {
        super(delegate);
        this.antivirusService = antivirusService;
    }

    @Override
    public void upload(String key, InputStream data, long size, String contentType) {
        Path temp = null;
        try {
            temp = Files.createTempFile("storage-scan-", ".tmp");
            long actual = Files.copy(data, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            if (antivirusService.strategy() == AntivirusService.Strategy.SYNC) {
                try (InputStream input = Files.newInputStream(temp)) {
                    VirusScanResult result = antivirusService.scan(input);
                    if (result.infected()) {
                        try (InputStream input2 = Files.newInputStream(temp)) {
                            antivirusService.quarantineStore().quarantine(key, input2, result);
                        }
                        throw new StorageException("文件未通过病毒扫描，命中特征：" + result.signature());
                    }
                    if (result.unavailable() && antivirusService.failClosed()) {
                        throw new StorageException("病毒扫描服务不可用且已配置为失败即拒绝：" + result.detail());
                    }
                    if (result.unavailable()) {
                        org.slf4j.LoggerFactory.getLogger(ScanningFileStorage.class)
                                .warn("病毒扫描服务不可用，按放行策略继续上传：{}，原因：{}", key, result.detail());
                    }
                }
            } else {
                antivirusService.scanAsync(key, temp, null);
            }
            try (InputStream input = Files.newInputStream(temp)) {
                delegate().upload(key, input, actual, contentType);
            }
        } catch (IOException ex) {
            throw new StorageException("病毒扫描前置处理失败：" + key, ex);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ex) {
                    temp.toFile().deleteOnExit();
                }
            }
        }
    }
}
