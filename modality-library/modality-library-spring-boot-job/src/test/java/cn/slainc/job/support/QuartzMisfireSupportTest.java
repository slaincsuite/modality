package cn.slainc.job.support;

import cn.slainc.job.enums.MisfirePolicy;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.TriggerBuilder;

import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

class QuartzMisfireSupportTest {

    @Test
    void shouldMapSkipToDoNothing() {
        assertThat(QuartzMisfireSupport.instructionOf(MisfirePolicy.SKIP))
                .isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
    }

    @Test
    void shouldMapFireOnceNowToFireAndProceed() {
        assertThat(QuartzMisfireSupport.instructionOf(MisfirePolicy.FIRE_ONCE_NOW))
                .isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
    }

    @Test
    void shouldTreatMissingPolicyAsSkip() {
        assertThat(QuartzMisfireSupport.instructionOf(null))
                .isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
    }

    @Test
    void shouldBuildTriggerWithMappedInstructionAndDefaultZone() {
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("misfire-trigger", "misfire-group")
                .withSchedule(QuartzMisfireSupport.scheduleBuilder("0 0 2 * * ?", MisfirePolicy.FIRE_ONCE_NOW))
                .build();

        assertThat(trigger.getCronExpression()).isEqualTo("0 0 2 * * ?");
        assertThat(trigger.getMisfireInstruction()).isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
        assertThat(trigger.getTimeZone().getID()).isEqualTo(TimeZone.getDefault().getID());
    }
}
