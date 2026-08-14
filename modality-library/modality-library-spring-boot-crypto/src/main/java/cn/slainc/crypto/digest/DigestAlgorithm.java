package cn.slainc.crypto.digest;

public enum DigestAlgorithm {

    MD5("MD5"),
    SHA1("SHA-1"),
    SHA256("SHA-256"),
    SHA384("SHA-384"),
    SHA512("SHA-512"),
    SHA3_256("SHA3-256"),
    SHA3_512("SHA3-512");

    private final String jcaName;

    DigestAlgorithm(String jcaName) {
        this.jcaName = jcaName;
    }

    public String jcaName() {
        return jcaName;
    }
}
