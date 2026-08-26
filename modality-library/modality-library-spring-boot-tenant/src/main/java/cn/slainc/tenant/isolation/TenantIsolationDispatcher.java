package cn.slainc.tenant.isolation;

import cn.slainc.core.exception.BizException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class TenantIsolationDispatcher {

    private final List<TenantIsolationHandler> handlers;

    private final TenantIsolationStrategyResolver strategyResolver;

    public TenantIsolationDispatcher(Collection<? extends TenantIsolationHandler> handlers,
                                     TenantIsolationStrategyResolver strategyResolver) {
        List<TenantIsolationHandler> collected = new ArrayList<>();
        if (handlers != null) {
            for (TenantIsolationHandler handler : handlers) {
                if (handler != null) {
                    collected.add(handler);
                }
            }
        }
        collected.sort(Comparator.comparingInt(TenantIsolationHandler::order));
        this.handlers = List.copyOf(collected);
        this.strategyResolver = strategyResolver;
    }

    public List<TenantIsolationHandler> handlers() {
        return handlers;
    }

    public TenantIsolationStrategy resolveStrategy(TenantInfo tenant) {
        TenantIsolationStrategy strategy = tenant.getIsolationStrategy();
        if (strategy != null) {
            return strategy;
        }
        return strategyResolver == null ? TenantIsolationStrategy.ROW : strategyResolver.resolve(tenant.getTenantId());
    }

    public TenantIsolationHandler handler(TenantIsolationStrategy strategy) {
        for (TenantIsolationHandler handler : handlers) {
            if (handler.supports(strategy)) {
                return handler;
            }
        }
        throw new BizException("没有可用的租户隔离处理器：" + strategy.getDisplayName());
    }

    public void apply(TenantInfo tenant) {
        handler(resolveStrategy(tenant)).apply(tenant);
    }

    public void remove(TenantInfo tenant) {
        handler(resolveStrategy(tenant)).remove(tenant);
    }
}
