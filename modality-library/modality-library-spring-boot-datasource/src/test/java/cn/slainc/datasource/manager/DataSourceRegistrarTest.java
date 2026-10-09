package cn.slainc.datasource.manager;

import cn.slainc.datasource.crypto.DataSourcePasswordEncryptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class DataSourceRegistrarTest {

    @Test
    @DisplayName("加密关闭且存在数据源时启动告警提示密码明文保存")
    void warnsWhenEncryptionDisabledAndDataSourcesExist(CapturedOutput output) {
        DynamicDataSourceManager manager = manager(List.of("master", "report"));

        new DataSourceRegistrar(manager, new DataSourcePasswordEncryptor(false, null, "modality"))
                .afterSingletonsInstantiated();

        assertTrue(output.getOut().contains("数据源密码将以明文写入配置表"), output.getOut());
    }

    @Test
    @DisplayName("加密开启时不产生明文告警")
    void silentWhenEncryptionEnabled(CapturedOutput output) {
        DynamicDataSourceManager manager = manager(List.of("master"));

        new DataSourceRegistrar(manager, new DataSourcePasswordEncryptor(true, "modality-test-kek", "test"))
                .afterSingletonsInstantiated();

        assertFalse(output.getOut().contains("数据源密码将以明文写入配置表"), output.getOut());
    }

    @Test
    @DisplayName("没有数据源时不产生明文告警")
    void silentWhenNoDataSourceConfigured(CapturedOutput output) {
        DynamicDataSourceManager manager = manager(List.of());

        new DataSourceRegistrar(manager, new DataSourcePasswordEncryptor(false, null, "modality"))
                .afterSingletonsInstantiated();

        assertFalse(output.getOut().contains("数据源密码将以明文写入配置表"), output.getOut());
    }

    private static DynamicDataSourceManager manager(List<String> names) {
        DynamicDataSourceManager manager = mock(DynamicDataSourceManager.class);
        when(manager.reload()).thenReturn(0);
        when(manager.dataSourceNames()).thenReturn(names);
        return manager;
    }
}
