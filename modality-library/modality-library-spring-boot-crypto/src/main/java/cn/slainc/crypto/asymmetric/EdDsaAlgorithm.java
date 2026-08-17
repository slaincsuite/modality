package cn.slainc.crypto.asymmetric;

public enum EdDsaAlgorithm {

    ED25519("Ed25519", 32),
    ED448("Ed448", 57);

    private final String jcaName;

    private final int keyLength;

    EdDsaAlgorithm(String jcaName, int keyLength) {
        this.jcaName = jcaName;
        this.keyLength = keyLength;
    }

    public String jcaName() {
        return jcaName;
    }

    public int keyLength() {
        return keyLength;
    }
}
