package cn.slainc.modules.infra.dict;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

// 字典管理：类型/字典项 CRUD、本地缓存失效、resolve 与 Excel 导入导出往返（无 Redis 路线）
class DictModuleTest extends cn.slainc.modules.infra.InfraModuleTestBase {

    @Test
    void typeCrudAndItemGuards() throws Exception {
        post("/infra/dict/types", "{\"code\":\"order_status\",\"name\":\"订单状态\",\"remark\":\"验收\"}");
        HttpResponse<String> duplicate = send("POST", "/infra/dict/types",
                "{\"code\":\"order_status\",\"name\":\"重复\"}");
        assertThat(duplicate.statusCode()).isEqualTo(400);
        assertThat(duplicate.body()).contains("字典编码已存在");

        post("/infra/dict/items", "{\"typeCode\":\"order_status\",\"label\":\"已支付\",\"value\":\"PAID\"}");
        HttpResponse<String> badType = send("POST", "/infra/dict/items",
                "{\"typeCode\":\"missing\",\"label\":\"无\",\"value\":\"X\"}");
        assertThat(badType.statusCode()).isEqualTo(400);
        assertThat(badType.body()).contains("字典类型不存在");

        HttpResponse<String> deleteGuard = send("DELETE", "/infra/dict/types/order_status", null);
        assertThat(deleteGuard.statusCode()).isEqualTo(400);
        assertThat(deleteGuard.body()).contains("先清空字典项");

        String types = get("/infra/dict/types/page?keyword=订单");
        assertThat(Integer.parseInt(JsonPath.read(types, "$.data.total").toString())).isEqualTo(1);
        assertThat(Integer.parseInt(JsonPath.read(types, "$.data.records[0].itemCount").toString())).isEqualTo(1);

        post("/infra/dict/items", "{\"typeCode\":\"order_status\",\"label\":\"已关闭\",\"value\":\"CLOSED\"}");
        String items = get("/infra/dict/items/by-type?typeCode=order_status");
        assertThat(items).contains("已支付").contains("已关闭");
    }

    @Test
    void resolveUsesCacheAndInvalidatesOnUpdate() throws Exception {
        post("/infra/dict/types", "{\"code\":\"gender\",\"name\":\"性别\"}");
        post("/infra/dict/items", "{\"typeCode\":\"gender\",\"label\":\"男\",\"value\":\"M\",\"sort\":1}");

        assertThat(JsonPath.read(get("/infra/dict/resolve?typeCode=gender&value=M"), "$.data").toString())
                .isEqualTo("男");

        long itemId = Long.parseLong(JsonPath.read(
                get("/infra/dict/items/page?typeCode=gender"), "$.data.records[0].id").toString());
        send("PUT", "/infra/dict/items/" + itemId, "{\"label\":\"男性\"}");
        assertThat(JsonPath.read(get("/infra/dict/resolve?typeCode=gender&value=M"), "$.data").toString())
                .isEqualTo("男性");

        send("PUT", "/infra/dict/items/" + itemId, "{\"enabled\":false}");
        Object resolved = JsonPath.read(get("/infra/dict/resolve?typeCode=gender&value=M"), "$.data");
        assertThat(resolved).isNull();
    }

    @Test
    void excelExportAndImportRoundTrip() throws Exception {
        post("/infra/dict/types", "{\"code\":\"level\",\"name\":\"级别\"}");
        post("/infra/dict/items", "{\"typeCode\":\"level\",\"label\":\"高\",\"value\":\"HIGH\"}");

        HttpResponse<byte[]> exported = sendBytes("GET", "/infra/dict/export?typeCode=level");
        assertThat(exported.statusCode()).isEqualTo(200);
        byte[] body = exported.body();
        assertThat(body.length).isGreaterThan(4);
        assertThat((body[0] & 0xFF) == 0x50 && (body[1] & 0xFF) == 0x4B).isTrue();

        HttpResponse<String> imported = upload("/infra/dict/import", "file", "dict-items.xlsx", body,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Map.of());
        assertThat(imported.statusCode()).isEqualTo(200);
        assertThat(Integer.parseInt(JsonPath.read(imported.body(), "$.data.succeeded").toString())).isEqualTo(1);

        HttpResponse<String> reImported = upload("/infra/dict/import", "file", "dict-items.xlsx", body,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                Map.of("overwrite", "true"));
        assertThat(Integer.parseInt(JsonPath.read(reImported.body(), "$.data.succeeded").toString())).isEqualTo(1);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_infra_dict_item WHERE type_code = 'level' AND item_value = 'HIGH'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
