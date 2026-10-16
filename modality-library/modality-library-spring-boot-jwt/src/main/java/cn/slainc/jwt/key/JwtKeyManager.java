package cn.slainc.jwt.key;

import cn.slainc.crypto.codec.Base64Utils;
import cn.slainc.crypto.jwt.JwtAlgorithm;
import cn.slainc.crypto.key.PemUtils;
import cn.slainc.crypto.key.SecretKeyUtils;
import cn.slainc.crypto.random.SecureRandomUtils;
import cn.slainc.jwt.config.JwtProperties;
import cn.slainc.jwt.verify.JwtVerifyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.crypto.SecretKey;

public class JwtKeyManager {

    public static final String GENERATED_KEY_WARNING = "未配置 jwt 密钥，已生成临时密钥，进程重启后旧令牌全部失效，生产环境请配置持久化；"
            + "如需直接阻断启动，请开启 modality.jwt.fail-on-missing-key";

    private static final Logger log = LoggerFactory.getLogger(JwtKeyManager.class);

    private final JwtProperties properties;

    private final JwtKeyPersistence persistence;

    private final Map<String, JwtKeyEntry> entries = new LinkedHashMap<>();

    private final CopyOnWriteArrayList<Runnable> rotationListeners = new CopyOnWriteArrayList<>();

    private volatile String currentKid;

    public JwtKeyManager(JwtProperties properties) {
        this(properties, null);
    }

    public JwtKeyManager(JwtProperties properties, JwtKeyPersistence persistence) {
        this.properties = properties;
        this.persistence = persistence;
        initialize();
    }

    private void initialize() {
        synchronized (entries) {
            if (!entries.isEmpty()) {
                return;
            }
            List<JwtKeyRecord> loaded = loadPersisted();
            for (JwtKeyRecord record : loaded) {
                JwtKeyEntry entry = fromRecord(record);
                entries.put(entry.kid(), entry);
            }
            int index = 0;
            for (JwtProperties.KeySpec spec : properties.getKeys()) {
                index++;
                String kid = spec.getKid() == null || spec.getKid().isBlank() ? "key-" + index : spec.getKid();
                if (entries.containsKey(kid)) {
                    continue;
                }
                JwtKeyEntry entry = fromSpec(kid, spec);
                entries.put(entry.kid(), entry);
            }
            if (entries.isEmpty()) {
                if (properties.isFailOnMissingKey()) {
                    throw new JwtVerifyException("未配置任何 jwt 密钥，已按 modality.jwt.fail-on-missing-key 拒绝自动生成临时密钥");
                }
                log.error(GENERATED_KEY_WARNING);
                JwtKeyEntry entry = generate(properties.getDefaultAlgorithm(), defaultKid());
                entries.put(entry.kid(), entry);
            }
            currentKid = newestKid();
            persistIfPresent();
        }
    }

    public String currentKid() {
        return currentKid;
    }

    public JwtKeyEntry current() {
        return requireByKid(currentKid);
    }

    public JwtKeyEntry findByKid(String kid) {
        synchronized (entries) {
            return kid == null ? null : entries.get(kid);
        }
    }

    public JwtKeyEntry requireByKid(String kid) {
        JwtKeyEntry entry = findByKid(kid);
        if (entry == null) {
            throw new JwtVerifyException("令牌头部 kid 未匹配到任何已配置的 jwt 密钥：" + kid);
        }
        return entry;
    }

    public List<JwtKeyEntry> allEntries() {
        synchronized (entries) {
            return List.copyOf(entries.values());
        }
    }

    public List<JwtKeyEntry> publicExportableEntries() {
        synchronized (entries) {
            return entries.values().stream().filter(entry -> !entry.isSymmetric()).toList();
        }
    }

    public JwtKeyEntry rotate() {
        return rotate(null);
    }

    public JwtKeyEntry rotate(String newKid) {
        synchronized (entries) {
            JwtAlgorithm algorithm = current().algorithm();
            String kid = newKid == null || newKid.isBlank() ? defaultKid() : newKid;
            if (entries.containsKey(kid)) {
                throw new JwtVerifyException("轮换失败，密钥标识（kid）已存在：" + kid);
            }
            JwtKeyEntry entry = generate(algorithm, kid);
            entries.put(kid, entry);
            currentKid = kid;
            evictBeyondRetention();
            persistIfPresent();
        }
        notifyRotation();
        JwtKeyEntry rotated = requireByKid(currentKid);
        log.info("jwt 密钥已轮换，当前签发密钥 kid：{}", rotated.kid());
        return rotated;
    }

    public void onRotation(Runnable listener) {
        if (listener != null) {
            rotationListeners.add(listener);
        }
    }

    private void notifyRotation() {
        for (Runnable listener : rotationListeners) {
            listener.run();
        }
    }

    private void evictBeyondRetention() {
        int retained = Math.max(1, properties.getRetainedKeys());
        while (entries.size() > retained) {
            String oldest = null;
            for (String kid : entries.keySet()) {
                if (kid.equals(currentKid)) {
                    continue;
                }
                oldest = kid;
                break;
            }
            if (oldest == null) {
                return;
            }
            entries.remove(oldest);
            log.info("超出保留上限，已移除旧的 jwt 密钥 kid：{}", oldest);
        }
    }

    private JwtKeyEntry fromSpec(String kid, JwtProperties.KeySpec spec) {
        JwtAlgorithm algorithm = spec.getAlgorithm() == null ? properties.getDefaultAlgorithm() : spec.getAlgorithm();
        if (spec.getPrivateKeyPem() != null && !spec.getPrivateKeyPem().isBlank()) {
            PrivateKey privateKey = PemUtils.readPrivateKey(spec.getPrivateKeyPem());
            return buildEntry(kid, resolveAlgorithm(privateKey, algorithm), privateKey, Instant.now());
        }
        if (spec.getSecretBase64() != null && !spec.getSecretBase64().isBlank()) {
            if (!algorithm.isHmac()) {
                throw new JwtVerifyException("密钥 " + kid + " 配置了 secret-base64，但算法 " + algorithm.alg() + " 不是对称算法");
            }
            SecretKey secretKey = SecretKeyUtils.decode(spec.getSecretBase64(), algorithm.jcaName());
            return buildEntry(kid, algorithm, secretKey, Instant.now());
        }
        return generate(algorithm, kid);
    }

    private JwtKeyEntry fromRecord(JwtKeyRecord record) {
        JwtAlgorithm algorithm;
        try {
            algorithm = JwtAlgorithm.valueOf(record.algorithm());
        } catch (IllegalArgumentException exception) {
            throw new JwtVerifyException("持久化密钥包含不支持的算法：" + record.algorithm());
        }
        if (algorithm.isHmac()) {
            SecretKey secretKey = SecretKeyUtils.decode(record.pem(), algorithm.jcaName());
            return buildEntry(record.kid(), algorithm, secretKey, record.createdAt());
        }
        PrivateKey privateKey = PemUtils.readPrivateKey(record.pem());
        return buildEntry(record.kid(), algorithm, privateKey, record.createdAt());
    }

    private JwtAlgorithm resolveAlgorithm(PrivateKey privateKey, JwtAlgorithm configured) {
        if (configured.isHmac()) {
            throw new JwtVerifyException("密钥配置了非对称私钥 PEM，但算法却是 " + configured.alg());
        }
        boolean keyIsRsa = KeyMaterials.rsaKeyBits(privateKey) > 0;
        if (configured.isRsa() != keyIsRsa) {
            throw new JwtVerifyException("私钥类型与配置的算法 " + configured.alg() + " 不一致");
        }
        if (configured.isEc() && !(privateKey instanceof java.security.interfaces.ECPrivateKey)) {
            throw new JwtVerifyException("私钥类型与配置的算法 " + configured.alg() + " 不一致");
        }
        if (configured.isOkp() && !(privateKey instanceof java.security.interfaces.EdECPrivateKey)) {
            throw new JwtVerifyException("私钥类型与配置的算法 " + configured.alg() + " 不一致");
        }
        return configured;
    }

    private JwtKeyEntry generate(JwtAlgorithm algorithm, String kid) {
        KeyMaterials.KeyPairMaterial material = KeyMaterials.generate(algorithm);
        return buildEntry(kid, algorithm, material.signingKey(), Instant.now());
    }

    private JwtKeyEntry buildEntry(String kid, JwtAlgorithm algorithm, Key signingKey, Instant createdAt) {
        validateStrength(kid, algorithm, signingKey);
        PublicKey publicKey = null;
        if (signingKey instanceof PrivateKey privateKey) {
            publicKey = KeyMaterials.derivePublicKey(privateKey);
        }
        return new JwtKeyEntry(kid, algorithm, signingKey, publicKey, createdAt);
    }

    private void validateStrength(String kid, JwtAlgorithm algorithm, Key signingKey) {
        if (algorithm.isHmac()) {
            int length = signingKey.getEncoded() == null ? 0 : signingKey.getEncoded().length;
            if (length < 32) {
                throw new JwtVerifyException("jwt 对称密钥 " + kid + " 长度 " + length + " 字节，低于安全下限 32 字节，已拒绝");
            }
            return;
        }
        if (algorithm.isRsa()) {
            int bits = KeyMaterials.rsaKeyBits(signingKey);
            if (bits < 2048) {
                throw new JwtVerifyException("jwt RSA 密钥 " + kid + " 强度 " + bits + " 位，低于安全下限 2048 位，已拒绝");
            }
            return;
        }
        if (algorithm.isEc()) {
            int fieldSize = ((java.security.interfaces.ECKey) signingKey).getParams().getCurve().getField().getFieldSize();
            if (fieldSize < 256) {
                throw new JwtVerifyException("jwt EC 密钥 " + kid + " 曲线位数 " + fieldSize + "，仅支持 P-256 及以上曲线，已拒绝");
            }
        }
    }

    private JwtKeyRecord toRecord(JwtKeyEntry entry) {
        String pem;
        if (entry.signingKey() instanceof SecretKey secretKey) {
            pem = Base64Utils.encode(secretKey.getEncoded());
        } else {
            pem = PemUtils.encodePrivateKey((PrivateKey) entry.signingKey());
        }
        return new JwtKeyRecord(entry.kid(), entry.algorithm().name(), pem, entry.createdAt());
    }

    private List<JwtKeyRecord> loadPersisted() {
        if (persistence == null) {
            return List.of();
        }
        try {
            List<JwtKeyRecord> records = persistence.loadAll();
            if (records == null || records.isEmpty()) {
                return List.of();
            }
            // 稳定排序：顺序敏感的「并列取后写入者」由排序前的实现顺序保留
            List<JwtKeyRecord> ordered = new ArrayList<>(records);
            ordered.sort(Comparator.comparing(JwtKeyRecord::createdAt,
                    Comparator.nullsFirst(Comparator.<Instant>naturalOrder())));
            return ordered;
        } catch (RuntimeException exception) {
            log.warn("加载持久化 jwt 密钥失败，将按配置或自动生成重建密钥", exception);
            return List.of();
        }
    }

    private void persistIfPresent() {
        if (persistence == null) {
            return;
        }
        List<JwtKeyRecord> records = new ArrayList<>();
        for (JwtKeyEntry entry : entries.values()) {
            records.add(toRecord(entry));
        }
        try {
            persistence.saveAll(records);
        } catch (RuntimeException exception) {
            log.warn("保存持久化 jwt 密钥失败，仅保留内存密钥，重启后可能无法恢复", exception);
        }
    }

    private String newestKid() {
        String newest = null;
        Instant newestAt = Instant.MIN;
        for (JwtKeyEntry entry : entries.values()) {
            // 时间相同时后写入者胜出：同一毫秒内完成轮换时，新密钥仍应是当前签发密钥
            if (newest == null || !newestAt.isAfter(entry.createdAt())) {
                newest = entry.kid();
                newestAt = entry.createdAt();
            }
        }
        return newest;
    }

    private String defaultKid() {
        return "kid-" + SecureRandomUtils.nextHex(8);
    }
}
