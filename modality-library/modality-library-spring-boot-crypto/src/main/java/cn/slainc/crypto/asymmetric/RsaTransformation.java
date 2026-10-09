package cn.slainc.crypto.asymmetric;

public enum RsaTransformation {

    PKCS1("RSA/ECB/PKCS1Padding", null),
    OAEP_SHA256("RSA/ECB/OAEPPadding", "SHA-256"),
    OAEP_SHA384("RSA/ECB/OAEPPadding", "SHA-384"),
    OAEP_SHA512("RSA/ECB/OAEPPadding", "SHA-512");

    private final String transformation;

    private final String digest;

    RsaTransformation(String transformation, String digest) {
        this.transformation = transformation;
        this.digest = digest;
    }

    public String transformation() {
        return transformation;
    }

    public String digest() {
        return digest;
    }

    public boolean isOaep() {
        return digest != null;
    }
}
