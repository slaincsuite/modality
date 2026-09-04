package cn.slainc.monitor.endpoint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitInfoProviderTest {

    @Test
    @DisplayName("缺少 git.properties 时降级为 unknown 且不抛异常")
    void shouldFallbackWhenResourceMissing() {
        GitInfoProvider provider = new GitInfoProvider("git-not-exists.properties");
        assertThat(provider.available()).isFalse();
        assertThat(provider.commit()).isEqualTo("unknown");
        assertThat(provider.commitFull()).isEqualTo("unknown");
        assertThat(provider.branch()).isEqualTo("unknown");
        assertThat(provider.commitTime()).isEqualTo("unknown");
        assertThat(provider.buildVersion()).isEqualTo("unknown");
        assertThat(provider.artifact()).isEqualTo("unknown");
        assertThat(provider.asMap()).containsEntry("available", "false").containsEntry("commit", "unknown");
    }

    @Test
    @DisplayName("存在 git.properties 时读取提交信息")
    void shouldReadGitProperties() {
        GitInfoProvider provider = new GitInfoProvider("git-test.properties");
        assertThat(provider.available()).isTrue();
        assertThat(provider.commit()).isEqualTo("abc1234");
        assertThat(provider.commitFull()).isEqualTo("abc1234567890abcdef1234567890abcdef12345");
        assertThat(provider.branch()).isEqualTo("feature/monitor");
        assertThat(provider.buildVersion()).isEqualTo("9.9.9-TEST");
        assertThat(provider.artifact()).isEqualTo("modality-boot");
        assertThat(provider.asMap()).containsEntry("available", "true");
    }

    @Test
    @DisplayName("默认构造使用 git.properties，本模块测试类路径上不存在该文件")
    void shouldUseDefaultResource() {
        GitInfoProvider provider = new GitInfoProvider();
        assertThat(provider.commit()).isEqualTo("unknown");
    }
}
