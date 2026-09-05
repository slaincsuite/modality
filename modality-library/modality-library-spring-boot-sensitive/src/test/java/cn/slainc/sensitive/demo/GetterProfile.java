package cn.slainc.sensitive.demo;

import cn.slainc.sensitive.annotation.Sensitive;
import cn.slainc.sensitive.annotation.SensitiveType;

public class GetterProfile {

    private String mobile = "13812348888";

    private String nickname = "小三";

    @Sensitive(type = SensitiveType.PHONE)
    public String getMobile() {
        return mobile;
    }

    public String getNickname() {
        return nickname;
    }
}
