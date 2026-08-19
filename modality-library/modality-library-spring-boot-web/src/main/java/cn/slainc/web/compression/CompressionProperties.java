package cn.slainc.web.compression;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "modality.web.compression")
public class CompressionProperties {

    private boolean enabled = true;

    private DataSize minResponseSize = DataSize.ofKilobytes(2);

    private List<String> mimeTypes = new ArrayList<>(List.of("text/html", "text/xml", "text/plain", "text/css",
            "text/javascript", "application/javascript", "application/json", "application/xml"));

    private List<String> excludedUserAgents = new ArrayList<>();

    private final Brotli brotli = new Brotli();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public DataSize getMinResponseSize() {
        return minResponseSize;
    }

    public void setMinResponseSize(DataSize minResponseSize) {
        this.minResponseSize = minResponseSize;
    }

    public List<String> getMimeTypes() {
        return mimeTypes;
    }

    public void setMimeTypes(List<String> mimeTypes) {
        this.mimeTypes = mimeTypes;
    }

    public List<String> getExcludedUserAgents() {
        return excludedUserAgents;
    }

    public void setExcludedUserAgents(List<String> excludedUserAgents) {
        this.excludedUserAgents = excludedUserAgents;
    }

    public Brotli getBrotli() {
        return brotli;
    }

    public static class Brotli {

        private boolean enabled = true;

        private int quality = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getQuality() {
            return quality;
        }

        public void setQuality(int quality) {
            this.quality = quality;
        }
    }
}
