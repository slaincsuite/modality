package cn.slainc.modules.open.web;

import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.modules.open.OpenKeys;
import cn.slainc.modules.open.dto.BatchRevokeRequest;
import cn.slainc.modules.open.dto.ScopeGroupRequest;
import cn.slainc.modules.open.web.app.OpenAppAdminController;
import cn.slainc.modules.open.web.app.OpenScopeGroupAdminController;
import cn.slainc.modules.open.web.consent.OpenConsentAdminController;
import cn.slainc.security.annotation.RequirePermission;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAdminEndpointContractTest {

    @Test
    void appExportCarriesPermissionAndAudit() throws Exception {
        Method method = OpenAppAdminController.class.getMethod("export", String.class, String.class, String.class,
                String.class, String.class, HttpServletResponse.class);

        assertPermission(method, OpenKeys.PERMISSION_APP_QUERY);
        assertThat(method.getAnnotation(AuditLog.class)).isNotNull();
    }

    @Test
    void scopeGroupRoutesCarryPermissionsAndWriteAudit() throws Exception {
        assertThat(OpenScopeGroupAdminController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/open/admin/scope-groups");

        Method list = OpenScopeGroupAdminController.class.getMethod("list");
        Method create = OpenScopeGroupAdminController.class.getMethod("create", ScopeGroupRequest.class);
        Method update = OpenScopeGroupAdminController.class.getMethod("update", String.class,
                ScopeGroupRequest.class);
        Method updateByBody = OpenScopeGroupAdminController.class.getMethod("updateByBody", ScopeGroupRequest.class);
        Method delete = OpenScopeGroupAdminController.class.getMethod("delete", String.class);
        Method deleteByName = OpenScopeGroupAdminController.class.getMethod("deleteByName", String.class);

        assertPermission(list, OpenKeys.PERMISSION_SCOPE_QUERY);
        assertPermission(create, OpenKeys.PERMISSION_SCOPE_CREATE);
        assertPermission(update, OpenKeys.PERMISSION_SCOPE_UPDATE);
        assertPermission(updateByBody, OpenKeys.PERMISSION_SCOPE_UPDATE);
        assertPermission(delete, OpenKeys.PERMISSION_SCOPE_DELETE);
        assertPermission(deleteByName, OpenKeys.PERMISSION_SCOPE_DELETE);
        assertThat(create.getAnnotation(AuditLog.class)).isNotNull();
        assertThat(update.getAnnotation(AuditLog.class)).isNotNull();
        assertThat(updateByBody.getAnnotation(AuditLog.class)).isNotNull();
        assertThat(delete.getAnnotation(AuditLog.class)).isNotNull();
        assertThat(deleteByName.getAnnotation(AuditLog.class)).isNotNull();
        assertThat(update.getAnnotation(PutMapping.class).value()).containsExactly("/{name}");
        assertThat(updateByBody.getAnnotation(PutMapping.class).value()).isEmpty();
        assertThat(delete.getAnnotation(DeleteMapping.class).value()).containsExactly("/{name}");
        assertThat(deleteByName.getAnnotation(DeleteMapping.class).value()).isEmpty();
    }

    @Test
    void batchRevokeAcceptsOptionalReasonAndKeepsAudit() throws Exception {
        Method method = OpenConsentAdminController.class.getMethod("batchRevoke", String.class,
                BatchRevokeRequest.class);

        assertPermission(method, OpenKeys.PERMISSION_CONSENT_REVOKE);
        assertThat(method.getAnnotation(AuditLog.class)).isNotNull();
        assertThat(method.getParameters()[1].getType()).isEqualTo(BatchRevokeRequest.class);
    }

    private static void assertPermission(Method method, String permission) {
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.permissions()).containsExactly(permission);
    }
}
