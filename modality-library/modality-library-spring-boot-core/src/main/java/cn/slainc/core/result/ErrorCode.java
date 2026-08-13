package cn.slainc.core.result;

public interface ErrorCode {

    int code();

    String message();

    default int httpStatus() {
        int value = code();
        if (value >= 100 && value <= 599) {
            return value;
        }
        int prefix = value / 1000;
        return prefix >= 100 && prefix <= 599 ? prefix : 500;
    }
}
