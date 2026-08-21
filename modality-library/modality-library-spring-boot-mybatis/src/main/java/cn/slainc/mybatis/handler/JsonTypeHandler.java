package cn.slainc.mybatis.handler;

import cn.slainc.utils.json.JsonUtils;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import tools.jackson.core.type.TypeReference;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(Object.class)
public class JsonTypeHandler<T> extends BaseTypeHandler<T> {

    private final Class<T> type;

    private final TypeReference<T> typeReference;

    public JsonTypeHandler(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("JSON 类型处理器的目标类型不能为空");
        }
        this.type = type;
        this.typeReference = null;
    }

    public JsonTypeHandler(TypeReference<T> typeReference) {
        this.type = null;
        this.typeReference = typeReference;
    }

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, T parameter, JdbcType jdbcType)
            throws SQLException {
        statement.setString(index, JsonUtils.toJson(parameter));
    }

    @Override
    public T getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return parse(resultSet.getString(columnName));
    }

    @Override
    public T getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return parse(resultSet.getString(columnIndex));
    }

    @Override
    public T getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return parse(statement.getString(columnIndex));
    }

    private T parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return typeReference != null ? JsonUtils.parse(json, typeReference) : JsonUtils.parse(json, type);
    }
}
