package cn.slainc.modules.open.service.consent;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.open.dto.ConsentView;
import cn.slainc.modules.open.store.ConsentQueryDao;
import cn.slainc.modules.open.store.ConsentQueryRow;
import cn.slainc.openapi.domain.ConsentRecord;
import cn.slainc.openapi.spi.ConsentService;
import cn.slainc.web.websocket.WebSocketPusher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 授权记录治理：多条件分页、用户一键解约、管理员强制解约、按应用批量解约与解约通知
public class OpenConsentAdminService {

    private static final Logger log = LoggerFactory.getLogger(OpenConsentAdminService.class);

    private final ConsentService consentService;

    private final ObjectProvider<ConsentQueryDao> queryDao;

    private final WebSocketPusher pusher;

    public OpenConsentAdminService(ConsentService consentService, ObjectProvider<ConsentQueryDao> queryDao,
                                   WebSocketPusher pusher) {
        this.consentService = consentService;
        this.queryDao = queryDao;
        this.pusher = pusher;
    }

    public PageResult<ConsentView> page(String userId, String appId, String scope, Boolean active,
                                        Long from, Long to, long current, long size) {
        ConsentQueryDao dao = requireDao();
        return dao.page(userId, appId, scope, active, from, to, current, size).map(OpenConsentAdminService::toView);
    }

    public List<ConsentView> listForUser(String userId, String appId, Boolean active) {
        return page(userId, appId, null, active, null, null, 1, 200).records();
    }

    // 用户主动解约：撤销本人对应用的整笔授权
    public ConsentRecord revokeByUser(String userId, String appId) {
        ConsentRecord record = consentService.revoke(userId, appId);
        notifyRevoked(userId, appId, "用户主动解约");
        return record;
    }

    // 用户签约授权：授予应用一组已登记 Scope（oauth2 授权链路之外的门户直连授权）
    public ConsentRecord grantByUser(String userId, String appId, List<String> scopes) {
        return consentService.grant(userId, appId, scopes, "PORTAL");
    }

    // 管理员强制解约
    public ConsentRecord forceRevoke(String userId, String appId, String operator) {
        ConsentRecord record = consentService.revoke(userId, appId);
        notifyRevoked(userId, appId, "管理员 " + operator + " 强制解约");
        return record;
    }

    public ConsentRecord revokeScope(String userId, String appId, String scope) {
        return consentService.revokeScope(userId, appId, scope);
    }

    // 按应用批量解约：撤销该应用全部活跃授权，返回解约数量
    public Map<String, Object> batchRevokeByApp(String appId, String operator) {
        return batchRevokeByApp(appId, operator, null);
    }

    public Map<String, Object> batchRevokeByApp(String appId, String operator, String reason) {
        String normalizedReason = normalizeReason(reason);
        int revoked = 0;
        ConsentQueryDao dao = queryDao.getIfAvailable();
        for (ConsentRecord record : consentService.listByApp(appId)) {
            if (record.active()) {
                ConsentRecord revokedRecord = consentService.revoke(record.getUserId(), appId);
                if (dao != null) {
                    dao.markRevokeReason(revokedRecord.getId(), normalizedReason);
                }
                notifyRevoked(record.getUserId(), appId, revokeMessage(operator, normalizedReason));
                revoked++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appId", appId);
        result.put("revoked", revoked);
        result.put("reason", normalizedReason);
        return result;
    }

    public List<ConsentView> exportRows(String userId, String appId, Boolean active, Long from, Long to,
                                        int limit) {
        return requireDao().page(userId, appId, null, active, from, to, 1,
                Math.min(Math.max(limit, 1), 10000)).records().stream()
                .map(OpenConsentAdminService::toView).toList();
    }

    private ConsentQueryDao requireDao() {
        ConsentQueryDao dao = queryDao.getIfAvailable();
        if (dao == null) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "授权查询存储不可用（缺少 JdbcTemplate）");
        }
        return dao;
    }

    // 有 WebSocketPusher 时按用户推送解约通知，宿主未提供则记录日志降级
    private void notifyRevoked(String userId, String appId, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "open-consent-revoked");
        payload.put("userId", userId);
        payload.put("appId", appId);
        payload.put("reason", reason);
        payload.put("timestamp", System.currentTimeMillis());
        if (!pusher.sendToUser(userId, payload)) {
            log.info("用户 {} 对应用 {} 的授权已解约: {}", userId, appId, reason);
        }
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String value = reason.trim();
        if (value.length() > 256) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR, "解约原因不能超过 256 个字符");
        }
        return value;
    }

    private static String revokeMessage(String operator, String reason) {
        String message = "管理员 " + operator + " 批量解约";
        return reason == null ? message : message + "：" + reason;
    }

    private static ConsentView toView(ConsentQueryRow row) {
        return new ConsentView(row.id(), row.userId(), row.appId(), row.appName(), row.scopes(), row.grantedAt(),
                row.revokedAt(), row.source(), row.revokeReason());
    }
}
