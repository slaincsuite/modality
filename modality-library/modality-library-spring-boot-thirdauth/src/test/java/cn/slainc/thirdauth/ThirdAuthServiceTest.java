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

    private FakeProvider feishu;

    private ThirdAuthService service;

    private ThirdAccountBinding lastBinding;

    @BeforeEach
    void setUp() {
        feishu = new FakeProvider(ThirdPartyPlatform.FEISHU);
        ThirdAuthProperties properties = new ThirdAuthProperties();
        properties.getPlatform().put(ThirdPartyPlatform.FEISHU.code(), enabledPlatform());
        InMemoryThirdAccountBindingStore bindingStore = new InMemoryThirdAccountBindingStore() {
            @Override
            public ThirdAccountBinding save(ThirdAccountBinding binding) {
                lastBinding = super.save(binding);
                return lastBinding;
            }
        };
        service = new ThirdAuthService(new ThirdAuthConfigRegistry(properties, null), List.of(feishu),
                new MemoryStateStore(), new MemoryQrTicketStore(), bindingStore, properties);
    }

    private ThirdAuthProperties.Platform enabledPlatform() {
        ThirdAuthProperties.Platform config = new ThirdAuthProperties.Platform();
        config.setEnabled(true);
        config.setAppKey("cli_app");
        config.setAppSecret("secret");
        config.setRedirectUri("https://cb.example.com/thirdauth/callback/feishu");
        return config;
    }

    @Test
    void beginLoginReturnsAuthorizeUrlAndState() {
        ThirdAuthService.ThirdAuthLoginContext context = service.beginLogin(ThirdPartyPlatform.FEISHU);
        assertTrue(context.authorizeUrl().contains("https://fake.example.com/oauth/authorize"));
        assertNotNull(context.state());
        assertEquals(32, context.state().length());
    }

    @Test
    void firstCallbackRequiresBinding() {
        ThirdAuthService.ThirdAuthLoginContext context = service.beginLogin(ThirdPartyPlatform.FEISHU);
        ThirdAuthService.ThirdAuthLoginResult result = service.handleCallback("feishu",
                Map.of("code", "code-1", "state", context.state()));
        assertTrue(result.needBinding());
        assertNotNull(result.user().getOpenId());
        assertEquals("code-1", feishu.lastCode());
    }

    @Test
    void stateIsOneShotAndReplayRejected() {
        ThirdAuthService.ThirdAuthLoginContext context = service.beginLogin(ThirdPartyPlatform.FEISHU);
        Map<String, String> params = Map.of("code", "code-1", "state", context.state());
        service.handleCallback("feishu", params);
        ThirdPartyException exception = assertThrows(ThirdPartyException.class,
                () -> service.handleCallback("feishu", params));
        assertTrue(exception.getMessage().contains("state 无效或已过期"));
    }

    @Test
    void forgedStateRejected() {
        ThirdPartyException exception = assertThrows(ThirdPartyException.class,
                () -> service.handleCallback("feishu", Map.of("code", "c", "state", "forged")));
        assertTrue(exception.getMessage().contains("state 无效或已过期"));
    }

    @Test
    void platformErrorAndMissingParamsGiveChineseError() {
        ThirdPartyException platformError = assertThrows(ThirdPartyException.class,
                () -> service.handleCallback("feishu",
                        Map.of("errcode", "40029", "errmsg", "invalid code")));
        assertTrue(platformError.getMessage().contains("40029"));

        ThirdPartyException noCode = assertThrows(ThirdPartyException.class,
                () -> service.handleCallback("feishu", Map.of("state", "x")));
        assertTrue(noCode.getMessage().contains("缺少授权码"));

        ThirdPartyException noState = assertThrows(ThirdPartyException.class,
                () -> service.handleCallback("feishu", Map.of("code", "c")));
        assertTrue(noState.getMessage().contains("缺少 state"));
    }

    @Test
    void pausedPlatformInCallbackGivesChineseError() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.handleCallback("wechat", Map.of("code", "c", "state", "s")));
        assertTrue(exception.getMessage().contains("不支持的第三方平台"));
        assertTrue(exception.getMessage().contains("feishu"));
    }

    @Test
    void callbackHitsLocalAccountAfterBinding() {
        feishu.nextUser(FakeProvider.userOf(ThirdPartyPlatform.FEISHU, "open-feishu", null));
        ThirdAuthService.ThirdAuthLoginContext context = service.beginLogin(ThirdPartyPlatform.FEISHU);
        ThirdAuthService.ThirdAuthLoginResult first = service.handleCallback("feishu",
                Map.of("code", "code-1", "state", context.state()));
        service.bind(first.user(), "2001");

        ThirdAuthService.ThirdAuthLoginContext second = service.beginLogin(ThirdPartyPlatform.FEISHU);
        ThirdAuthService.ThirdAuthLoginResult bound = service.handleCallback("feishu",
                Map.of("code", "code-2", "state", second.state()));
        assertTrue(bound.bound());
        assertEquals("2001", bound.userId());
        assertNotNull(lastBinding.getLastLoginAt());
        assertEquals("昵称open-feishu", lastBinding.getNickname());
    }

    @Test
    void bindingConflictAndRebindingSameUser() {
        ThirdPartyUser user = FakeProvider.userOf(ThirdPartyPlatform.FEISHU, "open-777", "union-777");
        service.bind(user, "2001");
        ThirdPartyException conflict = assertThrows(ThirdPartyException.class,
                () -> service.bind(user, "2002"));
        assertTrue(conflict.getMessage().contains("已绑定用户 2001"));
        service.bind(user, "2001");
    }

    @Test
    void oneUserCanBindMultipleThirdAccounts() {
        service.bind(FakeProvider.userOf(ThirdPartyPlatform.FEISHU, "open-a", "union-a"), "3001");
        service.bind(FakeProvider.userOf(ThirdPartyPlatform.FEISHU, "open-b", "union-b"), "3001");
        assertEquals(2, service.listBindings("3001").size());
    }

    @Test
    void unbindThenCallbackRequiresBindingAgain() {
        service.bind(FakeProvider.userOf(ThirdPartyPlatform.FEISHU, "open-999", "union-999"), "4001");
        ThirdPartyException wrongUser = assertThrows(ThirdPartyException.class,
                () -> service.unbind(ThirdPartyPlatform.FEISHU, "open-999", "other"));
        assertTrue(wrongUser.getMessage().contains("无权解绑"));

        service.unbind(ThirdPartyPlatform.FEISHU, "open-999", "4001");
        assertEquals(0, service.listBindings("4001").size());

        ThirdAuthService.ThirdAuthLoginContext context = service.beginLogin(ThirdPartyPlatform.FEISHU);
        feishu.nextUser(FakeProvider.userOf(ThirdPartyPlatform.FEISHU, "open-999", "union-999"));
        ThirdAuthService.ThirdAuthLoginResult result = service.handleCallback("feishu",
                Map.of("code", "c", "state", context.state()));
        assertTrue(result.needBinding());
    }

    @Test
    void queryCrossAppIdentityByUnionId() {
        service.bind(FakeProvider.userOf(ThirdPartyPlatform.FEISHU, "open-u1", "union-shared"), "5001");
        assertTrue(service.findByUnionId(ThirdPartyPlatform.FEISHU, "union-shared").isPresent());
        assertFalse(service.findByUnionId(ThirdPartyPlatform.FEISHU, "union-missing").isPresent());
    }

    @Test
    void unwiredPlatformGivesChineseError() {
        ThirdAuthProperties properties = new ThirdAuthProperties();
        ThirdAuthService unwired = new ThirdAuthService(new ThirdAuthConfigRegistry(properties, null), List.of(),
                new MemoryStateStore(), new MemoryQrTicketStore(), new InMemoryThirdAccountBindingStore(), properties);
        ThirdPartyException exception = assertThrows(ThirdPartyException.class,
                () -> unwired.beginLogin(ThirdPartyPlatform.FEISHU));
        assertTrue(exception.getMessage().contains("平台未启用"));
    }

    @Test
    void unconfiguredPlatformGivesChineseError() {
        ThirdAuthProperties properties = new ThirdAuthProperties();
        ThirdAuthService unconfigured = new ThirdAuthService(new ThirdAuthConfigRegistry(properties, null),
                List.of(feishu), new MemoryStateStore(), new MemoryQrTicketStore(),
                new InMemoryThirdAccountBindingStore(), properties);
        ThirdPartyException exception = assertThrows(ThirdPartyException.class,
                () -> unconfigured.beginLogin(ThirdPartyPlatform.FEISHU));
        assertTrue(exception.getMessage().contains("平台未配置"));
    }

    @Test
    void refreshUnsupportedPlatformGivesChineseError() {
        ThirdPartyException exception = assertThrows(ThirdPartyException.class,
                () -> service.refresh(ThirdPartyPlatform.FEISHU, "rt"));
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
        properties.getPlatform().put(ThirdPartyPlatform.FEISHU.code(), enabledPlatform());
        ThirdAuthService strict = new ThirdAuthService(
                new ThirdAuthConfigRegistry(properties, null), List.of(feishu),
                stateStore, new MemoryQrTicketStore(), new InMemoryThirdAccountBindingStore(), properties);
        assertThrows(ThirdPartyException.class,
                () -> strict.handleCallback("feishu", Map.of("code", "c", "state", "expired")));
    }
}
