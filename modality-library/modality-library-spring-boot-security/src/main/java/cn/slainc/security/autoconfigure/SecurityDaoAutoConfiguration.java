package cn.slainc.security.autoconfigure;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.slainc.security.config.SecurityProperties;
import cn.slainc.security.dao.DegradableSaTokenDao;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.SmartInitializingSingleton;

@AutoConfiguration
@AutoConfigureAfter(name = {"cn.slainc.redis.autoconfigure.RedisSaTokenAutoConfiguration",
        "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration"})
@ConditionalOnClass(name = "cn.dev33.satoken.dao.SaTokenDao")
@ConditionalOnBean(SaTokenDao.class)
@ConditionalOnProperty(prefix = "modality.security", name = "dao.enabled", matchIfMissing = true)
public class SecurityDaoAutoConfiguration {

    @Bean
    @Primary
    @ConditionalOnMissingBean(DegradableSaTokenDao.class)
    public DegradableSaTokenDao securitySaTokenDao(SaTokenDao delegate, SecurityProperties properties) {
        return new DegradableSaTokenDao(delegate, properties.getDao().getRetrySeconds());
    }

    @Bean
    public DaoStartupProbe daoStartupProbe(DegradableSaTokenDao dao) {
        return new DaoStartupProbe(dao);
    }

    public static class DaoStartupProbe implements SmartInitializingSingleton {

        private final DegradableSaTokenDao dao;

        public DaoStartupProbe(DegradableSaTokenDao dao) {
            this.dao = dao;
        }

        @Override
        public void afterSingletonsInstantiated() {
            dao.probeAndRecover();
        }
    }
}
