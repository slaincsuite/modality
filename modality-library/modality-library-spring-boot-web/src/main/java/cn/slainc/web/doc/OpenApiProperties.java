package cn.slainc.web.doc;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "modality.web.open-api")
public class OpenApiProperties {

    private boolean enabled = true;

    private String title = "Modality API";

    private String description = "Modality 平台接口文档";

    private String version = "v1";

    private String group = "default";

    private List<String> paths = new ArrayList<>(List.of("/**"));

    private List<String> packages = new ArrayList<>();

    private String bearerSchemeName = "bearerAuth";

    private boolean securityRequired = true;

    private boolean enumDisplay = true;

    private boolean errorCodes = true;

    private boolean resultWrappers = true;

    private Map<String, String> servers = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public List<String> getPaths() {
        return paths;
    }

    public void setPaths(List<String> paths) {
        this.paths = paths;
    }

    public List<String> getPackages() {
        return packages;
    }

    public void setPackages(List<String> packages) {
        this.packages = packages;
    }

    public String getBearerSchemeName() {
        return bearerSchemeName;
    }

    public void setBearerSchemeName(String bearerSchemeName) {
        this.bearerSchemeName = bearerSchemeName;
    }

    public boolean isSecurityRequired() {
        return securityRequired;
    }

    public void setSecurityRequired(boolean securityRequired) {
        this.securityRequired = securityRequired;
    }

    public boolean isEnumDisplay() {
        return enumDisplay;
    }

    public void setEnumDisplay(boolean enumDisplay) {
        this.enumDisplay = enumDisplay;
    }

    public boolean isErrorCodes() {
        return errorCodes;
    }

    public void setErrorCodes(boolean errorCodes) {
        this.errorCodes = errorCodes;
    }

    public boolean isResultWrappers() {
        return resultWrappers;
    }

    public void setResultWrappers(boolean resultWrappers) {
        this.resultWrappers = resultWrappers;
    }

    public Map<String, String> getServers() {
        return servers;
    }

    public void setServers(Map<String, String> servers) {
        this.servers = servers;
    }
}
