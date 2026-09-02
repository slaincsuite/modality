package cn.slainc.logger.mask;

public interface SensitiveFieldProvider {

    boolean isSensitive(String fieldName);

    default String mask(String fieldName, String value) {
        return value == null || value.isEmpty() ? value : "******";
    }

    default int order() {
        return 0;
    }
}
