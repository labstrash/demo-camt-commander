package com.example.commander.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.commander.config.SchedulingProperties;
import com.example.commander.domain.message.ReportType;
import com.example.commander.scheduling.ReportJobScheduleBuilder.ReportJobSchedule;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Trigger;

class ReportJobScheduleBuilderTest {

    @Test
    void buildsOneJobDetailAndOneCronTriggerPerReportTypeForACronSchedule() {
        SchedulingProperties properties = properties(
                cronSchedule("DAILY", "0 0 6 ? * TUE-SAT", 30, List.of(ReportType.CAMT053E, ReportType.CAMT054D)));

        ReportJobSchedule schedule = ReportJobScheduleBuilder.build(properties);

        assertThat(schedule.jobDetails()).hasSize(2);
        assertThat(schedule.triggers()).hasSize(2);

        JobDetail camt053e = jobDetailNamed(schedule, "CAMT053E-DAILY");
        assertThat(camt053e.getJobDataMap().getString(ReportJobScheduleBuilder.KEY_REPORT_TYPE))
                .isEqualTo("CAMT053E");
        assertThat(camt053e.getJobDataMap().getString(ReportJobScheduleBuilder.KEY_REPORT_FREQUENCY))
                .isEqualTo("DAILY");
        assertThat(camt053e.getJobDataMap().containsKey(ReportJobScheduleBuilder.KEY_BOUNDARIES))
                .isFalse();

        Trigger trigger = triggerNamed(schedule, "CAMT053E-DAILY-trigger");
        assertThat(trigger).isInstanceOf(CronTrigger.class);
        assertThat(((CronTrigger) trigger).getCronExpression()).isEqualTo("0 0 6 ? * TUE-SAT");
        assertThat(trigger.getMisfireInstruction()).isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
    }

    @Test
    void buildsOneTriggerPerBoundaryForABoundarySchedule() {
        SchedulingProperties properties = properties(
                boundarySchedule("FOUR_TIMES_PER_DAY", "10:00,13:00,18:00,21:00", List.of(ReportType.CAMT054C)));

        ReportJobSchedule schedule = ReportJobScheduleBuilder.build(properties);

        assertThat(schedule.jobDetails()).hasSize(1);
        assertThat(schedule.triggers()).hasSize(4);

        JobDetail jobDetail = jobDetailNamed(schedule, "CAMT054C-FOUR_TIMES_PER_DAY");
        assertThat(jobDetail.getJobDataMap().getString(ReportJobScheduleBuilder.KEY_BOUNDARIES))
                .isEqualTo("10:00,13:00,18:00,21:00");
        assertThat(jobDetail.getJobDataMap().containsKey(ReportJobScheduleBuilder.KEY_REPORT_FREQUENCY))
                .isTrue();

        Trigger third = triggerNamed(schedule, "CAMT054C-FOUR_TIMES_PER_DAY-window-02");
        assertThat(third.getJobDataMap().getString(ReportJobScheduleBuilder.KEY_WINDOW_SEQUENCE))
                .isEqualTo("2");
        assertThat(((CronTrigger) third).getCronExpression()).isEqualTo("0 0 18 ? * MON-FRI");
    }

    @Test
    void jobDetailsAreDurableAndRecoverable() {
        SchedulingProperties properties =
                properties(cronSchedule("DAILY", "0 0 6 ? * TUE-SAT", 30, List.of(ReportType.CAMT054D)));

        ReportJobSchedule schedule = ReportJobScheduleBuilder.build(properties);

        JobDetail jobDetail = schedule.jobDetails().get(0);
        assertThat(jobDetail.isDurable()).isTrue();
        assertThat(jobDetail.requestsRecovery()).isTrue();
        assertThat(jobDetail.getJobClass()).isEqualTo(ReportSchedulingJob.class);
    }

    @Test
    void invalidPropertiesFailFastBeforeBuildingAnything() {
        SchedulingProperties properties = new SchedulingProperties();
        properties.setTimezone("Europe/Stockholm");
        SchedulingProperties.Schedule invalid = new SchedulingProperties.Schedule();
        invalid.setFrequency("DAILY");
        // Neither cron nor boundaries set - invalid.
        invalid.setReportTypes(List.of(ReportType.CAMT054D));
        properties.setSchedules(List.of(invalid));

        assertThatThrownBy(() -> ReportJobScheduleBuilder.build(properties)).isInstanceOf(IllegalStateException.class);
    }

    private static JobDetail jobDetailNamed(ReportJobSchedule schedule, String name) {
        return schedule.jobDetails().stream()
                .filter(jd -> jd.getKey().getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No JobDetail named " + name));
    }

    private static Trigger triggerNamed(ReportJobSchedule schedule, String name) {
        return schedule.triggers().stream()
                .filter(t -> t.getKey().getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Trigger named " + name));
    }

    private static SchedulingProperties properties(SchedulingProperties.Schedule... schedules) {
        SchedulingProperties properties = new SchedulingProperties();
        properties.setTimezone("Europe/Stockholm");
        properties.setSchedules(List.of(schedules));
        return properties;
    }

    private static SchedulingProperties.Schedule cronSchedule(
            String frequency, String cron, Integer windowMinutes, List<ReportType> reportTypes) {
        SchedulingProperties.Schedule schedule = new SchedulingProperties.Schedule();
        schedule.setFrequency(frequency);
        schedule.setCron(cron);
        if (!"DAILY".equals(frequency)) {
            schedule.setWindowMinutes(windowMinutes);
        }
        schedule.setReportTypes(reportTypes);
        return schedule;
    }

    private static SchedulingProperties.Schedule boundarySchedule(
            String frequency, String boundaries, List<ReportType> reportTypes) {
        SchedulingProperties.Schedule schedule = new SchedulingProperties.Schedule();
        schedule.setFrequency(frequency);
        schedule.setBoundaries(boundaries);
        schedule.setReportTypes(reportTypes);
        return schedule;
    }
}
