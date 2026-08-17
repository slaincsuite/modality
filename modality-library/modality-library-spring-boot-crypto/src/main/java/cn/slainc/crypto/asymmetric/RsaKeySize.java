package cn.slainc.crypto.asymmetric;

public enum RsaKeySize {

    RSA_1024(1024),
    RSA_2048(2048),
    RSA_4096(4096);

    private final int bits;

    RsaKeySize(int bits) {
        this.bits = bits;
    }

    public int bits() {
        return bits;
    }
}
