package cn.slainc.crypto.symmetric;

public enum Padding {

    NONE,
    PKCS5,
    PKCS7;

    public boolean isPadded() {
        return this != NONE;
    }
}
