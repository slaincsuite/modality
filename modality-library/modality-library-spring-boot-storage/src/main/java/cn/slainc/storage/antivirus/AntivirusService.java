package cn.slainc.storage.antivirus;

import cn.slainc.storage.StorageException;
import cn.slainc.utils.thread.ThreadPoolUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class AntivirusService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AntivirusService.class);

    private final ClamAvClient client;

    private final QuarantineStore quarantineStore;

    private final Strategy strategy;

    private final boolean failClosed;

    private final ExecutorService executor;

    public AntivirusService(ClamAvClient client, QuarantineStore quarantineStore, Strategy strategy,
                            boolean failClosed) {
        this.client = client;
        this.quarantineStore = quarantineStore;
        this.strategy = strategy;
        this.failClosed = failClosed;
        this.executor = Executors.newFixedThreadPool(2, ThreadPoolUtils.namedThreadFactory("storage-antivirus"));
    }

    public VirusScanResult scan(InputStream data) {
        return client.scan(data);
    }

    public void scanOrReject(String key, InputStream data) {
        VirusScanResult result = client.scan(data);
        handle(key, result);
    }

    public void scanAsync(String key, Path file, Consumer<VirusScanResult> callback) {
        CompletableFuture.runAsync(() -> {
            VirusScanResult result;
            try (InputStream input = Files.newInputStream(file)) {
                result = client.scan(input);
            } catch (IOException ex) {
                result = VirusScanResult.unavailable("读取待扫描文件失败：" + ex.getMessage());
            }
            try {
                handle(key, result);
            } catch (StorageException ex) {
                log.warn("异步病毒扫描发现风险：{}，原因：{}", key, ex.getMessage());
            }
            if (callback != null) {
                // 回调承担临时文件清理与感染对象移除，异常分支也必须执行
                try {
                    callback.accept(result);
                } catch (RuntimeException ex) {
                    log.warn("异步病毒扫描回调处理失败：{}，原因：{}", key, ex.getMessage());
                }
            }
        }, executor).exceptionally(ex -> {
            log.warn("异步病毒扫描执行异常：{}，原因：{}", key, ex.getMessage());
            return null;
        });
    }

    private void handle(String key, VirusScanResult result) {
        if (result.infected()) {
            log.error("检测到感染文件：{}，特征：{}，已拒绝入库", key, result.signature());
            throw new StorageException("文件未通过病毒扫描，命中特征：" + result.signature());
        }
        if (result.unavailable() && failClosed) {
            throw new StorageException("病毒扫描服务不可用且已配置为失败即拒绝：" + result.detail());
        }
        if (result.unavailable()) {
            log.warn("病毒扫描服务不可用，按放行策略继续：{}，原因：{}", key, result.detail());
        }
    }

    public QuarantineStore quarantineStore() {
        return quarantineStore;
    }

    public Strategy strategy() {
        return strategy;
    }

    public boolean failClosed() {
        return failClosed;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            executor.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public enum Strategy {

        SYNC,
        ASYNC
    }
}
