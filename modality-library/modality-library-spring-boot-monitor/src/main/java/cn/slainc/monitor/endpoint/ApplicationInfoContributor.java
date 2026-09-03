package cn.slainc.monitor.endpoint;

import cn.slainc.monitor.autoconfigure.MonitorProperties;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class ApplicationInfoContributor implements InfoContributor {

    private final MonitorProperties properties;

    private final GitInfoProvider gitInfoProvider;

    private final StartupInfoRecorder startupInfoRecorder;

    public ApplicationInfoContributor(MonitorProperties properties, GitInfoProvider gitInfoProvider,
                                   StartupInfoRecorder startupInfoRecorder) {
        this.properties = properties;
        this.gitInfoProvider = gitInfoProvider;
        this.startupInfoRecorder = startupInfoRecorder;
    }

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("git", gitInfoProvider.asMap());
        detail.put("startup", startup());
        Map<String, String> business = properties.getInfo().getBusiness();
        if (business != null && !business.isEmpty()) {
            detail.put("business", business);
        }
        builder.withDetail("modality", detail);
    }

    private Map<String, Object> startup() {
        Map<String, Object> startup = new LinkedHashMap<>();
        startup.put("jvmStartTime", Instant.ofEpochMilli(startupInfoRecorder.jvmStartTime()).toString());
        startup.put("uptimeMillis", startupInfoRecorder.uptimeMillis());
        startup.put("uptime", startupInfoRecorder.humanUptime());
        return startup;
    }
}
