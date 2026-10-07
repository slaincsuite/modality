package cn.slainc.modules.upms.log;

import cn.slainc.core.event.LogoutEvent;
import cn.slainc.modules.upms.entity.UserOperationLogRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.mapper.UserMapper;
import cn.slainc.modules.upms.mapper.UserOperationLogMapper;
import cn.slainc.security.login.CurrentRequestReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;

// 旁路采集：登出日志落 t_user_operation_log，任何异常都不影响登出主链路
public class LogoutRecordCollector {

    private static final Logger log = LoggerFactory.getLogger(LogoutRecordCollector.class);

    private final UserOperationLogMapper operationLogMapper;

    private final UserMapper userMapper;

    private final ObjectProvider<CurrentRequestReader> requestReader;

    public LogoutRecordCollector(UserOperationLogMapper operationLogMapper, UserMapper userMapper,
                                 ObjectProvider<CurrentRequestReader> requestReader) {
        this.operationLogMapper = operationLogMapper;
        this.userMapper = userMapper;
        this.requestReader = requestReader;
    }

    @EventListener
    public void onLogout(LogoutEvent event) {
        if (event.getUserId() == null) {
            return;
        }
        try {
            UserOperationLogRecord record = new UserOperationLogRecord();
            record.setUserId(event.getUserId());
            record.setUsername(event.getUsername());
            record.setModuleName("auth");
            record.setOperation("登出");
            record.setDescription(event.isForced() ? "强制下线" : "主动登出");
            record.setSuccess(true);
            CurrentRequestReader reader = requestReader.getIfAvailable();
            if (reader != null) {
                record.setClientIp(reader.clientIp());
                record.setUserAgent(reader.userAgent());
            }
            UserRecord user = userMapper.selectById(event.getUserId());
            if (user != null) {
                record.setTenantId(user.getTenantId());
            }
            record.setCreateTime(System.currentTimeMillis());
            operationLogMapper.insert(record);
        } catch (Exception e) {
            log.warn("登出日志落库失败：{}", e.getMessage());
        }
    }
}
