package cn.slainc.datasource;

import com.baomidou.dynamic.datasource.annotation.DS;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootApplication
public class DataSourceTestApplication {

    @Bean
    public RoutingProbe routingProbe() {
        return new RoutingProbe();
    }

    @Bean
    public TransactionProbe transactionProbe(RoutingProbe routingProbe) {
        return new TransactionProbe(routingProbe);
    }

    public static class RoutingProbe {

        @DS("report")
        public String report() {
            return ProbeSql.label();
        }

        @DS("slave")
        public String slave() {
            return ProbeSql.label();
        }

        @DS("#target")
        public String byExpression(String target) {
            return ProbeSql.label();
        }

        @DS("slave")
        @Transactional
        public String slaveInTransaction() {
            return ProbeSql.label();
        }
    }

    public static class TransactionProbe {

        private final RoutingProbe routingProbe;

        public TransactionProbe(RoutingProbe routingProbe) {
            this.routingProbe = routingProbe;
        }

        @Transactional
        public String readSlaveInsideTransaction() {
            return routingProbe.slave();
        }
    }
}
