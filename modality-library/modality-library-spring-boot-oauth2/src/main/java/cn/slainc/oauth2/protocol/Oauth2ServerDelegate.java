package cn.slainc.oauth2.protocol;

import cn.dev33.satoken.oauth2.processor.SaOAuth2ServerProcessor;

public class Oauth2ServerDelegate {

    public Object handle() {
        return SaOAuth2ServerProcessor.instance.dister();
    }
}
