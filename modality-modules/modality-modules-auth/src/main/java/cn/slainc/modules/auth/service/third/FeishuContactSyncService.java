package cn.slainc.modules.auth.service.third;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.auth.store.FeishuSyncRecordStore;
import cn.slainc.modules.auth.third.FeishuSyncRecord;
import cn.slainc.thirdauth.ThirdPartyPlatform;
import cn.slainc.thirdauth.config.ThirdAuthProperties;
import cn.slainc.utils.json.JsonUtils;
import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class FeishuContactSyncService {

    private static final Logger log = LoggerFactory.getLogger(FeishuContactSyncService.class);

    private static final String TOKEN_URL =
            "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";

    private static final String USER_LIST_URL =
            "https://open.feishu.cn/open-apis/contact/v3/users?department_id=0&user_id_type=open_id&page_size=50";

    private static final int MAX_PAGE_COUNT = 200;

    private static final int MAX_MESSAGE_LENGTH = 512;

    private final FeishuAppConfigService configService;

    private final FeishuSyncRecordStore syncRecordStore;

    private final ThirdAuthProperties properties;

    private final HttpClient httpClient;

    public FeishuContactSyncService(FeishuAppConfigService configService,
                                    FeishuSyncRecordStore syncRecordStore,
                                    ThirdAuthProperties properties) {
        this(configService, syncRecordStore, properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds())))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    FeishuContactSyncService(FeishuAppConfigService configService, FeishuSyncRecordStore syncRecordStore,
                             ThirdAuthProperties properties, HttpClient httpClient) {
        this.configService = configService;
        this.syncRecordStore = syncRecordStore;
        this.properties = properties;
        this.httpClient = httpClient;
    }

    public record SyncRecordView(Long id, String platform, String syncType, String status, int totalCount,
                                 int successCount, int failureCount, String message, String operator,
                                 long startedAt, Long finishedAt) {

        static SyncRecordView of(FeishuSyncRecord record) {
            return new SyncRecordView(record.getId(), record.getPlatform(), record.getSyncType(),
                    record.getStatus(), value(record.getTotalCount()), value(record.getSuccessCount()),
                    value(record.getFailureCount()), record.getMessage(), record.getOperator(),
                    record.getStartedAt(), record.getFinishedAt());
        }
    }

    public SyncRecordView sync(String operator) {
        FeishuSyncRecord record = new FeishuSyncRecord();
        record.setPlatform(ThirdPartyPlatform.FEISHU.code());
        record.setSyncType(FeishuSyncRecord.TYPE_CONTACT);
        record.setStatus(FeishuSyncRecord.STATUS_RUNNING);
        record.setOperator(operator);
        record.setStartedAt(System.currentTimeMillis());
        syncRecordStore.start(record);
        try {
            String appId = configService.appId();
            String appSecret = configService.appSecret();
            if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
                throw new BizException(ResultCode.DATA_STATE_INVALID,
                        "飞书应用配置不完整，请先配置 App ID 与 App Secret");
            }
            int total = fetchUserCount(appId, appSecret);
            syncRecordStore.finish(record.getId(), FeishuSyncRecord.STATUS_SUCCESS, total, total, 0,
                    "飞书通讯录同步完成", System.currentTimeMillis());
            return SyncRecordView.of(syncRecordStore.find(record.getId()));
        } catch (BizException e) {
            syncRecordStore.finish(record.getId(), FeishuSyncRecord.STATUS_FAILED, 0, 0, 1,
                    trimMessage(e.getMessage()), System.currentTimeMillis());
            throw e;
        } catch (RuntimeException e) {
            log.warn("飞书通讯录同步失败：{}", e.getMessage());
            String message = "飞书通讯录同步失败，请检查应用凭据与通讯录权限";
            syncRecordStore.finish(record.getId(), FeishuSyncRecord.STATUS_FAILED, 0, 0, 1,
                    message, System.currentTimeMillis());
            throw new BizException(ResultCode.BAD_REQUEST, message);
        }
    }

    public PageResult<SyncRecordView> page(String status, long current, long size) {
        long normalizedCurrent = Math.max(1, current);
        long normalizedSize = Math.min(Math.max(1, size), 100);
        long total = syncRecordStore.count(status);
        List<SyncRecordView> records = syncRecordStore.page(status, normalizedCurrent, normalizedSize).stream()
                .map(SyncRecordView::of)
                .toList();
        return PageResult.of(records, total, normalizedCurrent, normalizedSize);
    }

    public List<SyncRecordView> exportList(String status) {
        return syncRecordStore.list(status).stream().map(SyncRecordView::of).toList();
    }

    public String exportCsv(String status) {
        StringBuilder csv = new StringBuilder("\uFEFF时间,类型,结果,总数,成功数,失败数,操作人,说明\r\n");
        for (SyncRecordView record : exportList(status)) {
            csv.append(csvCell(String.valueOf(record.startedAt()))).append(',')
                    .append(csvCell(record.syncType())).append(',')
                    .append(csvCell(record.status())).append(',')
                    .append(record.totalCount()).append(',')
                    .append(record.successCount()).append(',')
                    .append(record.failureCount()).append(',')
                    .append(csvCell(record.operator())).append(',')
                    .append(csvCell(record.message())).append("\r\n");
        }
        return csv.toString();
    }

    private int fetchUserCount(String appId, String appSecret) {
        String token = tenantAccessToken(appId, appSecret);
        int total = 0;
        String pageToken = null;
        for (int page = 0; page < MAX_PAGE_COUNT; page++) {
            String body = getUsers(token, pageToken);
            JsonNode root = JsonUtils.readTree(body);
            int code = responseCode(root);
            if (code != 0) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "飞书通讯录接口返回错误，错误码 " + code + responseMessage(root));
            }
            JsonNode data = root == null ? null : root.get("data");
            JsonNode items = data == null ? null : data.get("items");
            total += items != null && items.isArray() ? items.size() : 0;
            boolean hasMore = data != null && data.path("has_more").asBoolean(false);
            pageToken = text(data == null ? null : data.get("page_token"));
            if (!hasMore) {
                return total;
            }
            if (pageToken == null || pageToken.isBlank()) {
                throw new BizException(ResultCode.BAD_REQUEST, "飞书通讯录分页令牌缺失，无法继续同步");
            }
        }
        throw new BizException(ResultCode.BAD_REQUEST, "飞书通讯录分页超过安全上限，已中止同步");
    }

    private String tenantAccessToken(String appId, String appSecret) {
        String requestBody = JsonUtils.toJson(Map.of("app_id", appId, "app_secret", appSecret));
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .timeout(requestTimeout())
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        String body = send(request);
        JsonNode root = JsonUtils.readTree(body);
        int code = responseCode(root);
        String token = text(root == null ? null : root.get("tenant_access_token"));
        if (code != 0 || token == null || token.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "飞书租户访问令牌获取失败，错误码 " + code + responseMessage(root));
        }
        return token;
    }

    private String getUsers(String token, String pageToken) {
        String url = USER_LIST_URL;
        if (pageToken != null && !pageToken.isBlank()) {
            url += "&page_token=" + URLEncoder.encode(pageToken, StandardCharsets.UTF_8);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout())
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return send(request);
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String detail = responseDetail(response.body());
                log.warn("飞书接口请求失败：HTTP {}，{}", response.statusCode(), detail);
                throw new BizException(ResultCode.BAD_REQUEST,
                        "飞书接口请求失败，HTTP 状态码 " + response.statusCode() + detail);
            }
            return response.body();
        } catch (BizException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ResultCode.BAD_REQUEST, "飞书通讯录同步被中断，请稍后重试");
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST, "无法连接飞书开放平台，请检查网络与出口白名单");
        }
    }

    private Duration requestTimeout() {
        return Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds()));
    }

    private static int responseCode(JsonNode root) {
        return root == null ? -1 : root.path("code").asInt(-1);
    }

    static int responseCode(String body) {
        return responseCode(JsonUtils.readTree(body));
    }

    static String responseDetail(String body) {
        JsonNode root;
        try {
            root = JsonUtils.readTree(body);
        } catch (RuntimeException e) {
            return "";
        }
        int code = responseCode(root);
        String message = responseMessage(root);
        if (code < 0 && message.isBlank()) {
            return "";
        }
        return (code < 0 ? "" : "，错误码 " + code) + message;
    }

    private static String responseMessage(JsonNode root) {
        String message = root == null ? null : text(root.get("msg"));
        return message == null || message.isBlank() ? "" : "：" + message;
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asString();
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static String trimMessage(String message) {
        if (message == null) {
            return "飞书通讯录同步失败";
        }
        return message.length() <= MAX_MESSAGE_LENGTH ? message : message.substring(0, MAX_MESSAGE_LENGTH);
    }

    private static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String safe = value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
