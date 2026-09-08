package cn.slainc.security.handler;

import cn.dev33.satoken.annotation.handler.SaAnnotationHandlerInterface;
import cn.slainc.security.StpKit;
import cn.slainc.security.annotation.RequireSafe;

import java.lang.reflect.AnnotatedElement;

public class RequireSafeHandler implements SaAnnotationHandlerInterface<RequireSafe> {

    @Override
    public Class<RequireSafe> getHandlerAnnotationClass() {
        return RequireSafe.class;
    }

    @Override
    public void checkMethod(RequireSafe at, AnnotatedElement element) {
        StpKit.of(at.value()).checkSafe(at.service());
    }
}
