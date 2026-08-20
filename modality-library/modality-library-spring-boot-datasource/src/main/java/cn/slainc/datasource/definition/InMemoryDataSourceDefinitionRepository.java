package cn.slainc.datasource.definition;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class InMemoryDataSourceDefinitionRepository implements DataSourceDefinitionRepository {

    private final Map<String, DataSourceDefinition> store = new ConcurrentHashMap<>();

    private final AtomicLong sequence = new AtomicLong(1);

    @Override
    public List<DataSourceDefinition> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<DataSourceDefinition> findEnabled() {
        return store.values().stream().filter(DataSourceDefinition::isEnabled).collect(Collectors.toList());
    }

    @Override
    public Optional<DataSourceDefinition> findByName(String name) {
        return Optional.ofNullable(store.get(name));
    }

    @Override
    public Optional<DataSourceDefinition> findById(Long id) {
        return store.values().stream().filter(item -> id != null && id.equals(item.getId())).findFirst();
    }

    @Override
    public DataSourceDefinition save(DataSourceDefinition definition) {
        if (definition.getId() == null) {
            definition.setId(sequence.getAndIncrement());
        }
        definition.setCreatedAt(LocalDateTime.now());
        definition.setUpdatedAt(LocalDateTime.now());
        store.put(definition.getName(), definition);
        return definition;
    }

    @Override
    public DataSourceDefinition update(DataSourceDefinition definition) {
        definition.setUpdatedAt(LocalDateTime.now());
        store.put(definition.getName(), definition);
        return definition;
    }

    @Override
    public boolean deleteByName(String name) {
        return store.remove(name) != null;
    }
}
