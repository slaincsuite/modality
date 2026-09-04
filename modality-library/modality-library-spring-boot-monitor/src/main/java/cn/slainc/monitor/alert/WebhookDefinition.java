package cn.slainc.monitor.alert;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class WebhookDefinition {

    private String name;

    private WebhookPlatform platform = WebhookPlatform.CUSTOM;

    private String url;

    private String secret;

    private String keyword;

    private AlertLevel minLevel = AlertLevel.INFO;

    private boolean enabled = true;

    private String template;

    private Map<String, String> headers = new LinkedHashMap<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public WebhookPlatform getPlatform() {
        return platform;
    }

    public void setPlatform(WebhookPlatform platform) {
        this.platform = platform == null ? WebhookPlatform.CUSTOM : platform;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public AlertLevel getMinLevel() {
        return minLevel;
    }

    public void setMinLevel(AlertLevel minLevel) {
        this.minLevel = minLevel == null ? AlertLevel.INFO : minLevel;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers == null ? new LinkedHashMap<>() : headers;
    }

    public String resolvedName() {
        if (name != null && !name.isBlank()) {
            return name;
        }
        return platform.name().toLowerCase(Locale.ROOT);
    }

    public void validate() {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("告警 Webhook [" + resolvedName() + "] 缺少 url");
        }
    }

    public String signature() {
        return String.join("|", resolvedName(), platform.name(), url, text(secret), text(keyword), minLevel.name(),
                String.valueOf(enabled), text(template), headers.toString());
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
