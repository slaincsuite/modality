package cn.slainc.redis.pubsub;

import cn.slainc.redis.autoconfigure.RedisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RedisListenerRegistrar implements SmartInitializingSingleton, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RedisListenerRegistrar.class);

    private final RedisPubSubService pubSubService;

    private final ListableBeanFactory beanFactory;

    private final RedisProperties properties;

    private final List<Object[]> registered = new ArrayList<>();

    public RedisListenerRegistrar(RedisPubSubService pubSubService,
                                  ListableBeanFactory beanFactory,
                                  RedisProperties properties) {
        this.pubSubService = pubSubService;
        this.beanFactory = beanFactory;
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (properties.getPubsub() != null && !properties.getPubsub().isEnabled()) {
            return;
        }
        for (String name : beanNames()) {
            Class<?> type;
            try {
                type = beanFactory.getType(name, false);
            } catch (Exception exception) {
                continue;
            }
            if (type == null || type.isAnnotation() || type.isInterface()) {
                continue;
            }
            Class<?> userType = ClassUtils.getUserClass(type);
            for (Method method : ReflectionUtils.getAllDeclaredMethods(userType)) {
                RedisListener annotation = AnnotationUtils.findAnnotation(method, RedisListener.class);
                if (annotation == null) {
                    continue;
                }
                Object bean = beanFactory.getBean(name);
                register(bean, userType, method, annotation);
            }
        }
    }

    private Set<String> beanNames() {
        Set<String> names = new LinkedHashSet<>(Arrays.asList(beanFactory.getBeanDefinitionNames()));
        names.addAll(Arrays.asList(beanFactory.getBeanNamesForType(Object.class, false, false)));
        return names;
    }

    private void register(Object bean, Class<?> type, Method method, RedisListener annotation) {
        MessageListener listener = (message, pattern) -> {
            try {
                method.setAccessible(true);
                method.invoke(bean, resolveArgs(method, message));
            } catch (Exception exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                log.warn("Redis 监听方法执行失败，类={}，方法={}，原因={}", type.getSimpleName(), method.getName(),
                        cause.getMessage());
            }
        };
        for (String channel : annotation.channels()) {
            pubSubService.subscribe(channel, listener);
            registered.add(new Object[]{channel, listener, false});
        }
        for (String pattern : annotation.patterns()) {
            pubSubService.subscribePattern(pattern, listener);
            registered.add(new Object[]{pattern, listener, true});
        }
    }

    private Object[] resolveArgs(Method method, Message message) {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        String body = message.getBody() == null ? null : new String(message.getBody(), StandardCharsets.UTF_8);
        String channel = message.getChannel() == null ? null : new String(message.getChannel(), StandardCharsets.UTF_8);
        for (int i = 0; i < parameters.length; i++) {
            Class<?> type = parameters[i].getType();
            if (Message.class.isAssignableFrom(type)) {
                args[i] = message;
            } else if (byte[].class == type) {
                args[i] = message.getBody();
            } else if (String.class == type) {
                String name = parameters[i].getName();
                args[i] = name != null && (name.contains("channel") || name.contains("topic")) ? channel : body;
            } else {
                args[i] = null;
            }
        }
        return args;
    }

    @Override
    public void destroy() {
        for (Object[] entry : registered) {
            if (Boolean.TRUE.equals(entry[2])) {
                continue;
            }
            pubSubService.unsubscribe((String) entry[0]);
        }
        registered.clear();
    }
}
