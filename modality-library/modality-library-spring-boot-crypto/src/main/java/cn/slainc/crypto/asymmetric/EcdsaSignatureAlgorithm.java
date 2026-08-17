package cn.slainc.crypto.asymmetric;

public enum EcdsaSignatureAlgorithm {

    SHA256_WITH_ECDSA("SHA256withECDSA", 256),
    SHA384_WITH_ECDSA("SHA384withECDSA", 384),
    SHA512_WITH_ECDSA("SHA512withECDSA", 521);

    private final String jcaName;

    private final int fieldSize;

    EcdsaSignatureAlgorithm(String jcaName, int fieldSize) {
        this.jcaName = jcaName;
        this.fieldSize = fieldSize;
    }

    public String jcaName() {
        return jcaName;
    }

    public int fieldSize() {
        return fieldSize;
    }

    public static EcdsaSignatureAlgorithm forFieldSize(int fieldSize) {
        if (fieldSize <= 256) {
            return SHA256_WITH_ECDSA;
        }
        return fieldSize <= 384 ? SHA384_WITH_ECDSA : SHA512_WITH_ECDSA;
    }
}
