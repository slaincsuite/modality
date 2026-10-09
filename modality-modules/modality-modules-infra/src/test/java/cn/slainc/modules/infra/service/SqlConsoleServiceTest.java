package cn.slainc.modules.infra.service;

import cn.slainc.core.exception.BizException;
import cn.slainc.mybatis.sql.SqlAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SqlConsoleServiceTest {

    private SqlAnalyzer analyzer;

    private SqlConsoleService service;

    @BeforeEach
    void setUp() {
        analyzer = mock(SqlAnalyzer.class);
        service = new SqlConsoleService(analyzer);
    }

    @Test
    void dataModifyingCteIsRejected() {
        assertThatThrownBy(() -> service.explain(
                "WITH removed AS (DELETE FROM t_infra_dict_type RETURNING *) SELECT * FROM removed"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("只读控制台拒绝数据修改语句");
        verifyNoInteractions(analyzer);
    }

    @Test
    void chainedStatementIsRejected() {
        assertThatThrownBy(() -> service.explain("SELECT 1; DROP TABLE t_infra_dict_type"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("单条只读查询语句");
        verifyNoInteractions(analyzer);
    }

    @Test
    void plainDeleteStillRejectedByPrefix() {
        assertThatThrownBy(() -> service.explain("DELETE FROM t_infra_dict_type"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("只接受 SELECT / WITH");
        verifyNoInteractions(analyzer);
    }

    @Test
    void keywordsInsideLiteralsDoNotBlockReadOnlyQuery() {
        when(analyzer.explain(anyString())).thenReturn(List.of());

        service.explain("SELECT 'DELETE FROM t_infra_dict_type' AS note, \"UPDATE\" FROM dual");

        verify(analyzer).explain(anyString());
    }
}
