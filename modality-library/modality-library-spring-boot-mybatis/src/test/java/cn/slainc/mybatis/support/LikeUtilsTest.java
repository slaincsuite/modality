package cn.slainc.mybatis.support;

import cn.slainc.mybatis.exception.MybatisException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LikeUtilsTest {

    private static final char ESCAPE = LikeUtils.DEFAULT_ESCAPE;

    @Test
    @DisplayName("百分号与下划线被转义为字面量")
    void escapesWildcards() {
        assertEquals("50" + ESCAPE + "%" + ESCAPE + "_off", LikeUtils.escapeLike("50%_off"));
        assertEquals("abc", LikeUtils.escapeLike("abc"));
    }

    @Test
    @DisplayName("转义符自身被重复转义")
    void escapesEscapeCharacterItself() {
        assertEquals("a" + ESCAPE + ESCAPE + "b", LikeUtils.escapeLike("a" + ESCAPE + "b"));
    }

    @Test
    @DisplayName("支持自定义转义符")
    void supportsCustomEscapeCharacter() {
        assertEquals("50!%!_off", LikeUtils.escapeLike("50%_off", '!'));
        assertEquals("a!!b", LikeUtils.escapeLike("a!b", '!'));
        assertEquals(" ESCAPE '!'", LikeUtils.escapeClause('!'));
    }

    @Test
    @DisplayName("空值与空串原样返回")
    void nullAndEmptyAreReturnedAsIs() {
        assertNull(LikeUtils.escapeLike(null));
        assertEquals("", LikeUtils.escapeLike(""));
    }

    @Test
    @DisplayName("模糊匹配模式按字面量拼装")
    void buildsMatchPatterns() {
        assertEquals("%100" + ESCAPE + "%%", LikeUtils.contains("100%"));
        assertEquals("100" + ESCAPE + "%%", LikeUtils.startsWith("100%"));
        assertEquals("%100" + ESCAPE + "%", LikeUtils.endsWith("100%"));
    }

    @Test
    @DisplayName("空匹配值不拼出 null 字面量")
    void nullValueProducesNoLiteral() {
        assertNull(LikeUtils.contains(null));
        assertNull(LikeUtils.startsWith(null));
        assertNull(LikeUtils.endsWith(null));
    }

    @Test
    @DisplayName("默认 ESCAPE 子句使用反斜杠")
    void defaultEscapeClauseUsesBackslash() {
        assertEquals(" ESCAPE '" + ESCAPE + "'", LikeUtils.escapeClause());
    }

    @Test
    @DisplayName("引号、通配符与空白不能作为转义符")
    void rejectsUnusableEscapeCharacters() {
        assertThrows(MybatisException.class, () -> LikeUtils.escapeLike("a", '%'));
        assertThrows(MybatisException.class, () -> LikeUtils.escapeLike("a", '_'));
        assertThrows(MybatisException.class, () -> LikeUtils.escapeClause('\''));
        assertThrows(MybatisException.class, () -> LikeUtils.escapeClause(' '));
    }
}
