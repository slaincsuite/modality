package cn.slainc.oauth2.pkce;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.oauth2.data.generate.SaOAuth2DataGenerateDefaultImpl;
import cn.dev33.satoken.oauth2.data.model.AccessTokenModel;
import cn.dev33.satoken.oauth2.data.model.CodeModel;
import cn.dev33.satoken.oauth2.data.model.request.RequestAuthModel;
import cn.dev33.satoken.oauth2.exception.SaOAuth2Exception;
import cn.slainc.oauth2.config.Oauth2Properties;

public class PkceDataGenerate extends SaOAuth2DataGenerateDefaultImpl {

    public static final String PARAM_CODE_CHALLENGE = "code_challenge";

    public static final String PARAM_CODE_CHALLENGE_METHOD = "code_challenge_method";

    public static final String PARAM_CODE_VERIFIER = "code_verifier";

    private final PkceService pkceService;

    private final Oauth2Properties properties;

    public PkceDataGenerate(PkceService pkceService, Oauth2Properties properties) {
        this.pkceService = pkceService;
        this.properties = properties;
    }

    @Override
    public CodeModel generateCode(RequestAuthModel ra) {
        CodeModel codeModel = super.generateCode(ra);
        if (!properties.getPkce().isEnabled()) {
            return codeModel;
        }
        String challenge = SaHolder.getRequest().getParam(PARAM_CODE_CHALLENGE);
        if (challenge == null || challenge.isBlank()) {
            if (properties.getPkce().isRequired()) {
                throw new SaOAuth2Exception("缺少 code_challenge 参数，本次授权要求启用 PKCE");
            }
            return codeModel;
        }
        String method = SaHolder.getRequest().getParam(PARAM_CODE_CHALLENGE_METHOD);
        pkceService.saveChallenge(codeModel.code, method, challenge, properties.getCodeTimeoutSeconds());
        return codeModel;
    }

    @Override
    public AccessTokenModel generateAccessToken(String code) {
        if (properties.getPkce().isEnabled() && pkceService.hasChallenge(code)) {
            String verifier = SaHolder.getRequest().getParam(PARAM_CODE_VERIFIER);
            pkceService.verifyAndConsume(code, verifier);
        }
        return super.generateAccessToken(code);
    }
}
