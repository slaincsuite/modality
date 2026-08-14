package cn.slainc.crypto.hash;

import org.bouncycastle.crypto.params.Argon2Parameters;

public enum Argon2Variant {

    ARGON2D(Argon2Parameters.ARGON2_d, "argon2d"),
    ARGON2I(Argon2Parameters.ARGON2_i, "argon2i"),
    ARGON2ID(Argon2Parameters.ARGON2_id, "argon2id");

    private final int type;

    private final String label;

    Argon2Variant(int type, String label) {
        this.type = type;
        this.label = label;
    }

    public int type() {
        return type;
    }

    public String label() {
        return label;
    }

    public static Argon2Variant fromLabel(String label) {
        for (Argon2Variant variant : values()) {
            if (variant.label.equalsIgnoreCase(label)) {
                return variant;
            }
        }
        throw new IllegalArgumentException("不支持的 Argon2 变体：" + label);
    }
}
