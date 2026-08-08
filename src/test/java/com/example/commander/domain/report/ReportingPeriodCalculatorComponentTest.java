package com.example.commander.domain.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.commander.adapter.scheduling.SchedulingProperties;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportingPeriodCalculatorComponentTest {

    @Test
    void shouldHandleDSTTransitionForDailyWindow() {
        // Test DST transition in Europe/Stockholm (March 31, 2024, 02:00 -> 03:00)
        SchedulingProperties properties = new SchedulingProperties();
        properties.setTimezone("Europe/Stockholm");
        ReportingPeriodCalculator calculator = new ReportingPeriodCalculator(properties);

        // Fire at 10:00 on March 31, 2024 (after DST transition)
        ZonedDateTime fireTime = ZonedDateTime.of(2024, 3, 31, 10, 0, 0, 0, ZoneId.of("UTC"));
        ReportWindow window = calculator.calculate(ReportFrequency.DAILY, fireTime.toInstant());

        // Point-in-time at midnight of the day before the fire's date (start == end) - what's
        // under test here is that "midnight of yesterday" resolves to the correct UTC instant
        // even when the fire date itself is a DST transition day, not the window's duration
        // (there isn't one - start == end regardless of DST).
        ZonedDateTime expectedStart = fireTime.withZoneSameInstant(ZoneId.of("Europe/Stockholm"))
                .toLocalDate()
                .atStartOfDay(ZoneId.of("Europe/Stockholm"))
                .minusDays(1);
        ZonedDateTime expectedEnd = expectedStart;

        assertThat(window.windowStartUtc()).isEqualTo(expectedStart.toInstant());
        assertThat(window.windowEndUtc()).isEqualTo(expectedEnd.toInstant());
    }

    @Test
    void shouldHandleDifferentTimezones() {
        SchedulingProperties properties = new SchedulingProperties();
        properties.setTimezone("America/New_York");
        ReportingPeriodCalculator calculator = new ReportingPeriodCalculator(properties);

        ZonedDateTime fireTime = ZonedDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneId.of("UTC"));
        ReportWindow window = calculator.calculate(ReportFrequency.DAILY, fireTime.toInstant());

        // Point-in-time at midnight of the day before the fire's date, converted to New
        // York time - start == end.
        ZonedDateTime expectedStart = fireTime.withZoneSameInstant(ZoneId.of("America/New_York"))
                .toLocalDate()
                .atStartOfDay(ZoneId.of("America/New_York"))
                .minusDays(1);
        ZonedDateTime expectedEnd = expectedStart;

        assertThat(window.windowStartUtc()).isEqualTo(expectedStart.toInstant());
        assertThat(window.windowEndUtc()).isEqualTo(expectedEnd.toInstant());
    }
}
