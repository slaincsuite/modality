package cn.slainc.mybatis.procedure;

import cn.slainc.mybatis.exception.MybatisException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StoredProcedureExecutor {

    private final JdbcTemplate jdbcTemplate;

    public StoredProcedureExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public StoredProcedureResult call(StoredProcedureCall call) {
        if (call == null) {
            throw new MybatisException("存储过程调用参数不能为空");
        }
        return jdbcTemplate.execute((ConnectionCallback<StoredProcedureResult>) connection -> {
            try (CallableStatement statement = connection.prepareCall(call.callSql())) {
                if (call.getQueryTimeoutSeconds() > 0) {
                    statement.setQueryTimeout(call.getQueryTimeoutSeconds());
                }
                if (call.getFetchSize() != null) {
                    statement.setFetchSize(call.getFetchSize());
                }
                bind(statement, call);
                boolean hasResultSet = statement.execute();
                List<List<Map<String, Object>>> resultSets = new ArrayList<>();
                int updateCount = -1;
                while (true) {
                    if (hasResultSet) {
                        try (ResultSet resultSet = statement.getResultSet()) {
                            resultSets.add(read(resultSet));
                        }
                    } else {
                        updateCount = statement.getUpdateCount();
                        if (updateCount == -1) {
                            break;
                        }
                    }
                    hasResultSet = statement.getMoreResults();
                    if (!hasResultSet && statement.getUpdateCount() == -1) {
                        break;
                    }
                }
                return new StoredProcedureResult(readOut(statement, call), resultSets, updateCount);
            }
        });
    }

    private static void bind(CallableStatement statement, StoredProcedureCall call) throws SQLException {
        int index = 1;
        for (StoredProcedureCall.Parameter parameter : call.getParameters()) {
            switch (parameter.mode()) {
                case IN -> setIn(statement, index, parameter);
                case OUT -> statement.registerOutParameter(index, parameter.sqlType());
                case INOUT -> {
                    setIn(statement, index, parameter);
                    statement.registerOutParameter(index, parameter.sqlType());
                }
            }
            index++;
        }
    }

    private static void setIn(CallableStatement statement, int index, StoredProcedureCall.Parameter parameter)
            throws SQLException {
        if (parameter.sqlType() == java.sql.Types.OTHER) {
            statement.setObject(index, parameter.value());
        } else {
            statement.setObject(index, parameter.value(), parameter.sqlType());
        }
    }

    private static Map<String, Object> readOut(CallableStatement statement, StoredProcedureCall call)
            throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        int index = 1;
        for (StoredProcedureCall.Parameter parameter : call.getParameters()) {
            if (parameter.mode() != StoredProcedureCall.Mode.IN) {
                result.put(parameter.name(), statement.getObject(index));
            }
            index++;
        }
        return result;
    }

    private static List<Map<String, Object>> read(ResultSet resultSet) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        while (resultSet.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(metaData.getColumnLabel(i), resultSet.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }
}
