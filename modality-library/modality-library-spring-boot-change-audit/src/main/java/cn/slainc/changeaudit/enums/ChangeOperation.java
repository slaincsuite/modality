package cn.slainc.changeaudit.enums;

import cn.slainc.utils.string.StringUtils;

import java.util.Locale;

public enum ChangeOperation {

    AUTO,

    INSERT,

    UPDATE,

    DELETE;

    public static ChangeOperation of(String value) {
        if (StringUtils.isBlank(value)) {
            return AUTO;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // 解析失败时退回 AUTO
            return AUTO;
        }
    }
}
