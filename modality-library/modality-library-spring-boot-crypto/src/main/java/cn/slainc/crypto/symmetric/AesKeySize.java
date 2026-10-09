package cn.slainc.crypto.symmetric;

public enum AesKeySize {

    AES_128(16),
    AES_256(32);

    private final int length;

    AesKeySize(int length) {
        this.length = length;
    }

    public int length() {
        return length;
    }

    public int bits() {
        return length * 8;
    }
}
