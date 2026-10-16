package cn.slainc.boot;

import cn.slainc.storage.s3.S3FileStorage;
import cn.slainc.web.compression.BrotliCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

// 批次 17 整机验收：启动模块补齐的运行时依赖真正可用 —— 响应 Brotli 协商与 S3 存储后端装配
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "modality.web.compression.min-response-size=1B",
        "modality.storage.s3.enabled=true",
        "modality.storage.s3.endpoint=http://127.0.0.1:19000",
        "modality.storage.s3.access-key=batch17-key",
        "modality.storage.s3.secret-key=batch17-secret",
        "modality.storage.s3.bucket=batch17-bucket"
})
@ActiveProfiles("dev")
class Batch17StorageCompressionAcceptanceTest {

    @LocalServerPort
    private int port;

    @Autowired
    private S3FileStorage s3FileStorage;

    @Autowired
    private BrotliCodec brotliCodec;

    @Test
    @DisplayName("Brotli 原生库与 S3 存储后端在启动模块开箱可用")
    void runtimeDependenciesAreAvailable() {
        assertThat(brotliCodec.available()).as("Brotli 原生库应在当前平台可用").isTrue();
        assertThat(s3FileStorage.backend()).isEqualTo("s3");
    }

    @Test
    @DisplayName("客户端同时接受两种编码时优先返回 Brotli，正文可解回原 JSON")
    void brotliNegotiatedWhenClientAcceptsBoth() throws Exception {
        HttpResponse<byte[]> response = get("gzip, br");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue(HttpHeaders.CONTENT_ENCODING).orElse(null)).isEqualTo("br");
        assertThat(new String(brotliDecode(response.body()), StandardCharsets.UTF_8)).contains("\"code\":200");
    }

    @Test
    @DisplayName("客户端只接受 gzip 时压缩行为不变")
    void gzipStillWorksWithoutBrotliAcceptance() throws Exception {
        HttpResponse<byte[]> response = get("gzip");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue(HttpHeaders.CONTENT_ENCODING).orElse(null)).isEqualTo("gzip");
    }

    private HttpResponse<byte[]> get(String acceptEncoding) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/auth/third/platforms"))
                .header(HttpHeaders.ACCEPT_ENCODING, acceptEncoding)
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    // brotli4j 只按 runtimeOnly 进启动模块，测试内用反射解码以保持依赖作用域不变
    private byte[] brotliDecode(byte[] compressed) throws Exception {
        Class<?> decoder = Class.forName("com.aayushatharva.brotli4j.decoder.Decoder");
        Object direct = decoder.getMethod("decompress", byte[].class).invoke(null, (Object) compressed);
        return (byte[]) direct.getClass().getMethod("getDecompressedData").invoke(direct);
    }
}
