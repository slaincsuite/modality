package cn.slainc.storage.antivirus;

import cn.slainc.storage.StorageException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClamAvClientTest {

    @Test
    @DisplayName("INSTREAM 协议：干净文件返回 OK，且分片长度与内容被完整发送")
    void instreamClean() throws Exception {
        try (FakeClamAvServer server = new FakeClamAvServer("stream: OK")) {
            ClamAvClient client = new ClamAvClient("127.0.0.1", server.port(), Duration.ofSeconds(5));
            byte[] data = "这是一个用于扫描的测试文件".getBytes(StandardCharsets.UTF_8);
            VirusScanResult result = client.scan(new ByteArrayInputStream(data));
            assertThat(result.clean()).isTrue();
            assertThat(result.signature()).isNull();
            assertThat(server.scannedBytes()).isEqualTo(data.length);
        }
    }

    @Test
    @DisplayName("INSTREAM 协议：命中特征时解析出病毒名")
    void instreamInfected() throws Exception {
        try (FakeClamAvServer server = new FakeClamAvServer("stream: Eicar-Test-Signature FOUND")) {
            ClamAvClient client = new ClamAvClient("127.0.0.1", server.port(), Duration.ofSeconds(5));
            VirusScanResult result = client.scan("X5O!P%@AP".getBytes(StandardCharsets.UTF_8));
            assertThat(result.infected()).isTrue();
            assertThat(result.signature()).isEqualTo("Eicar-Test-Signature");
        }
    }

    @Test
    @DisplayName("ClamAV 不可达时返回不可用而不是抛异常")
    void unavailableWhenServerDown() {
        ClamAvClient client = new ClamAvClient("127.0.0.1", 1, Duration.ofMillis(500));
        VirusScanResult result = client.scan(new byte[]{1, 2, 3});
        assertThat(result.unavailable()).isTrue();
        assertThat(result.detail()).contains("ClamAV 服务不可达");
        assertThat(client.ping()).isFalse();
    }

    @Test
    @DisplayName("感染文件在同步策略下被拒绝并隔离，失败放行策略下继续")
    void scanningStorageRejectsInfectedFile() throws Exception {
        Path quarantine = Files.createTempDirectory("clamav-quarantine");
        try (FakeClamAvServer server = new FakeClamAvServer("stream: Win.Test.EICAR_HDB-1 FOUND")) {
            QuarantineStore store = new QuarantineStore(quarantine);
            AntivirusService service = new AntivirusService(
                    new ClamAvClient("127.0.0.1", server.port(), Duration.ofSeconds(5)), store,
                    AntivirusService.Strategy.SYNC, false);
            cn.slainc.storage.local.LocalFileStorage local = new cn.slainc.storage.local.LocalFileStorage(
                    Files.createTempDirectory("clamav-storage"));
            ScanningFileStorage storage = new ScanningFileStorage(local, service);
            assertThatThrownBy(() -> storage.upload("a.txt",
                    new ByteArrayInputStream("virus".getBytes(StandardCharsets.UTF_8)), 5, "text/plain"))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("未通过病毒扫描");
            assertThat(store.list()).hasSize(1);
        }
    }

    @Test
    @DisplayName("扫描服务不可用且 fail-closed 时拒绝上传；fail-open 时放行")
    void failClosedBehaviour() throws Exception {
        Path root = Files.createTempDirectory("clamav-open");
        cn.slainc.storage.local.LocalFileStorage local = new cn.slainc.storage.local.LocalFileStorage(root);
        AntivirusService open = new AntivirusService(new ClamAvClient("127.0.0.1", 1, Duration.ofMillis(300)),
                new QuarantineStore(root.resolve("q")), AntivirusService.Strategy.SYNC, false);
        ScanningFileStorage openStorage = new ScanningFileStorage(local, open);
        openStorage.upload("ok.txt", new ByteArrayInputStream(new byte[]{1, 2}), 2, "text/plain");
        assertThat(local.exists("ok.txt")).isTrue();

        AntivirusService closed = new AntivirusService(new ClamAvClient("127.0.0.1", 1, Duration.ofMillis(300)),
                new QuarantineStore(root.resolve("q2")), AntivirusService.Strategy.SYNC, true);
        ScanningFileStorage closedStorage = new ScanningFileStorage(local, closed);
        assertThatThrownBy(() -> closedStorage.upload("blocked.txt", new ByteArrayInputStream(new byte[]{1}), 1,
                "text/plain")).isInstanceOf(StorageException.class)
                .hasMessageContaining("失败即拒绝");
    }
}
