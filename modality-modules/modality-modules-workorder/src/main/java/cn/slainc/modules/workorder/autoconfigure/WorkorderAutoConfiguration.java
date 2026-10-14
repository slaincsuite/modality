package cn.slainc.modules.workorder.autoconfigure;

import cn.slainc.modules.workorder.excel.WorkOrderExportService;
import cn.slainc.modules.workorder.mapper.WorkOrderActivityMapper;
import cn.slainc.modules.workorder.mapper.WorkOrderAttachmentMapper;
import cn.slainc.modules.workorder.mapper.WorkOrderCommentMapper;
import cn.slainc.modules.workorder.mapper.WorkOrderNotificationMapper;
import cn.slainc.modules.workorder.mapper.WorkOrderMapper;
import cn.slainc.modules.workorder.mapper.WorkOrderReplyMapper;
import cn.slainc.modules.workorder.mapper.WorkOrderSubscriptionMapper;
import cn.slainc.modules.workorder.service.order.WorkOrderAttachmentService;
import cn.slainc.modules.workorder.service.order.WorkOrderService;
import cn.slainc.modules.workorder.service.stats.WorkOrderStatsService;
import cn.slainc.workorder.spi.WorkOrderCommentStore;
import cn.slainc.workorder.spi.WorkOrderNotificationStore;
import cn.slainc.workorder.spi.WorkOrderSubscriptionStore;
import cn.slainc.modules.workorder.store.MpWorkOrderActivityStore;
import cn.slainc.modules.workorder.store.MpWorkOrderCommentStore;
import cn.slainc.modules.workorder.store.MpWorkOrderNotificationStore;
import cn.slainc.modules.workorder.store.MpWorkOrderStore;
import cn.slainc.modules.workorder.store.MpWorkOrderSubscriptionStore;
import cn.slainc.storage.StorageTemplate;
import cn.slainc.modules.workorder.web.order.WorkOrderAttachmentController;
import cn.slainc.modules.workorder.web.portal.WorkOrderCommentPortalController;
import cn.slainc.modules.workorder.web.manage.WorkOrderFlowController;
import cn.slainc.modules.workorder.web.manage.WorkOrderManageController;
import cn.slainc.modules.workorder.web.portal.WorkOrderNotificationPortalController;
import cn.slainc.modules.workorder.web.portal.WorkOrderPortalController;
import cn.slainc.modules.workorder.web.stats.WorkOrderStatsController;
import cn.slainc.modules.workorder.web.portal.WorkOrderSubscriptionPortalController;
import cn.slainc.workorder.autoconfigure.WorkorderEngineAutoConfiguration;
import cn.slainc.workorder.engine.WorkOrderEngine;
import cn.slainc.workorder.spi.WorkOrderActivityStore;
import cn.slainc.workorder.spi.WorkOrderStore;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;


@AutoConfiguration
@AutoConfigureBefore(WorkorderEngineAutoConfiguration.class)
@ConditionalOnProperty(prefix = "modality.workorder", name = "enabled", matchIfMissing = true)
@Import({WorkOrderManageController.class, WorkOrderFlowController.class, WorkOrderAttachmentController.class,
        WorkOrderPortalController.class, WorkOrderCommentPortalController.class,
        WorkOrderSubscriptionPortalController.class, WorkOrderNotificationPortalController.class,
        WorkOrderStatsController.class})
public class WorkorderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WorkOrderService workOrderService(WorkOrderMapper workOrderMapper, WorkOrderReplyMapper replyMapper,
                                             WorkOrderEngine engine,
                                             WorkOrderActivityStore activityStore) {
        return new WorkOrderService(workOrderMapper, replyMapper, engine, activityStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkOrderAttachmentService workOrderAttachmentService(StorageTemplate storageTemplate,
                                                                 WorkOrderAttachmentMapper attachmentMapper) {
        return new WorkOrderAttachmentService(storageTemplate, attachmentMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkOrderStatsService workOrderStatsService(WorkOrderMapper workOrderMapper) {
        return new WorkOrderStatsService(workOrderMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkOrderExportService workOrderExportService(WorkOrderService workOrderService) {
        return new WorkOrderExportService(workOrderService);
    }

    @Bean
    @ConditionalOnMissingBean(WorkOrderStore.class)
    public MpWorkOrderStore mpWorkOrderStore(WorkOrderMapper workOrderMapper) {
        return new MpWorkOrderStore(workOrderMapper);
    }

    @Bean
    @ConditionalOnMissingBean(WorkOrderCommentStore.class)
    public MpWorkOrderCommentStore mpWorkOrderCommentStore(WorkOrderCommentMapper commentMapper) {
        return new MpWorkOrderCommentStore(commentMapper);
    }

    @Bean
    @ConditionalOnMissingBean(WorkOrderSubscriptionStore.class)
    public MpWorkOrderSubscriptionStore mpWorkOrderSubscriptionStore(WorkOrderSubscriptionMapper subscriptionMapper) {
        return new MpWorkOrderSubscriptionStore(subscriptionMapper);
    }

    @Bean
    @ConditionalOnMissingBean(WorkOrderNotificationStore.class)
    public MpWorkOrderNotificationStore mpWorkOrderNotificationStore(WorkOrderNotificationMapper notificationMapper) {
        return new MpWorkOrderNotificationStore(notificationMapper);
    }

    @Bean
    @ConditionalOnMissingBean(WorkOrderActivityStore.class)
    public MpWorkOrderActivityStore mpWorkOrderActivityStore(WorkOrderActivityMapper activityMapper) {
        return new MpWorkOrderActivityStore(activityMapper);
    }
}
