package cn.slainc.thirdauth;

import cn.slainc.core.exception.ThirdPartyException;
import cn.slainc.thirdauth.binding.ThirdAccountBinding;
import cn.slainc.thirdauth.binding.ThirdAccountBindingStore;
import cn.slainc.thirdauth.config.ThirdAuthConfigRegistry;
import cn.slainc.thirdauth.config.ThirdAuthProperties;
import cn.slainc.thirdauth.provider.ThirdPartyAuthProvider;
import cn.slainc.thirdauth.qr.QrTicket;
import cn.slainc.thirdauth.qr.QrTicketStore;
import cn.slainc.thirdauth.state.StateStore;
import cn.slainc.utils.json.JsonUtils;

import java.security.SecureRandom;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ThirdAuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ThirdAuthConfigRegistry configRegistry;

    private final Map<ThirdPartyPlatform, ThirdPartyAuthProvider> providers = new EnumMap<>(ThirdPartyPlatform.class);

    private final StateStore stateStore;

    private final QrTicketStore qrTicketStore;

    private final ThirdAccountBindingStore bindingStore;

    private final ThirdAuthProperties properties;

    public ThirdAuthService(ThirdAuthConfigRegistry configRegistry, List<ThirdPartyAuthProvider> providers,
                            StateStore stateStore, QrTicketStore qrTicketStore,
                            ThirdAccountBindingStore bindingStore, ThirdAuthProperties properties) {
        this.configRegistry = configRegistry;
        providers.forEach(provider -> this.providers.put(provider.getPlatform(), provider));
        this.stateStore = stateStore;
        this.qrTicketStore = qrTicketStore;
        this.bindingStore = bindingStore;
        this.properties = properties;
    }

    public ThirdAuthLoginContext beginLogin(ThirdPartyPlatform platform) {
        ThirdPartyAuthProvider provider = requireProvider(platform);
        String state = stateStore.create(properties.getStateTtlSeconds());
        return new ThirdAuthLoginContext(provider.getAuthorizeUrl(state, null), state);
    }

    public ThirdAuthLoginContext beginQrLogin(ThirdPartyPlatform platform) {
        ThirdPartyAuthProvider provider = requireProvider(platform);
        QrTicket ticket = newQrTicket(platform, QrTicket.Purpose.LOGIN, null, null);
        String ticketId = ticket.getTicketId();
        long ttl = remainingTtl(ticket);
        qrTicketStore.save(ticket, ttl);
        stateStore.put(ticketId, ttl);
        return new ThirdAuthLoginContext(provider.getAuthorizeUrl(ticketId, null), ticketId);
    }

    public ThirdAuthLoginContext beginQrBinding(ThirdPartyPlatform platform, String userId, String loginType) {
        if (userId == null || userId.isBlank()) {
            throw new ThirdPartyException("绑定目标用户不能为空");
        }
        ThirdPartyAuthProvider provider = requireProvider(platform);
        QrTicket ticket = newQrTicket(platform, QrTicket.Purpose.BIND, userId, loginType);
        String ticketId = ticket.getTicketId();
        long ttl = remainingTtl(ticket);
        qrTicketStore.save(ticket, ttl);
        stateStore.put(ticketId, ttl);
        return new ThirdAuthLoginContext(provider.getAuthorizeUrl(ticketId, null), ticketId);
    }

    public QrTicket markScanned(String ticketId) {
        QrTicket ticket = requireTicket(ticketId);
        ticket.setStatus(QrTicket.Status.SCANNED.name());
        qrTicketStore.save(ticket, remainingTtl(ticket));
        return ticket;
    }

    public QrTicket pollQr(String ticketId) {
        return qrTicketStore.find(ticketId).orElseGet(() -> {
            QrTicket expired = new QrTicket();
            expired.setTicketId(ticketId);
            expired.setStatus(QrTicket.Status.EXPIRED.name());
            return expired;
        });
    }

    public Optional<ThirdAuthLoginResult> consumeQr(String ticketId) {
        return qrTicketStore.consume(ticketId, QrTicket.Status.CONFIRMED)
                .filter(ticket -> QrTicket.Purpose.LOGIN.name().equals(ticket.getPurpose()))
                .map(ticket -> JsonUtils.parse(ticket.getPayload(), ThirdAuthLoginResult.class));
    }

    public Optional<QrTicket> pollBindingQr(ThirdPartyPlatform platform, String ticketId, String userId,
                                            String loginType) {
        Optional<QrTicket> found = qrTicketStore.find(ticketId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        QrTicket ticket = found.get();
        if (ticket.getPlatform() != platform
                || !QrTicket.Purpose.BIND.name().equals(ticket.getPurpose())
                || !String.valueOf(userId).equals(ticket.getTargetUserId())
                || (ticket.getTargetLoginType() != null
                && !ticket.getTargetLoginType().equals(loginType))) {
            throw new ThirdPartyException("绑定二维码不存在或无权访问");
        }
        return found;
    }

    public Optional<ThirdAccountBinding> bindingResult(QrTicket ticket) {
        if (ticket == null || !QrTicket.Status.CONFIRMED.name().equals(ticket.getStatus())
                || ticket.getPayload() == null || ticket.getPayload().isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(JsonUtils.parse(ticket.getPayload(), ThirdAccountBinding.class));
    }

    public long qrTicketTtlSeconds() {
        return Math.max(1, properties.getQrTicketTtlSeconds());
    }

    public ThirdAuthLoginResult handleCallback(String platform, Map<String, String> params) {
        ThirdPartyPlatform parsed = ThirdPartyPlatform.of(platform);
        if (params == null || params.isEmpty()) {
            throw new ThirdPartyException(parsed.label() + "回调参数为空");
        }
        String errorMessage = errorMessageOf(params);
        if (errorMessage != null) {
            throw new ThirdPartyException(parsed.label() + "回调携带平台错误: " + errorMessage);
        }
        String code = params.get("code");
        if (code == null || code.isBlank()) {
            throw new ThirdPartyException(parsed.label() + "回调缺少授权码 code");
        }
        String state = params.get("state");
        if (state == null || state.isBlank()) {
            throw new ThirdPartyException(parsed.label() + "回调缺少 state");
        }
        if (!stateStore.consume(state)) {
            throw new ThirdPartyException("state 无效或已过期，请重新发起" + parsed.label() + "登录");
        }
        Optional<QrTicket> qrTicket = qrTicketStore.find(state);
        ThirdPartyAuthProvider provider = requireProvider(parsed);
        ThirdPartyUser user = provider.getUserInfo(code, state);
        if (qrTicket.isPresent() && QrTicket.Purpose.BIND.name().equals(qrTicket.get().getPurpose())) {
            QrTicket ticket = qrTicket.get();
            ThirdAccountBinding binding = bind(user, ticket.getTargetUserId());
            confirmQrTicket(ticket, JsonUtils.toJson(binding));
            return new ThirdAuthLoginResult(true, ticket.getTargetUserId(), user);
        }
        ThirdAuthLoginResult result = resolveBinding(user);
        qrTicket.ifPresent(ticket -> confirmQrTicket(ticket, JsonUtils.toJson(result)));
        return result;
    }

    public ThirdAccountBinding bind(ThirdPartyUser user, String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ThirdPartyException("绑定目标用户不能为空");
        }
        Optional<ThirdAccountBinding> existing = bindingStore.findActive(user.getPlatform(), user.getOpenId());
        if (existing.isPresent()) {
            ThirdAccountBinding bound = existing.get();
            if (!bound.getUserId().equals(userId)) {
                throw new ThirdPartyException("该" + user.getPlatform().label()
                        + "账号已绑定用户 " + bound.getUserId() + "，不能重复绑定");
            }
            return syncProfile(bound, user);
        }
        ThirdAccountBinding binding = ThirdAccountBinding.create(user.getPlatform(), user.getOpenId(),
                user.getUnionId(), userId);
        syncProfile(binding, user);
        return bindingStore.save(binding);
    }

    public ThirdAccountBinding unbind(ThirdPartyPlatform platform, String openId, String userId) {
        ThirdAccountBinding binding = bindingStore.findActive(platform, openId)
                .orElseThrow(() -> new ThirdPartyException(platform.label() + "账号未绑定，无法解绑"));
        if (!binding.getUserId().equals(userId)) {
            throw new ThirdPartyException("该绑定不属于用户 " + userId + "，无权解绑");
        }
        binding.setStatus(ThirdAccountBinding.Status.UNBOUND.name());
        return bindingStore.save(binding);
    }

    public List<ThirdAccountBinding> listBindings(String userId) {
        return bindingStore.listByUser(userId);
    }

    public Optional<ThirdAccountBinding> findByUnionId(ThirdPartyPlatform platform, String unionId) {
        return bindingStore.findByUnionId(platform, unionId);
    }

    public ThirdPartyUser refresh(ThirdPartyPlatform platform, String refreshToken) {
        ThirdPartyAuthProvider provider = requireProvider(platform);
        if (!provider.supportsRefresh()) {
            throw new ThirdPartyException(platform.label() + "平台不支持刷新访问令牌");
        }
        return provider.refresh(refreshToken);
    }

    private ThirdAuthLoginResult resolveBinding(ThirdPartyUser user) {
        Optional<ThirdAccountBinding> bound = bindingStore.findActive(user.getPlatform(), user.getOpenId());
        if (bound.isPresent()) {
            ThirdAccountBinding binding = bound.get();
            syncProfile(binding, user);
            binding.setLastLoginAt(System.currentTimeMillis());
            bindingStore.save(binding);
            return new ThirdAuthLoginResult(true, binding.getUserId(), user);
        }
        return new ThirdAuthLoginResult(false, null, user);
    }

    private ThirdAccountBinding syncProfile(ThirdAccountBinding binding, ThirdPartyUser user) {
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            binding.setNickname(user.getNickname());
        }
        if (user.getAvatar() != null && !user.getAvatar().isBlank()) {
            binding.setAvatar(user.getAvatar());
        }
        if (user.getUnionId() != null && !user.getUnionId().isBlank()) {
            binding.setUnionId(user.getUnionId());
        }
        return binding;
    }

    private void confirmQrTicket(QrTicket scanned, String payload) {
        scanned.setStatus(QrTicket.Status.CONFIRMED.name());
        scanned.setPayload(payload);
        qrTicketStore.save(scanned, remainingTtl(scanned));
    }

    private QrTicket requireTicket(String ticketId) {
        return qrTicketStore.find(ticketId)
                .orElseThrow(() -> new ThirdPartyException("二维码票据不存在或已过期，请重新生成"));
    }

    private long remainingTtl(QrTicket ticket) {
        long remaining = (ticket.getExpireAt() - System.currentTimeMillis()) / 1000;
        return Math.max(remaining, 1);
    }

    private QrTicket newQrTicket(ThirdPartyPlatform platform, QrTicket.Purpose purpose, String targetUserId,
                                 String targetLoginType) {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        long ttl = qrTicketTtlSeconds();
        QrTicket ticket = new QrTicket();
        ticket.setTicketId(HexFormat.of().formatHex(bytes));
        ticket.setPlatform(platform);
        ticket.setPurpose(purpose.name());
        ticket.setTargetUserId(targetUserId);
        ticket.setTargetLoginType(targetLoginType);
        ticket.setCreatedAt(System.currentTimeMillis());
        ticket.setExpireAt(ticket.getCreatedAt() + ttl * 1000);
        return ticket;
    }

    private ThirdPartyAuthProvider requireProvider(ThirdPartyPlatform platform) {
        ThirdPartyAuthProvider provider = providers.get(platform);
        if (provider == null) {
            throw new ThirdPartyException(platform.label() + "平台未启用，请检查 thirdauth 配置");
        }
        configRegistry.require(platform);
        return provider;
    }

    private String errorMessageOf(Map<String, String> params) {
        String error = params.get("error");
        if (error != null && !error.isBlank()) {
            String description = params.get("error_description");
            return description == null ? error : error + " (" + description + ")";
        }
        String errCode = params.get("errcode");
        if (errCode != null && !errCode.isBlank() && !"0".equals(errCode.trim())) {
            String message = params.get("errmsg");
            return errCode + (message == null ? "" : " " + message);
        }
        return null;
    }

    public record ThirdAuthLoginContext(String authorizeUrl, String state) {
    }

    public record ThirdAuthLoginResult(boolean bound, String userId, ThirdPartyUser user) {

        public boolean needBinding() {
            return !bound;
        }
    }
}
