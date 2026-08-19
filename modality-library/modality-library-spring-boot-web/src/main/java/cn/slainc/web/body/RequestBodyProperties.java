package cn.slainc.web.body;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "modality.web.request-body")
public class RequestBodyProperties {

    private boolean enabled = true;

    private DataSize maxRequestSize = DataSize.ofMegabytes(10);

    private DataSize maxFileSize = DataSize.ofMegabytes(1);

    private DataSize maxFormPostSize = DataSize.ofMegabytes(2);

    private DataSize maxSwallowSize = DataSize.ofMegabytes(2);

    private List<String> excludePaths = new ArrayList<>();

    private String message = "请求体过大";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public DataSize getMaxRequestSize() {
        return maxRequestSize;
    }

    public void setMaxRequestSize(DataSize maxRequestSize) {
        this.maxRequestSize = maxRequestSize;
    }

    public DataSize getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public DataSize getMaxFormPostSize() {
        return maxFormPostSize;
    }

    public void setMaxFormPostSize(DataSize maxFormPostSize) {
        this.maxFormPostSize = maxFormPostSize;
    }

    public DataSize getMaxSwallowSize() {
        return maxSwallowSize;
    }

    public void setMaxSwallowSize(DataSize maxSwallowSize) {
        this.maxSwallowSize = maxSwallowSize;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
