package cn.slainc.modules.workorder.store;

import cn.slainc.core.context.TenantContext;
import cn.slainc.modules.workorder.WorkorderModuleApplication;
import cn.slainc.modules.workorder.entity.WorkOrderNotificationRecord;
import cn.slainc.utils.json.JsonUtils;
import cn.slainc.workorder.notify.WorkOrderNotification;
import cn.slainc.workorder.spi.WorkOrderNotificationStore;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(classes = WorkorderModuleApplication.class)
class WorkOrderNotificationStoreTest {

    @Autowired
    private WorkOrderNotificationStore notificationStore;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        jdbc.execute("DELETE FROM t_work_order_notification");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void selectAliasDoesNotUseMysqlReservedWord() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(WorkOrderNotificationRecord.class);
        if (tableInfo == null) {
            tableInfo = TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                    WorkOrderNotificationRecord.class);
        }
        String selectSql = tableInfo.getAllSqlSelect();
        assertThat(selectSql).contains("is_read");
        assertThat(selectSql).as("MySQL 8 中 read 是保留字，别名不能是裸 read")
                .doesNotMatch("(?i).*\\bas read\\b.*");
    }

    @Test
    void readFieldKeepsFrontendContractAndFiltersUnread() {
        WorkOrderNotification notification = notificationStore.save(notification(101L, "工单已分派"));
        String json = JsonUtils.toJson(notificationStore.findByRecipient(101L, false));
        assertThat(json).contains("\"read\":false");
        assertThat(notificationStore.findByRecipient(101L, true)).hasSize(1);
        assertThat(notificationStore.countUnread(101L)).isEqualTo(1);

        assertThat(notificationStore.markRead(notification.getId(), 202L)).isFalse();
        assertThat(notificationStore.countUnread(101L)).isEqualTo(1);
        assertThat(notificationStore.markRead(notification.getId(), 101L)).isTrue();
        assertThat(notificationStore.countUnread(101L)).isZero();
        assertThat(notificationStore.findByRecipient(101L, true)).isEmpty();
    }

    @Test
    void tenantIsolationHidesOtherTenantNotifications() {
        notificationStore.save(notification(101L, "租户一通知"));
        TenantContext.set(2L);
        assertThat(notificationStore.findByRecipient(101L, false)).isEmpty();
        assertThat(notificationStore.countUnread(101L)).isZero();
    }

    private WorkOrderNotification notification(Long recipientId, String title) {
        WorkOrderNotification notification = new WorkOrderNotification();
        notification.setRecipientId(recipientId);
        notification.setEventCode("ORDER_ASSIGNED");
        notification.setTitle(title);
        notification.setContent("内容");
        notification.setChannel("IN_APP");
        notification.setCreateTime(System.currentTimeMillis());
        return notification;
    }
}
