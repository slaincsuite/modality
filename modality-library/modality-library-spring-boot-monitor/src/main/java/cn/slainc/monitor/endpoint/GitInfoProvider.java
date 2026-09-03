package cn.slainc.monitor.endpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class GitInfoProvider {

    private static final Logger log = LoggerFactory.getLogger(GitInfoProvider.class);

    public static final String UNKNOWN = "unknown";

    private static final String DEFAULT_RESOURCE = "git.properties";

    private final Map<String, String> properties;

    public GitInfoProvider() {
        this(DEFAULT_RESOURCE);
    }

    public GitInfoProvider(String resource) {
        this.properties = load(resource);
    }

    public boolean available() {
        return !properties.isEmpty();
    }

    public String commit() {
        return first("git.commit.id.abbrev", "git.commit.id", UNKNOWN);
    }

    public String commitFull() {
        return first("git.commit.id.full", "git.commit.id", UNKNOWN);
    }

    public String branch() {
        return first("git.branch", UNKNOWN);
    }

    public String commitTime() {
        return first("git.commit.time", "git.build.time", UNKNOWN);
    }

    public String buildVersion() {
        return first("git.build.version", UNKNOWN);
    }

    public String artifact() {
        return first("git.build.artifact", UNKNOWN);
    }

    public Map<String, String> asMap() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("available", String.valueOf(available()));
        info.put("commit", commit());
        info.put("commitFull", commitFull());
        info.put("branch", branch());
        info.put("commitTime", commitTime());
        info.put("version", buildVersion());
        info.put("artifact", artifact());
        return info;
    }

    private String first(String key, String defaultValue) {
        return first(key, key, defaultValue);
    }

    private String first(String primary, String secondary, String defaultValue) {
        String value = properties.get(primary);
        if (value == null || value.isBlank()) {
            value = properties.get(secondary);
        }
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static Map<String, String> load(String resource) {
        Map<String, String> loaded = new LinkedHashMap<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = GitInfoProvider.class.getClassLoader();
        }
        try (InputStream stream = classLoader == null ? null : classLoader.getResourceAsStream(resource)) {
            if (stream == null) {
                log.info("未找到 {}，Git 信息降级为 {}，不影响应用启动", resource, UNKNOWN);
                return Map.of();
            }
            Properties properties = new Properties();
            properties.load(stream);
            properties.forEach((key, value) -> loaded.put(String.valueOf(key), String.valueOf(value)));
        } catch (IOException | RuntimeException exception) {
            log.info("读取 {} 失败，Git 信息降级为 {}: {}", resource, UNKNOWN, exception.getMessage());
            return Map.of();
        }
        return Map.copyOf(loaded);
    }
}
