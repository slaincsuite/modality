package cn.slainc.tenant.resolver;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class TenantResolverComposite implements TenantResolver {

    private final List<TenantResolver> resolvers;

    public TenantResolverComposite(Collection<? extends TenantResolver> resolvers) {
        List<TenantResolver> collected = new ArrayList<>();
        if (resolvers != null) {
            for (TenantResolver resolver : resolvers) {
                if (resolver != null && !(resolver instanceof TenantResolverComposite)) {
                    collected.add(resolver);
                }
            }
        }
        collected.sort(Comparator.comparingInt(TenantResolver::order));
        this.resolvers = List.copyOf(collected);
    }

    @Override
    public Long resolve(HttpServletRequest request) {
        TenantResolution resolution = resolveWith(request);
        return resolution == null ? null : resolution.tenantId();
    }

    public TenantResolution resolveWith(HttpServletRequest request) {
        for (TenantResolver resolver : resolvers) {
            Long tenantId = resolver.resolve(request);
            if (tenantId != null) {
                return new TenantResolution(tenantId, resolver);
            }
        }
        return null;
    }

    @Override
    public int order() {
        return Integer.MIN_VALUE;
    }

    public List<TenantResolver> resolvers() {
        return resolvers;
    }

    public record TenantResolution(Long tenantId, TenantResolver resolver) {
    }
}
