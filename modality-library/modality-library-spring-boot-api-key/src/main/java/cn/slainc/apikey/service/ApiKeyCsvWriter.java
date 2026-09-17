package cn.slainc.apikey.service;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.slainc.apikey.ApiKeyRecord;
import cn.slainc.apikey.ApiKeyStatus;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ApiKeyCsvWriter {

    public static final String BOM = "\uFEFF";

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(ZoneId.systemDefault());

    private static final Map<String, String> STATUS_TEXT = Map.of(
            ApiKeyStatus.ENABLED, "启用",
            ApiKeyStatus.DISABLED, "停用",
            ApiKeyStatus.DELETED, "已删除");

    private static final String[] HEADERS = {"名称", "介绍", "API Key", "账号ID", "授权范围", "状态",
            "创建时间", "过期时间"};

    public String write(List<ApiKeyRecord> records) {
        StringBuilder builder = new StringBuilder(BOM);
        builder.append(String.join(",", HEADERS)).append('\n');
        if (records != null) {
            for (ApiKeyRecord record : records) {
                builder.append(cell(record.getTitle())).append(',')
                        .append(cell(record.getIntro())).append(',')
                        .append(cell(record.getApiKey())).append(',')
                        .append(cell(record.getLoginId())).append(',')
                        .append(cell(record.getScopes())).append(',')
                        .append(cell(statusText(record.getStatus()))).append(',')
                        .append(cell(format(record.getCreateTime()))).append(',')
                        .append(cell(formatExpiry(record.getExpiresTime()))).append('\n');
            }
        }
        return builder.toString();
    }

    private String statusText(String status) {
        return STATUS_TEXT.getOrDefault(status, status);
    }

    private String format(Long millis) {
        return millis == null ? "" : TIME_FORMAT.format(Instant.ofEpochMilli(millis));
    }

    private String formatExpiry(Long millis) {
        if (millis == null) {
            return "";
        }
        return millis == SaTokenDao.NEVER_EXPIRE ? "永不过期" : TIME_FORMAT.format(Instant.ofEpochMilli(millis));
    }

    private String cell(String value) {
        String text = value == null ? "" : value;
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
