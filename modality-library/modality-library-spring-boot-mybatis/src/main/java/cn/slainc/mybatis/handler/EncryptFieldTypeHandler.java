package cn.slainc.mybatis.handler;

import cn.slainc.mybatis.crypto.FieldEncryptor;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(String.class)
public class EncryptFieldTypeHandler extends BaseTypeHandler<String> {

    private final FieldEncryptor encryptor;

    public EncryptFieldTypeHandler(FieldEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, String parameter, JdbcType jdbcType)
            throws SQLException {
        statement.setString(index, encryptor.encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return encryptor.decrypt(resultSet.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return encryptor.decrypt(resultSet.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return encryptor.decrypt(statement.getString(columnIndex));
    }
}
