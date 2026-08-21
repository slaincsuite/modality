package cn.slainc.datasource.manager;

import cn.slainc.datasource.crypto.DataSourcePasswordEncryptor;
import cn.slainc.datasource.definition.DataSourceDefinition;
import cn.slainc.datasource.definition.DataSourceDefinitionRepository;
import cn.slainc.datasource.enums.DataSourceStatus;
import cn.slainc.datasource.enums.DatabaseType;
import cn.slainc.datasource.enums.PoolType;
import cn.slainc.datasource.exception.DataSourceException;
import cn.slainc.datasource.health.DataSourceHealthChecker;
import cn.slainc.datasource.properties.DataSourceProperties;
import cn.slainc.datasource.routing.RoutingDataSource;
import cn.slainc.datasource.strategy.WeightedDynamicDataSourceStrategy;
import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.dynamic.datasource.creator.DefaultDataSourceCreator;
import com.baomidou.dynamic.datasource.creator.hikaricp.HikariCpConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicDataSourceManager {

    private static final Logger log = LoggerFactory.getLogger(DynamicDataSourceManager.class);

    private final RoutingDataSource routingDataSource;

    private final DefaultDataSourceCreator dataSourceCreator;

    private final DataSourceDefinitionRepository repository;

    private final DataSourcePasswordEncryptor encryptor;

    private final DataSourceProperties properties;

    private final Set<String> registered = ConcurrentHashMap.newKeySet();

    private final Map<String, DataSourceHealth> healthStates = new ConcurrentHashMap<>();

    public DynamicDataSourceManager(RoutingDataSource routingDataSource,
                                    DefaultDataSourceCreator dataSourceCreator,
                                    DataSourceDefinitionRepository repository,
                                    DataSourcePasswordEncryptor encryptor,
                                    DataSourceProperties properties) {
        this.routingDataSource = routingDataSource;
        this.dataSourceCreator = dataSourceCreator;
        this.repository = repository;
        this.encryptor = encryptor;
        this.properties = properties;
    }

    public Map<String, DataSource> dataSources() {
        return routingDataSource.getDataSources();
    }

    public List<String> dataSourceNames() {
        return new ArrayList<>(routingDataSource.getDataSources().keySet());
    }

    public boolean exists(String name) {
        return routingDataSource.getDataSources().containsKey(name);
    }

    public String primaryName() {
        return routingDataSource.primaryName();
    }

    public void setPrimary(String name) {
        if (!exists(name)) {
            throw new DataSourceException("数据源不存在: " + name);
        }
        routingDataSource.setPrimary(name);
        log.info("动态数据源主库已切换为 [{}]", name);
    }

    public List<DataSourceDefinition> definitions() {
        return repository.findAll();
    }

    public List<DataSourceDefinition> registeredDefinitions() {
        return repository.findAll().stream().filter(item -> registered.contains(item.getName())).toList();
    }

    public Map<String, DataSourceHealth> healthStates() {
        return Map.copyOf(healthStates);
    }

    public DataSourceHealth health(String name) {
        DataSource dataSource = routingDataSource.getDataSources().get(name);
        if (dataSource == null) {
            return DataSourceHealth.unknown(name);
        }
        return healthStates.getOrDefault(name, DataSourceHealth.unknown(name));
    }

    public synchronized DataSourceDefinition register(DataSourceDefinition definition) {
        validate(definition);
        if (exists(definition.getName())) {
            throw new DataSourceException("数据源已存在: " + definition.getName());
        }
        definition.setPassword(encryptor.encrypt(definition.getPassword()));
        DataSourceDefinition saved = repository.save(definition);
        try {
            apply(saved);
        } catch (RuntimeException exception) {
            repository.deleteByName(saved.getName());
            throw exception;
        }
        return saved;
    }

    public synchronized DataSourceDefinition update(DataSourceDefinition definition) {
        validate(definition);
        DataSourceDefinition existing = repository.findByName(definition.getName())
                .orElseThrow(() -> new DataSourceException("数据源不存在: " + definition.getName()));
        definition.setPassword(encryptor.encrypt(definition.getPassword()));
        DataSourceDefinition saved = repository.update(definition);
        try {
            apply(saved);
        } catch (RuntimeException exception) {
            repository.update(existing);
            throw exception;
        }
        return saved;
    }

    public synchronized boolean remove(String name) {
        if (!exists(name) || !registered.contains(name)) {
            throw new DataSourceException("数据源未注册或不可移除: " + name);
        }
        if (name.equals(primaryName())) {
            throw new DataSourceException("主数据源不可移除，请先切换主库: " + name);
        }
        repository.deleteByName(name);
        detach(name);
        return true;
    }

    public synchronized int reload() {
        Set<String> expected = new LinkedHashSet<>();
        int loaded = 0;
        for (DataSourceDefinition definition : repository.findEnabled()) {
            expected.add(definition.getName());
            if (!exists(definition.getName())) {
                apply(definition);
                loaded++;
            }
        }
        for (String name : new ArrayList<>(registered)) {
            if (!expected.contains(name) && !name.equals(primaryName())) {
                detach(name);
            }
        }
        return loaded;
    }

    public DataSourceHealth checkConnection(DataSourceDefinition definition) {
        DataSourceProperty property = toProperty(definition);
        DataSource dataSource = null;
        try {
            dataSource = dataSourceCreator.createDataSource(property);
            return new DataSourceHealthChecker(properties).check(definition.getName(), dataSource);
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            return new DataSourceHealth(definition.getName(), DataSourceStatus.DEGRADED, false, 0L, message,
                    LocalDateTime.now());
        } finally {
            closeQuietly(dataSource);
        }
    }

    private static void closeQuietly(DataSource dataSource) {
        if (dataSource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // 探测用数据源关闭失败不影响主流程
            }
        }
    }

    public DataSourceProperty toProperty(DataSourceDefinition definition) {
        DataSourceProperty property = new DataSourceProperty();
        property.setPoolName(definition.getName());
        DatabaseType databaseType = definition.resolveDatabaseType();
        property.setDriverClassName(firstNonBlank(definition.getDriverClassName(),
                databaseType == null ? null : databaseType.getDriverClassName()));
        property.setUrl(definition.getUrl());
        property.setUsername(definition.getUsername());
        property.setPassword(encryptor.decrypt(definition.getPassword()));
        applyPoolType(property, definition, databaseType);
        HikariCpConfig hikari = new HikariCpConfig();
        hikari.setMinimumIdle(firstNonNull(definition.getMinimumIdle(), properties.getPool().getMinimumIdle()));
        hikari.setMaximumPoolSize(firstNonNull(definition.getMaximumPoolSize(), properties.getPool().getMaximumPoolSize()));
        hikari.setConnectionTimeout(firstNonNull(definition.getConnectionTimeout(), properties.getPool().getConnectionTimeout()));
        hikari.setIdleTimeout(firstNonNull(definition.getIdleTimeout(), properties.getPool().getIdleTimeout()));
        hikari.setMaxLifetime(firstNonNull(definition.getMaxLifetime(), properties.getPool().getMaxLifetime()));
        hikari.setLeakDetectionThreshold(firstNonNull(definition.getLeakDetectionThreshold(),
                properties.getPool().getLeakDetectionThreshold()));
        property.setHikari(hikari);
        return property;
    }

    public DataSource createDataSource(DataSourceDefinition definition) {
        return dataSourceCreator.createDataSource(toProperty(definition));
    }

    private void applyPoolType(DataSourceProperty property, DataSourceDefinition definition, DatabaseType databaseType) {
        PoolType poolType = definition.getPoolType() == null
                ? properties.getPoolType() : definition.getPoolType();
        if (poolType == null || poolType == PoolType.HIKARI) {
            return;
        }
        if (poolType == PoolType.ATOMIKOS) {
            String xaClassName = firstNonBlank(definition.getXaDataSourceClassName(),
                    databaseType == null ? null : databaseType.getXaDataSourceClassName());
            if (xaClassName == null) {
                throw new DataSourceException("数据库类型 "
                        + (databaseType == null ? "未知" : databaseType.getDisplayName())
                        + " 不支持 XA 数据源，无法启用跨数据源强一致事务");
            }
        }
        property.setType(resolvePoolClass(poolType));
    }

    private static Class<? extends DataSource> resolvePoolClass(PoolType poolType) {
        try {
            return Class.forName(poolType.getClassName()).asSubclass(DataSource.class);
        } catch (ClassNotFoundException exception) {
            throw new DataSourceException("启用 " + poolType.getDescription()
                    + " 连接池需要把对应依赖加入运行期类路径: " + poolType.getClassName(), exception);
        }
    }

    public void recordHealth(DataSourceHealth health) {
        healthStates.put(health.name(), health);
    }

    private void apply(DataSourceDefinition definition) {
        DataSource dataSource = createDataSource(definition);
        routingDataSource.addDataSource(definition.getName(), dataSource);
        registered.add(definition.getName());
        WeightedDynamicDataSourceStrategy.setWeight(definition.getName(), weightOf(definition));
        log.info("动态数据源 [{}] 已装载，当前共 {} 个数据源", definition.getName(), dataSourceNames().size());
    }

    private void detach(String name) {
        routingDataSource.removeDataSource(name);
        registered.remove(name);
        WeightedDynamicDataSourceStrategy.removeWeight(name);
        healthStates.remove(name);
        log.info("动态数据源 [{}] 已移除，当前共 {} 个数据源", name, dataSourceNames().size());
    }

    private static int weightOf(DataSourceDefinition definition) {
        return definition.getWeight() == null || definition.getWeight() <= 0 ? 1 : definition.getWeight();
    }

    private static void validate(DataSourceDefinition definition) {
        if (definition == null) {
            throw new DataSourceException("数据源定义不能为空");
        }
        if (definition.getName() == null || definition.getName().isBlank()) {
            throw new DataSourceException("数据源名称不能为空");
        }
        if (definition.getUrl() == null || definition.getUrl().isBlank()) {
            throw new DataSourceException("数据源连接地址不能为空");
        }
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    public Optional<DataSourceDefinition> findDefinition(String name) {
        return repository.findByName(name);
    }
}
