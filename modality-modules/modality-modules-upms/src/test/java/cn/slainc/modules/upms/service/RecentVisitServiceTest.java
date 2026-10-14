package cn.slainc.modules.upms.service;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.modules.upms.UpmsKeys;
import cn.slainc.modules.upms.dto.RecentVisitRequest;
import cn.slainc.modules.upms.entity.MenuRecord;
import cn.slainc.modules.upms.entity.RecentVisitRecord;
import cn.slainc.modules.upms.mapper.MenuMapper;
import cn.slainc.modules.upms.mapper.RecentVisitMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecentVisitServiceTest {

    private RecentVisitMapper recentVisitMapper;

    private MenuMapper menuMapper;

    private RecentVisitService recentVisitService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                RecentVisitRecord.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                MenuRecord.class);
        recentVisitMapper = mock(RecentVisitMapper.class);
        menuMapper = mock(MenuMapper.class);
        recentVisitService = new RecentVisitService(recentVisitMapper, menuMapper);
        UserContext.set(UserContext.UserInfo.of(7L, "bob", LoginType.TENANT_USER));
        when(recentVisitMapper.selectList(any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        UserContext.set(null);
    }

    @Test
    void recordInsertsNewVisitWithNormalizedType() {
        when(recentVisitMapper.selectOne(any())).thenReturn(null);
        recentVisitService.record(new RecentVisitRequest("menu", "/workorder/my", "我的工单", "/workorder/my"));
        ArgumentCaptor<RecentVisitRecord> captor = ArgumentCaptor.forClass(RecentVisitRecord.class);
        verify(recentVisitMapper).insert(captor.capture());
        RecentVisitRecord inserted = captor.getValue();
        assertThat(inserted.getTargetType()).isEqualTo("MENU");
        assertThat(inserted.getUserId()).isEqualTo(7L);
        assertThat(inserted.getVisitedAt()).isNotNull();
    }

    @Test
    void recordRefreshesExistingVisitInsteadOfNewRow() {
        RecentVisitRecord exists = new RecentVisitRecord();
        exists.setId(3L);
        exists.setUserId(7L);
        exists.setTargetType("MENU");
        exists.setTargetKey("/workorder/my");
        exists.setName("旧名称");
        when(recentVisitMapper.selectOne(any())).thenReturn(exists);

        RecentVisitRecord result = recentVisitService.record(
                new RecentVisitRequest("MENU", "/workorder/my", "新名称", "/workorder/my"));

        verify(recentVisitMapper).updateById(exists);
        assertThat(result.getName()).isEqualTo("新名称");
        assertThat(result.getVisitedAt()).isNotNull();
    }

    @Test
    void recordPrunesBeyondKeepLimit() {
        java.util.List<RecentVisitRecord> visits = new java.util.ArrayList<>();
        for (int i = 1; i <= 51; i++) {
            RecentVisitRecord visit = new RecentVisitRecord();
            visit.setId((long) i);
            visits.add(visit);
        }
        when(recentVisitMapper.selectList(any())).thenReturn(visits);
        recentVisitService.record(new RecentVisitRequest("menu", "/a", "名称", "/a"));
        verify(recentVisitMapper).deleteByIds(List.of(51L));
    }

    @Test
    void recordRejectsBlankName() {
        assertThatThrownBy(() -> recentVisitService.record(new RecentVisitRequest("menu", "/a", " ", null)))
                .isInstanceOf(BizException.class).hasMessageContaining("名称");
    }

    @Test
    void hotReturnsEnabledMenusOrderedByVisits() {
        Map<String, Object> first = new HashMap<>();
        first.put("target_key", "/b");
        Map<String, Object> second = new HashMap<>();
        second.put("target_key", "/a");
        when(recentVisitMapper.selectMaps(any())).thenReturn(List.of(first, second));

        MenuRecord menuB = new MenuRecord();
        menuB.setId(2L);
        menuB.setPath("/b");
        menuB.setStatus(UpmsKeys.STATUS_ENABLED);
        MenuRecord menuA = new MenuRecord();
        menuA.setId(1L);
        menuA.setPath("/a");
        menuA.setStatus(UpmsKeys.STATUS_ENABLED);
        when(menuMapper.selectList(any())).thenReturn(List.of(menuA, menuB));

        List<MenuRecord> hot = recentVisitService.hot(null);
        assertThat(hot).extracting(MenuRecord::getPath).containsExactly("/b", "/a");
    }

    @Test
    void hotSkipsMissingMenus() {
        Map<String, Object> row = new HashMap<>();
        row.put("TARGET_KEY", "/gone");
        when(recentVisitMapper.selectMaps(any())).thenReturn(List.of(row));
        when(menuMapper.selectList(any())).thenReturn(List.of());
        assertThat(recentVisitService.hot(50)).isEmpty();
    }
}
