package com.abhedyam.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** When may a push go out, given the owner's quiet hours? Pure, so it is unit tested. */
public final class QuietHours {
    private QuietHours() {}

    /**
     * @return {@code now} if pushing is allowed, otherwise the instant quiet hours end.
     *         The window is [start, end) and may wrap past midnight (21:00-08:00); start == end means "no quiet hours".
     */
    public static Instant nextSendTime(Instant now, LocalTime start, LocalTime end, ZoneId zone) {
        if (start.equals(end)) return now;
        ZonedDateTime local = now.atZone(zone);
        LocalTime t = local.toLocalTime();
        boolean wraps = start.isAfter(end);
        boolean quiet = wraps ? (!t.isBefore(start) || t.isBefore(end)) : (!t.isBefore(start) && t.isBefore(end));
        if (!quiet) return now;
        LocalDate day = local.toLocalDate();
        if (wraps && !t.isBefore(start)) day = day.plusDays(1); // evening part: ends tomorrow morning
        return ZonedDateTime.of(day, end, zone).toInstant();
    }
}
