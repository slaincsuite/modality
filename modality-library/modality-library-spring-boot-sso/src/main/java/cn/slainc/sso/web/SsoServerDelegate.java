package cn.slainc.sso.web;

import cn.dev33.satoken.sso.processor.SaSsoServerProcessor;
import cn.slainc.sso.template.DynamicSsoServerTemplate;

public class SsoServerDelegate {

    private final SaSsoServerProcessor processor = new SaSsoServerProcessor();

    public SsoServerDelegate(DynamicSsoServerTemplate template) {
        processor.ssoServerTemplate = template;
    }

    public Object handle() {
        return processor.dister();
    }
}
