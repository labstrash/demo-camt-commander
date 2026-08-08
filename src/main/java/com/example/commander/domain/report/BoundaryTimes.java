package com.example.commander.domain.report;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * Parses the comma-separated {@code HH:mm} boundary-time strings used by {@code
 * commander.scheduling} schedules (e.g. {@code "09:00,13:00,17:00"}).
 *
 * <p>Single shared implementation for every caller that needs to turn that config string into
 * {@link LocalTime}s — previously duplicated across {@link ReportingPeriodCalculator}, {@code
 * SchedulingProperties}, and {@code ReportJobScheduleBuilder}.
 */
public final class BoundaryTimes {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private BoundaryTimes() {}

    /**
     * @param boundaries comma-separated time strings (e.g. "09:00,13:00,17:00")
     * @return list of parsed LocalTime objects, or empty list if input is null/blank
     */
    public static List<LocalTime> parse(String boundaries) {
        if (boundaries == null || boundaries.isBlank()) {
            return List.of();
        }
        return Arrays.stream(boundaries.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> LocalTime.parse(value, TIME_FORMAT))
                .toList();
    }
}
