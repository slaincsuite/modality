package cn.slainc.storage.antivirus;

import cn.slainc.storage.local.LocalFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class ScanningFileStorageTest {

    @TempDir
    Path directory;

    @Test
    void asyncScanReadsContentAndCallbackDeletesTempFile() throws Exception {
        Path storageRoot = directory.resolve("storage");
        ProbeAntivirusService antivirus = new ProbeAntivirusService(directory.resolve("quarantine"),
                VirusScanResult.clean("未发现威胁"));
        ScanningFileStorage storage = new ScanningFileStorage(new LocalFileStorage("local", storageRoot, null),
                antivirus);
        byte[] content = "异步扫描内容".getBytes(StandardCharsets.UTF_8);
        try {
            storage.upload("dir/async.txt", new ByteArrayInputStream(content), content.length, "text/plain");

            assertThat(antivirus.scannedBytes()).isEqualTo(content);
            assertThat(Files.exists(antivirus.scannedFile().get())).isFalse();
        } finally {
            antivirus.close();
        }
        try (InputStream input = storage.download("dir/async.txt")) {
            assertThat(input.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void asyncInfectedObjectIsRemovedAndQuarantined() throws Exception {
        Path storageRoot = directory.resolve("storage");
        ProbeAntivirusService antivirus = new ProbeAntivirusService(directory.resolve("quarantine"),
                VirusScanResult.infected("Eicar-Test-Signature", "测试特征"));
        ScanningFileStorage storage = new ScanningFileStorage(new LocalFileStorage("local", storageRoot, null),
                antivirus);
        byte[] content = "感染内容".getBytes(StandardCharsets.UTF_8);
        try {
            storage.upload("dir/infected.txt", new ByteArrayInputStream(content), content.length, "text/plain");

            assertThat(storage.exists("dir/infected.txt")).isFalse();
            assertThat(antivirus.quarantineStore().list()).hasSize(1);
            assertThat(Files.exists(antivirus.scannedFile().get())).isFalse();
        } finally {
            antivirus.close();
        }
    }

    private static final class ProbeAntivirusService extends AntivirusService {

        private final VirusScanResult result;

        private final AtomicReference<Path> scannedFile = new AtomicReference<>();

        private volatile byte[] scannedBytes;

        private ProbeAntivirusService(Path quarantineDirectory, VirusScanResult result) {
            super(new ClamAvClient("127.0.0.1", 1, Duration.ofMillis(1)), new QuarantineStore(quarantineDirectory),
                    Strategy.ASYNC, true);
            this.result = result;
        }

        @Override
        public void scanAsync(String key, Path file, Consumer<VirusScanResult> callback) {
            scannedFile.set(file);
            try {
                scannedBytes = Files.readAllBytes(file);
            } catch (IOException ex) {
                scannedBytes = null;
            }
            if (callback != null) {
                callback.accept(result);
            }
        }

        private byte[] scannedBytes() {
            return scannedBytes;
        }

        private AtomicReference<Path> scannedFile() {
            return scannedFile;
        }
    }
}
