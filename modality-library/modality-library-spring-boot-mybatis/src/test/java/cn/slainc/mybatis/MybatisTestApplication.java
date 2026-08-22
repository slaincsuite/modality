package cn.slainc.mybatis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("cn.slainc.mybatis.mapper")
public class MybatisTestApplication {
}
