package cn.slainc.mybatis.datascope;

import java.util.List;

public record DataScopeCondition(boolean unrestricted, List<String> expressions) {

    public static DataScopeCondition noRestriction() {
        return new DataScopeCondition(true, List.of());
    }

    public static DataScopeCondition of(String... expressions) {
        return new DataScopeCondition(false, List.of(expressions));
    }
}
