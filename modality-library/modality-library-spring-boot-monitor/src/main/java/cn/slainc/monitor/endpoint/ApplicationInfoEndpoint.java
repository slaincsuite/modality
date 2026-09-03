package cn.slainc.monitor.endpoint;

import cn.slainc.monitor.autoconfigure.MonitorProperties;
import cn.slainc.monitor.metrics.BusinessMetrics;
import cn.slainc.monitor.metrics.MetricsTemplate;
import cn.slainc.monitor.satoken.SaTokenMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.core.env.Environment;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Endpoint(id = "modality")
public class ApplicationInfoEndpoint {

    private static final String UNKNOWN = "unknown";

    private final MonitorProperties properties;

    private final Environment environment;

    private final GitInfoProvider gitInfoProvider;

    private final StartupInfoRecorder startupInfoRecorder;

    private final ObjectProvider<BusinessMetrics> businessMetrics;

    private final ObjectProvider<SaTokenMetrics> saTokenMetrics;

    private final ObjectProvider<MetricsTemplate> metricsTemplate;

    public ApplicationInfoEndpoint(MonitorProperties properties, Environment environment, GitInfoProvider gitInfoProvider,
                            StartupInfoRecorder startupInfoRecorder, ObjectProvider<BusinessMetrics> businessMetrics,
                            ObjectProvider<SaTokenMetrics> saTokenMetrics,
                            ObjectProvider<MetricsTemplate> metricsTemplate) {
        this.properties = properties;
        this.environment = environment;
        this.gitInfoProvider = gitInfoProvider;
        this.startupInfoRecorder = startupInfoRecorder;
        this.businessMetrics = businessMetrics;
        this.saTokenMetrics = saTokenMetrics;
        this.metricsTemplate = metricsTemplate;
    }

    @ReadOperation
    public Map<String, Object> info() {
        MetricsTemplate template = metricsTemplate.getIfAvailable();
        if (template != null) {
            template.increment("modality.endpoint.invocations", "endpoint", "modality");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("application", application());
        result.put("build", build());
        result.put("startup", startup());
        Map<String, String> business = properties.getInfo().getBusiness();
        result.put("business", business == null ? Map.of() : business);
        BusinessMetrics businessMetricsBean = businessMetrics.getIfAvailable();
        if (businessMetricsBean != null) {
            result.put("businessMetrics", businessMetricsBean.snapshot());
        }
        SaTokenMetrics saTokenMetricsBean = saTokenMetrics.getIfAvailable();
        if (saTokenMetricsBean != null) {
            result.put("saTokenMetrics", saTokenMetricsBean.snapshot());
        }
        return result;
    }

    private Map<String, Object> application() {
        Map<String, Object> application = new LinkedHashMap<>();
        application.put("name", environment.getProperty("spring.application.name", UNKNOWN));
        application.put("version", version());
        List<String> profiles = environment.getActiveProfiles().length > 0
                ? Arrays.asList(environment.getActiveProfiles())
                : Arrays.asList(environment.getDefaultProfiles());
        application.put("profiles", profiles);
        application.put("java", System.getProperty("java.version", UNKNOWN));
        application.put("jvm", System.getProperty("java.vm.name", UNKNOWN));
        application.put("os", System.getProperty("os.name", UNKNOWN) + " " + System.getProperty("os.arch", UNKNOWN));
        application.put("pid", ManagementFactory.getRuntimeMXBean().getName());
        return application;
    }

    private String version() {
        String version = environment.getProperty("spring.application.version");
        if (version != null && !version.isBlank()) {
            return version;
        }
        String gitVersion = gitInfoProvider.buildVersion();
        return UNKNOWN.equals(gitVersion) ? UNKNOWN : gitVersion;
    }

    private Map<String, Object> build() {
        Map<String, Object> build = new LinkedHashMap<>();
        build.put("artifact", gitInfoProvider.artifact());
        build.put("version", gitInfoProvider.buildVersion());
        build.put("git", gitInfoProvider.asMap());
        return build;
    }

    private Map<String, Object> startup() {
        Map<String, Object> startup = new LinkedHashMap<>();
        startup.put("jvmStartTime", Instant.ofEpochMilli(startupInfoRecorder.jvmStartTime()).toString());
        long readyTime = startupInfoRecorder.readyTime();
        startup.put("readyTime", readyTime == 0L ? UNKNOWN : Instant.ofEpochMilli(readyTime).toString());
        startup.put("uptimeMillis", startupInfoRecorder.uptimeMillis());
        startup.put("uptime", startupInfoRecorder.humanUptime());
        startup.put("startupMillis", startupInfoRecorder.startupMillis());
        startup.put("startup", StartupInfoRecorder.human(startupInfoRecorder.startupMillis()));
        return startup;
    }
}
