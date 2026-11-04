package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class QuietHoursTest {
    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final LocalTime START = LocalTime.of(21, 0), END = LocalTime.of(8, 0);

    /** IST wall-clock time on 2026-11-04 (or day+1) as an Instant. */
    static Instant ist(int day, int h, int m) {
        return java.time.ZonedDateTime.of(2026, 11, day, h, m, 0, 0, IST).toInstant();
    }

    @Test
    void daytimeSendsNow() {
        Instant now = ist(4, 14, 30);
        assertEquals(now, QuietHours.nextSendTime(now, START, END, IST));
    }

    @Test
    void eveningWaitsUntilTomorrowMorning() {
        assertEquals(ist(5, 8, 0), QuietHours.nextSendTime(ist(4, 23, 10), START, END, IST));
    }

    @Test
    void earlyMorningWaitsUntilThisMorning() {
        assertEquals(ist(4, 8, 0), QuietHours.nextSendTime(ist(4, 2, 0), START, END, IST));
    }

    @Test
    void boundaries() {
        assertEquals(ist(5, 8, 0), QuietHours.nextSendTime(ist(4, 21, 0), START, END, IST)); // start is quiet
        Instant atEnd = ist(4, 8, 0);
        assertEquals(atEnd, QuietHours.nextSendTime(atEnd, START, END, IST));                // end is allowed
    }

    @Test
    void sameDayWindowDoesNotWrap() {
        LocalTime s = LocalTime.of(13, 0), e = LocalTime.of(15, 0);
        assertEquals(ist(4, 15, 0), QuietHours.nextSendTime(ist(4, 13, 30), s, e, IST));
        Instant evening = ist(4, 18, 0);
        assertEquals(evening, QuietHours.nextSendTime(evening, s, e, IST));
    }

    @Test
    void startEqualsEndMeansNoQuietHours() {
        Instant now = ist(4, 23, 0);
        assertEquals(now, QuietHours.nextSendTime(now, START, START, IST));
    }

    @Test
    void usesTheOwnersZoneNotUtc() {
        // 23:00 IST is 17:30 UTC; in UTC it would look like daytime.
        assertEquals(ist(5, 8, 0), QuietHours.nextSendTime(ist(4, 23, 0), START, END, IST));
    }
}
