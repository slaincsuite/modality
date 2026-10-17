package cn.slainc.modules.auth.service.security;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.auth.service.user.TargetUserGuard;
import cn.slainc.security.spi.AccountAdminPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TargetUserGuardTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        UserContext.clear();
    }

    @Test
    void rejectsTargetUserFromAnotherTenant() {
        TargetUserGuard guard = guardOf(2L);
        TenantContext.set(1L);
        UserContext.set(new UserContext.UserInfo(10L, "tenant-admin", LoginType.TENANT_USER,
                Set.of("admin"), Set.of("upms:user:update"), false));

        assertThatThrownBy(() -> guard.require(99L))
                .isInstanceOf(BizException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((BizException) error).code())
                        .isEqualTo(ResultCode.FORBIDDEN.code()));
    }

    @Test
    void platformSuperAdminCanOperateAcrossTenants() {
        TargetUserGuard guard = guardOf(2L);
        TenantContext.set(1L);
        UserContext.set(new UserContext.UserInfo(10L, "platform-admin", LoginType.PLATFORM_ADMIN,
                Set.of("platform-admin"), Set.of(), true));

        assertThatCode(() -> guard.require(99L)).doesNotThrowAnyException();
    }

    @SuppressWarnings("unchecked")
    private TargetUserGuard guardOf(Long tenantId) {
        AccountAdminPort port = mock(AccountAdminPort.class);
        when(port.tenantIdOf(99L)).thenReturn(tenantId);
        ObjectProvider<AccountAdminPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(port);
        return new TargetUserGuard(provider);
    }
}
