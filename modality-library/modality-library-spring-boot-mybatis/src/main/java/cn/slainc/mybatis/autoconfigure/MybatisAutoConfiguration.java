package cn.slainc.mybatis.autoconfigure;

import cn.slainc.mybatis.batch.BatchHelper;
import cn.slainc.mybatis.config.MybatisConfigRefresher;
import cn.slainc.mybatis.crypto.FieldEncryptor;
import cn.slainc.mybatis.datascope.DataScopeProvider;
import cn.slainc.mybatis.datascope.UserDataScopeProvider;
import cn.slainc.mybatis.dynamic.DefaultTableNameHandler;
import cn.slainc.mybatis.fill.DefaultMetaObjectHandler;
import cn.slainc.mybatis.join.JoinTemplate;
import cn.slainc.mybatis.procedure.StoredProcedureExecutor;
import cn.slainc.mybatis.interceptor.DataScopeInnerInterceptor;
import cn.slainc.mybatis.interceptor.FieldSecurityInterceptor;
import cn.slainc.mybatis.properties.MybatisProperties;
import cn.slainc.mybatis.sql.SqlAnalyzer;
import cn.slainc.mybatis.sql.SqlLogInterceptor;
import cn.slainc.mybatis.interceptor.FieldSecurityInterceptor;
import cn.slainc.mybatis.sql.SqlStatistics;
import cn.slainc.mybatis.tenant.DefaultTenantLineHandler;
import cn.slainc.mybatis.tenant.TenantIgnoreInterceptor;
import org.apache.ibatis.plugin.Interceptor;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.IllegalSQLInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

@AutoConfiguration
@AutoConfigureAfter(name = "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration")
@ConditionalOnClass(MybatisPlusInterceptor.class)
@ConditionalOnProperty(prefix = "modality.mybatis", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MybatisProperties.class)
public class MybatisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(MybatisProperties properties,
                                                         ObjectProvider<DataScopeProvider> dataScopeProvider,
                                                         DefaultTenantLineHandler tenantLineHandler) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        List<InnerInterceptor> inner = new ArrayList<>();
        if (properties.getTenant().isEnabled()) {
            inner.add(new TenantLineInnerInterceptor(tenantLineHandler));
        }
        DataScopeProvider provider = dataScopeProvider.getIfAvailable();
        if (properties.getDataScope().isEnabled() && provider != null) {
            inner.add(new DataScopeInnerInterceptor(provider));
        }
        if (properties.getDynamicTable().isEnabled()) {
            DynamicTableNameInnerInterceptor dynamicTableName = new DynamicTableNameInnerInterceptor();
            dynamicTableName.setTableNameHandler(new DefaultTableNameHandler(properties));
            inner.add(dynamicTableName);
        }
        if (properties.getPagination().isEnabled()) {
            inner.add(pagination(properties));
        }
        inner.add(new OptimisticLockerInnerInterceptor());
        if (properties.getPagination().isBlockAttack()) {
            inner.add(new BlockAttackInnerInterceptor());
        }
        if (properties.getPagination().isIllegalSql()) {
            inner.add(new IllegalSQLInnerInterceptor());
        }
        interceptor.setInterceptors(inner);
        return interceptor;
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultTenantLineHandler tenantLineHandler(MybatisProperties properties) {
        return new DefaultTenantLineHandler(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public MybatisConfigRefresher mybatisConfigRefresher(MybatisProperties properties,
                                                                 DefaultTenantLineHandler tenantLineHandler) {
        return new MybatisConfigRefresher(properties, tenantLineHandler);
    }

    @Bean
    @ConditionalOnMissingBean
    public DataScopeProvider dataScopeProvider() {
        return new UserDataScopeProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public SqlStatistics sqlStatistics(MybatisProperties properties) {
        return new SqlStatistics(properties.getSqlLog().getSlowTopSize());
    }

    @Bean
    @ConditionalOnMissingBean
    public FieldEncryptor fieldEncryptor(MybatisProperties properties) {
        return new FieldEncryptor(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.mybatis.fill", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MetaObjectHandler metaObjectHandler(MybatisProperties properties) {
        return new DefaultMetaObjectHandler(properties);
    }

    @Bean
    @ConditionalOnMissingBean(SqlLogInterceptor.class)
    @ConditionalOnProperty(prefix = "modality.mybatis.sql-log", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SqlLogInterceptor sqlLogInterceptor(MybatisProperties properties, SqlStatistics statistics,
                                               ApplicationEventPublisher eventPublisher) {
        return new SqlLogInterceptor(properties, statistics, eventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean(TenantIgnoreInterceptor.class)
    @ConditionalOnProperty(prefix = "modality.mybatis.tenant", name = "enabled", havingValue = "true", matchIfMissing = true)
    public TenantIgnoreInterceptor tenantIgnoreInterceptor() {
        return new TenantIgnoreInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(FieldSecurityInterceptor.class)
    public FieldSecurityInterceptor fieldSecurityInterceptor(FieldEncryptor encryptor) {
        return new FieldSecurityInterceptor(encryptor, true);
    }

    @Bean
    @ConditionalOnMissingBean
    public BatchHelper batchHelper(SqlSessionFactory sqlSessionFactory) {
        return new BatchHelper(sqlSessionFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcTemplate.class)
    public JoinTemplate joinTemplate(JdbcTemplate jdbcTemplate) {
        return new JoinTemplate(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcTemplate.class)
    public StoredProcedureExecutor storedProcedureExecutor(JdbcTemplate jdbcTemplate) {
        return new StoredProcedureExecutor(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(JdbcTemplate.class)
    public SqlAnalyzer sqlAnalyzer(ObjectProvider<JdbcTemplate> jdbcTemplateProvider, SqlStatistics statistics) {
        return new SqlAnalyzer(jdbcTemplateProvider.getIfAvailable(), statistics);
    }

    private PaginationInnerInterceptor pagination(MybatisProperties properties) {
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor();
        pagination.setMaxLimit(properties.getPagination().getMaxLimit());
        pagination.setOverflow(properties.getPagination().isOverflow());
        pagination.setOptimizeJoin(properties.getPagination().isOptimizeJoin());
        return pagination;
    }
}
