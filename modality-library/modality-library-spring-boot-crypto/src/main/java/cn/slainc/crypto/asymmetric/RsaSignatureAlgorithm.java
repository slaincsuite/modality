package cn.slainc.crypto.asymmetric;

public enum RsaSignatureAlgorithm {

    SHA256_WITH_RSA("SHA256withRSA", null, 0),
    SHA384_WITH_RSA("SHA384withRSA", null, 0),
    SHA512_WITH_RSA("SHA512withRSA", null, 0),
    SHA256_WITH_RSA_PSS("RSASSA-PSS", "SHA-256", 32),
    SHA384_WITH_RSA_PSS("RSASSA-PSS", "SHA-384", 48),
    SHA512_WITH_RSA_PSS("RSASSA-PSS", "SHA-512", 64);

    private final String jcaName;

    private final String pssDigest;

    private final int pssSaltLength;

    RsaSignatureAlgorithm(String jcaName, String pssDigest, int pssSaltLength) {
        this.jcaName = jcaName;
        this.pssDigest = pssDigest;
        this.pssSaltLength = pssSaltLength;
    }

    public String jcaName() {
        return jcaName;
    }

    public String pssDigest() {
        return pssDigest;
    }

    public int pssSaltLength() {
        return pssSaltLength;
    }

    public boolean isPss() {
        return pssDigest != null;
    }
}
