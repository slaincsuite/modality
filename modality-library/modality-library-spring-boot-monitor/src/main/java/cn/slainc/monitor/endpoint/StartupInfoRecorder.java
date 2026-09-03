package cn.slainc.monitor.endpoint;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

import java.lang.management.ManagementFactory;

public class StartupInfoRecorder implements ApplicationListener<ApplicationReadyEvent> {

    private final long jvmStartTime = ManagementFactory.getRuntimeMXBean().getStartTime();

    private volatile long readyTime;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (readyTime == 0L) {
            readyTime = System.currentTimeMillis();
        }
    }

    public long jvmStartTime() {
        return jvmStartTime;
    }

    public long readyTime() {
        return readyTime;
    }

    public long uptimeMillis() {
        return Math.max(System.currentTimeMillis() - jvmStartTime, 0L);
    }

    public long startupMillis() {
        long ready = readyTime;
        return ready == 0L ? 0L : Math.max(ready - jvmStartTime, 0L);
    }

    public String humanUptime() {
        return human(uptimeMillis());
    }

    public String humanStartup() {
        return human(startupMillis());
    }

    public static String human(long millis) {
        long seconds = Math.max(millis, 0L) / 1000L;
        long days = seconds / 86400L;
        long hours = seconds % 86400L / 3600L;
        long minutes = seconds % 3600L / 60L;
        long remaining = seconds % 60L;
        StringBuilder builder = new StringBuilder();
        if (days > 0L) {
            builder.append(days).append("天");
        }
        if (hours > 0L) {
            builder.append(hours).append("小时");
        }
        if (minutes > 0L) {
            builder.append(minutes).append("分钟");
        }
        builder.append(remaining).append("秒");
        return builder.toString();
    }
}
