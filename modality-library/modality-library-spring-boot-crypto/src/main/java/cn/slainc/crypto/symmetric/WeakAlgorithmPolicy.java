package cn.slainc.crypto.symmetric;

import cn.slainc.crypto.CryptoException;

public final class WeakAlgorithmPolicy {

    public static final String OPT_IN_PROPERTY = "modality.crypto.allow-weak-algorithms";

    private WeakAlgorithmPolicy() {
    }

    public static void requireOptIn(String algorithm) {
        if (isAllowed()) {
            return;
        }
        throw new CryptoException(algorithm + " 属于已弃用的弱算法，默认禁止使用；确需兼容旧数据请设置系统属性 -D"
                + OPT_IN_PROPERTY + "=true 或调用 WeakAlgorithmPolicy.allowWeakAlgorithms(true)");
    }

    public static boolean isAllowed() {
        return Boolean.getBoolean(OPT_IN_PROPERTY);
    }

    public static void allowWeakAlgorithms(boolean allow) {
        if (allow) {
            System.setProperty(OPT_IN_PROPERTY, "true");
        } else {
            System.clearProperty(OPT_IN_PROPERTY);
        }
    }
}
