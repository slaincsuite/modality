package cn.slainc.crypto.hash;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.random.SecureRandomUtils;
import cn.slainc.crypto.util.ConstantTimeUtils;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;

public final class BCryptUtils {

    public static final String VERSION = "2a";

    public static final int SALT_LENGTH = 16;

    public static final int DEFAULT_COST = 10;

    public static final int MIN_COST = 4;

    public static final int MAX_COST = 31;

    private BCryptUtils() {
    }

    public static String hash(String password) {
        return hash(password, DEFAULT_COST);
    }

    public static String hash(String password, int cost) {
        if (password == null) {
            throw new CryptoException("口令不能为空");
        }
        requireCost(cost);
        return OpenBSDBCrypt.generate(VERSION, password.toCharArray(), SecureRandomUtils.nextBytes(SALT_LENGTH), cost);
    }

    public static boolean verify(String password, String hash) {
        if (password == null || hash == null) {
            return false;
        }
        try {
            return OpenBSDBCrypt.checkPassword(hash, password.toCharArray());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static boolean verifyQuietly(String password, String hash) {
        return verify(password, hash);
    }

    public static boolean matches(String hash, String password, String candidate) {
        return ConstantTimeUtils.equals(hash, hash(password, cost(hash))) && verify(candidate, hash);
    }

    public static int cost(String hash) {
        if (hash == null || hash.length() < 7) {
            throw new CryptoException("非法的 bcrypt 哈希：" + hash);
        }
        try {
            return Integer.parseInt(hash.substring(4, 6));
        } catch (NumberFormatException exception) {
            throw new CryptoException("非法的 bcrypt 哈希：" + hash, exception);
        }
    }

    public static boolean needsRehash(String hash, int cost) {
        return hash == null || hash.length() < 7 || cost(hash) != cost;
    }

    private static void requireCost(int cost) {
        if (cost < MIN_COST || cost > MAX_COST) {
            throw new CryptoException("bcrypt 强度必须在 " + MIN_COST + " 与 " + MAX_COST + " 之间");
        }
    }
}
