package cn.slainc.mybatis.interceptor;

import cn.slainc.mybatis.datascope.DataScope;
import cn.slainc.mybatis.datascope.DataScopeCondition;
import cn.slainc.mybatis.datascope.DataScopeProvider;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.StringJoiner;

public class DataScopeInnerInterceptor implements InnerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DataScopeInnerInterceptor.class);

    private final DataScopeProvider provider;

    public DataScopeInnerInterceptor(DataScopeProvider provider) {
        this.provider = provider;
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds,
                            ResultHandler resultHandler, BoundSql boundSql) {
        DataScope dataScope = resolveAnnotation(ms);
        if (dataScope == null) {
            return;
        }
        DataScopeCondition condition = provider.resolve(dataScope, ms.getId());
        if (condition == null || condition.unrestricted() || condition.expressions().isEmpty()) {
            return;
        }
        String sql = boundSql.getSql();
        String rewritten = rewrite(sql, condition.expressions());
        if (rewritten != null && !rewritten.equals(sql)) {
            PluginUtils.mpBoundSql(boundSql).sql(rewritten);
        }
    }

    private String rewrite(String sql, List<String> expressions) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof Select select)) {
                return null;
            }
            Expression condition = CCJSqlParserUtil.parseCondExpression(combine(expressions));
            apply(select, condition);
            return select.toString();
        } catch (Exception exception) {
            log.warn("数据权限改写失败，已跳过: {}", exception.getMessage());
            return null;
        }
    }

    private void apply(Select select, Expression condition) throws Exception {
        if (select instanceof PlainSelect plainSelect) {
            plainSelect.setWhere(merge(plainSelect.getWhere(), condition));
        } else if (select instanceof SetOperationList setOperationList) {
            for (Select item : setOperationList.getSelects()) {
                apply(item, condition);
            }
        }
    }

    private Expression merge(Expression where, Expression condition) throws Exception {
        if (where == null) {
            return condition;
        }
        return CCJSqlParserUtil.parseCondExpression("(" + where + ") AND (" + condition + ")");
    }

    private String combine(List<String> expressions) {
        StringJoiner joiner = new StringJoiner(" OR ", "(", ")");
        for (String expression : expressions) {
            joiner.add("(" + expression + ")");
        }
        return joiner.toString();
    }

    private DataScope resolveAnnotation(MappedStatement statement) {
        return MapperAnnotations.findMapperAnnotation(statement, DataScope.class);
    }
}
