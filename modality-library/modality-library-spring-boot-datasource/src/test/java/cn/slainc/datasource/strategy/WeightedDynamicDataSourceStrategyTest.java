package cn.slainc.datasource.strategy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeightedDynamicDataSourceStrategyTest {

    private final WeightedDynamicDataSourceStrategy strategy = new WeightedDynamicDataSourceStrategy();

    @BeforeEach
    void setUp() {
        WeightedDynamicDataSourceStrategy.clear();
    }

    @AfterEach
    void tearDown() {
        WeightedDynamicDataSourceStrategy.clear();
    }

    @Test
    @DisplayName("按权重比例分配流量")
    void distributesTrafficByWeight() {
        WeightedDynamicDataSourceStrategy.setWeight("slave_1", 3);
        WeightedDynamicDataSourceStrategy.setWeight("slave_2", 1);
        Map<String, Integer> counts = new HashMap<>();

        for (int i = 0; i < 400; i++) {
            counts.merge(strategy.determineKey(List.of("slave_1", "slave_2")), 1, Integer::sum);
        }

        assertEquals(300, counts.get("slave_1"));
        assertEquals(100, counts.get("slave_2"));
    }

    @Test
    @DisplayName("无权重时仍在全部候选中选择")
    void picksFromAllCandidatesWithoutWeights() {
        List<String> names = List.of("slave_1", "slave_2");
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < 200; i++) {
            seen.add(strategy.determineKey(names));
        }

        assertEquals(Set.copyOf(names), seen);
    }

    @Test
    @DisplayName("游标键稳定且移除数据源时被清理")
    void cursorKeysAreStableAndPurged() {
        WeightedDynamicDataSourceStrategy.setWeight("slave_1", 2);
        WeightedDynamicDataSourceStrategy.setWeight("slave_2", 1);

        strategy.determineKey(List.of("slave_1", "slave_2"));
        strategy.determineKey(List.of("slave_2", "slave_1"));

        assertEquals(Set.of("slave_1,slave_2"), WeightedDynamicDataSourceStrategy.cursorKeys());

        WeightedDynamicDataSourceStrategy.removeWeight("slave_1");

        assertTrue(WeightedDynamicDataSourceStrategy.cursorKeys().isEmpty());
    }

    @Test
    @DisplayName("权重被移除后回退到默认权重")
    void removedWeightFallsBackToDefault() {
        WeightedDynamicDataSourceStrategy.setWeight("slave_1", 5);
        WeightedDynamicDataSourceStrategy.removeWeight("slave_1");

        assertEquals(WeightedDynamicDataSourceStrategy.DEFAULT_WEIGHT,
                WeightedDynamicDataSourceStrategy.weightOf("slave_1"));
        assertEquals("slave_1", strategy.determineKey(List.of("slave_1")));
    }

    @Test
    @DisplayName("候选为空时返回空值")
    void emptyCandidatesReturnNull() {
        assertNull(strategy.determineKey(List.of()));
        assertNull(strategy.determineKey(null));
    }
}
