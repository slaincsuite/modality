package cn.slainc.sign.tool;

import cn.slainc.crypto.random.SecureRandomUtils;
import cn.slainc.sign.SignAlgorithm;
import cn.slainc.sign.composer.SignComposer;
import cn.slainc.sign.composer.SignResult;
import cn.slainc.sign.key.SignKeyDefinition;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class SignTool {

    private final SignComposer composer;

    public SignTool(SignComposer composer) {
        if (composer == null) {
            throw new IllegalArgumentException("SignComposer 不能为空");
        }
        this.composer = composer;
    }

    public SignResult sign(SignAlgorithm algorithm, SignKeyDefinition keyDefinition, int version,
                           Map<String, List<String>> params) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = SecureRandomUtils.nextHex(16);
        return sign(algorithm, keyDefinition, version, timestamp, nonce, params, false);
    }

    public SignResult sign(SignAlgorithm algorithm, SignKeyDefinition keyDefinition, int version,
                           String timestamp, String nonce, Map<String, List<String>> params, boolean includeEmpty) {
        if (keyDefinition == null || keyDefinition.getAppId() == null) {
            throw new IllegalArgumentException("密钥定义缺少 appId，无法生成签名");
        }
        String canonical = composer.compose(version, keyDefinition.getAppId(), timestamp, nonce, params, includeEmpty);
        String signature = composer.sign(version, algorithm, keyDefinition, canonical);
        return new SignResult(canonical, signature, timestamp, nonce);
    }
}
