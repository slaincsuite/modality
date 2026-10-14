package cn.slainc.modules.upms.service;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.modules.upms.dto.FavoriteRequest;
import cn.slainc.modules.upms.entity.FavoriteRecord;
import cn.slainc.modules.upms.mapper.FavoriteMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FavoriteServiceTest {

    private FavoriteMapper favoriteMapper;

    private FavoriteService favoriteService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                FavoriteRecord.class);
        favoriteMapper = mock(FavoriteMapper.class);
        favoriteService = new FavoriteService(favoriteMapper);
        UserContext.set(UserContext.UserInfo.of(9L, "alice", LoginType.TENANT_USER));
    }

    @AfterEach
    void tearDown() {
        UserContext.set(null);
    }

    @Test
    void addNormalizesTypeAndInsertsOnce() {
        when(favoriteMapper.selectOne(any())).thenReturn(null);
        favoriteService.add(new FavoriteRequest(" menu ", " /upms/menu ", "菜单管理", null, null, 5));
        ArgumentCaptor<FavoriteRecord> captor = ArgumentCaptor.forClass(FavoriteRecord.class);
        verify(favoriteMapper).insert(captor.capture());
        FavoriteRecord inserted = captor.getValue();
        assertThat(inserted.getTargetType()).isEqualTo("MENU");
        assertThat(inserted.getTargetKey()).isEqualTo("/upms/menu");
        assertThat(inserted.getUserId()).isEqualTo(9L);
        assertThat(inserted.getSort()).isEqualTo(5);
        assertThat(inserted.getCreateTime()).isNotNull();
    }

    @Test
    void addSameTargetRefreshesSnapshotWithoutSecondRow() {
        FavoriteRecord exists = new FavoriteRecord();
        exists.setId(1L);
        exists.setUserId(9L);
        exists.setTargetType("MENU");
        exists.setTargetKey("/upms/menu");
        exists.setName("旧名称");
        exists.setSort(0);
        when(favoriteMapper.selectOne(any())).thenReturn(exists);

        FavoriteRecord result = favoriteService.add(
                new FavoriteRequest("menu", "/upms/menu", "新名称", "/upms/menu", "star", 3));

        verify(favoriteMapper, never()).insert(any(FavoriteRecord.class));
        verify(favoriteMapper).updateById(exists);
        assertThat(result.getName()).isEqualTo("新名称");
        assertThat(result.getIcon()).isEqualTo("star");
        assertThat(result.getSort()).isEqualTo(3);
    }

    @Test
    void addRejectsBlankTargetKey() {
        assertThatThrownBy(() -> favoriteService.add(new FavoriteRequest("menu", "  ", "名称", null, null, null)))
                .isInstanceOf(BizException.class).hasMessageContaining("目标标识");
    }

    @Test
    void removeReturnsZeroForEmptyIds() {
        assertThat(favoriteService.remove(List.of())).isZero();
        verify(favoriteMapper, never()).delete(any());
    }

    @Test
    void removeDelegatesToDelete() {
        when(favoriteMapper.delete(any())).thenReturn(2);
        assertThat(favoriteService.remove(List.of(1L, 2L))).isEqualTo(2);
        verify(favoriteMapper).delete(any());
    }

    @Test
    void rejectsMissingLogin() {
        UserContext.set(null);
        assertThatThrownBy(() -> favoriteService.list()).isInstanceOf(BizException.class)
                .hasMessageContaining("登录");
    }
}
