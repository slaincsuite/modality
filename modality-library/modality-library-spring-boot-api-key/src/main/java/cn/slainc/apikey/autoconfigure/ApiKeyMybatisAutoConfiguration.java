package cn.slainc.apikey.autoconfigure;

import cn.dev33.satoken.apikey.template.SaApiKeyTemplate;
import cn.slainc.apikey.ApiKeyRecord;
import cn.slainc.apikey.config.ApiKeyProperties;
import cn.slainc.apikey.loader.JdbcApiKeyDataLoader;
import cn.slainc.apikey.loader.ApiKeyMapper;
import cn.slainc.apikey.admin.ApiKeyAdminService;
import cn.slainc.mybatis.properties.MybatisProperties;
import cn.slainc.mybatis.tenant.DefaultTenantLineHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import java.util.List;

@AutoConfiguration(afterName = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@AutoConfigureBefore(name = "cn.slainc.mybatis.autoconfigure.MybatisAutoConfiguration")
@ConditionalOnClass(name = "cn.slainc.mybatis.autoconfigure.MybatisAutoConfiguration")
@ConditionalOnProperty(prefix = "modality.apikey", name = "enabled", matchIfMissing = true)
public class ApiKeyMybatisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ApiKeyTableWhitelist.class)
    public ApiKeyTableWhitelist apiKeyTableWhitelist(ObjectProvider<MybatisProperties> mybatisProperties,
                                                    ObjectProvider<DefaultTenantLineHandler> tenantLineHandler) {
        return new ApiKeyTableWhitelist(List.of(ApiKeyRecord.TABLE_NAME), mybatisProperties.getIfAvailable(),
                tenantLineHandler.getIfAvailable());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({SqlSessionFactory.class, MapperFactoryBean.class})
    @ConditionalOnBean(SqlSessionFactory.class)
    static class ApiKeyPersistenceConfiguration {

        @Bean
        @ConditionalOnMissingBean(ApiKeyMapper.class)
        public MapperFactoryBean<ApiKeyMapper> apiKeyMapper(SqlSessionFactory sqlSessionFactory) {
            MapperFactoryBean<ApiKeyMapper> factory = new MapperFactoryBean<>(ApiKeyMapper.class);
            factory.setSqlSessionFactory(sqlSessionFactory);
            return factory;
        }

        @Bean
        @ConditionalOnMissingBean(JdbcApiKeyDataLoader.class)
        public JdbcApiKeyDataLoader jdbcApiKeyDataLoader(ApiKeyMapper apiKeyMapper, ApiKeyProperties properties) {
            return new JdbcApiKeyDataLoader(apiKeyMapper, properties.getNamespace());
        }

        @Bean
        @ConditionalOnMissingBean
        public ApiKeyAdminService apiKeyAdminService(ApiKeyMapper apiKeyMapper,
                                                     ObjectProvider<SaApiKeyTemplate> template,
                                                     ApiKeyProperties properties) {
            return new ApiKeyAdminService(apiKeyMapper, template.getIfAvailable(), properties);
        }
    }
}
