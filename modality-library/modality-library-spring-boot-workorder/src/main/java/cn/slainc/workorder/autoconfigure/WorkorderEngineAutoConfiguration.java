package cn.slainc.workorder.autoconfigure;

import cn.slainc.workorder.engine.WorkOrderEngine;
import cn.slainc.workorder.number.MemoryWorkOrderSequenceStore;
import cn.slainc.workorder.number.SequenceWorkOrderNumberGenerator;
import cn.slainc.workorder.number.WorkOrderNumberGenerator;
import cn.slainc.workorder.number.WorkOrderSequenceStore;
import cn.slainc.workorder.assign.LeastBusyWorkOrderAssignStrategy;
import cn.slainc.workorder.assign.RoundRobinWorkOrderAssignStrategy;
import cn.slainc.workorder.assign.WorkOrderAssignStrategy;
import cn.slainc.workorder.comment.WorkOrderCommentService;
import cn.slainc.workorder.notify.InAppWorkOrderNotificationProvider;
import cn.slainc.workorder.notify.WorkOrderNotificationDispatcher;
import cn.slainc.workorder.spi.WorkOrderActivityStore;
import cn.slainc.workorder.spi.WorkOrderCommentStore;
import cn.slainc.workorder.spi.WorkOrderNotificationStore;
import cn.slainc.workorder.spi.WorkOrderSubscriptionStore;
import cn.slainc.workorder.store.MemoryWorkOrderCommentStore;
import cn.slainc.workorder.store.MemoryWorkOrderNotificationStore;
import cn.slainc.workorder.store.MemoryWorkOrderSubscriptionStore;
import cn.slainc.workorder.subscribe.WorkOrderSubscriptionService;
import cn.slainc.workorder.spi.WorkOrderStore;
import cn.slainc.workorder.spi.WorkOrderTemplateStore;
import cn.slainc.workorder.store.MemoryWorkOrderActivityStore;
import cn.slainc.workorder.statemachine.WorkOrderTransitionHook;
import cn.slainc.workorder.store.MemoryWorkOrderStore;
import cn.slainc.workorder.store.MemoryWorkOrderTemplateStore;
import cn.slainc.workorder.template.WorkOrderFormValidator;
import cn.slainc.workorder.template.WorkOrderTemplateService;
import cn.slainc.workorder.type.WorkOrderTypeRegistry;
import cn.slainc.workorder.spi.WorkOrderTypeProvider;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;


@AutoConfiguration
@ConditionalOnProperty(prefix = "modality.workorder-engine", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(WorkorderEngineProperties.class)
public class WorkorderEngineAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(WorkOrderSequenceStore.class)
    public MemoryWorkOrderSequenceStore memoryWorkOrderSequenceStore() {
        return new MemoryWorkOrderSequenceStore();
    }

    @Bean
    @ConditionalOnMissingBean(WorkOrderNumberGenerator.class)
    public SequenceWorkOrderNumberGenerator sequenceWorkOrderNumberGenerator(WorkOrderSequenceStore store,
                                                                             WorkorderEngineProperties properties) {
        return new SequenceWorkOrderNumberGenerator(store, properties.getNumber().getPrefix(),
                properties.getNumber().getReset(), properties.getNumber().getPadWidth(), Clock.systemDefaultZone());
    }

    @Bean
    @ConditionalOnMissingBean(WorkOrderStore.class)
    public MemoryWorkOrderStore memoryWorkOrderStore() {
        return new MemoryWorkOrderStore();
    }

    @Bean
    @ConditionalOnMissingBean(WorkOrderActivityStore.class)
    public MemoryWorkOrderActivityStore memoryWorkOrderActivityStore() {
        return new MemoryWorkOrderActivityStore();
    }

    @Bean
    public RoundRobinWorkOrderAssignStrategy roundRobinWorkOrderAssignStrategy() {
        return new RoundRobinWorkOrderAssignStrategy();
    }

    @Bean
    public LeastBusyWorkOrderAssignStrategy leastBusyWorkOrderAssignStrategy() {
        return new LeastBusyWorkOrderAssignStrategy();
    }

    @Bean
    @ConditionalOnMissingBean(WorkOrderCommentStore.class)
    public MemoryWorkOrderCommentStore memoryWorkOrderCommentStore() {
        return new MemoryWorkOrderCommentStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkOrderCommentService workOrderCommentService(WorkOrderCommentStore commentStore,
                                                           WorkOrderStore orderStore,
                                                           ApplicationEventPublisher publisher) {
        return new WorkOrderCommentService(commentStore, orderStore, publisher);
    }

    @Bean
    @ConditionalOnMissingBean(WorkOrderSubscriptionStore.class)
    public MemoryWorkOrderSubscriptionStore memoryWorkOrderSubscriptionStore() {
        return new MemoryWorkOrderSubscriptionStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkOrderSubscriptionService workOrderSubscriptionService(
            WorkOrderSubscriptionStore subscriptionStore, WorkOrderStore orderStore) {
        return new WorkOrderSubscriptionService(subscriptionStore, orderStore);
    }

    @Bean
    @ConditionalOnMissingBean(WorkOrderNotificationStore.class)
    public MemoryWorkOrderNotificationStore memoryWorkOrderNotificationStore() {
        return new MemoryWorkOrderNotificationStore();
    }

    @Bean
    @ConditionalOnMissingBean(InAppWorkOrderNotificationProvider.class)
    public InAppWorkOrderNotificationProvider inAppWorkOrderNotificationProvider(
            WorkOrderNotificationStore notificationStore) {
        return new InAppWorkOrderNotificationProvider(notificationStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkOrderNotificationDispatcher workOrderNotificationDispatcher(
            WorkOrderStore orderStore, WorkOrderSubscriptionStore subscriptionStore,
            WorkOrderNotificationStore notificationStore,
            List<InAppWorkOrderNotificationProvider> inAppProviders) {
        return new WorkOrderNotificationDispatcher(orderStore, subscriptionStore, notificationStore, inAppProviders);
    }

    @Bean
    @ConditionalOnMissingBean(WorkOrderTemplateStore.class)
    public MemoryWorkOrderTemplateStore memoryWorkOrderTemplateStore() {
        return new MemoryWorkOrderTemplateStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkOrderTemplateService workOrderTemplateService(WorkOrderTemplateStore templateStore) {
        return new WorkOrderTemplateService(templateStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkOrderFormValidator workOrderFormValidator() {
        return new WorkOrderFormValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkOrderTypeRegistry workOrderTypeRegistry(
            ObjectProvider<WorkOrderTypeProvider> typeProviders,
            ObjectProvider<WorkOrderTransitionHook> hooks) {
        return new WorkOrderTypeRegistry(typeProviders.orderedStream().toList(),
                hooks.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkOrderEngine workOrderEngine(WorkOrderStore store, WorkOrderNumberGenerator numberGenerator,
                                           WorkOrderTypeRegistry typeRegistry,
                                           WorkOrderTemplateService templateService,
                                           WorkOrderFormValidator formValidator,
                                           WorkOrderActivityStore activityStore,
                                           ObjectProvider<WorkOrderTransitionHook> hooks,
                                           List<WorkOrderAssignStrategy> strategies,
                                           ApplicationEventPublisher publisher) {
        return new WorkOrderEngine(store, numberGenerator, typeRegistry, templateService, formValidator,
                activityStore, strategies, publisher);
    }
}
