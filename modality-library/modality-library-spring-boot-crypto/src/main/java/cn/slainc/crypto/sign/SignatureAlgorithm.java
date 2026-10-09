package cn.slainc.crypto.sign;

public enum SignatureAlgorithm {

    SHA256_WITH_RSA("SHA256withRSA", null, 0),
    SHA384_WITH_RSA("SHA384withRSA", null, 0),
    SHA512_WITH_RSA("SHA512withRSA", null, 0),
    SHA256_WITH_RSA_PSS("RSASSA-PSS", "SHA-256", 32),
    SHA384_WITH_RSA_PSS("RSASSA-PSS", "SHA-384", 48),
    SHA512_WITH_RSA_PSS("RSASSA-PSS", "SHA-512", 64),
    SHA256_WITH_ECDSA("SHA256withECDSA", null, 0),
    SHA384_WITH_ECDSA("SHA384withECDSA", null, 0),
    SHA512_WITH_ECDSA("SHA512withECDSA", null, 0),
    SM3_WITH_SM2("SM3withSM2", null, 0),
    ED25519("Ed25519", null, 0),
    ED448("Ed448", null, 0);

    private final String jcaName;

    private final String pssDigest;

    private final int pssSaltLength;

    SignatureAlgorithm(String jcaName, String pssDigest, int pssSaltLength) {
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

    public boolean isSm2() {
        return this == SM3_WITH_SM2;
    }
}
