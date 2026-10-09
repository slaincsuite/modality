package cn.slainc.datasource.definition;

import java.util.List;
import java.util.Optional;

public interface DataSourceDefinitionRepository {

    List<DataSourceDefinition> findAll();

    List<DataSourceDefinition> findEnabled();

    Optional<DataSourceDefinition> findByName(String name);

    DataSourceDefinition save(DataSourceDefinition definition);

    DataSourceDefinition update(DataSourceDefinition definition);

    boolean deleteByName(String name);
}
