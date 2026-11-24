package com.eye.core;

import java.time.*;

/**
 * Maps wall-clock instants to EyeRelax "study days".  A study day normally starts at 04:00,
 * so activity between midnight and the configured boundary belongs to the previous day.
 */
public final class StudyDay {
    public static final int DEFAULT_BOUNDARY_SECONDS = 4 * 60 * 60;

    private StudyDay() {}

    public static int sanitizeBoundarySeconds(int seconds) {
        return Math.max(0, Math.min(24 * 60 * 60 - 1, seconds));
    }

    public static LocalDate dateNow(Config config) {
        return dateAt(System.currentTimeMillis(), ZoneId.systemDefault(),
                config == null ? DEFAULT_BOUNDARY_SECONDS : config.dayBoundarySeconds);
    }

    public static LocalDate dateAt(long epochMillis, ZoneId zone, int boundarySeconds) {
        ZonedDateTime local = Instant.ofEpochMilli(epochMillis).atZone(zone);
        LocalTime boundary = LocalTime.ofSecondOfDay(sanitizeBoundarySeconds(boundarySeconds));
        LocalDate day = local.toLocalDate();
        // Compare instants, not repeated/nonexistent local times during DST transitions.
        if (epochMillis < ZonedDateTime.of(day, boundary, zone).toInstant().toEpochMilli()) day = day.minusDays(1);
        return day;
    }

    public static long nextBoundaryAfter(long epochMillis, ZoneId zone, int boundarySeconds) {
        ZonedDateTime local = Instant.ofEpochMilli(epochMillis).atZone(zone);
        LocalTime boundary = LocalTime.ofSecondOfDay(sanitizeBoundarySeconds(boundarySeconds));
        LocalDate date = local.toLocalDate();
        ZonedDateTime candidate = ZonedDateTime.of(date, boundary, zone);
        if (!candidate.toInstant().isAfter(Instant.ofEpochMilli(epochMillis))) {
            candidate = ZonedDateTime.of(date.plusDays(1), boundary, zone);
        }
        return candidate.toInstant().toEpochMilli();
    }

    public static long boundaryAtStartOf(LocalDate studyDate, ZoneId zone, int boundarySeconds) {
        LocalTime boundary = LocalTime.ofSecondOfDay(sanitizeBoundarySeconds(boundarySeconds));
        return ZonedDateTime.of(studyDate, boundary, zone).toInstant().toEpochMilli();
    }
}
