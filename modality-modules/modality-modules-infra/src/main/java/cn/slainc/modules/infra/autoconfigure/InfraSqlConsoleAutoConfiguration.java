package cn.slainc.modules.infra.autoconfigure;

import cn.slainc.modules.infra.service.SqlConsoleService;
import cn.slainc.modules.infra.web.SqlConsoleController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

// mybatis 是 compileOnly 依赖：类条件必须走字符串形式并独立注册（§6.5），宿主没有 mybatis 时整组静默退避
@AutoConfiguration(afterName = "cn.slainc.mybatis.autoconfigure.MybatisAutoConfiguration")
@ConditionalOnClass(name = "cn.slainc.mybatis.sql.SqlAnalyzer")
@ConditionalOnBean(name = "sqlAnalyzer")
@ConditionalOnProperty(prefix = "modality.infra", name = "enabled", matchIfMissing = true)
@Import(SqlConsoleController.class)
public class InfraSqlConsoleAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SqlConsoleService sqlConsoleService(cn.slainc.mybatis.sql.SqlAnalyzer sqlAnalyzer) {
        return new SqlConsoleService(sqlAnalyzer);
    }
}
