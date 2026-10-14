package cn.slainc.modules.upms.log;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.event.LogoutEvent;
import cn.slainc.modules.upms.UpmsIntegrationTestBase;
import cn.slainc.modules.upms.entity.UserOperationLogRecord;
import cn.slainc.modules.upms.mapper.UserOperationLogMapper;
import cn.slainc.modules.upms.service.user.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;

// 登出日志采集自 auth 模块迁入：LogoutEvent 由认证侧发布，这里验证旁路落库与异常吞掉不影响主链路
class LogoutRecordCollectorTest extends UpmsIntegrationTestBase {

    @Autowired
    private UserService userService;

    @Autowired
    private UserOperationLogMapper operationLogMapper;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Test
    void logoutEventPersistsOperationLog() {
        var user = userService.create("logout-user", null, "Policy#12345", null, null, null,
                LoginType.TENANT_USER, 1L, "tester");
        publisher.publishEvent(new LogoutEvent(user.getId(), user.getUsername(), LoginType.TENANT_USER, false));

        UserOperationLogRecord record = operationLogMapper.selectOne(new LambdaQueryWrapper<UserOperationLogRecord>()
                .eq(UserOperationLogRecord::getUserId, user.getId())
                .eq(UserOperationLogRecord::getOperation, "登出"));
        assertThat(record).isNotNull();
        assertThat(record.getModuleName()).isEqualTo("auth");
        assertThat(record.getDescription()).isEqualTo("主动登出");
        assertThat(record.getTenantId()).isEqualTo(user.getTenantId());
    }

    @Test
    void forcedLogoutAndMissingUserAreHandledGracefully() {
        var user = userService.create("logout-forced", null, "Policy#12345", null, null, null,
                LoginType.TENANT_USER, 1L, "tester");
        publisher.publishEvent(new LogoutEvent(user.getId(), user.getUsername(), LoginType.TENANT_USER, true));
        publisher.publishEvent(new LogoutEvent(null, "ghost", LoginType.TENANT_USER, true));

        UserOperationLogRecord record = operationLogMapper.selectOne(new LambdaQueryWrapper<UserOperationLogRecord>()
                .eq(UserOperationLogRecord::getUserId, user.getId()));
        assertThat(record).isNotNull();
        assertThat(record.getDescription()).isEqualTo("强制下线");
    }
}
