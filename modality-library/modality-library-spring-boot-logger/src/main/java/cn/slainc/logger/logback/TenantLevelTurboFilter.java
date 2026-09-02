package cn.slainc.logger.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import cn.slainc.core.constant.SystemConstants;
import org.slf4j.MDC;
import org.slf4j.Marker;

public class TenantLevelTurboFilter extends TurboFilter {

    private final LogbackLevelManager levelManager;

    public TenantLevelTurboFilter(LogbackLevelManager levelManager) {
        this.levelManager = levelManager;
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params,
                              Throwable throwable) {
        String tenantText = MDC.get(SystemConstants.TENANT_ID_MDC_KEY);
        if (tenantText == null || level == null || levelManager == null) {
            return FilterReply.NEUTRAL;
        }
        Long tenantId;
        try {
            tenantId = Long.valueOf(tenantText.trim());
        } catch (NumberFormatException ex) {
            return FilterReply.NEUTRAL;
        }
        Level minimum = levelManager.levelForTenant(tenantId);
        if (minimum == null) {
            return FilterReply.NEUTRAL;
        }
        return level.isGreaterOrEqual(minimum) ? FilterReply.NEUTRAL : FilterReply.DENY;
    }
}
