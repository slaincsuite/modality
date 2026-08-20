package cn.slainc.datasource.enums;

import cn.slainc.datasource.strategy.WeightedDynamicDataSourceStrategy;
import com.baomidou.dynamic.datasource.strategy.DynamicDataSourceStrategy;
import com.baomidou.dynamic.datasource.strategy.LoadBalanceDynamicDataSourceStrategy;
import com.baomidou.dynamic.datasource.strategy.RandomDynamicDataSourceStrategy;

public enum LoadBalanceStrategy {

    ROUND_ROBIN("轮询", LoadBalanceDynamicDataSourceStrategy.class),
    RANDOM("随机", RandomDynamicDataSourceStrategy.class),
    WEIGHTED("权重", WeightedDynamicDataSourceStrategy.class);

    private final String description;

    private final Class<? extends DynamicDataSourceStrategy> strategyClass;

    LoadBalanceStrategy(String description, Class<? extends DynamicDataSourceStrategy> strategyClass) {
        this.description = description;
        this.strategyClass = strategyClass;
    }

    public String getDescription() {
        return description;
    }

    public Class<? extends DynamicDataSourceStrategy> getStrategyClass() {
        return strategyClass;
    }
}
