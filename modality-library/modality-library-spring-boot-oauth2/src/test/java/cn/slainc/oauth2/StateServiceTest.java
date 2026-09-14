package cn.slainc.oauth2;

import cn.slainc.oauth2.state.StateService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StateServiceTest {

    private final StateService stateService = new StateService(null, 60);

    @Test
    void stateIsValidatedOnlyOnce() {
        String state = stateService.issue();
        assertThat(state).hasSize(32);
        assertThat(stateService.validateOnce(state)).isTrue();
        assertThat(stateService.validateOnce(state)).isFalse();
    }

    @Test
    void blankStateIsRejected() {
        assertThat(stateService.validateOnce(null)).isFalse();
        assertThat(stateService.validateOnce("")).isFalse();
    }
}
