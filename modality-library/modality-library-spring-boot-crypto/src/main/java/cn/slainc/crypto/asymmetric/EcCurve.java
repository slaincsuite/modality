package cn.slainc.crypto.asymmetric;

public enum EcCurve {

    P_256("secp256r1", 256),
    P_384("secp384r1", 384),
    P_521("secp521r1", 521);

    private final String name;

    private final int fieldSize;

    EcCurve(String name, int fieldSize) {
        this.name = name;
        this.fieldSize = fieldSize;
    }

    public String curveName() {
        return name;
    }

    public int fieldSize() {
        return fieldSize;
    }
}
