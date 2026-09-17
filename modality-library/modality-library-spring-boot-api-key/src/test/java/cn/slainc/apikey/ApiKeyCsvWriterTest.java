package cn.slainc.apikey;

import cn.slainc.apikey.service.ApiKeyCsvWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyCsvWriterTest {

    private final ApiKeyCsvWriter writer = new ApiKeyCsvWriter();

    @Test
    @DisplayName("输出带 UTF-8 BOM 与中文表头")
    void writesBomAndHeader() {
        String csv = writer.write(List.of());
        assertThat(csv).startsWith(ApiKeyCsvWriter.BOM);
        assertThat(csv).contains("名称,介绍,API Key,账号ID,授权范围,状态,创建时间,过期时间");
    }

    @Test
    @DisplayName("记录行包含 Key、账号与状态中文文案")
    void writesRecords() {
        ApiKeyRecord record = new ApiKeyRecord();
        record.setApiKey("sk_abcdef123456");
        record.setTitle("开门钥匙");
        record.setIntro("测试用途");
        record.setLoginId("2001");
        record.setScopes("order:read,order:write");
        record.setStatus(ApiKeyStatus.ENABLED);
        record.setCreateTime(1760000000000L);
        record.setExpiresTime(-1L);
        String csv = writer.write(List.of(record));
        assertThat(csv).contains("sk_abcdef123456");
        assertThat(csv).contains("开门钥匙");
        assertThat(csv).contains("order:read,order:write");
        assertThat(csv).contains("启用");
        assertThat(csv).contains("永不过期");
    }

    @Test
    @DisplayName("含逗号与引号的字段被转义")
    void escapesSpecialCharacters() {
        ApiKeyRecord record = new ApiKeyRecord();
        record.setApiKey("sk_x");
        record.setTitle("带,逗号\"引号\"");
        record.setLoginId("1");
        record.setStatus(ApiKeyStatus.DISABLED);
        record.setCreateTime(1760000000000L);
        record.setExpiresTime(1760000000000L);
        String csv = writer.write(List.of(record));
        assertThat(csv).contains("\"带,逗号\"\"引号\"\"\"");
        assertThat(csv).contains("停用");
    }

    @Test
    @DisplayName("空列表仅输出表头")
    void emptyListKeepsHeaderOnly() {
        String csv = writer.write(List.of());
        assertThat(csv.lines()).hasSize(1);
    }
}
