package cn.slainc.openapi.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "modality.open-api")
public class OpenApiProperties {

    private boolean enabled = true;

    private boolean generatedCredentialEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isGeneratedCredentialEnabled() {
        return generatedCredentialEnabled;
    }

    public void setGeneratedCredentialEnabled(boolean generatedCredentialEnabled) {
        this.generatedCredentialEnabled = generatedCredentialEnabled;
    }
}
