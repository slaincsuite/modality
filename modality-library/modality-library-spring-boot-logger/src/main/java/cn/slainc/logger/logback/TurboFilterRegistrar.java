package cn.slainc.logger.logback;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.List;

public class TurboFilterRegistrar implements InitializingBean, DisposableBean {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(TurboFilterRegistrar.class);

    private final List<TurboFilter> filters;

    public TurboFilterRegistrar(List<TurboFilter> filters) {
        this.filters = filters == null ? List.of() : List.copyOf(filters);
    }

    @Override
    public void afterPropertiesSet() {
        LoggerContext context = LogbackLevelManager.currentContext();
        if (context == null) {
            return;
        }
        for (TurboFilter filter : filters) {
            try {
                filter.setContext(context);
                if (!filter.isStarted()) {
                    filter.start();
                }
                context.addTurboFilter(filter);
            } catch (RuntimeException ex) {
                log.warn("注册日志 TurboFilter 失败：{}", filter.getClass().getName(), ex);
            }
        }
    }

    @Override
    public void destroy() {
        LoggerContext context = LogbackLevelManager.currentContext();
        if (context == null) {
            return;
        }
        for (TurboFilter filter : filters) {
            context.getTurboFilterList().remove(filter);
        }
    }
}
