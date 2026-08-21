package cn.slainc.mybatis.exception;

public class MybatisException extends RuntimeException {

    public MybatisException(String message) {
        super(message);
    }

    public MybatisException(String message, Throwable cause) {
        super(message, cause);
    }
}
