package cn.slainc.mybatis.fill;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.core.entity.BaseEntity;
import cn.slainc.core.constant.SystemConstants;
import cn.slainc.mybatis.properties.MybatisProperties;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

public class DefaultMetaObjectHandler implements MetaObjectHandler {

    private final MybatisProperties properties;

    public DefaultMetaObjectHandler(MybatisProperties properties) {
        this.properties = properties;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        if (properties.getFill().isFillAudit()) {
            strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
            strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
            strictInsertFill(metaObject, "createdBy", String.class, operator());
            strictInsertFill(metaObject, "updatedBy", String.class, operator());
        }
        strictInsertFill(metaObject, "deleted", Integer.class, SystemConstants.NOT_DELETED);
        strictInsertFill(metaObject, "version", Integer.class, 0);
        if (properties.getFill().isFillTenant() && properties.getTenant().isEnabled()) {
            strictInsertFill(metaObject, "tenantId", Long.class, TenantContext.getOrDefault());
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        if (properties.getFill().isFillAudit()) {
            strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            strictUpdateFill(metaObject, "updatedBy", String.class, operator());
        }
    }

    private static String operator() {
        String username = UserContext.username();
        if (username != null && !username.isBlank()) {
            return username;
        }
        Long userId = UserContext.userId();
        return userId == null ? SystemConstants.SYSTEM_USER : String.valueOf(userId);
    }

    public static boolean supports(Class<?> type) {
        return BaseEntity.class.isAssignableFrom(type);
    }
}
