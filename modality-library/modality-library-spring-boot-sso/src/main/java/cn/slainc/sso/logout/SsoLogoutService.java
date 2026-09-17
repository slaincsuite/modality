package cn.slainc.sso.logout;

import cn.dev33.satoken.sso.config.SaSsoClientModel;
import cn.dev33.satoken.stp.parameter.SaLogoutParameter;
import cn.dev33.satoken.stp.StpLogic;
import cn.slainc.sso.template.DynamicSsoServerTemplate;

public class SsoLogoutService {

    private final DynamicSsoServerTemplate template;

    public SsoLogoutService(DynamicSsoServerTemplate template) {
        this.template = template;
    }

    public void logoutAll(Object loginId) {
        template.ssoLogout(loginId);
    }

    public void logoutDevice(Object loginId, String deviceId) {
        StpLogic stpLogic = template.getStpLogicOrGlobal();
        SaLogoutParameter parameter = stpLogic.createSaLogoutParameter();
        parameter.setDeviceId(deviceId);
        template.ssoLogout(loginId, parameter, null);
    }

    public void logoutAllExcept(Object loginId, String ignoreClient) {
        template.ssoLogout(loginId, template.getStpLogicOrGlobal().createSaLogoutParameter(), ignoreClient);
    }

    public String logoutSingleClient(String client, Object loginId) {
        SaSsoClientModel clientModel = template.getClientNotNull(client);
        return template.pushToClientByLogoutCall(clientModel, loginId, false,
                template.getStpLogicOrGlobal().createSaLogoutParameter());
    }
}
