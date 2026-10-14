package cn.slainc.boot;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.mapper.UserMapper;
import cn.slainc.modules.upms.security.UpmsAccountAuthProvider;
import cn.slainc.modules.upms.service.permission.PermissionService;
import cn.slainc.modules.upms.service.role.RoleService;
import cn.slainc.modules.upms.service.user.UserService;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

// 批次 12 整机验收：动态配置管理台、文件管理、验证码、通知、Redis/SQL/日志/缓存/系统监控台（真实 Tomcat + 真实 Redis + H2）
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "modality.storage.signed-url.base-url=http://127.0.0.1")
@ActiveProfiles("dev")
class Batch12InfraAcceptanceTest {

    private static final String PASSWORD = "Password#123";

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private DynamicConfigService configService;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private PermissionService permissionService;

    @LocalServerPort
    private int port;

    private com.sun.net.httpserver.HttpServer webhookServer;

    private final java.util.concurrent.atomic.AtomicInteger webhookHits = new java.util.concurrent.atomic.AtomicInteger();

    @TestConfiguration(proxyBeanMethods = false)
    static class DbAccountConfiguration {

        @Bean
        @Primary
        UpmsAccountAuthProvider batch12DbAccountProvider(UserMapper userMapper) {
            return new UpmsAccountAuthProvider(userMapper);
        }
    }

    @BeforeEach
    void resetState() throws Exception {
        TenantContext.set(1L);
        var staleCaches = redis.keys("modality:security:perm:*");
        if (staleCaches != null && !staleCaches.isEmpty()) {
            redis.delete(staleCaches);
        }
        var staleCaptcha = redis.keys("modality:captcha:*");
        if (staleCaptcha != null && !staleCaptcha.isEmpty()) {
            redis.delete(staleCaptcha);
        }
        redis.delete("modality:config:store");
        for (String table : new String[]{"t_infra_config_history", "t_infra_dict_type", "t_infra_dict_item",
                "t_infra_notice", "t_infra_notify_template", "t_infra_notify_message", "t_infra_notify_webhook",
                "t_infra_notify_record", "t_infra_file_record", "t_dynamic_config", "t_audit_log", "t_user_role",
                "t_role_permission", "t_permission", "t_role", "t_user"}) {
            try {
                jdbc.execute("DELETE FROM " + table);
            } catch (org.springframework.dao.DataAccessException ignored) {
                // 表可能懒建
            }
        }
        startWebhookServer();
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        if (webhookServer != null) {
            webhookServer.stop(0);
        }
    }

    @Test
    @DisplayName("动态配置管理台：加密落库、历史回滚、广播生效、审计联动、导入导出")
    void dynamicConfigAdminFullChain() throws Exception {
        var operator = prepareOperator();
        String token = login(operator.getUsername());

        post(token, "/infra/config", "{\"key\":\"batch12.plain\",\"value\":\"v1\",\"group\":\"批次12\","
                + "\"description\":\"普通配置\"}", "$.code");
        post(token, "/infra/config", "{\"key\":\"batch12.secret\",\"value\":\"plain-secret\",\"group\":\"批次12\","
                + "\"encrypted\":true}", "$.code");

        String cipher = jdbc.queryForObject(
                "SELECT config_value FROM t_dynamic_config WHERE config_key = 'batch12.secret'", String.class);
        assertThat(cipher).isNotEqualTo("plain-secret");
        assertThat(configService.getEncrypted("batch12.secret")).isEqualTo("plain-secret");
        assertThat(configService.getString("batch12.plain", null)).isEqualTo("v1");

        String secretDetail = send("GET", "/infra/config/batch12.secret", token, null).body();
        assertThat(secretDetail).contains("\"encrypted\":true");
        assertThat(secretDetail).doesNotContain("plain-secret");

        send("PUT", "/infra/config/batch12.plain", token, "{\"value\":\"v2\"}");
        assertThat(configService.getString("batch12.plain", null)).isEqualTo("v2");

        String historyId = JsonPath.read(
                get(token, "/infra/config/history/page?key=batch12.plain"), "$.data.records[0].id").toString();
        post(token, "/infra/config/history/" + historyId + "/rollback", null, "$.code");
        assertThat(configService.getString("batch12.plain", null)).isEqualTo("v1");

        Integer auditRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_audit_log WHERE category = 'SYSTEM' AND operation = 'CONFIG'"
                        + " AND target IN ('batch12.plain', 'batch12.secret')", Integer.class);
        assertThat(auditRows).isGreaterThanOrEqualTo(3);

        String exported = send("GET", "/infra/config/export?group=批次12", token, null).body();
        assertThat(exported).contains("batch12.plain");
        String summary = post(token, "/infra/config/import", "{\"items\":[{\"key\":\"batch12.imported\","
                + "\"value\":\"导入值\",\"group\":\"批次12\"}]}", "$.data.succeeded");
        assertThat(summary).isEqualTo("1");
        assertThat(configService.getString("batch12.imported", null)).isEqualTo("导入值");
    }

    @Test
    @DisplayName("文件管理：上传/下载/预览/秒传/分片断点续传/回收站/物理删除/签名 URL")
    void fileManagementFullChain() throws Exception {
        var operator = prepareOperator();
        String token = login(operator.getUsername());

        byte[] content = randomBytes(4096);
        String md5 = md5Hex(content);
        String uploaded = upload(token, "/infra/file/upload", "验收.txt", content, "text/plain", Map.of("md5", md5))
                .body();
        String id = JsonPath.read(uploaded, "$.data.id").toString();
        assertThat(Long.parseLong(JsonPath.read(uploaded, "$.data.uploaderId").toString()))
                .isEqualTo(operator.getId());

        HttpResponse<byte[]> downloaded = sendBytes(token, "GET", "/infra/file/" + id + "/download");
        assertThat(downloaded.body()).isEqualTo(content);
        HttpResponse<byte[]> previewed = sendBytes(token, "GET", "/infra/file/" + id + "/preview");
        assertThat(new String(previewed.body(), StandardCharsets.UTF_8))
                .isEqualTo(new String(content, StandardCharsets.UTF_8));

        HttpResponse<String> instant = upload(token, "/infra/file/upload", "秒传.txt", content, "text/plain",
                Map.of("md5", md5));
        assertThat(instant.statusCode()).as(instant.body()).isEqualTo(200);

        int totalSize = 6 * 1024 * 1024;
        byte[] big = randomBytes(totalSize);
        HttpResponse<String> initResponse = send("POST", "/infra/file/chunk/init", token,
                "{\"filename\":\"分片大文件.bin\",\"totalSize\":" + totalSize
                        + ",\"contentType\":\"application/octet-stream\"}");
        assertThat(initResponse.statusCode()).as(initResponse.body()).isEqualTo(200);
        String init = initResponse.body();
        String uploadId = JsonPath.read(init, "$.data.uploadId").toString();
        int partSize = Integer.parseInt(JsonPath.read(init, "$.data.partSize").toString());
        upload(token, "/infra/file/chunk/" + uploadId + "/part", "p1", head(big, partSize),
                "application/octet-stream", Map.of("partNumber", "1"));
        String parts = get(token, "/infra/file/chunk/" + uploadId + "/parts");
        assertThat(Integer.parseInt(JsonPath.read(parts, "$.data.pendingPartNumbers.length()").toString()))
                .isEqualTo(1);
        upload(token, "/infra/file/chunk/" + uploadId + "/part", "p2", tail(big, partSize),
                "application/octet-stream", Map.of("partNumber", "2"));
        String chunkFileId = post(token, "/infra/file/chunk/" + uploadId + "/complete", null, "$.data.id");
        assertThat(sendBytes(token, "GET", "/infra/file/" + chunkFileId + "/download").body()).isEqualTo(big);

        String url = post(token, "/infra/file/" + id + "/signed-url", "{\"ttlMinutes\":5}", "$.data.url");
        assertThat(url).contains("signature");

        send("DELETE", "/infra/file/" + id, token, null);
        assertThat(get(token, "/infra/file/" + id)).contains("RECYCLED");
        post(token, "/infra/file/" + id + "/restore", null, "$.code");
        post(token, "/infra/file/" + id + "/move", "{\"folder\":\"批次12归档\"}", "$.code");
        send("DELETE", "/infra/file/" + id + "/purge", token, null);
        HttpResponse<String> missing = send("GET", "/infra/file/" + id, token, null);
        assertThat(missing.statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("验证码：匿名生成四类、答案只存 Redis、一次性校验即焚")
    void captchaAnonymousGenerateAndVerify() throws Exception {
        var operator = prepareOperator();
        String token = login(operator.getUsername());

        HttpResponse<String> types = send("GET", "/captcha/types", token, null);
        assertThat(types.statusCode()).isEqualTo(200);
        assertThat(types.body()).contains("ARITHMETIC").contains("SLIDE").contains("CLICK");

        String anonymousGenerate = send("POST", "/captcha/generate",
                null, "{\"type\":\"ARITHMETIC\"}").body();
        assertThat(anonymousGenerate).contains("imageBase64");
        String captchaId = JsonPath.read(anonymousGenerate, "$.data.id").toString();

        String stored = redis.opsForValue().get("modality:captcha:data:WEB:" + captchaId);
        assertThat(stored).isNotBlank();
        String answer = JsonPath.read(stored, "$.answer").toString();

        String verified = send("POST", "/captcha/verify", null,
                "{\"captchaId\":\"" + captchaId + "\",\"code\":\"" + answer + "\"}").body();
        assertThat(JsonPath.read(verified, "$.data.success").toString()).isEqualTo("true");
        assertThat(redis.opsForValue().get("modality:captcha:data:WEB:" + captchaId)).isNull();

        String replay = send("POST", "/captcha/verify", null,
                "{\"captchaId\":\"" + captchaId + "\",\"code\":\"" + answer + "\"}").body();
        assertThat(JsonPath.read(replay, "$.data.success").toString()).isEqualTo("false");

        String slide = send("POST", "/captcha/generate", null, "{\"type\":\"SLIDE\"}").body();
        assertThat(slide).contains("sliderImageBase64").doesNotContain("slideX");
    }

    @Test
    @DisplayName("消息通知：模板站内信已读闭环、Webhook 真实发送、失败重试")
    void notifyInAppWebhookAndRetry() throws Exception {
        var operator = prepareOperator();
        String token = login(operator.getUsername());
        long receiverId = operator.getId();

        post(token, "/infra/notify/templates", "{\"code\":\"batch12.done\",\"name\":\"批次12通知\","
                + "\"titleTemplate\":\"任务 {task} 完成\",\"contentTemplate\":\"经办人 {owner}，任务 {task} 已完成\"}",
                "$.code");
        post(token, "/infra/notify/in-app", "{\"templateCode\":\"batch12.done\",\"receivers\":[" + receiverId + "],"
                + "\"params\":{\"task\":\"验收\",\"owner\":\"batch12-operator\"}}", "$.data");

        assertThat(Integer.parseInt(JsonPath.read(get(token, "/infra/notify/my/unread-count"), "$.data").toString()))
                .isEqualTo(1);
        assertThat(get(token, "/infra/notify/my/page")).contains("任务 验收 完成");
        long messageId = Long.parseLong(JsonPath.read(get(token, "/infra/notify/my/page?read=false"),
                "$.data.records[0].id").toString());
        post(token, "/infra/notify/my/" + messageId + "/read", null, "$.code");
        assertThat(Integer.parseInt(JsonPath.read(get(token, "/infra/notify/my/unread-count"), "$.data").toString()))
                .isZero();

        post(token, "/infra/notify/webhooks", "{\"name\":\"批次12钩子\",\"platform\":\"DINGTALK\","
                + "\"url\":\"http://127.0.0.1:" + webhookServer.getAddress().getPort() + "/hook\",\"secret\":"
                + "\"batch12-secret\"}", "$.code");
        long webhookId = Long.parseLong(JsonPath.read(get(token, "/infra/notify/webhooks/page"),
                "$.data.records[0].id").toString());
        String status = post(token, "/infra/notify/webhooks/" + webhookId + "/send",
                "{\"params\":{\"title\":\"验收通知\",\"content\":\"来自批次12\"}}", "$.data.status");
        assertThat(status).isEqualTo("SUCCESS");
        assertThat(webhookHits.get()).isEqualTo(1);

        jdbc.update("UPDATE t_infra_notify_webhook SET url = 'http://127.0.0.1:9/hook' WHERE id = ?", webhookId);
        HttpResponse<String> failed = send("POST", "/infra/notify/webhooks/" + webhookId + "/send", token,
                "{\"params\":{\"title\":\"失败通知\",\"content\":\"打不通\"}}");
        assertThat(failed.statusCode()).isEqualTo(502);
        String failedId = JsonPath.read(get(token, "/infra/notify/records/page?status=FAILED"),
                "$.data.records[0].id").toString();

        jdbc.update("UPDATE t_infra_notify_webhook SET url = 'http://127.0.0.1:"
                + webhookServer.getAddress().getPort() + "/hook' WHERE id = ?", webhookId);
        String retried = post(token, "/infra/notify/records/" + failedId + "/retry", null, "$.data.status");
        assertThat(retried).isEqualTo("SUCCESS");
        assertThat(webhookHits.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Redis 与缓存监控台：键扫描/详情/删除、内存统计、客户端列表、缓存名与配置预热")
    void redisAndCacheConsoleOverRealRedis() throws Exception {
        var operator = prepareOperator();
        String token = login(operator.getUsername());

        redis.opsForValue().set("modality:batch12:probe", "probe-value");
        String keys = get(token, "/infra/redis/keys?prefix=modality:batch12");
        assertThat(keys).contains("modality:batch12:probe");

        String detail = get(token, "/infra/redis/key/modality:batch12:probe");
        assertThat(detail).contains("probe-value").contains("string");

        String stats = get(token, "/infra/redis/stats");
        assertThat(Integer.parseInt(JsonPath.read(stats, "$.data.dbSize").toString())).isGreaterThan(0);
        assertThat(JsonPath.read(stats, "$.data.mode").toString()).isEqualTo("STANDALONE");
        assertThat(Long.parseLong(JsonPath.read(stats, "$.data.usedMemoryBytes").toString())).isGreaterThan(0);

        assertThat(get(token, "/infra/redis/clients")).contains("addr");
        assertThat(get(token, "/infra/redis/slowlog")).contains("[");

        post(token, "/infra/cache/warm", null, "$.code");
        String cacheNames = get(token, "/infra/cache/names");
        assertThat(cacheNames).contains("[");

        HttpResponse<String> removed = send("DELETE", "/infra/redis/key/modality:batch12:probe", token, null);
        assertThat(removed.statusCode()).as(removed.body()).isEqualTo(200);
        assertThat(JsonPath.read(removed.body(), "$.data").toString()).isEqualTo("true");
        assertThat(redis.hasKey("modality:batch12:probe")).isFalse();
    }

    @Test
    @DisplayName("SQL 与系统监控台：explain 只读、慢 SQL 榜、健康状态、测试告警通道")
    void sqlAndSystemConsole() throws Exception {
        var operator = prepareOperator();
        String token = login(operator.getUsername());

        String explain = post(token, "/infra/sql/explain",
                "{\"sql\":\"SELECT COUNT(*) FROM t_user\"}", "$.data[0]");
        assertThat(explain).isNotBlank();

        HttpResponse<String> danger = send("POST", "/infra/sql/explain", token,
                "{\"sql\":\"UPDATE t_user SET status = 0\"}");
        assertThat(danger.statusCode()).isEqualTo(400);
        assertThat(danger.body()).contains("只接受 SELECT / WITH");

        String stats = get(token, "/infra/sql/stats");
        assertThat(stats).contains("countByType");

        String health = get(token, "/infra/system/health");
        assertThat(JsonPath.read(health, "$.data.status").toString()).isEqualTo("UP");

        int sent = Integer.parseInt(post(token, "/infra/system/alerts/test",
                "{\"level\":\"WARN\",\"message\":\"批次12测试告警\"}", "$.data"));
        assertThat(sent).isGreaterThanOrEqualTo(1);

        String rules = get(token, "/infra/system/alerts/rules");
        assertThat(Integer.parseInt(JsonPath.read(rules, "$.data.count").toString())).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("系统日志：级别动态调整即时生效、日志查询命中运行日志、文件清单可下载")
    void logConsoleOverRealApp() throws Exception {
        var operator = prepareOperator();
        String token = login(operator.getUsername());

        HttpResponse<String> setDebug = send("PUT", "/infra/logs/level", token,
                "{\"logger\":\"cn.slainc.boot\",\"level\":\"DEBUG\"}");
        assertThat(setDebug.statusCode()).as(setDebug.body()).isEqualTo(200);
        assertThat(JsonPath.read(get(token, "/infra/logs/level?logger=cn.slainc.boot"), "$.data").toString())
                .isEqualTo("DEBUG");
        send("PUT", "/infra/logs/level", token, "{\"logger\":\"cn.slainc.boot\",\"level\":\"INFO\"}");

        org.slf4j.LoggerFactory.getLogger("batch12.probe").info("batch12-log-probe-{}", System.currentTimeMillis());
        String page = "";
        for (int attempt = 0; attempt < 20; attempt++) {
            page = get(token, "/infra/logs/page?keyword=batch12-log-probe");
            if (Integer.parseInt(JsonPath.read(page, "$.data.total").toString()) >= 1) {
                break;
            }
            Thread.sleep(300);
        }
        assertThat(Integer.parseInt(JsonPath.read(page, "$.data.total").toString())).isGreaterThanOrEqualTo(1);

        HttpResponse<String> files = send("GET", "/infra/logs/files", token, null);
        assertThat(files.body()).contains(".log");
    }

    @Test
    @DisplayName("字典与公告：缓存失效即时生效、Excel 往返、公告发布生效窗口")
    void dictAndNoticeChains() throws Exception {
        var operator = prepareOperator();
        String token = login(operator.getUsername());

        post(token, "/infra/dict/types", "{\"code\":\"batch12.status\",\"name\":\"批次12状态\"}", "$.code");
        post(token, "/infra/dict/items", "{\"typeCode\":\"batch12.status\",\"label\":\"进行中\",\"value\":"
                + "\"RUNNING\"}", "$.code");
        assertThat(JsonPath.read(get(token, "/infra/dict/resolve?typeCode=batch12.status&value=RUNNING"),
                "$.data").toString()).isEqualTo("进行中");
        long itemId = Long.parseLong(JsonPath.read(get(token, "/infra/dict/items/page?typeCode=batch12.status"),
                "$.data.records[0].id").toString());
        send("PUT", "/infra/dict/items/" + itemId, token, "{\"label\":\"处理中\"}");
        assertThat(JsonPath.read(get(token, "/infra/dict/resolve?typeCode=batch12.status&value=RUNNING"),
                "$.data").toString()).isEqualTo("处理中");

        HttpResponse<byte[]> exported = sendBytes(token, "GET", "/infra/dict/export?typeCode=batch12.status");
        assertThat((exported.body()[0] & 0xFF) == 0x50 && (exported.body()[1] & 0xFF) == 0x4B).isTrue();
        HttpResponse<String> imported = upload(token, "/infra/dict/import", "dict.xlsx", exported.body(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Map.of());
        assertThat(Integer.parseInt(JsonPath.read(imported.body(), "$.data.succeeded").toString())).isEqualTo(1);

        String noticeId = post(token, "/infra/notice", "{\"title\":\"批次12公告\",\"content\":\"验收公告内容\"}",
                "$.data.id");
        post(token, "/infra/notice/" + noticeId + "/publish", null, "$.code");
        assertThat(get(token, "/infra/notice/active")).contains("批次12公告");
        long expiredId = Long.parseLong(post(token, "/infra/notice", "{\"title\":\"过期公告\",\"content\":\"x\","
                + "\"expireTime\":" + (System.currentTimeMillis() - 60000) + "}", "$.data.id"));
        post(token, "/infra/notice/" + expiredId + "/publish", null, "$.code");
        assertThat(get(token, "/infra/notice/active")).doesNotContain("过期公告");
    }

    private UserRecord prepareOperator() {
        List<String> codes = List.of("infra:config:query", "infra:config:create", "infra:config:update",
                "infra:config:delete", "infra:config:rollback", "infra:config:transfer", "infra:dict:query",
                "infra:dict:create", "infra:dict:update", "infra:dict:delete", "infra:dict:transfer",
                "infra:notice:query", "infra:notice:create", "infra:notice:update", "infra:notice:delete",
                "infra:notify:query", "infra:notify:create", "infra:notify:update", "infra:notify:delete",
                "infra:notify:send", "infra:notify:retry", "infra:file:query", "infra:file:upload",
                "infra:file:delete", "infra:file:manage", "infra:redis:query", "infra:redis:delete",
                "infra:sql:query", "infra:log:query", "infra:log:level", "infra:cache:query", "infra:cache:clear",
                "infra:system:query", "infra:system:alert");
        List<Long> permissionIds = codes.stream()
                .map(code -> permissionService.create(code, "批次12-" + code, "API", null, null, null).getId())
                .toList();
        var role = roleService.create("batch12-operator", "批次12验收员", null, null, null, null, null);
        roleService.assignPermissions(role.getId(), permissionIds);
        var user = userService.create("batch12-operator", null, PASSWORD, null, null, null,
                LoginType.TENANT_USER, 1L, "tester");
        userService.assignRoles(user.getId(), List.of(role.getId()));
        return user;
    }

    private String login(String username) throws Exception {
        HttpResponse<String> response = send("POST", "/auth/login", null, """
                {"username":"%s","password":"%s","deviceType":"PC","deviceId":"batch12-device"}
                """.formatted(username, PASSWORD));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.parse(response.body()).read("$.data.tokenValue");
    }

    private String get(String token, String path) throws Exception {
        return send("GET", path, token, null).body();
    }

    private String post(String token, String path, String body, String jsonPath) throws Exception {
        HttpResponse<String> response = send("POST", path, token, body);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.read(response.body(), jsonPath).toString();
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("X-Login-Type", "TENANT_USER");
        if (token != null) {
            builder.header("satoken", token);
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> sendBytes(String token, String method, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(60))
                .header("X-Login-Type", "TENANT_USER")
                .header("satoken", token)
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<String> upload(String token, String path, String filename, byte[] content,
                                        String contentType, Map<String, String> fields) throws Exception {
        String boundary = "batch12-" + UUID.randomUUID();
        List<byte[]> parts = new ArrayList<>();
        StringBuilder head = new StringBuilder();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            head.append("--").append(boundary).append("\r\n")
                    .append("Content-Disposition: form-data; name=\"").append(field.getKey()).append("\"\r\n\r\n")
                    .append(field.getValue()).append("\r\n");
        }
        parts.add(head.toString().getBytes(StandardCharsets.UTF_8));
        parts.add(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + filename + "\"\r\nContent-Type: " + contentType + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        parts.add(content);
        parts.add(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        int total = parts.stream().mapToInt(part -> part.length).sum();
        byte[] body = new byte[total];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, body, offset, part.length);
            offset += part.length;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-Login-Type", "TENANT_USER")
                .header("satoken", token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void startWebhookServer() throws IOException {
        webhookServer = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        webhookServer.createContext("/hook", exchange -> {
            webhookHits.incrementAndGet();
            byte[] response = "{\"errcode\":0}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response);
            }
        });
        webhookServer.start();
    }

    private static byte[] randomBytes(int size) {
        byte[] data = new byte[size];
        RANDOM.nextBytes(data);
        return data;
    }

    private static byte[] head(byte[] data, int length) {
        byte[] result = new byte[length];
        System.arraycopy(data, 0, result, 0, length);
        return result;
    }

    private static byte[] tail(byte[] data, int from) {
        byte[] result = new byte[data.length - from];
        System.arraycopy(data, from, result, 0, result.length);
        return result;
    }

    private static String md5Hex(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(data);
        StringBuilder builder = new StringBuilder();
        for (byte b : digest) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
