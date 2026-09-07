package cn.slainc.sensitive.metrics;

import cn.slainc.sensitive.core.SensitiveContext;
import cn.slainc.sensitive.core.SensitiveMasker;
import cn.slainc.sensitive.demo.GetterProfile;
import cn.slainc.sensitive.demo.UserProfile;
import cn.slainc.sensitive.support.SensitiveTestSupport;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicrometerSensitiveMetricsTest {

    @Test
    void countsMaskedValuesByTypeAndStrategy() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SensitiveMasker masker = SensitiveTestSupport.masker(SensitiveTestSupport.keyProvider(),
                new MicrometerSensitiveMetrics(registry));
        JsonMapper mapper = SensitiveTestSupport.mapper(masker);

        mapper.writeValueAsString(new GetterProfile());

        Counter counter = registry.find(MicrometerSensitiveMetrics.MASK_COUNTER)
                .tag("type", "PHONE")
                .tag("strategy", "MASK")
                .counter();
        assertNotNull(counter, "应记录脱敏计数");
        assertEquals(1.0, counter.count());
    }

    @Test
    void countsSkippedValuesWhenDisabled() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SensitiveMasker masker = SensitiveTestSupport.masker(SensitiveTestSupport.keyProvider(),
                new MicrometerSensitiveMetrics(registry));
        JsonMapper mapper = SensitiveTestSupport.mapper(masker);

        SensitiveContext.runWithoutSensitive(() -> {
            try {
                mapper.writeValueAsString(new UserProfile());
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });

        Counter counter = registry.find(MicrometerSensitiveMetrics.SKIP_COUNTER).counter();
        assertNotNull(counter, "应记录跳过计数");
        assertTrue(counter.count() > 0);
    }
}
