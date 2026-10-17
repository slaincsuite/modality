package cn.slainc.modules.auth.service.passkey;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.auth.dto.PasskeyCreateRequest;
import cn.slainc.modules.auth.passkey.PasskeyRecord;
import cn.slainc.modules.auth.store.PasskeyStore;

import java.util.List;

public class PasskeyService {

    private static final int MAX_CREDENTIAL_ID_LENGTH = 256;

    private static final int MAX_NAME_LENGTH = 128;

    private static final int MAX_PUBLIC_KEY_LENGTH = 16_384;

    private final PasskeyStore store;

    public PasskeyService(PasskeyStore store) {
        this.store = store;
    }

    public record PasskeyView(Long id, String credentialId, String name, String publicKey, long createdAt,
                              Long lastUsedAt) {

        static PasskeyView of(PasskeyRecord record) {
            return new PasskeyView(record.getId(), record.getCredentialId(), record.getName(), record.getPublicKey(),
                    record.getCreatedAt(), record.getLastUsedAt());
        }
    }

    public List<PasskeyView> list(Long userId) {
        requireUserId(userId);
        return store.listByUser(userId).stream().map(PasskeyView::of).toList();
    }

    public PasskeyView create(Long userId, PasskeyCreateRequest request) {
        requireUserId(userId);
        if (request == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "通行密钥注册信息不能为空");
        }
        String credentialId = required(request.credentialId(), MAX_CREDENTIAL_ID_LENGTH, "通行密钥标识");
        String name = required(request.name(), MAX_NAME_LENGTH, "通行密钥名称");
        String publicKey = required(request.publicKey(), MAX_PUBLIC_KEY_LENGTH, "通行密钥公钥");
        if (store.findByCredentialId(credentialId) != null) {
            throw new BizException(ResultCode.DATA_DUPLICATE, "通行密钥标识已存在");
        }
        PasskeyRecord record = new PasskeyRecord();
        record.setTenantId(TenantContext.get());
        record.setUserId(userId);
        record.setCredentialId(credentialId);
        record.setName(name);
        record.setPublicKey(publicKey);
        record.setCreatedAt(System.currentTimeMillis());
        return PasskeyView.of(store.save(record));
    }

    public void delete(Long userId, String credentialId) {
        requireUserId(userId);
        String normalized = required(credentialId, MAX_CREDENTIAL_ID_LENGTH, "通行密钥标识");
        if (!store.delete(userId, normalized)) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "通行密钥不存在或不属于该用户");
        }
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "用户标识不能为空");
        }
    }

    private String required(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, label + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BizException(ResultCode.BAD_REQUEST, label + "长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }
}
