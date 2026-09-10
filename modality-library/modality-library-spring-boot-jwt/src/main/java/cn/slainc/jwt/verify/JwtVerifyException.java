package cn.slainc.jwt.verify;

public class JwtVerifyException extends RuntimeException {

    public JwtVerifyException(String message) {
        super(message);
    }

    public JwtVerifyException(String message, Throwable cause) {
        super(message, cause);
    }
}
