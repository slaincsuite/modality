package cn.slainc.modules.policy.contract;

import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.modules.policy.PolicyCenterKeys;
import cn.slainc.modules.policy.web.binding.PolicyBindingPortalController;
import cn.slainc.modules.policy.web.catalog.PolicyCatalogController;
import cn.slainc.modules.policy.web.generator.PolicyGeneratorController;
import cn.slainc.modules.policy.web.manage.PolicyManageController;
import cn.slainc.modules.policy.web.self.PolicySelfController;
import cn.slainc.security.annotation.RequirePermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyCenterPermissionContractTest {

    @Test
    void newEndpointsCarryPermissionCodesAndWritesCarryAudit() {
        assertPermission(PolicyManageController.class, "customTemplates", PolicyCenterKeys.PERMISSION_POLICY_TEMPLATE_QUERY);
        assertPermission(PolicyManageController.class, "createCustomTemplate", PolicyCenterKeys.PERMISSION_POLICY_TEMPLATE_SAVE);
        assertPermission(PolicyManageController.class, "updateCustomTemplate", PolicyCenterKeys.PERMISSION_POLICY_TEMPLATE_SAVE);
        assertPermission(PolicyManageController.class, "updateCustomTemplateByBody", PolicyCenterKeys.PERMISSION_POLICY_TEMPLATE_SAVE);
        assertPermission(PolicyManageController.class, "deleteCustomTemplate", PolicyCenterKeys.PERMISSION_POLICY_TEMPLATE_SAVE);
        assertPermission(PolicyManageController.class, "deleteCustomTemplateByQuery", PolicyCenterKeys.PERMISSION_POLICY_TEMPLATE_SAVE);
        assertPermission(PolicyManageController.class, "parse", PolicyCenterKeys.PERMISSION_POLICY_VALIDATE);
        assertPermission(PolicyManageController.class, "batchGrant", PolicyCenterKeys.PERMISSION_POLICY_GRANT);
        assertPermission(PolicyManageController.class, "bindings", PolicyCenterKeys.PERMISSION_BINDING_QUERY);
        assertPermission(PolicyManageController.class, "exportBindings", PolicyCenterKeys.PERMISSION_BINDING_EXPORT);
        assertPermission(PolicyManageController.class, "boundary", PolicyCenterKeys.PERMISSION_POLICY_BOUNDARY_QUERY);
        assertPermission(PolicyManageController.class, "updateBoundary", PolicyCenterKeys.PERMISSION_POLICY_BOUNDARY_UPDATE);
        assertPermission(PolicyBindingPortalController.class, "bindGroup", PolicyCenterKeys.PERMISSION_BINDING_BIND);
        assertPermission(PolicyGeneratorController.class, "capabilities",
                PolicyCenterKeys.PERMISSION_POLICY_CATALOG_QUERY);
        assertPermission(PolicyGeneratorController.class, "preview",
                PolicyCenterKeys.PERMISSION_POLICY_VALIDATE);
        assertPermission(PolicyGeneratorController.class, "create",
                PolicyCenterKeys.PERMISSION_POLICY_CREATE);

        for (String method : new String[]{"products", "services", "actions", "resources"}) {
            assertPermission(PolicyCatalogController.class, method, PolicyCenterKeys.PERMISSION_POLICY_CATALOG_QUERY);
        }

        for (String method : new String[]{"createCustomTemplate", "updateCustomTemplate", "updateCustomTemplateByBody",
                "deleteCustomTemplate", "deleteCustomTemplateByQuery", "batchGrant", "updateBoundary"}) {
            assertAudit(PolicyManageController.class, method);
        }
        assertAudit(PolicyBindingPortalController.class, "bindGroup");
        assertAudit(PolicyGeneratorController.class, "create");
        assertThat(Arrays.stream(PolicySelfController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("policies"))
                .findFirst().orElseThrow().getAnnotation(RequirePermission.class)).isNull();
    }

    private void assertPermission(Class<?> type, String methodName, String permission) {
        RequirePermission annotation = method(type, methodName).getAnnotation(RequirePermission.class);
        assertThat(annotation).as(methodName + " 缺少权限注解").isNotNull();
        assertThat(annotation.permissions()).contains(permission);
    }

    private void assertAudit(Class<?> type, String methodName) {
        assertThat(method(type, methodName).getAnnotation(AuditLog.class))
                .as(methodName + " 缺少审计注解").isNotNull();
    }

    private Method method(Class<?> type, String methodName) {
        return Arrays.stream(type.getDeclaredMethods()).filter(method -> method.getName().equals(methodName))
                .findFirst().orElseThrow();
    }
}
