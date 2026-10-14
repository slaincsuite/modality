package cn.slainc.recycle.autoconfigure;

import cn.slainc.recycle.cleaner.RecycleBinCleaner;
import cn.slainc.recycle.registry.RecycleBinRegistry;
import cn.slainc.recycle.registry.RecyclableType;
import cn.slainc.recycle.spi.RecycleItemStore;
import cn.slainc.recycle.support.RecycleSnapshotCodec;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecycleAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RecycleAutoConfiguration.class));

    @Test
    void shouldRegisterTheRecycleBinBeans() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RecycleBinRegistry.class);
            assertThat(context).hasSingleBean(RecycleSnapshotCodec.class);
            assertThat(context).doesNotHaveBean(RecycleItemStore.class);
            assertThat(context).hasSingleBean(RecycleBinCleaner.class);
            assertThat(context.getBean(RecycleBinCleaner.class).purgeExpired()).isZero();
        });
    }

    @Test
    void shouldNotRegisterAnythingWhenTheRecycleBinIsDisabled() {
        runner.withPropertyValues("modality.recycle.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(RecycleBinCleaner.class);
            assertThat(context).doesNotHaveBean(RecycleBinRegistry.class);
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCollectTheRegisteredRecyclableTypes() {
        RecyclableType type = RecyclableType.of("DEMO", RecycleAutoConfigurationTest.class,
                mock(BaseMapper.class), entity -> 1L, entity -> "demo", true);
        runner.withBean("demoRecyclableType", RecyclableType.class, () -> type).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RecycleBinRegistry.class).all()).containsExactly(type);
        });
    }

    @Test
    void shouldPurgeExpiredItemsThroughTheRegisteredStore() {
        RecycleItemStore store = mock(RecycleItemStore.class);
        when(store.deleteExpired(any())).thenReturn(4);
        runner.withBean(RecycleItemStore.class, () -> store).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RecycleBinCleaner.class).purgeExpired()).isEqualTo(4);
            verify(store, atLeastOnce()).deleteExpired(any());
        });
    }
}
