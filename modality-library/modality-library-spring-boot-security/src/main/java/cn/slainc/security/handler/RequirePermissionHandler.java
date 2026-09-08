package cn.slainc.security.handler;

import cn.dev33.satoken.annotation.handler.SaAnnotationHandlerInterface;
import cn.slainc.security.StpKit;
import cn.slainc.security.annotation.CheckMode;
import cn.slainc.security.annotation.RequirePermission;

import java.lang.reflect.AnnotatedElement;

public class RequirePermissionHandler implements SaAnnotationHandlerInterface<RequirePermission> {

    @Override
    public Class<RequirePermission> getHandlerAnnotationClass() {
        return RequirePermission.class;
    }

    @Override
    public void checkMethod(RequirePermission at, AnnotatedElement element) {
        if (at.permissions() == null || at.permissions().length == 0) {
            return;
        }
        if (at.mode() == CheckMode.ANY) {
            StpKit.of(at.value()).checkPermissionOr(at.permissions());
        } else {
            StpKit.of(at.value()).checkPermissionAnd(at.permissions());
        }
    }
}
