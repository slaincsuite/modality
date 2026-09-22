package cn.slainc.modules.upms;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

// 测试驱动入口：UPMS 的 Bean 全部经 AutoConfiguration.imports 装配，无需组件扫描
@SpringBootConfiguration
@EnableAutoConfiguration
public class UpmsTestApplication {
}
