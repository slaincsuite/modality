package cn.slainc.utils.spring;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.Map;

public final class SpringUtils {

    private SpringUtils() {
    }

    public static ApplicationContext getApplicationContext() {
        return SpringContextHolder.applicationContext();
    }

    public static boolean isReady() {
        return SpringContextHolder.applicationContext() != null;
    }

    public static <T> T getBean(Class<T> type) {
        ApplicationContext context = requireContext();
        return context.getBean(type);
    }

    public static Object getBean(String name) {
        return requireContext().getBean(name);
    }

    public static <T> T getBean(String name, Class<T> type) {
        return requireContext().getBean(name, type);
    }

    public static <T> Map<String, T> getBeansOfType(Class<T> type) {
        return requireContext().getBeansOfType(type);
    }

    public static boolean containsBean(String name) {
        return requireContext().containsBean(name);
    }

    public static boolean isSingleton(String name) {
        return requireContext().isSingleton(name);
    }

    public static String getProperty(String key) {
        Environment environment = requireEnvironment();
        return environment.getProperty(key);
    }

    public static String getProperty(String key, String defaultValue) {
        Environment environment = requireEnvironment();
        return environment.getProperty(key, defaultValue);
    }

    public static <T> T getProperty(String key, Class<T> type) {
        return requireEnvironment().getProperty(key, type);
    }

    public static boolean getBooleanProperty(String key, boolean defaultValue) {
        return requireEnvironment().getProperty(key, Boolean.class, defaultValue);
    }

    public static int getIntProperty(String key, int defaultValue) {
        return requireEnvironment().getProperty(key, Integer.class, defaultValue);
    }

    public static String[] getActiveProfiles() {
        return requireEnvironment().getActiveProfiles();
    }

    public static boolean acceptsProfile(String profile) {
        return requireEnvironment().acceptsProfiles(Profiles.of(profile));
    }

    public static String getMessage(String code, Object... args) {
        return SpringContextHolder.messageSource().getMessage(code, args, null);
    }

    public static String getMessageOrDefault(String code, String defaultMessage, Object... args) {
        return SpringContextHolder.messageSource().getMessage(code, args, defaultMessage, null);
    }

    public static boolean isAopProxy(Object candidate) {
        return candidate != null && AopUtils.isAopProxy(candidate);
    }

    public static boolean isCglibProxy(Object candidate) {
        return candidate != null && AopUtils.isCglibProxy(candidate);
    }

    public static Class<?> getTargetClass(Object candidate) {
        return candidate == null ? null : AopUtils.getTargetClass(candidate);
    }

    private static ApplicationContext requireContext() {
        ApplicationContext context = SpringContextHolder.applicationContext();
        if (context == null) {
            throw new IllegalStateException("Spring 应用上下文尚未初始化");
        }
        return context;
    }

    private static Environment requireEnvironment() {
        Environment environment = SpringContextHolder.environment();
        if (environment == null) {
            throw new IllegalStateException("Spring 环境尚未初始化");
        }
        return environment;
    }
}
