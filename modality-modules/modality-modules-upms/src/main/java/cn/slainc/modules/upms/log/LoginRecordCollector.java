package cn.slainc.modules.upms.log;

import cn.slainc.core.event.LoginEvent;
import cn.slainc.modules.upms.entity.UserLoginRecordRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.mapper.UserLoginRecordMapper;
import cn.slainc.modules.upms.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.slainc.security.login.CurrentRequestReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

// 登录事件采集：成功失败都落 t_user_login_record，供登录记录查询与 IP/设备统计
public class LoginRecordCollector {

    private static final Logger log = LoggerFactory.getLogger(LoginRecordCollector.class);

    private final UserLoginRecordMapper loginRecordMapper;

    private final UserMapper userMapper;

    private final CurrentRequestReader requestReader;

    public LoginRecordCollector(UserLoginRecordMapper loginRecordMapper, UserMapper userMapper,
                                CurrentRequestReader requestReader) {
        this.loginRecordMapper = loginRecordMapper;
        this.userMapper = userMapper;
        this.requestReader = requestReader;
    }

    @EventListener
    public void onLoginEvent(LoginEvent event) {
        try {
            UserLoginRecordRecord record = new UserLoginRecordRecord();
            record.setTenantId(resolveTenant(event));
            record.setUserId(event.getUserId());
            record.setUsername(event.getUsername());
            record.setLoginType(event.getLoginType() == null ? null : event.getLoginType().value());
            record.setClientIp(event.getClientIp());
            String userAgent = requestReader == null ? null : requestReader.userAgent();
            record.setUserAgent(userAgent);
            record.setDevice(DeviceParser.parse(userAgent));
            record.setSucceeded(event.isSucceeded());
            record.setFailureReason(event.getFailureReason());
            record.setCreateTime(System.currentTimeMillis());
            loginRecordMapper.insert(record);
        } catch (Exception e) {
            // 登录记录是旁路采集，任何落库异常都不能影响登录主链路
            log.warn("登录记录落库失败: {}", e.getMessage());
        }
    }

    private Long resolveTenant(LoginEvent event) {
        if (event.getUserId() != null && event.getLoginType() != null) {
            UserRecord user = userMapper.selectOne(new LambdaQueryWrapper<UserRecord>()
                    .eq(UserRecord::getId, event.getUserId())
                    .eq(UserRecord::getLoginType, event.getLoginType().value())
                    .last("LIMIT 1"));
            if (user != null && user.getTenantId() != null) {
                return user.getTenantId();
            }
        }
        return 1L;
    }
}
