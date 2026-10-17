package cn.slainc.modules.workorder.web;

import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.modules.workorder.WorkorderKeys;
import cn.slainc.modules.workorder.web.manage.WorkOrderFlowController;
import cn.slainc.modules.workorder.web.manage.WorkOrderManageController;
import cn.slainc.modules.workorder.web.order.WorkOrderAttachmentController;
import cn.slainc.modules.workorder.web.order.WorkOrderTypeController;
import cn.slainc.modules.workorder.web.portal.WorkOrderNotificationPortalController;
import cn.slainc.modules.workorder.web.portal.WorkOrderPortalController;
import cn.slainc.security.annotation.RequireLogin;
import cn.slainc.security.annotation.RequirePermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;


class WorkOrderEndpointContractTest {

    @Test
    void manageReadEndpointsRequireOrderQueryPermission() {
        assertPermission(WorkOrderManageController.class, "page", WorkorderKeys.PERMISSION_ORDER_QUERY);
        assertPermission(WorkOrderManageController.class, "comments", WorkorderKeys.PERMISSION_ORDER_QUERY);
    }

    @Test
    void manageWriteEndpointsRequirePermissionAndAudit() {
        assertAudited(WorkOrderManageController.class, "addComment", WorkorderKeys.PERMISSION_COMMENT_MANAGE);
        assertAudited(WorkOrderFlowController.class, "reopen", WorkorderKeys.PERMISSION_ORDER_ASSIGN);
        assertAudited(WorkOrderFlowController.class, "batchReopen", WorkorderKeys.PERMISSION_ORDER_ASSIGN);
        assertAudited(WorkOrderAttachmentController.class, "uploadAttachment",
                WorkorderKeys.PERMISSION_ATTACHMENT_MANAGE);
        assertAudited(WorkOrderAttachmentController.class, "downloadAttachment",
                WorkorderKeys.PERMISSION_ORDER_QUERY);
    }

    @Test
    void portalWriteEndpointsAreAudited() {
        assertThat(method(WorkOrderPortalController.class, "create").getAnnotation(AuditLog.class)).isNotNull();
        assertThat(method(WorkOrderPortalController.class, "close").getAnnotation(AuditLog.class)).isNotNull();
    }

    @Test
    void portalDictionariesRequireLogin() {
        assertThat(WorkOrderTypeController.class.getAnnotation(RequireLogin.class)).isNotNull();
        assertThat(WorkOrderNotificationPortalController.class.getAnnotation(RequireLogin.class)).isNotNull();
    }

    private void assertPermission(Class<?> type, String methodName, String permission) {
        RequirePermission annotation = method(type, methodName).getAnnotation(RequirePermission.class);
        assertThat(annotation).as("%s#%s 必须声明权限", type.getSimpleName(), methodName).isNotNull();
        assertThat(annotation.permissions()).contains(permission);
    }

    private void assertAudited(Class<?> type, String methodName, String permission) {
        assertPermission(type, methodName, permission);
        assertThat(method(type, methodName).getAnnotation(AuditLog.class))
                .as("%s#%s 必须记录审计", type.getSimpleName(), methodName)
                .isNotNull();
    }

    private Method method(Class<?> type, String methodName) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到方法：" + type.getSimpleName() + "#" + methodName));
    }
}
