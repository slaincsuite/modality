package cn.slainc.modules.infra.service.recycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.modules.infra.context.CurrentUser;
import cn.slainc.modules.infra.entity.RecycleItemEntity;
import cn.slainc.modules.infra.mapper.RecycleItemMapper;
import cn.slainc.recycle.config.RecycleProperties;
import cn.slainc.recycle.registry.RecycleBinRegistry;
import cn.slainc.recycle.registry.RecyclableEntity;
import cn.slainc.recycle.registry.RecyclableType;
import cn.slainc.recycle.support.RecycleSnapshotCodec;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

class RecycleBinServiceTest {

    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private RecycleItemMapper mapper;

    private JdbcTemplate jdbcTemplate;

    private BaseMapper<LogicEntity> logicMapper;

    private BaseMapper<PhysicalEntity> physicalMapper;

    private RecycleProperties properties;

    private RecycleSnapshotCodec codec;

    private RecyclableType logicType;

    private RecyclableType physicalType;

    private RecycleBinService service;

    private final CurrentUser currentUser = mock(CurrentUser.class);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, RecycleItemEntity.class);
        TableInfoHelper.initTableInfo(assistant, LogicEntity.class);
        TableInfoHelper.initTableInfo(assistant, PhysicalEntity.class);
        mapper = mock(RecycleItemMapper.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        logicMapper = mock(BaseMapper.class);
        physicalMapper = mock(BaseMapper.class);
        properties = new RecycleProperties();
        codec = new RecycleSnapshotCodec(JsonMapper.builder().build(), properties.getMaxSnapshotBytes());
        logicType = RecyclableType.of("DEMO", LogicEntity.class, logicMapper, LogicEntity::getId,
                LogicEntity::getName, true);
        physicalType = RecyclableType.of("NOTE", PhysicalEntity.class, physicalMapper, PhysicalEntity::getId,
                PhysicalEntity::getName, false);
        RecycleBinRegistry registry = new RecycleBinRegistry(List.of(logicType, physicalType), properties);
        when(currentUser.operator()).thenReturn("tester");
        when(currentUser.userId()).thenReturn(11L);
        service = new RecycleBinService(registry, mapper, codec, properties, jdbcTemplate, currentUser);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldStashASnapshotWithRetentionMetadata() {
        TenantContext.set(22L);
        LogicEntity entity = logicEntity(7L, "logo.png");

        RecycleItemEntity item = service.stash(logicType, entity);

        ArgumentCaptor<RecycleItemEntity> captor = ArgumentCaptor.forClass(RecycleItemEntity.class);
        verify(mapper).insert(captor.capture());
        RecycleItemEntity stored = captor.getValue();
        assertThat(stored.getEntityType()).isEqualTo("DEMO");
        assertThat(stored.getEntityId()).isEqualTo("7");
        assertThat(stored.getEntityName()).isEqualTo("logo.png");
        assertThat(stored.getSnapshot()).contains("logo.png").contains("\"id\":7");
        assertThat(stored.getOperator()).isEqualTo("tester");
        assertThat(stored.getOperatorId()).isEqualTo(11L);
        assertThat(stored.getTenantId()).isEqualTo(22L);
        assertThat(stored.getRestored()).isFalse();
        assertThat(stored.getRestoredAt()).isNull();
        assertThat(stored.getExpireAt()).isEqualTo(stored.getDeletedAt() + 30L * DAY_MILLIS);
        assertThat(item.getEntityType()).isEqualTo(stored.getEntityType());
    }

    @Test
    void shouldSkipStashingWhenTheTypeIsNotRegistered() {
        RecyclableType unknown = RecyclableType.of("UNKNOWN", LogicEntity.class, logicMapper, LogicEntity::getId,
                LogicEntity::getName, true);
        RecycleBinRegistry registry = new RecycleBinRegistry(List.of(unknown), properties);
        RecycleBinService typed = new RecycleBinService(registry, mapper, codec, properties, jdbcTemplate,
                currentUser);

        assertThat(typed.stashByType("DEMO", logicEntity(7L, "logo.png"))).isNull();
        verify(mapper, never()).insert(any(RecycleItemEntity.class));
    }

    @Test
    void shouldRejectAnUnregisteredTypeWhenStashing() {
        RecyclableType unknown = RecyclableType.of("UNKNOWN", LogicEntity.class, logicMapper, LogicEntity::getId,
                LogicEntity::getName, true);

        assertThatThrownBy(() -> service.stash(unknown, logicEntity(7L, "logo.png")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未注册的可回收类型");
        verify(mapper, never()).insert(any(RecycleItemEntity.class));
    }

    @Test
    void shouldRejectAMismatchedEntityWhenStashing() {
        assertThatThrownBy(() -> service.stash(logicType, physicalEntity(1L, "note")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("实体类型与可回收类型不匹配");
    }

    @Test
    void shouldRestoreALogicallyDeletedRecordByFlippingTheFlag() {
        LogicEntity entity = logicEntity(7L, "logo.png");
        RecycleItemEntity item = item(5L, "DEMO", "7", 1L, false, codec.serialize(entity));
        when(mapper.selectById(5L)).thenReturn(item);
        when(jdbcTemplate.update(eq("UPDATE t_recycle_demo_logic SET deleted = 0 WHERE id = ?"), eq(7L)))
                .thenReturn(1);

        RecycleItemEntity restored = service.restore(5L);

        verify(jdbcTemplate).update("UPDATE t_recycle_demo_logic SET deleted = 0 WHERE id = ?", 7L);
        verify(mapper).updateById(any(RecycleItemEntity.class));
        assertThat(restored.getRestored()).isTrue();
        assertThat(restored.getRestoredAt()).isNotNull();
    }

    @Test
    void shouldFailWhenTheLogicalRecordIsGoneFromTheBusinessTable() {
        LogicEntity entity = logicEntity(7L, "logo.png");
        RecycleItemEntity item = item(5L, "DEMO", "7", 1L, false, codec.serialize(entity));
        when(mapper.selectById(5L)).thenReturn(item);
        when(jdbcTemplate.update(eq("UPDATE t_recycle_demo_logic SET deleted = 0 WHERE id = ?"), eq(7L)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.restore(5L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("恢复失败");
        verify(mapper, never()).updateById(any(RecycleItemEntity.class));
    }

    @Test
    void shouldRestoreAPhysicallyDeletedRecordByInsertingTheSnapshot() {
        RecycleItemEntity item = item(6L, "NOTE", "9", null, false,
                codec.serialize(physicalEntity(9L, "note")));
        when(mapper.selectById(6L)).thenReturn(item);

        RecycleItemEntity restored = service.restore(6L);

        ArgumentCaptor<PhysicalEntity> captor = ArgumentCaptor.forClass(PhysicalEntity.class);
        verify(physicalMapper).insert(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(9L);
        assertThat(captor.getValue().getName()).isEqualTo("note");
        assertThat(restored.getRestored()).isTrue();
    }

    @Test
    void shouldRejectARestoreWhenTheRecordAlreadyExists() {
        RecycleItemEntity item = item(6L, "NOTE", "9", null, false,
                codec.serialize(physicalEntity(9L, "note")));
        when(mapper.selectById(6L)).thenReturn(item);
        when(physicalMapper.insert(any(PhysicalEntity.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry '9'"));

        assertThatThrownBy(() -> service.restore(6L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("记录已存在");
    }

    @Test
    void shouldRejectARestoreWhenTheItemWasAlreadyRestored() {
        RecycleItemEntity item = item(5L, "DEMO", "7", 1L, true, "{}");
        when(mapper.selectById(5L)).thenReturn(item);

        assertThatThrownBy(() -> service.restore(5L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("记录已恢复");
    }

    @Test
    void shouldRejectARestoreWhenTheTypeIsNoLongerRegistered() {
        RecycleItemEntity item = item(5L, "GONE", "7", 1L, false, "{}");
        when(mapper.selectById(5L)).thenReturn(item);

        assertThatThrownBy(() -> service.restore(5L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未注册的可回收类型");
    }

    @Test
    void shouldReportAMissingItem() {
        when(mapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("回收站记录不存在");
    }

    @Test
    void shouldRejectCrossTenantOperations() {
        TenantContext.set(2L);
        RecycleItemEntity item = item(5L, "DEMO", "7", 1L, false, "{}");
        when(mapper.selectById(5L)).thenReturn(item);

        assertThatThrownBy(() -> service.restore(5L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权操作其他租户");
        assertThatThrownBy(() -> service.purge(5L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权操作其他租户");
        verify(mapper, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    void shouldPurgeAnOwnedItem() {
        RecycleItemEntity item = item(5L, "DEMO", "7", 1L, false, "{}");
        when(mapper.selectById(5L)).thenReturn(item);
        when(mapper.deleteById(5L)).thenReturn(1);

        assertThat(service.purge(5L)).isTrue();
        verify(mapper).deleteById(5L);
    }

    @Test
    void shouldPurgeExpiredItems() {
        when(mapper.delete(any())).thenReturn(3);

        assertThat(service.purgeExpired()).isEqualTo(3);
        verify(mapper).delete(any());
    }

    @Test
    void shouldCompareExpirationWithEpochMillis() {
        when(mapper.delete(any())).thenReturn(1);
        java.time.LocalDateTime threshold = java.time.LocalDateTime.of(2026, 10, 17, 12, 0);
        long expected = threshold.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

        assertThat(service.deleteExpired(threshold)).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<RecycleItemEntity>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(mapper).delete(captor.capture());
        var wrapper = (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) captor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("expire_at");
        var values = wrapper.getParamNameValuePairs().values();
        assertThat(values).anyMatch(value -> value instanceof Long number && number == expected);
    }

    @Test
    void shouldPageItemsWithKeywordFilter() {
        org.mockito.Mockito.doReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<RecycleItemEntity>())
                .when(mapper).selectPage(any(), any());

        service.page("DEMO", "logo", null, null, null, null, 1L, 10L);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<RecycleItemEntity>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(mapper).selectPage(any(), captor.capture());
        assertThat(captor.getValue().getSqlSegment())
                .contains("entity_type")
                .contains("entity_name");
        assertThat(((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) captor.getValue())
                .getParamNameValuePairs().values().toString()).contains("logo");
    }

    @Test
    void shouldFailWithAClearMessageWhenTheMapperIsMissing() {
        RecycleBinRegistry registry = new RecycleBinRegistry(List.of(logicType), properties);
        RecycleBinService withoutMapper = new RecycleBinService(registry, null, codec, properties, jdbcTemplate,
                currentUser);

        assertThatThrownBy(() -> withoutMapper.stash(logicType, logicEntity(7L, "logo.png")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("回收站不可用");
    }

    @Test
    void shouldFailWithAClearMessageWhenTheJdbcTemplateIsMissing() {
        RecycleBinRegistry registry = new RecycleBinRegistry(List.of(logicType), properties);
        RecycleBinService withoutTemplate = new RecycleBinService(registry, mapper, codec, properties, null,
                currentUser);
        RecycleItemEntity item = item(5L, "DEMO", "7", null, false, "{}");
        when(mapper.selectById(5L)).thenReturn(item);

        assertThatThrownBy(() -> withoutTemplate.restore(5L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("缺少 JdbcTemplate");
    }

    private static RecycleItemEntity item(Long id, String type, String entityId, Long tenantId, boolean restored,
                                          String snapshot) {
        RecycleItemEntity item = new RecycleItemEntity();
        item.setId(id);
        item.setEntityType(type);
        item.setEntityId(entityId);
        item.setEntityName("demo");
        item.setSnapshot(snapshot);
        item.setDeletedAt(System.currentTimeMillis() - DAY_MILLIS);
        item.setExpireAt(System.currentTimeMillis() + 29L * DAY_MILLIS);
        item.setRestored(restored);
        item.setTenantId(tenantId);
        item.setVersion(0);
        return item;
    }

    private static LogicEntity logicEntity(Long id, String name) {
        LogicEntity entity = new LogicEntity();
        entity.setId(id);
        entity.setName(name);
        return entity;
    }

    private static PhysicalEntity physicalEntity(Long id, String name) {
        PhysicalEntity entity = new PhysicalEntity();
        entity.setId(id);
        entity.setName(name);
        return entity;
    }

    @TableName("t_recycle_demo_logic")
    static class LogicEntity extends RecyclableEntity {

        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @TableName("t_recycle_demo_physical")
    static class PhysicalEntity {

        private Long id;

        private String name;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
