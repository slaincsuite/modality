package cn.slainc.modules.policy;

import cn.slainc.utils.json.JsonUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 测试与验收共用的请求体构造：避免手写多层转义 JSON 造成的隐蔽语法错误
public final class PolicyTestBodies {

    public static String document(String sid, String effect, String action) {
        Map<String, Object> statement = new LinkedHashMap<>();
        statement.put("sid", sid);
        statement.put("effect", effect);
        statement.put("actions", List.of(action));
        statement.put("resources", List.of("*"));
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("version", "2.0");
        doc.put("statements", List.of(statement));
        return JsonUtils.toJson(doc);
    }

    public static String document(String sid, String effect, String action, String resource) {
        Map<String, Object> statement = new LinkedHashMap<>();
        statement.put("sid", sid);
        statement.put("effect", effect);
        statement.put("actions", List.of(action));
        statement.put("resources", List.of(resource));
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("version", "2.0");
        doc.put("statements", List.of(statement));
        return JsonUtils.toJson(doc);
    }

    public static String createRequest(String name, String subjectType, String subjectId, String group,
                                       String documentJson) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("subjectType", subjectType);
        if (subjectId != null) {
            body.put("subjectId", subjectId);
        }
        if (group != null) {
            body.put("group", group);
        }
        body.put("documentJson", documentJson);
        return JsonUtils.toJson(body);
    }

    public static String updateRequest(String documentJson) {
        return JsonUtils.toJson(Map.of("documentJson", documentJson));
    }

    public static String wrapDocument(String documentJson) {
        return JsonUtils.toJson(Map.of("document", JsonUtils.parse(documentJson, Object.class)));
    }

    private PolicyTestBodies() {
    }
}
