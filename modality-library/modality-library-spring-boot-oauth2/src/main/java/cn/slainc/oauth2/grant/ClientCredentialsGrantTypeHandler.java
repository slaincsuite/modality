package cn.slainc.oauth2.grant;

import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.oauth2.SaOAuth2Manager;
import cn.dev33.satoken.oauth2.consts.GrantType;
import cn.dev33.satoken.oauth2.data.model.AccessTokenModel;
import cn.dev33.satoken.oauth2.data.model.ClientTokenModel;
import cn.dev33.satoken.oauth2.granttype.handler.SaOAuth2GrantTypeHandlerInterface;

import java.util.List;

public class ClientCredentialsGrantTypeHandler implements SaOAuth2GrantTypeHandlerInterface {

    @Override
    public String getHandlerGrantType() {
        return GrantType.client_credentials;
    }

    @Override
    public AccessTokenModel getAccessToken(SaRequest req, String clientId, List<String> scopes) {
        ClientTokenModel ct = SaOAuth2Manager.getDataGenerate().generateClientToken(clientId, scopes);
        AccessTokenModel at = new AccessTokenModel();
        at.accessToken = ct.clientToken;
        at.clientId = ct.clientId;
        at.scopes = ct.scopes;
        at.expiresTime = ct.expiresTime;
        at.tokenType = ct.tokenType;
        at.grantType = GrantType.client_credentials;
        at.extraData = ct.extraData;
        at.createTime = ct.createTime;
        return at;
    }
}
