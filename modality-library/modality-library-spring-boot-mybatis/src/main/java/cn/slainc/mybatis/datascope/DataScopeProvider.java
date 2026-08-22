package cn.slainc.mybatis.datascope;

public interface DataScopeProvider {

    DataScopeCondition resolve(DataScope dataScope, String statementId);
}
