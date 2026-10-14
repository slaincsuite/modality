package cn.slainc.changeaudit.support;

import cn.slainc.changeaudit.spi.ChangeRecord;
import cn.slainc.changeaudit.spi.ChangeRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoopChangeRecorder implements ChangeRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoopChangeRecorder.class);

    @Override
    public void record(ChangeRecord change) {
        if (change != null) {
            LOGGER.debug("变更审计 {} {} 未落库: 容器中不存在 ChangeRecorder 实现", change.entityType(),
                    change.entityId());
        }
    }
}
