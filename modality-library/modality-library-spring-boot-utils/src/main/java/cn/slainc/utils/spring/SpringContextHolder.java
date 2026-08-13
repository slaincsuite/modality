package cn.slainc.utils.spring;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.core.env.Environment;

public class SpringContextHolder implements ApplicationContextAware, EnvironmentAware, MessageSourceAware {

    private static volatile ApplicationContext applicationContext;
    private static volatile Environment environment;
    private static volatile MessageSource messageSource;

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        applicationContext = context;
    }

    @Override
    public void setEnvironment(Environment env) {
        environment = env;
    }

    @Override
    public void setMessageSource(MessageSource source) {
        messageSource = source;
    }

    static ApplicationContext applicationContext() {
        return applicationContext;
    }

    static Environment environment() {
        return environment;
    }

    static MessageSource messageSource() {
        return messageSource;
    }

    static void reset() {
        applicationContext = null;
        environment = null;
        messageSource = null;
    }
}
