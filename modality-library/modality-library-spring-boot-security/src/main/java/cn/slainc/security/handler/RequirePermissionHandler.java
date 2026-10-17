package cn.slainc.security.handler;

import cn.dev33.satoken.annotation.handler.SaAnnotationHandlerInterface;
import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpLogic;
import cn.slainc.security.StpKit;
import cn.slainc.security.annotation.CheckMode;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.security.util.PermissionWildcardMatcher;

import java.lang.reflect.AnnotatedElement;
import java.util.List;

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
        StpLogic logic = StpKit.of(at.value());
        logic.checkLogin();
        Object loginId = logic.getLoginIdDefaultNull();
        List<String> granted = SaManager.getStpInterface().getPermissionList(loginId, at.value().value());
        if (at.mode() == CheckMode.ANY) {
            for (String permission : at.permissions()) {
                if (PermissionWildcardMatcher.granted(granted, permission)) {
                    return;
                }
            }
            throw new NotPermissionException(String.join(",", at.permissions()));
        }
        for (String permission : at.permissions()) {
            if (!PermissionWildcardMatcher.granted(granted, permission)) {
                throw new NotPermissionException(permission);
            }
        }
    }
}
