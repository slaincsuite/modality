package cn.slainc.datasource.autoconfigure;

import cn.slainc.datasource.exception.DataSourceException;
import cn.slainc.datasource.xa.XaDataSourceCreator;
import com.atomikos.icatch.jta.UserTransactionImp;
import com.atomikos.icatch.jta.UserTransactionManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.jta.JtaTransactionManager;

import jakarta.transaction.SystemException;
import jakarta.transaction.UserTransaction;

@AutoConfiguration(after = RoutingDataSourceAutoConfiguration.class)
@ConditionalOnClass(name = "com.atomikos.icatch.jta.UserTransactionManager")
@ConditionalOnProperty(prefix = "modality.datasource.jta", name = "enabled", havingValue = "true")
public class JtaAutoConfiguration {

    @Bean(initMethod = "init", destroyMethod = "close")
    @ConditionalOnMissingBean
    public UserTransactionManager atomikosTransactionManager() {
        UserTransactionManager manager = new UserTransactionManager();
        manager.setForceShutdown(false);
        return manager;
    }

    @Bean
    @ConditionalOnMissingBean
    public UserTransaction atomikosUserTransaction(cn.slainc.datasource.properties.DataSourceProperties properties) {
        UserTransactionImp userTransaction = new UserTransactionImp();
        try {
            userTransaction.setTransactionTimeout(properties.getJta().getTransactionTimeout());
        } catch (SystemException exception) {
            throw new DataSourceException("设置 JTA 事务超时失败", exception);
        }
        return userTransaction;
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(PlatformTransactionManager.class)
    public PlatformTransactionManager transactionManager(UserTransactionManager transactionManager,
                                                         UserTransaction userTransaction) {
        JtaTransactionManager manager = new JtaTransactionManager();
        manager.setTransactionManager(transactionManager);
        manager.setUserTransaction(userTransaction);
        manager.afterPropertiesSet();
        return manager;
    }

    @Bean
    @ConditionalOnMissingBean
    public XaDataSourceCreator xaDataSourceCreator(cn.slainc.datasource.properties.DataSourceProperties properties) {
        return new XaDataSourceCreator(properties);
    }
}
