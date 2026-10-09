package cn.slainc.datasource.manager;

import cn.slainc.datasource.crypto.DataSourcePasswordEncryptor;
import cn.slainc.datasource.definition.DataSourceDefinition;
import cn.slainc.datasource.definition.DataSourceDefinitionRepository;
import cn.slainc.datasource.exception.DataSourceException;
import cn.slainc.datasource.health.DataSourceHealthChecker;
import cn.slainc.datasource.properties.DataSourceProperties;
import cn.slainc.datasource.routing.RoutingDataSource;
import com.baomidou.dynamic.datasource.creator.DefaultDataSourceCreator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamicDataSourceManagerTest {

    @Test
    @DisplayName("并发插入同名数据源时转为中文冲突异常")
    void duplicateInsertIsTranslatedToChineseException() {
        DynamicDataSourceManager manager = manager(repositoryThrowingDuplicate());

        DataSourceException exception = assertThrows(DataSourceException.class, () -> manager.register(definition()));

        assertTrue(exception.getMessage().contains("数据源已存在"), exception.getMessage());
    }

    private static DataSourceDefinitionRepository repositoryThrowingDuplicate() {
        DataSourceDefinitionRepository repository = mock(DataSourceDefinitionRepository.class);
        when(repository.findByName(any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenThrow(new DuplicateKeyException("uk_data_source_name"));
        return repository;
    }

    private static DynamicDataSourceManager manager(DataSourceDefinitionRepository repository) {
        RoutingDataSource routing = mock(RoutingDataSource.class);
        when(routing.getDataSources()).thenReturn(new ConcurrentHashMap<String, DataSource>());
        return new DynamicDataSourceManager(routing, mock(DefaultDataSourceCreator.class), repository,
                new DataSourcePasswordEncryptor(false, null, "modality"), mock(DataSourceHealthChecker.class),
                new DataSourceProperties());
    }

    private static DataSourceDefinition definition() {
        DataSourceDefinition definition = new DataSourceDefinition();
        definition.setName("report");
        definition.setUrl("jdbc:h2:mem:report");
        definition.setUsername("sa");
        definition.setPassword("");
        return definition;
    }
}
