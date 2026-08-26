package cn.slainc.tenant.lifecycle;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.tenant.entity.Tenant;
import cn.slainc.tenant.enums.TenantStatus;
import cn.slainc.tenant.isolation.TenantInfo;
import cn.slainc.tenant.isolation.TenantIsolationDispatcher;
import cn.slainc.tenant.service.TenantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class TenantLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(TenantLifecycleService.class);

    private final TenantService tenantService;

    private final TenantIsolationDispatcher isolationDispatcher;

    private final List<TenantInitializer> initializers;

    private final TenantLifecycleOptions options;

    public TenantLifecycleService(TenantService tenantService, TenantIsolationDispatcher isolationDispatcher,
                                  Collection<? extends TenantInitializer> initializers,
                                  TenantLifecycleOptions options) {
        this.tenantService = tenantService;
        this.isolationDispatcher = isolationDispatcher;
        List<TenantInitializer> collected = new ArrayList<>();
        if (initializers != null) {
            for (TenantInitializer initializer : initializers) {
                if (initializer != null) {
                    collected.add(initializer);
                }
            }
        }
        collected.sort(Comparator.comparingInt(TenantInitializer::order));
        this.initializers = List.copyOf(collected);
        this.options = options == null ? TenantLifecycleOptions.defaults() : options;
    }

    public Tenant create(Tenant tenant) {
        if (tenant.getExpireAt() == null && options.defaultExpireDays() > 0) {
            tenant.setExpireAt(LocalDateTime.now().plusDays(options.defaultExpireDays()));
        }
        Tenant saved = tenantService.create(tenant);
        applyIsolation(saved);
        initializeData(saved);
        log.info("租户 [{}] 创建完成，隔离策略 [{}]", saved.getId(),
                saved.getIsolationStrategy() == null ? null : saved.getIsolationStrategy().getDisplayName());
        return saved;
    }

    public Tenant trial(Tenant tenant) {
        tenant.setStatus(TenantStatus.TRIAL);
        if (tenant.getExpireAt() == null) {
            tenant.setExpireAt(LocalDateTime.now().plusDays(Math.max(options.trialDays(), 1)));
        }
        return create(tenant);
    }

    public Tenant renew(Long tenantId, LocalDateTime expireAt) {
        if (expireAt == null || !expireAt.isAfter(LocalDateTime.now())) {
            throw new BizException("续期时间必须晚于当前时间");
        }
        Tenant tenant = tenantService.requireById(tenantId);
        rejectDisabled(tenant, "续期");
        tenant.setExpireAt(expireAt);
        if (tenant.getStatus() == TenantStatus.TRIAL || tenant.getStatus() == TenantStatus.EXPIRED) {
            tenant.setStatus(TenantStatus.ACTIVE);
        }
        return tenantService.update(tenant);
    }

    public Tenant expire(Long tenantId) {
        Tenant tenant = tenantService.requireById(tenantId);
        rejectDisabled(tenant, "置为过期");
        tenant.setStatus(TenantStatus.EXPIRED);
        return tenantService.update(tenant);
    }

    public Tenant freeze(Long tenantId) {
        Tenant tenant = tenantService.requireById(tenantId);
        rejectDisabled(tenant, "冻结");
        tenant.setStatus(TenantStatus.FROZEN);
        return tenantService.update(tenant);
    }

    public Tenant activate(Long tenantId) {
        Tenant tenant = tenantService.requireById(tenantId);
        if (tenant.getStatus() == TenantStatus.DISABLED) {
            throw new BizException(ResultCode.DATA_STATE_INVALID, "已停用的租户不允许直接启用：" + tenantId);
        }
        tenant.setStatus(TenantStatus.ACTIVE);
        return tenantService.update(tenant);
    }

    public void delete(Long tenantId) {
        Tenant tenant = tenantService.requireById(tenantId);
        if (tenant.getStatus() == TenantStatus.ACTIVE) {
            log.warn("租户 [{}] 仍处于正常状态，将执行标记删除", tenantId);
        }
        if (options.removeIsolationOnDelete() && isolationDispatcher != null) {
            isolationDispatcher.remove(info(tenant));
        }
        tenantService.delete(tenantId);
        log.info("租户 [{}] 已标记删除", tenantId);
    }

    public List<TenantInitializer> initializers() {
        return initializers;
    }

    private void applyIsolation(Tenant tenant) {
        if (isolationDispatcher == null) {
            return;
        }
        TenantInfo info = info(tenant);
        try {
            isolationDispatcher.apply(info);
        } catch (BizException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BizException(ResultCode.BUSINESS_ERROR,
                    "租户 [" + tenant.getId() + "] 隔离初始化失败：" + exception.getMessage(), exception);
        }
        if (!Objects.equals(tenant.getSchemaName(), info.getSchemaName())
                || !Objects.equals(tenant.getDatasourceName(), info.getDatasourceName())) {
            tenant.setSchemaName(info.getSchemaName());
            tenant.setDatasourceName(info.getDatasourceName());
            tenantService.update(tenant);
        }
    }

    private void initializeData(Tenant tenant) {
        if (!options.initializeOnCreate() || initializers.isEmpty()) {
            return;
        }
        for (TenantInitializer initializer : initializers) {
            initializer.initialize(tenant);
        }
    }

    private static TenantInfo info(Tenant tenant) {
        TenantInfo info = TenantInfo.of(tenant.getId(), tenant.getTenantCode(), tenant.getIsolationStrategy());
        info.setTenantName(tenant.getTenantName());
        info.setSchemaName(tenant.getSchemaName());
        info.setDatasourceName(tenant.getDatasourceName());
        return info;
    }

    private static void rejectDisabled(Tenant tenant, String action) {
        if (tenant.getStatus() == TenantStatus.DISABLED) {
            throw new BizException(ResultCode.DATA_STATE_INVALID,
                    "已停用的租户不允许" + action + "：" + tenant.getId());
        }
    }
}
