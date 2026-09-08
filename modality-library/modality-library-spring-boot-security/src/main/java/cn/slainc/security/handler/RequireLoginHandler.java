package cn.slainc.security.handler;

import cn.dev33.satoken.annotation.handler.SaAnnotationHandlerInterface;
import cn.slainc.security.StpKit;
import cn.slainc.security.annotation.RequireLogin;

import java.lang.reflect.AnnotatedElement;

public class RequireLoginHandler implements SaAnnotationHandlerInterface<RequireLogin> {

    @Override
    public Class<RequireLogin> getHandlerAnnotationClass() {
        return RequireLogin.class;
    }

    @Override
    public void checkMethod(RequireLogin at, AnnotatedElement element) {
        StpKit.of(at.value()).checkLogin();
    }
}
