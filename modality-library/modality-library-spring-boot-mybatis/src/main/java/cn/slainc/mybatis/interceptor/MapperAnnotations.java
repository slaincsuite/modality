package cn.slainc.mybatis.interceptor;

import org.apache.ibatis.mapping.MappedStatement;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

public final class MapperAnnotations {

    private MapperAnnotations() {
    }

    public static <A extends Annotation> A findMapperAnnotation(MappedStatement statement, Class<A> type) {
        String id = statement.getId();
        int index = id.lastIndexOf('.');
        if (index <= 0) {
            return null;
        }
        try {
            Class<?> mapperType = Class.forName(id.substring(0, index));
            String methodName = id.substring(index + 1);
            for (Method method : mapperType.getMethods()) {
                if (method.getName().equals(methodName)) {
                    A annotation = AnnotationUtils.findAnnotation(method, type);
                    if (annotation != null) {
                        return annotation;
                    }
                }
            }
            return AnnotationUtils.findAnnotation(mapperType, type);
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }
}
