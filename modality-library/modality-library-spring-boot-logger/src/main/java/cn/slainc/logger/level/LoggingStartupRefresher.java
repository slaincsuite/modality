package cn.slainc.logger.level;

import cn.slainc.logger.sample.SamplingManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

public class LoggingStartupRefresher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LoggingStartupRefresher.class);

    private final LogLevelManager levelManager;

    private final SamplingManager samplingManager;

    public LoggingStartupRefresher(LogLevelManager levelManager, SamplingManager samplingManager) {
        this.levelManager = levelManager;
        this.samplingManager = samplingManager;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 放到容器就绪之后执行：动态配置实现可能要连 DB/Redis，不能在 Bean 构造期就依赖基础设施
        if (levelManager != null) {
            try {
                levelManager.refresh();
            } catch (RuntimeException ex) {
                log.warn("启动时刷新动态日志级别失败：{}", ex.getMessage());
            }
        }
        if (samplingManager != null) {
            try {
                samplingManager.refresh();
            } catch (RuntimeException ex) {
                log.warn("启动时刷新日志采样配置失败：{}", ex.getMessage());
            }
        }
    }
}
