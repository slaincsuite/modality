package cn.slainc.mybatis.procedure;

import cn.slainc.mybatis.exception.MybatisException;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StoredProcedureCall {

    public enum Mode {
        IN, OUT, INOUT
    }

    public record Parameter(String name, Mode mode, Object value, int sqlType) {
    }

    private final String procedureName;

    private final List<Parameter> parameters = new ArrayList<>();

    private Integer fetchSize;

    private int queryTimeoutSeconds = -1;

    private StoredProcedureCall(String procedureName) {
        if (procedureName == null || procedureName.isBlank()) {
            throw new MybatisException("存储过程名称不能为空");
        }
        this.procedureName = procedureName;
    }

    public static StoredProcedureCall of(String procedureName) {
        return new StoredProcedureCall(procedureName);
    }

    public static StoredProcedureCall call(String procedureName) {
        return new StoredProcedureCall(procedureName);
    }

    public StoredProcedureCall in(String name, Object value) {
        parameters.add(new Parameter(name, Mode.IN, value, Types.OTHER));
        return this;
    }

    public StoredProcedureCall in(String name, Object value, int sqlType) {
        parameters.add(new Parameter(name, Mode.IN, value, sqlType));
        return this;
    }

    public StoredProcedureCall out(String name, int sqlType) {
        parameters.add(new Parameter(name, Mode.OUT, null, sqlType));
        return this;
    }

    public StoredProcedureCall inout(String name, Object value, int sqlType) {
        parameters.add(new Parameter(name, Mode.INOUT, value, sqlType));
        return this;
    }

    public StoredProcedureCall fetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
        return this;
    }

    public StoredProcedureCall queryTimeout(int seconds) {
        this.queryTimeoutSeconds = seconds;
        return this;
    }

    public String getProcedureName() {
        return procedureName;
    }

    public List<Parameter> getParameters() {
        return Collections.unmodifiableList(parameters);
    }

    public Integer getFetchSize() {
        return fetchSize;
    }

    public int getQueryTimeoutSeconds() {
        return queryTimeoutSeconds;
    }

    public String callSql() {
        if (parameters.isEmpty()) {
            return "{call " + procedureName + "()}";
        }
        return "{call " + procedureName + "(" + "?,".repeat(parameters.size() - 1) + "?)}";
    }
}
