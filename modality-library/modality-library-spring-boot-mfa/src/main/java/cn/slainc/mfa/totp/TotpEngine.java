package cn.slainc.mfa.totp;

import cn.slainc.crypto.random.SecureRandomUtils;
import cn.slainc.crypto.util.ConstantTimeUtils;
import cn.slainc.mfa.config.MfaConfigReader;
import cn.slainc.utils.string.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

public class TotpEngine {

    private static final Logger log = LoggerFactory.getLogger(TotpEngine.class);

    public static final String HMAC_ALGORITHM = "HmacSHA1";

    public static final int SECRET_KEY_LENGTH = 20;

    private static final int[] POWER_OF_TEN = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000};

    private static final int MIN_DIGITS = 6;

    private static final int MAX_DIGITS = 8;

    private static final int DEFAULT_PERIOD_SECONDS = 30;

    private final MfaConfigReader config;

    public TotpEngine(MfaConfigReader config) {
        this.config = config == null ? new MfaConfigReader(null, null) : config;
    }

    public int digits() {
        return Math.min(Math.max(config.digits(), MIN_DIGITS), MAX_DIGITS);
    }

    public int periodSeconds() {
        return config.periodSeconds() > 0 ? config.periodSeconds() : DEFAULT_PERIOD_SECONDS;
    }

    public int window() {
        return Math.max(config.window(), 0);
    }

    public String generateSecretKey() {
        return Base32Codec.encode(SecureRandomUtils.nextBytes(SECRET_KEY_LENGTH));
    }

    public String generate(String secretKey) {
        return generate(secretKey, System.currentTimeMillis() / 1000L);
    }

    public String generate(String secretKey, long epochSecond) {
        byte[] key = Base32Codec.decode(normalizeSecret(secretKey));
        return hotp(key, epochSecond / periodSeconds(), digits());
    }

    public boolean verify(String secretKey, String code) {
        return verify(secretKey, code, window());
    }

    public boolean verify(String secretKey, String code, int window) {
        return verify(secretKey, code, window, System.currentTimeMillis() / 1000L);
    }

    public boolean verify(String secretKey, String code, int window, long epochSecond) {
        if (StringUtils.isBlank(secretKey)) {
            return false;
        }
        String normalized = normalizeCode(code);
        if (normalized == null) {
            return false;
        }
        try {
            byte[] key = Base32Codec.decode(normalizeSecret(secretKey));
            long counter = epochSecond / periodSeconds();
            int span = Math.max(window, 0);
            for (int offset = -span; offset <= span; offset++) {
                long candidate = counter + offset;
                if (candidate < 0) {
                    continue;
                }
                if (ConstantTimeUtils.equals(hotp(key, candidate, digits()), normalized)) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException e) {
            log.warn("TOTP 校验失败：{}", e.getMessage());
            return false;
        }
    }

    public String otpAuthUri(String issuer, String account, String secretKey) {
        return new OtpAuthUrlBuilder().build(issuer, account, secretKey, digits(), periodSeconds());
    }

    private String hotp(byte[] key, long counter, int digits) {
        byte[] counterBytes = new byte[8];
        long value = counter;
        for (int i = 7; i >= 0; i--) {
            counterBytes[i] = (byte) (value & 0xFF);
            value >>>= 8;
        }
        byte[] mac = hmacSha1(key, counterBytes);
        // RFC 4226 动态截断：以末字节低 4 位为偏移取 31 位整数
        int offset = mac[mac.length - 1] & 0x0F;
        int binary = ((mac[offset] & 0x7F) << 24) | ((mac[offset + 1] & 0xFF) << 16)
                | ((mac[offset + 2] & 0xFF) << 8) | (mac[offset + 3] & 0xFF);
        int modulus = POWER_OF_TEN[digits];
        return StringUtils.leftPad(Integer.toString(binary % modulus), digits, '0');
    }

    private byte[] hmacSha1(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("当前 JDK 不支持 HmacSHA1", e);
        }
    }

    private String normalizeCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        StringBuilder builder = new StringBuilder(code.length());
        for (int i = 0; i < code.length(); i++) {
            char current = code.charAt(i);
            if (Character.isWhitespace(current)) {
                continue;
            }
            if (current < '0' || current > '9') {
                return null;
            }
            builder.append(current);
        }
        return builder.length() == digits() ? builder.toString() : null;
    }

    static String normalizeSecret(String secretKey) {
        if (secretKey == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(secretKey.length());
        for (int i = 0; i < secretKey.length(); i++) {
            char current = secretKey.charAt(i);
            if (current == ' ' || current == '-' || current == '=') {
                continue;
            }
            builder.append(Character.toUpperCase(current));
        }
        return builder.toString();
    }
}
