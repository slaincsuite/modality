package cn.slainc.thirdauth;

import cn.slainc.core.exception.ThirdPartyException;
import cn.slainc.thirdauth.binding.InMemoryThirdAccountBindingStore;
import cn.slainc.thirdauth.binding.ThirdAccountBinding;
import cn.slainc.thirdauth.config.ThirdAuthConfigRegistry;
import cn.slainc.thirdauth.config.ThirdAuthProperties;
import cn.slainc.thirdauth.qr.MemoryQrTicketStore;
import cn.slainc.thirdauth.state.MemoryStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThirdAuthServiceTest {

    private FakeProvider wechat;

    private FakeProvider wechatWork;

    private ThirdAuthService service;

    private ThirdAccountBinding lastBinding;

    @BeforeEach
    void setUp() {
        wechat = new FakeProvider(ThirdPartyPlatform.WECHAT);
        wechatWork = new FakeProvider(ThirdPartyPlatform.WECHAT_WORK);
        ThirdAuthProperties properties = new ThirdAuthProperties();
        properties.getPlatform().put(ThirdPartyPlatform.WECHAT.code(), enabledPlatform());
        ThirdAuthProperties.Platform workConfig = new ThirdAuthProperties.Platform();
        workConfig.setEnabled(true);
        workConfig.setAppKey("corp_app");
        workConfig.setAppSecret("secret");
        workConfig.setRedirectUri("https://cb.example.com/thirdauth/callback/wechat_work");
        properties.getPlatform().put(ThirdPartyPlatform.WECHAT_WORK.code(), workConfig);
        ThirdAuthConfigRegistry registry = new ThirdAuthConfigRegistry(properties, null);
        InMemoryThirdAccountBindingStore bindingStore = new InMemoryThirdAccountBindingStore() {
            @Override
            public ThirdAccountBinding save(ThirdAccountBinding binding) {
                lastBinding = super.save(binding);
                return lastBinding;
            }
        };
        service = new ThirdAuthService(registry, List.of(wechat, wechatWork), new MemoryStateStore(),
                new MemoryQrTicketStore(), bindingStore, properties);
    }

    private ThirdAuthProperties.Platform enabledPlatform() {
        ThirdAuthProperties.Platform config = new ThirdAuthProperties.Platform();
        config.setEnabled(true);
        config.setAppKey("wx_app");
        config.setAppSecret("secret");
        config.setRedirectUri("https://cb.example.com/thirdauth/callback/wechat");
        return config;
    }

    @Test
    void beginLoginReturnsAuthorizeUrlAndState() {
        ThirdAuthService.ThirdAuthLoginContext context = service.beginLogin(ThirdPartyPlatform.WECHAT);
        assertTrue(context.authorizeUrl().contains("https://fake.example.com/oauth/authorize"));
        assertNotNull(context.state());
        assertEquals(32, context.state().length());
    }

    @Test
    void firstCallbackRequiresBinding() {
        ThirdAuthService.ThirdAuthLoginContext context = service.beginLogin(ThirdPartyPlatform.WECHAT);
        ThirdAuthService.ThirdAuthLoginResult result = service.handleCallback("wechat",
                Map.of("code", "code-1", "state", context.state()));
        assertTrue(result.needBinding());
        assertNotNull(result.user().getOpenId());
        assertEquals("code-1", wechat.lastCode());
    }

    @Test
    void stateIsOneShotAndReplayRejected() {
        ThirdAuthService.ThirdAuthLoginContext context = service.beginLogin(ThirdPartyPlatform.WECHAT);
        Map<String, String> params = Map.of("code", "code-1", "state", context.state());
        service.handleCallback("wechat", params);
        ThirdPartyException exception = assertThrows(ThirdPartyException.class,
                () -> service.handleCallback("wechat", params));
        assertTrue(exception.getMessage().contains("state 无效或已过期"));
    }

    @Test
    void forgedStateRejected() {
        ThirdPartyException exception = assertThrows(ThirdPartyException.class,
                () -> service.handleCallback("wechat", Map.of("code", "c", "state", "forged")));
        assertTrue(exception.getMessage().contains("state 无效或已过期"));
    }

    @Test
    void platformErrorAndMissingParamsGiveChineseError() {
        ThirdPartyException platformError = assertThrows(ThirdPartyException.class,
                () -> service.handleCallback("wechat",
                        Map.of("errcode", "40029", "errmsg", "invalid code")));
        assertTrue(platformError.getMessage().contains("40029"));

        ThirdPartyException noCode = assertThrows(ThirdPartyException.class,
                () -> service.handleCallback("wechat", Map.of("state", "x")));
        assertTrue(noCode.getMessage().contains("缺少授权码"));

        ThirdPartyException noState = assertThrows(ThirdPartyException.class,
                () -> service.handleCallback("wechat", Map.of("code", "c")));
        assertTrue(noState.getMessage().contains("缺少 state"));

        assertThrows(IllegalArgumentException.class,
                () -> service.handleCallback("twitter", Map.of()));
    }

    @Test
    void callbackHitsLocalAccountAfterBinding() {
        wechat.nextUser(FakeProvider.userOf(ThirdPartyPlatform.WECHAT, "open-wechat", null));
        ThirdAuthService.ThirdAuthLoginContext context = service.beginLogin(ThirdPartyPlatform.WECHAT);
        ThirdAuthService.ThirdAuthLoginResult first = service.handleCallback("wechat",
                Map.of("code", "code-1", "state", context.state()));
        service.bind(first.user(), "2001");

        ThirdAuthService.ThirdAuthLoginContext second = service.beginLogin(ThirdPartyPlatform.WECHAT);
        ThirdAuthService.ThirdAuthLoginResult bound = service.handleCallback("wechat",
                Map.of("code", "code-2", "state", second.state()));
        assertTrue(bound.bound());
        assertEquals("2001", bound.userId());
        assertNotNull(lastBinding.getLastLoginAt());
        assertEquals("昵称open-wechat", lastBinding.getNickname());
    }

    @Test
    void bindingConflictAndRebindingSameUser() {
        ThirdPartyUser user = FakeProvider.userOf(ThirdPartyPlatform.WECHAT, "open-777", "union-777");
        service.bind(user, "2001");
        ThirdPartyException conflict = assertThrows(ThirdPartyException.class,
                () -> service.bind(user, "2002"));
        assertTrue(conflict.getMessage().contains("已绑定用户 2001"));
        service.bind(user, "2001");
    }

    @Test
    void oneUserCanBindMultipleThirdAccounts() {
        service.bind(FakeProvider.userOf(ThirdPartyPlatform.WECHAT, "open-a", "union-a"), "3001");
        service.bind(FakeProvider.userOf(ThirdPartyPlatform.WECHAT, "open-b", "union-b"), "3001");
        assertEquals(2, service.listBindings("3001").size());
    }

    @Test
    void unbindThenCallbackRequiresBindingAgain() {
        service.bind(FakeProvider.userOf(ThirdPartyPlatform.WECHAT, "open-999", "union-999"), "4001");
        ThirdPartyException wrongUser = assertThrows(ThirdPartyException.class,
                () -> service.unbind(ThirdPartyPlatform.WECHAT, "open-999", "other"));
        assertTrue(wrongUser.getMessage().contains("无权解绑"));

        service.unbind(ThirdPartyPlatform.WECHAT, "open-999", "4001");
        assertEquals(0, service.listBindings("4001").size());

        ThirdAuthService.ThirdAuthLoginContext context = service.beginLogin(ThirdPartyPlatform.WECHAT);
        wechat.nextUser(FakeProvider.userOf(ThirdPartyPlatform.WECHAT, "open-999", "union-999"));
        ThirdAuthService.ThirdAuthLoginResult result = service.handleCallback("wechat",
                Map.of("code", "c", "state", context.state()));
        assertTrue(result.needBinding());
    }

    @Test
    void queryCrossAppIdentityByUnionId() {
        service.bind(FakeProvider.userOf(ThirdPartyPlatform.WECHAT, "open-u1", "union-shared"), "5001");
        assertTrue(service.findByUnionId(ThirdPartyPlatform.WECHAT, "union-shared").isPresent());
        assertFalse(service.findByUnionId(ThirdPartyPlatform.WECHAT, "union-missing").isPresent());
        assertFalse(service.findByUnionId(ThirdPartyPlatform.FEISHU, "union-shared").isPresent());
    }

    @Test
    void disabledPlatformGivesChineseError() {
        ThirdPartyException exception = assertThrows(ThirdPartyException.class,
                () -> service.beginLogin(ThirdPartyPlatform.DINGTALK));
        assertTrue(exception.getMessage().contains("平台未启用"));
    }

    @Test
    void refreshUnsupportedPlatformGivesChineseError() {
        ThirdPartyException exception = assertThrows(ThirdPartyException.class,
                () -> service.refresh(ThirdPartyPlatform.WECHAT_WORK, "rt"));
        assertTrue(exception.getMessage().contains("不支持刷新"));
    }

    @Test
    void expiredStateRejected() {
        MemoryStateStore stateStore = new MemoryStateStore() {
            @Override
            public boolean consume(String state) {
                return false;
            }
        };
        ThirdAuthProperties properties = new ThirdAuthProperties();
        properties.getPlatform().put(ThirdPartyPlatform.WECHAT.code(), enabledPlatform());
        ThirdAuthService strict = new ThirdAuthService(
                new ThirdAuthConfigRegistry(properties, null), List.of(wechat),
                stateStore, new MemoryQrTicketStore(), new InMemoryThirdAccountBindingStore(), properties);
        assertThrows(ThirdPartyException.class,
                () -> strict.handleCallback("wechat", Map.of("code", "c", "state", "expired")));
    }
}
