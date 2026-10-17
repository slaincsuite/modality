package cn.slainc.modules.workorder.service.order;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.modules.workorder.WorkorderModuleApplication;
import cn.slainc.modules.workorder.entity.WorkOrderAttachmentRecord;
import cn.slainc.modules.workorder.entity.WorkOrderRecord;
import cn.slainc.workorder.template.WorkOrderFieldDefinition;
import cn.slainc.workorder.template.WorkOrderFieldType;
import cn.slainc.workorder.template.WorkOrderTemplateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = WorkorderModuleApplication.class)
class WorkOrderAttachmentServiceTest {

    @Autowired
    private WorkOrderAttachmentService attachmentService;

    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WorkOrderTemplateService templateService;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        asUser(101L, "alice");
        jdbc.execute("DELETE FROM t_work_order_attachment");
        if (templateService.findEnabledByType("generic").isEmpty()) {
            templateService.create("generic-template", "通用工单模板", "generic", List.of(
                    new WorkOrderFieldDefinition("impactUsers", "影响人数", WorkOrderFieldType.NUMBER, false, null,
                            null, null, null, 0, 100000, null, null, false, 1)
            ));
        }
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        TenantContext.clear();
    }

    private void asUser(Long userId, String username) {
        UserContext.set(UserContext.UserInfo.of(userId, username, LoginType.TENANT_USER));
    }

    @Test
    void uploadListThenDeleteAttachment() {
        WorkOrderRecord order = workOrderService.create("附件全链路", "上传、列表、删除", null);
        byte[] content = ("诊断日志-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        WorkOrderAttachmentRecord uploaded = attachmentService.upload(order.getId(), "diagnosis.log",
                content, "text/plain");
        assertThat(uploaded.getId()).isNotNull();
        assertThat(uploaded.getObjectKey()).isNotBlank();
        assertThat(uploaded.getFileSize()).isEqualTo((long) content.length);
        assertThat(uploaded.getUploaderId()).isEqualTo(101L);
        assertThat(jdbc.queryForObject(
                "SELECT deleted FROM t_work_order_attachment WHERE id = " + uploaded.getId(), Integer.class))
                .isEqualTo(0);
        List<WorkOrderAttachmentRecord> list = attachmentService.listByOrder(order.getId());
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getFileName()).isEqualTo("diagnosis.log");
        assertThat(attachmentService.downloadUrl(uploaded.getId())).contains("signature=");
        MockHttpServletResponse response = new MockHttpServletResponse();
        attachmentService.download(uploaded.getId(), response);
        assertThat(response.getContentAsByteArray()).isEqualTo(content);
        assertThat(response.getContentType()).startsWith("text/plain");
        assertThat(response.getHeader("Content-Disposition")).contains("diagnosis.log");
        attachmentService.delete(uploaded.getId());
        assertThat(attachmentService.listByOrder(order.getId())).isEmpty();
        assertThatThrownBy(() -> attachmentService.downloadUrl(uploaded.getId()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("附件不存在");
        assertThatThrownBy(() -> attachmentService.delete(uploaded.getId()))
                .isInstanceOf(BizException.class);
    }

    @Test
    void emptyContentUploadIsRejected() {
        WorkOrderRecord order = workOrderService.create("空附件", "内容为空不允许上传", null);
        assertThatThrownBy(() -> attachmentService.upload(order.getId(), "empty.txt", new byte[0], "text/plain"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("附件内容不能为空");
        assertThat(attachmentService.listByOrder(order.getId())).isEmpty();
    }

    @Test
    void uploadToMissingOrderIsRejected() {
        assertThatThrownBy(() -> attachmentService.upload(999999L, "missing.txt",
                "内容".getBytes(StandardCharsets.UTF_8), "text/plain"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("工单不存在");
    }

    @Test
    void downloadIsTenantIsolated() {
        WorkOrderRecord order = workOrderService.create("跨租户附件", "内容", null);
        WorkOrderAttachmentRecord uploaded = attachmentService.upload(order.getId(), "tenant.log",
                "租户一".getBytes(StandardCharsets.UTF_8), "text/plain");
        TenantContext.set(2L);
        try {
            assertThatThrownBy(() -> attachmentService.download(uploaded.getId(), new MockHttpServletResponse()))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("附件不存在");
        } finally {
            TenantContext.set(1L);
        }
    }
}
