package cn.slainc.mybatis.procedure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StoredProcedureResult {

    private final Map<String, Object> outParameters;

    private final List<List<Map<String, Object>>> resultSets;

    private final int updateCount;

    public StoredProcedureResult(Map<String, Object> outParameters, List<List<Map<String, Object>>> resultSets,
                                 int updateCount) {
        this.outParameters = outParameters == null ? Map.of() : new LinkedHashMap<>(outParameters);
        this.resultSets = resultSets == null ? List.of() : List.copyOf(resultSets);
        this.updateCount = updateCount;
    }

    public Map<String, Object> getOutParameters() {
        return outParameters;
    }

    public Object out(String name) {
        return outParameters.get(name);
    }

    public Long outLong(String name) {
        Object value = outParameters.get(name);
        return value instanceof Number number ? number.longValue() : null;
    }

    public Integer outInt(String name) {
        Object value = outParameters.get(name);
        return value instanceof Number number ? number.intValue() : null;
    }

    public String outString(String name) {
        Object value = outParameters.get(name);
        return value == null ? null : String.valueOf(value);
    }

    public List<List<Map<String, Object>>> getResultSets() {
        return resultSets;
    }

    public List<Map<String, Object>> firstResultSet() {
        return resultSets.isEmpty() ? List.of() : resultSets.get(0);
    }

    public int getUpdateCount() {
        return updateCount;
    }
}
