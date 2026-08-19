package cn.slainc.web.autoconfigure;

import cn.slainc.web.compression.Brotli4jCodec;
import cn.slainc.web.compression.BrotliCodec;
import cn.slainc.web.compression.BrotliCompressionFilter;
import cn.slainc.web.compression.CompressionProperties;
import cn.slainc.web.filter.FilterOrder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "com.aayushatharva.brotli4j.Brotli4jLoader")
@ConditionalOnProperty(prefix = "modality.web.compression.brotli", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(CompressionProperties.class)
public class BrotliCompressionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(BrotliCodec.class)
    public Brotli4jCodec brotli4jCodec(CompressionProperties properties) {
        return new Brotli4jCodec(properties.getBrotli().getQuality());
    }

    @Bean
    @ConditionalOnMissingBean(BrotliCompressionFilter.class)
    public FilterRegistrationBean<BrotliCompressionFilter> brotliCompressionFilter(CompressionProperties properties,
                                                                                   BrotliCodec codec) {
        FilterRegistrationBean<BrotliCompressionFilter> registration =
                new FilterRegistrationBean<>(new BrotliCompressionFilter(properties, codec));
        registration.setOrder(FilterOrder.COMPRESSION);
        registration.setName("brotliCompressionFilter");
        registration.addUrlPatterns("/*");
        registration.setEnabled(true);
        return registration;
    }
}
