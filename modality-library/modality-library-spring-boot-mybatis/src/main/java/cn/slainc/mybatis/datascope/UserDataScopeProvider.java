package cn.slainc.mybatis.datascope;

import cn.slainc.core.context.UserContext;

public class UserDataScopeProvider implements DataScopeProvider {

    @Override
    public DataScopeCondition resolve(DataScope dataScope, String statementId) {
        if (UserContext.isSuperAdmin()) {
            return DataScopeCondition.noRestriction();
        }
        Long userId = UserContext.userId();
        if (userId == null) {
            return DataScopeCondition.noRestriction();
        }
        if (!dataScope.userScope()) {
            return DataScopeCondition.noRestriction();
        }
        return DataScopeCondition.of(column(dataScope, dataScope.userColumn()) + " = " + userId);
    }

    private static String column(DataScope dataScope, String column) {
        String alias = dataScope.alias();
        return alias == null || alias.isBlank() ? column : alias + "." + column;
    }
}
