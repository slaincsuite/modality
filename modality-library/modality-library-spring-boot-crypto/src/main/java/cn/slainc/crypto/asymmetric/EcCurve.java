package cn.slainc.crypto.asymmetric;

import cn.slainc.crypto.CryptoException;

public enum EcCurve {

    P_256("secp256r1", 256, "P-256"),
    P_384("secp384r1", 384, "P-384"),
    P_521("secp521r1", 521, "P-521");

    private final String name;

    private final int fieldSize;

    private final String jwkLabel;

    EcCurve(String name, int fieldSize, String jwkLabel) {
        this.name = name;
        this.fieldSize = fieldSize;
        this.jwkLabel = jwkLabel;
    }

    public String curveName() {
        return name;
    }

    public int fieldSize() {
        return fieldSize;
    }

    public String jwkLabel() {
        return jwkLabel;
    }

    public static EcCurve fromFieldSize(int fieldSize) {
        for (EcCurve curve : values()) {
            if (curve.fieldSize == fieldSize) {
                return curve;
            }
        }
        throw new CryptoException("不支持的椭圆曲线位数：" + fieldSize);
    }

    public static EcCurve fromJwkLabel(String label) {
        for (EcCurve curve : values()) {
            if (curve.jwkLabel.equals(label)) {
                return curve;
            }
        }
        throw new CryptoException("不支持的椭圆曲线：" + label);
    }
}
