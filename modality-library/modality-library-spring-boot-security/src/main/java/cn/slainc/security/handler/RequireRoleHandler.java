package cn.slainc.security.handler;

import cn.dev33.satoken.annotation.handler.SaAnnotationHandlerInterface;
import cn.slainc.security.StpKit;
import cn.slainc.security.annotation.CheckMode;
import cn.slainc.security.annotation.RequireRole;

import java.lang.reflect.AnnotatedElement;

public class RequireRoleHandler implements SaAnnotationHandlerInterface<RequireRole> {

    @Override
    public Class<RequireRole> getHandlerAnnotationClass() {
        return RequireRole.class;
    }

    @Override
    public void checkMethod(RequireRole at, AnnotatedElement element) {
        if (at.roles() == null || at.roles().length == 0) {
            return;
        }
        if (at.mode() == CheckMode.ANY) {
            StpKit.of(at.value()).checkRoleOr(at.roles());
        } else {
            StpKit.of(at.value()).checkRoleAnd(at.roles());
        }
    }
}
