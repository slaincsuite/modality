package cn.slainc.sso.web;

import cn.dev33.satoken.sso.processor.SaSsoClientProcessor;
import cn.slainc.sso.template.DynamicSsoClientTemplate;

public class SsoClientDelegate {

    private final SaSsoClientProcessor processor = new SaSsoClientProcessor();

    public SsoClientDelegate(DynamicSsoClientTemplate template) {
        processor.ssoClientTemplate = template;
    }

    public Object handle() {
        return processor.dister();
    }
}
