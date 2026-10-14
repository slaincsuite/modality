package cn.slainc.modules.infra.service.recycle;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.infra.context.CurrentUser;
import cn.slainc.modules.infra.entity.RecycleItemEntity;
import cn.slainc.modules.infra.mapper.RecycleItemMapper;
import cn.slainc.mybatis.support.LikeUtils;
import cn.slainc.recycle.config.RecycleProperties;
import cn.slainc.recycle.registry.RecycleBinRegistry;
import cn.slainc.recycle.registry.RecyclableType;
import cn.slainc.recycle.spi.RecycleItemQuery;
import cn.slainc.recycle.spi.RecycleItemRecord;
import cn.slainc.recycle.spi.RecycleItemStore;
import cn.slainc.recycle.support.EntityIds;
import cn.slainc.recycle.support.RecycleSnapshotCodec;
import cn.slainc.utils.string.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

public class RecycleBinService implements RecycleItemStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecycleBinService.class);

    private static final String RESTORE_LOGIC_DELETE_SQL = "UPDATE %s SET deleted = 0 WHERE id = ?";

    private static final long DEFAULT_PAGE_SIZE = 10L;

    private static final long MAX_PAGE_SIZE = 200L;

    private final RecycleBinRegistry registry;

    private final RecycleItemMapper recycleItemMapper;

    private final RecycleSnapshotCodec snapshotCodec;

    private final RecycleProperties properties;

    private final JdbcTemplate jdbcTemplate;

    private final CurrentUser currentUser;

    public RecycleBinService(RecycleBinRegistry registry, RecycleItemMapper recycleItemMapper,
                             RecycleSnapshotCodec snapshotCodec, RecycleProperties properties,
                             JdbcTemplate jdbcTemplate, CurrentUser currentUser) {
        this.registry = registry;
        this.recycleItemMapper = recycleItemMapper;
        this.snapshotCodec = snapshotCodec;
        this.properties = properties == null ? new RecycleProperties() : properties;
        this.jdbcTemplate = jdbcTemplate;
        this.currentUser = currentUser;
    }

    // 业务删除前的入口：类型未注册或未启用时直接跳过，不阻塞业务删除
    public RecycleItemEntity stashByType(String type, Object entity) {
        if (registry == null || StringUtils.isBlank(type) || entity == null) {
            return null;
        }
        RecyclableType registered = registry.find(type);
        if (registered == null || !registry.isEnabled(type)) {
            return null;
        }
        return stash(registered, entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public RecycleItemEntity stash(RecyclableType type, Object entity) {
        if (entity == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "回收站快照对象不能为空");
        }
        if (type == null || StringUtils.isBlank(type.type())) {
            throw new BizException(ResultCode.BAD_REQUEST, "可回收类型不能为空");
        }
        RecyclableType registered = registry.requireEnabled(type.type());
        if (!registered.entityClass().isInstance(entity)) {
            throw new BizException(ResultCode.BAD_REQUEST, "实体类型与可回收类型不匹配: " + registered.type());
        }
        Object entityId = registered.idOf(entity);
        if (entityId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "记录标识为空，无法放入回收站");
        }
        long now = System.currentTimeMillis();
        RecycleItemEntity item = new RecycleItemEntity();
        item.setEntityType(registered.type());
        item.setEntityId(EntityIds.stringify(entityId));
        item.setEntityName(registered.nameOf(entity));
        item.setSnapshot(snapshotCodec.serialize(entity));
        item.setOperator(currentUser == null ? null : currentUser.operator());
        item.setOperatorId(currentUser == null ? null : currentUser.userId());
        item.setDeletedAt(now);
        item.setExpireAt(now + retentionMillis());
        item.setRestored(Boolean.FALSE);
        item.setTenantId(TenantContext.getOrDefault());
        item.setCreatedBy(currentUser == null ? null : currentUser.operator());
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        item.setId(insert(toRecord(item)));
        LOGGER.info("已将 {} {} 放入回收站", registered.type(), item.getEntityId());
        return item;
    }

    public PageResult<RecycleItemEntity> page(String entityType, String keyword, String operator, Boolean restored,
                                              Long beginTime, Long endTime, long pageNum, long pageSize) {
        RecycleItemQuery query = new RecycleItemQuery(entityType, keyword, operator, restored,
                toDateTime(beginTime), toDateTime(endTime), Math.max(1, pageNum),
                pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE));
        return page(query).map(RecycleBinService::toEntity);
    }

    public RecycleItemEntity get(Long id) {
        return require(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public RecycleItemEntity restore(Long id) {
        RecycleItemEntity item = require(id);
        checkTenant(item);
        if (Boolean.TRUE.equals(item.getRestored())) {
            throw new BizException(ResultCode.DATA_STATE_INVALID, "记录已恢复，请勿重复操作");
        }
        RecyclableType type = registry.require(item.getEntityType());
        if (type.logicDelete()) {
            restoreLogicalDeleted(type, item);
        } else {
            restorePhysicalDeleted(type, item);
        }
        long now = System.currentTimeMillis();
        item.setRestored(Boolean.TRUE);
        item.setRestoredAt(now);
        item.setUpdatedAt(now);
        markRestored(toRecord(item));
        LOGGER.info("已从回收站恢复 {} {}", item.getEntityType(), item.getEntityId());
        return item;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean purge(Long id) {
        RecycleItemEntity item = require(id);
        checkTenant(item);
        boolean removed = deleteById(id);
        if (removed) {
            LOGGER.info("已彻底删除回收站记录 {}（{} {}）", id, item.getEntityType(), item.getEntityId());
        }
        return removed;
    }

    @Transactional(rollbackFor = Exception.class)
    public int purgeExpired() {
        int deleted = deleteExpired(LocalDateTime.now());
        if (deleted > 0) {
            LOGGER.info("已清理 {} 条过期回收站记录", deleted);
        }
        return deleted;
    }

    @Override
    public Long insert(RecycleItemRecord item) {
        if (item == null) {
            return null;
        }
        RecycleItemEntity entity = toEntity(item);
        mapper().insert(entity);
        return entity.getId();
    }

    @Override
    public RecycleItemRecord findById(Long id) {
        if (id == null || recycleItemMapper == null) {
            return null;
        }
        return toRecord(recycleItemMapper.selectById(id));
    }

    @Override
    public PageResult<RecycleItemRecord> page(RecycleItemQuery query) {
        RecycleItemQuery filter = query == null
                ? new RecycleItemQuery(null, null, null, null, null, null, 1L, DEFAULT_PAGE_SIZE)
                : query;
        if (recycleItemMapper == null) {
            return PageResult.empty(filter.pageNum(), filter.pageSize());
        }
        Page<RecycleItemEntity> page = recycleItemMapper.selectPage(
                new Page<>(Math.max(1L, filter.pageNum()), Math.max(1L, filter.pageSize())), wrapper(filter));
        List<RecycleItemRecord> records = new ArrayList<>();
        for (RecycleItemEntity entity : page.getRecords()) {
            records.add(toRecord(entity));
        }
        return PageResult.of(records, page.getTotal(), Math.max(1L, filter.pageNum()),
                Math.max(1L, filter.pageSize()));
    }

    @Override
    public void markRestored(RecycleItemRecord item) {
        if (item == null || recycleItemMapper == null) {
            return;
        }
        recycleItemMapper.updateById(toEntity(item));
    }

    @Override
    public boolean deleteById(Long id) {
        return id != null && recycleItemMapper != null && recycleItemMapper.deleteById(id) > 0;
    }

    @Override
    public int deleteExpired(LocalDateTime now) {
        if (recycleItemMapper == null) {
            return 0;
        }
        return recycleItemMapper.delete(Wrappers.<RecycleItemEntity>lambdaQuery()
                .isNotNull(RecycleItemEntity::getExpireAt)
                .le(RecycleItemEntity::getExpireAt, now == null ? LocalDateTime.now() : now));
    }

    private void restoreLogicalDeleted(RecyclableType type, RecycleItemEntity item) {
        if (jdbcTemplate == null) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "回收站不可用: 缺少 JdbcTemplate，无法恢复逻辑删除记录");
        }
        Object entityId = EntityIds.parse(type.entityClass(), item.getEntityId());
        String sql = String.format(RESTORE_LOGIC_DELETE_SQL, type.tableName());
        int updated = jdbcTemplate.update(sql, entityId);
        if (updated <= 0) {
            throw new BizException(ResultCode.BUSINESS_ERROR, "恢复失败：原记录不存在");
        }
    }

    private void restorePhysicalDeleted(RecyclableType type, RecycleItemEntity item) {
        BaseMapper<?> mapper = type.mapper();
        if (mapper == null) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE,
                    "可回收类型 " + type.type() + " 未配置 Mapper，无法恢复");
        }
        Object entity = snapshotCodec.deserialize(item.getSnapshot(), type.entityClass());
        if (entity == null) {
            throw new BizException(ResultCode.BUSINESS_ERROR, "回收站快照为空，无法恢复");
        }
        try {
            insertEntity(mapper, entity);
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.DATA_DUPLICATE, "记录已存在，无法恢复", e);
        } catch (RuntimeException e) {
            if (isDuplicate(e)) {
                throw new BizException(ResultCode.DATA_DUPLICATE, "记录已存在，无法恢复", e);
            }
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private static void insertEntity(BaseMapper<?> mapper, Object entity) {
        ((BaseMapper<Object>) mapper).insert(entity);
    }

    private static boolean isDuplicate(RuntimeException error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message == null) {
                continue;
            }
            String text = message.toLowerCase(Locale.ROOT);
            if (text.contains("duplicate") || text.contains("unique")) {
                return true;
            }
        }
        return false;
    }

    private RecycleItemEntity require(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "回收站记录ID不能为空");
        }
        RecycleItemEntity item = toEntity(findById(id));
        if (item == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "回收站记录不存在");
        }
        return item;
    }

    private void checkTenant(RecycleItemEntity item) {
        Long current = TenantContext.get();
        if (current == null || current.equals(item.getTenantId())) {
            return;
        }
        throw new BizException(ResultCode.FORBIDDEN, "无权操作其他租户的回收站记录");
    }

    private RecycleItemMapper mapper() {
        if (recycleItemMapper == null) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "回收站不可用: 未配置 RecycleItemMapper");
        }
        return recycleItemMapper;
    }

    private long retentionMillis() {
        return Math.max(1, properties.getRetentionDays()) * 24L * 60L * 60L * 1000L;
    }

    private static LambdaQueryWrapper<RecycleItemEntity> wrapper(RecycleItemQuery filter) {
        return Wrappers.<RecycleItemEntity>lambdaQuery()
                .eq(StringUtils.isNotBlank(filter.entityType()), RecycleItemEntity::getEntityType,
                        StringUtils.trim(filter.entityType()))
                .like(StringUtils.isNotBlank(filter.keyword()), RecycleItemEntity::getEntityName,
                        LikeUtils.escapeLike(StringUtils.trimToEmpty(filter.keyword())))
                .eq(StringUtils.isNotBlank(filter.operator()), RecycleItemEntity::getOperator,
                        StringUtils.trim(filter.operator()))
                .eq(filter.restored() != null, RecycleItemEntity::getRestored, filter.restored())
                .ge(filter.beginTime() != null, RecycleItemEntity::getDeletedAt, toMillis(filter.beginTime()))
                .le(filter.endTime() != null, RecycleItemEntity::getDeletedAt, toMillis(filter.endTime()))
                .orderByDesc(RecycleItemEntity::getId);
    }

    public static RecycleItemRecord toRecord(RecycleItemEntity item) {
        if (item == null) {
            return null;
        }
        return new RecycleItemRecord(item.getId(), item.getEntityType(), item.getEntityId(), item.getEntityName(),
                item.getSnapshot(), item.getOperator(), item.getOperatorId(), toDateTime(item.getDeletedAt()),
                toDateTime(item.getExpireAt()), item.getRestored(), toDateTime(item.getRestoredAt()),
                item.getTenantId(), item.getCreatedBy(), toDateTime(item.getCreatedAt()), item.getUpdatedBy(),
                toDateTime(item.getUpdatedAt()), null, item.getVersion());
    }

    public static RecycleItemEntity toEntity(RecycleItemRecord item) {
        if (item == null) {
            return null;
        }
        RecycleItemEntity entity = new RecycleItemEntity();
        entity.setId(item.id());
        entity.setEntityType(item.entityType());
        entity.setEntityId(item.entityId());
        entity.setEntityName(item.entityName());
        entity.setSnapshot(item.snapshot());
        entity.setOperator(item.operator());
        entity.setOperatorId(item.operatorId());
        entity.setDeletedAt(toMillis(item.deletedAt()));
        entity.setExpireAt(toMillis(item.expireAt()));
        entity.setRestored(item.restored());
        entity.setRestoredAt(toMillis(item.restoredAt()));
        entity.setTenantId(item.tenantId());
        entity.setCreatedBy(item.createdBy());
        entity.setCreatedAt(toMillis(item.createdAt()));
        entity.setUpdatedBy(item.updatedBy());
        entity.setUpdatedAt(toMillis(item.updatedAt()));
        entity.setVersion(item.version());
        return entity;
    }

    private static LocalDateTime toDateTime(Long millis) {
        return millis == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    private static Long toMillis(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
