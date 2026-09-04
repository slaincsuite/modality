package cn.slainc.sensitive.annotation;

public enum SensitiveType {

    PHONE(3, 4),
    ID_CARD(6, 4),
    BANK_CARD(4, 4),
    EMAIL(1, 0),
    NAME(1, 0),
    ADDRESS(6, 0),
    PASSWORD(0, 0),
    CUSTOM(0, 0);

    private final int prefixKeep;

    private final int suffixKeep;

    SensitiveType(int prefixKeep, int suffixKeep) {
        this.prefixKeep = prefixKeep;
        this.suffixKeep = suffixKeep;
    }

    public int prefixKeep() {
        return prefixKeep;
    }

    public int suffixKeep() {
        return suffixKeep;
    }
}
