package cn.slainc.modules.infra.file;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;
import org.junit.jupiter.api.Test;

// 文件上传与管理：单/多上传、下载一致性、秒传、分片断点续传、回收站、改名移动、预览（无 Redis 路线，本地存储后端）
class FileModuleTest extends cn.slainc.modules.infra.InfraModuleTestBase {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    void uploadDownloadRecycleRestoreAndPurge() throws Exception {
        byte[] content = randomBytes(2048);
        HttpResponse<String> uploaded = upload("/infra/file/upload", "file", "验收文档.txt", content,
                "text/plain", Map.of("folder", "验收"));
        assertThat(uploaded.statusCode()).as(uploaded.body()).isEqualTo(200);
        String id = JsonPath.read(uploaded.body(), "$.data.id").toString();
        assertThat(JsonPath.read(uploaded.body(), "$.data.name").toString()).isEqualTo("验收文档.txt");
        assertThat(JsonPath.read(uploaded.body(), "$.data.status").toString()).isEqualTo("NORMAL");

        HttpResponse<byte[]> downloaded = sendBytes("GET", "/infra/file/" + id + "/download");
        assertThat(downloaded.statusCode()).isEqualTo(200);
        assertThat(downloaded.body()).isEqualTo(content);

        HttpResponse<byte[]> previewed = sendBytes("GET", "/infra/file/" + id + "/preview");
        assertThat(previewed.statusCode()).isEqualTo(200);
        assertThat(new String(previewed.body(), StandardCharsets.UTF_8)).isEqualTo(new String(content,
                StandardCharsets.UTF_8));

        send("DELETE", "/infra/file/" + id, null);
        assertThat(JsonPath.read(getDetail(id), "$.data.record.status").toString()).isEqualTo("RECYCLED");
        send("POST", "/infra/file/" + id + "/restore", null);
        assertThat(JsonPath.read(getDetail(id), "$.data.record.status").toString()).isEqualTo("NORMAL");

        send("POST", "/infra/file/" + id + "/rename", "{\"name\":\"改名后的文档.txt\"}");
        assertThat(JsonPath.read(getDetail(id), "$.data.record.name").toString()).isEqualTo("改名后的文档.txt");
        send("POST", "/infra/file/" + id + "/move", "{\"folder\":\"归档\"}");
        assertThat(JsonPath.read(getDetail(id), "$.data.record.folder").toString()).isEqualTo("归档");

        String url = post("/infra/file/" + id + "/signed-url", "{\"ttlMinutes\":10}", "$.data.url");
        assertThat(url).contains("signature");

        send("DELETE", "/infra/file/" + id + "/purge", null);
        HttpResponse<String> missing = send("GET", "/infra/file/" + id, null);
        assertThat(missing.statusCode()).isEqualTo(400);
        assertThat(missing.body()).contains("文件记录不存在");
    }

    @Test
    void batchUploadWithPerFileResult() throws Exception {
        byte[] good = randomBytes(64);
        byte[] another = randomBytes(128);
        HttpResponse<String> batch = uploadMulti("/infra/file/upload/batch",
                Map.of("批量A.bin", good, "批量B.bin", another), "application/octet-stream", Map.of());
        assertThat(batch.statusCode()).as(batch.body()).isEqualTo(200);
        assertThat(Integer.parseInt(JsonPath.read(batch.body(), "$.data.length()").toString())).isEqualTo(2);
        assertThat(batch.body()).contains("\"success\":true");

        String page = get("/infra/file/page?keyword=批量");
        assertThat(Integer.parseInt(JsonPath.read(page, "$.data.total").toString())).isEqualTo(2);
    }

    @Test
    void instantUploadHitsDedupByMd5() throws Exception {
        byte[] content = randomBytes(4096);
        String md5 = md5Hex(content);

        HttpResponse<String> first = upload("/infra/file/upload", "file", "秒传一.bin", content,
                "application/octet-stream", fields("md5", md5));
        assertThat(first.statusCode()).as(first.body()).isEqualTo(200);
        assertThat(JsonPath.read(first.body(), "$.data.md5").toString()).isEqualTo(md5);

        HttpResponse<String> second = upload("/infra/file/upload", "file", "秒传二.bin", content,
                "application/octet-stream", fields("md5", md5));
        assertThat(second.statusCode()).as(second.body()).isEqualTo(200);
        assertThat(JsonPath.read(second.body(), "$.data.md5").toString()).isEqualTo(md5);

        String stats = get("/infra/file/stats/instant");
        assertThat(Integer.parseInt(JsonPath.read(stats, "$.data.hits").toString())).isGreaterThanOrEqualTo(1);

        String secondId = JsonPath.read(second.body(), "$.data.id").toString();
        assertThat(sendBytes("GET", "/infra/file/" + secondId + "/download").body()).isEqualTo(content);
    }

    @Test
    void chunkUploadResumeAndComplete() throws Exception {
        int totalSize = 6 * 1024 * 1024;
        byte[] content = randomBytes(totalSize);
        String init = post("/infra/file/chunk/init", "{\"filename\":\"大文件分片.bin\",\"totalSize\":" + totalSize
                + ",\"contentType\":\"application/octet-stream\"}");
        String uploadId = JsonPath.read(init, "$.data.uploadId").toString();
        int partSize = Integer.parseInt(JsonPath.read(init, "$.data.partSize").toString());
        int totalParts = Integer.parseInt(JsonPath.read(init, "$.data.totalParts").toString());
        assertThat(totalParts).isEqualTo(2);
        assertThat(partSize).isEqualTo(5 * 1024 * 1024);

        byte[] part1 = new byte[partSize];
        byte[] part2 = new byte[totalSize - partSize];
        System.arraycopy(content, 0, part1, 0, part1.length);
        System.arraycopy(content, part1.length, part2, 0, part2.length);

        HttpResponse<String> progress1 = upload("/infra/file/chunk/" + uploadId + "/part", "file", "p1", part1,
                "application/octet-stream", fields("partNumber", "1"));
        assertThat(progress1.statusCode()).as(progress1.body()).isEqualTo(200);
        assertThat(progress1.body()).contains("\"completedParts\":1");

        String partsView = get("/infra/file/chunk/" + uploadId + "/parts");
        assertThat(Integer.parseInt(JsonPath.read(partsView, "$.data.pendingPartNumbers[0]").toString()))
                .isEqualTo(2);

        HttpResponse<String> progress2 = upload("/infra/file/chunk/" + uploadId + "/part", "file", "p2", part2,
                "application/octet-stream", fields("partNumber", "2"));
        assertThat(progress2.body()).contains("\"percent\":100.0");

        String fileId = post("/infra/file/chunk/" + uploadId + "/complete", null, "$.data.id");
        HttpResponse<byte[]> downloaded = sendBytes("GET", "/infra/file/" + fileId + "/download");
        assertThat(downloaded.body()).isEqualTo(content);
    }

    @Test
    void chunkAbortRemovesTask() throws Exception {
        String init = post("/infra/file/chunk/init",
                "{\"filename\":\"中止.bin\",\"totalSize\":5242880,\"contentType\":\"application/octet-stream\"}");
        String uploadId = JsonPath.read(init, "$.data.uploadId").toString();
        post("/infra/file/chunk/" + uploadId + "/abort", null);
        HttpResponse<String> missing = send("GET", "/infra/file/chunk/" + uploadId + "/parts", null);
        assertThat(missing.statusCode()).isEqualTo(400);
        assertThat(missing.body()).contains("分片上传任务不存在");
    }

    private String getDetail(String id) throws Exception {
        return raw("GET", "/infra/file/" + id, null);
    }

    private static byte[] randomBytes(int size) {
        byte[] data = new byte[size];
        RANDOM.nextBytes(data);
        return data;
    }
}
