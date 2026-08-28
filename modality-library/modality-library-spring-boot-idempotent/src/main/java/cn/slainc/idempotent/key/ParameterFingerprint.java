package cn.slainc.idempotent.key;

import cn.slainc.idempotent.support.IdempotentContext;
import cn.slainc.idempotent.support.IdempotentSettings;
import cn.slainc.utils.digest.DigestUtils;
import cn.slainc.utils.json.JsonUtils;
import cn.slainc.utils.string.StringUtils;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.security.Principal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class ParameterFingerprint {

    private static final Logger log = LoggerFactory.getLogger(ParameterFingerprint.class);

    public String fingerprint(IdempotentContext context) {
        IdempotentSettings settings = context.settings();
        Method method = context.method();
        Object[] args = context.args() == null ? new Object[0] : context.args();
        Parameter[] parameters = method == null ? new Parameter[0] : method.getParameters();
        Map<String, String> canonical = new TreeMap<>();
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (ignorable(arg)) {
                continue;
            }
            String name = i < parameters.length ? parameters[i].getName() : "arg" + i;
            String value = toCanonicalJson(arg, name, settings.ignoreFields());
            if (value != null) {
                canonical.put(name, value);
            }
        }
        canonical.put("@method", context.methodId() == null ? "" : context.methodId());
        if (settings.includeUserId()) {
            canonical.put("@userId", context.userId() == null ? "匿名" : String.valueOf(context.userId()));
        }
        if (settings.includePath() && StringUtils.isNotBlank(context.requestPath())) {
            canonical.put("@path", context.requestPath());
        }
        if (StringUtils.isNotBlank(context.keyValue())) {
            canonical.put("@key", context.keyValue());
        }
        return DigestUtils.md5(JsonUtils.toJson(canonical));
    }

    private String toCanonicalJson(Object arg, String name, List<String> ignoredFields) {
        JsonNode node;
        try {
            node = JsonUtils.mapper().valueToTree(arg);
        } catch (Exception exception) {
            log.warn("参数 {} 无法序列化，已从幂等指纹中排除，原因={}", name, exception.getMessage());
            return null;
        }
        if (node == null || node.isNull()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        append(builder, node, name, ignoredFields);
        return builder.toString();
    }

    private void append(StringBuilder builder, JsonNode node, String path, List<String> ignoredFields) {
        if (node == null || node.isNull()) {
            builder.append("null");
            return;
        }
        if (node.isObject()) {
            List<Map.Entry<String, JsonNode>> fields = new ArrayList<>(node.properties());
            fields.sort(Map.Entry.comparingByKey());
            builder.append('{');
            Iterator<Map.Entry<String, JsonNode>> iterator = fields.iterator();
            boolean first = true;
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> field = iterator.next();
                String childPath = path + "." + field.getKey();
                if (ignored(childPath, field.getKey(), ignoredFields)) {
                    continue;
                }
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(JsonUtils.toJson(field.getKey())).append(':');
                append(builder, field.getValue(), childPath, ignoredFields);
            }
            builder.append('}');
            return;
        }
        if (node.isArray()) {
            builder.append('[');
            boolean first = true;
            for (JsonNode element : node.values()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                append(builder, element, path, ignoredFields);
            }
            builder.append(']');
            return;
        }
        builder.append(JsonUtils.toJson(node));
    }

    private static boolean ignored(String path, String leaf, List<String> ignoredFields) {
        if (ignoredFields == null || ignoredFields.isEmpty()) {
            return false;
        }
        for (String field : ignoredFields) {
            if (field.equals(path) || field.equals(leaf)) {
                return true;
            }
        }
        return false;
    }

    private static boolean ignorable(Object arg) {
        if (arg == null) {
            return true;
        }
        if (arg instanceof ServletRequest || arg instanceof ServletResponse || arg instanceof MultipartFile) {
            return true;
        }
        if (arg instanceof Errors || arg instanceof Model || arg instanceof WebRequest || arg instanceof NativeWebRequest) {
            return true;
        }
        if (arg instanceof Principal || arg instanceof Locale || arg instanceof ZoneId
                || arg instanceof java.util.TimeZone) {
            return true;
        }
        if (arg instanceof InputStream || arg instanceof OutputStream || arg instanceof Reader || arg instanceof Writer) {
            return true;
        }
        return arg instanceof Class<?> || arg instanceof Method;
    }
}
