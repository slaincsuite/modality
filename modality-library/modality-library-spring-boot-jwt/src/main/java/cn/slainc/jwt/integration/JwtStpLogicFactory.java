package cn.slainc.jwt.integration;

import cn.dev33.satoken.jwt.StpLogicJwtForMixin;
import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.jwt.StpLogicJwtForStateless;
import cn.dev33.satoken.stp.StpLogic;

// 本工厂是 jwt 与 sa-token-jwt 的可选集成点，Bean 不自动注册：
// 多账号体系的 StpLogic 必须由使用方自己声明，否则会与 security 模块
// 注册的同 loginType StpLogic 冲突覆盖。
// 取舍说明：SIMPLE/MIXIN/STATELESS 三档能力依次递进；
// 开启 STATELESS（无状态）后，令牌完全自包含、服务端不再保存会话映射，
// 踢人下线（kickout）与在线用户列表将不可用；
// 同时 JWT 模式与 session 模块互斥，二者不要对同一 loginType 同时启用。
// 签名密钥取自 sa-token 全局配置 SaTokenConfig.setJwtSecretKey(...)，由使用方设置。
public class JwtStpLogicFactory {

    public StpLogic create(String loginType) {
        return create(loginType, JwtStpMode.SIMPLE);
    }

    public StpLogic create(String loginType, JwtStpMode mode) {
        if (loginType == null || loginType.isBlank()) {
            throw new IllegalArgumentException("loginType 不能为空");
        }
        return switch (mode == null ? JwtStpMode.SIMPLE : mode) {
            case SIMPLE -> new StpLogicJwtForSimple(loginType);
            case MIXIN -> new StpLogicJwtForMixin(loginType);
            case STATELESS -> new StpLogicJwtForStateless(loginType);
        };
    }
}
