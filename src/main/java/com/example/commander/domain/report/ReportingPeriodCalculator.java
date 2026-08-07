package com.example.commander.domain.report;

import com.example.commander.config.SchedulingProperties;
import com.example.commander.domain.message.ReportType;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Calculates UTC reporting windows for scheduled report executions.
 *
 * <p>All time calculations are performed in the configured business timezone and
 * converted to UTC at the end, ensuring consistent window boundaries regardless
 * of system timezone.
 *
 * <p>Supports three window calculation strategies:
 * <ul>
 *   <li><b>Rolling intervals</b> (EVERY_30_MIN, EVERY_1_HOUR, etc.): Fixed duration ending at fire time</li>
 *   <li><b>Calendar-day</b> (DAILY): Point-in-time at midnight of the previous day (start == end)</li>
 *   <li><b>Boundary-based</b> (ONE_TIME_PER_DAY, FOUR_TIMES_PER_DAY, etc.): Fixed clock-time boundaries</li>
 * </ul>
 */
@Component
public class ReportingPeriodCalculator {

    private static final DateTimeFormatter BOUNDARY_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final ZoneId businessZone;
    private final SchedulingProperties schedulingProperties;

    private static final Set<ReportFrequency> INTERVAL_FREQUENCIES = EnumSet.of(
            ReportFrequency.EVERY_30_MIN,
            ReportFrequency.EVERY_1_HOUR,
            ReportFrequency.EVERY_2_HOURS,
            ReportFrequency.EVERY_4_HOURS);

    public ReportingPeriodCalculator(SchedulingProperties properties) {
        this.businessZone = ZoneId.of(properties.getTimezone());
        this.schedulingProperties = properties;
    }

    /**
     * Calculates a reporting window for a non-window-time frequency.
     *
     * @param frequency the report frequency
     * @param fireTimeUtc the scheduled fire time in UTC
     * @return the calculated reporting window
     */
    public ReportWindow calculate(ReportFrequency frequency, Instant fireTimeUtc) {
        return calculate(frequency, fireTimeUtc, null, null);
    }

    /**
     * Calculates a reporting window with an optional window sequence number.
     *
     * <p>Currently unused for non-window-time frequencies, but kept for API symmetry.
     *
     * @param frequency the report frequency
     * @param fireTimeUtc the scheduled fire time in UTC
     * @param windowSequence the 0-based slot index (required for window-time frequencies)
     * @return the calculated reporting window
     */
    public ReportWindow calculate(ReportFrequency frequency, Instant fireTimeUtc, Integer windowSequence) {
        return calculate(frequency, fireTimeUtc, windowSequence, null);
    }

    /**
     * Calculates a reporting window with full parameters.
     *
     * <p>For window-time frequencies (ONE_TIME_PER_DAY, FOUR_TIMES_PER_DAY, EIGHT_TIMES_PER_DAY),
     * both {@code windowSequence} and {@code boundaries} are required. The boundaries define
     * the clock times (e.g., "09:00,13:00,17:00"), and the sequence selects which window
     * to use (0 = midnight to first boundary, 1 = first to second boundary, etc.).
     *
     * @param frequency the report frequency
     * @param fireTimeUtc the scheduled fire time in UTC
     * @param windowSequence the 0-based slot index (required for window-time frequencies)
     * @param boundaries the configured boundary times (required for window-time frequencies)
     * @return the calculated reporting window
     * @throws NullPointerException if frequency or fireTimeUtc is null
     * @throws IllegalArgumentException if required parameters are missing for window-time frequencies
     */
    public ReportWindow calculate(
            ReportFrequency frequency, Instant fireTimeUtc, Integer windowSequence, List<LocalTime> boundaries) {
        Objects.requireNonNull(frequency, "frequency");
        Objects.requireNonNull(fireTimeUtc, "fireTimeUtc");

        ZonedDateTime fireTimeLocal = fireTimeUtc.atZone(businessZone);
        ZonedDateTime start;
        ZonedDateTime end;

        if (INTERVAL_FREQUENCIES.contains(frequency)) {
            end = fireTimeLocal;
            start = fireTimeLocal.minus(intervalFor(frequency));

        } else if (frequency == ReportFrequency.DAILY) {
            start = fireTimeLocal.toLocalDate().atStartOfDay(businessZone).minusDays(1);
            end = start;

        } else if (frequency.isWindowTimeFrequency()) {
            if (windowSequence == null) {
                throw new IllegalArgumentException("windowSequence is required for window-time frequency " + frequency);
            }
            if (boundaries == null || boundaries.isEmpty()) {
                throw new IllegalArgumentException("boundaries is required for window-time frequency " + frequency);
            }
            ZonedDateTime[] bounds = resolveWindowTimeBounds(fireTimeLocal.toLocalDate(), boundaries, windowSequence);
            start = bounds[0];
            end = bounds[1];

        } else {
            throw new IllegalArgumentException("Unhandled ReportFrequency: " + frequency);
        }

        return new ReportWindow(start.toInstant(), end.toInstant());
    }

    /**
     * Derives a reporting window from an arbitrary reference instant rather than a scheduler
     * fire time — for callers (e.g. an on-demand trigger) with no upfront trigger context to
     * supply a {@code windowSequence} from.
     *
     * <p>For every frequency shape except the window-time ones, this is exactly {@link
     * #calculate(ReportFrequency, Instant)} — those shapes only ever needed {@code
     * (frequency, instant)} in the first place, so there's nothing reference-instant-specific
     * to add.
     *
     * <p>Window-time frequencies ({@code ONE_TIME_PER_DAY}, {@code FOUR_TIMES_PER_DAY}, {@code
     * EIGHT_TIMES_PER_DAY}) are different: their {@code windowSequence} and {@code boundaries}
     * only exist via the schedule configured for a report type. This resolves them from
     * {@code commander.scheduling} instead — the schedule whose {@code report-types}
     * includes {@code reportType} and whose {@code frequency} matches — and picks the most
     * recently <em>completed</em> window as of {@code referenceInstant}: the window whose end
     * boundary is the latest one at or before the reference's local time, rolling back to the
     * previous day's last window if the reference is before the day's first boundary. This
     * keeps the derived window's end at or before the reference instant for every frequency
     * shape alike, so a caller rejecting a window that ends after "now" can do so consistently
     * regardless of which boundary happened to be picked.
     *
     * <p>This overload — and the boundary-resolution it does — covers every frequency shape a
     * reference-instant caller might need, not just the ones {@link #calculate(ReportFrequency,
     * Instant)} already handles for a fire-time caller.
     *
     * @param frequency the resolved config's report frequency
     * @param referenceInstant caller-supplied reference instant (e.g. an on-demand request's
     *     "as of" point)
     * @param reportType the resolved config's report type, used to find its configured
     *     schedule boundaries when frequency is a window-time frequency
     * @return the derived reporting window
     * @throws IllegalStateException if frequency is a window-time frequency and no configured
     *     {@code commander.scheduling} schedule matches {@code reportType}/{@code frequency}
     */
    public ReportWindow calculateForReference(
            ReportFrequency frequency, Instant referenceInstant, ReportType reportType) {
        Objects.requireNonNull(frequency, "frequency");
        Objects.requireNonNull(referenceInstant, "referenceInstant");

        if (!frequency.isWindowTimeFrequency()) {
            return calculate(frequency, referenceInstant);
        }

        Objects.requireNonNull(reportType, "reportType");
        List<LocalTime> boundaries = boundariesFor(frequency, reportType);

        LocalTime referenceLocalTime = referenceInstant.atZone(businessZone).toLocalTime();
        int sequence = -1;
        for (int i = 0; i < boundaries.size(); i++) {
            if (!boundaries.get(i).isAfter(referenceLocalTime)) {
                sequence = i;
            }
        }

        Instant fireInstantForDate = referenceInstant;
        if (sequence < 0) {
            // Reference falls before the day's first boundary — the most recently completed
            // window is the previous day's last one.
            sequence = boundaries.size() - 1;
            fireInstantForDate = referenceInstant.minus(1, ChronoUnit.DAYS);
        }

        return calculate(frequency, fireInstantForDate, sequence, boundaries);
    }

    private List<LocalTime> boundariesFor(ReportFrequency frequency, ReportType reportType) {
        for (SchedulingProperties.Schedule schedule : schedulingProperties.getSchedules()) {
            if (frequency.name().equals(schedule.getFrequency())
                    && schedule.getReportTypes() != null
                    && schedule.getReportTypes().contains(reportType)) {
                return parseBoundaries(schedule.getBoundaries());
            }
        }
        throw new IllegalStateException("No configured commander.scheduling schedule for reportType=" + reportType
                + ", frequency=" + frequency);
    }

    private static List<LocalTime> parseBoundaries(String boundaries) {
        if (boundaries == null || boundaries.isBlank()) {
            return List.of();
        }
        return Arrays.stream(boundaries.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> LocalTime.parse(value, BOUNDARY_TIME_FORMAT))
                .toList();
    }

    private ZonedDateTime[] resolveWindowTimeBounds(
            LocalDate fireDate, List<LocalTime> windowTimes, int windowSequence) {

        if (windowSequence < 0 || windowSequence >= windowTimes.size()) {
            throw new IllegalArgumentException("windowSequence " + windowSequence + " out of range for "
                    + windowTimes.size() + " configured window time(s)");
        }

        LocalTime endTime = windowTimes.get(windowSequence);
        LocalTime startTime = windowSequence == 0 ? LocalTime.MIDNIGHT : windowTimes.get(windowSequence - 1);

        return new ZonedDateTime[] {
            ZonedDateTime.of(fireDate, startTime, businessZone), ZonedDateTime.of(fireDate, endTime, businessZone)
        };
    }

    private Duration intervalFor(ReportFrequency frequency) {
        return switch (frequency) {
            case EVERY_30_MIN -> Duration.ofMinutes(30);
            case EVERY_1_HOUR -> Duration.ofHours(1);
            case EVERY_2_HOURS -> Duration.ofHours(2);
            case EVERY_4_HOURS -> Duration.ofHours(4);
            default -> throw new IllegalArgumentException("Not an interval frequency: " + frequency);
        };
    }
}
