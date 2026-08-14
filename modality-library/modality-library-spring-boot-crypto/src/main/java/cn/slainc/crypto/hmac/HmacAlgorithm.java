package cn.slainc.crypto.hmac;

public enum HmacAlgorithm {

    HMAC_SHA1("HmacSHA1"),
    HMAC_SHA256("HmacSHA256"),
    HMAC_SHA384("HmacSHA384"),
    HMAC_SHA512("HmacSHA512"),
    HMAC_SM3("HmacSM3");

    private final String jcaName;

    HmacAlgorithm(String jcaName) {
        this.jcaName = jcaName;
    }

    public String jcaName() {
        return jcaName;
    }
}
