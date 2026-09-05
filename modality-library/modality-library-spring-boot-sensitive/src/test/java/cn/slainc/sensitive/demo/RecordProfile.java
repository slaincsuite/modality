package cn.slainc.sensitive.demo;

import cn.slainc.sensitive.annotation.Sensitive;
import cn.slainc.sensitive.annotation.SensitiveType;

public record RecordProfile(@Sensitive(type = SensitiveType.PHONE) String phone, String name) {
}
