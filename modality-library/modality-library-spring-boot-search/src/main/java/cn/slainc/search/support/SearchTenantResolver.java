package cn.slainc.search.support;

import cn.slainc.core.context.TenantContext;

import java.util.function.Supplier;

public class SearchTenantResolver implements Supplier<Long> {

    @Override
    public Long get() {
        return TenantContext.get();
    }
}
