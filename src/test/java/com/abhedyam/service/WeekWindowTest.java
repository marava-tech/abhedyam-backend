package com.abhedyam.service;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeekWindowTest {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Test
    void wednesdayIsComparedWithLastWeeksMondayToWednesdaySameTime() {
        ZonedDateTime wed = ZonedDateTime.of(2026, 11, 11, 15, 30, 0, 0, IST);
        OwnerService.WeekWindow w = OwnerService.WeekWindow.at(wed);
        assertEquals(ZonedDateTime.of(2026, 11, 9, 0, 0, 0, 0, IST).toInstant(), w.thisStart());
        assertEquals(ZonedDateTime.of(2026, 11, 2, 0, 0, 0, 0, IST).toInstant(), w.lastStart());
        assertEquals(ZonedDateTime.of(2026, 11, 4, 15, 30, 0, 0, IST).toInstant(), w.lastSameTime());
    }

    @Test
    void sundayBelongsToTheWeekThatStartedMonday() {
        OwnerService.WeekWindow w = OwnerService.WeekWindow.at(ZonedDateTime.of(2026, 11, 8, 23, 0, 0, 0, IST));
        assertEquals(ZonedDateTime.of(2026, 11, 2, 0, 0, 0, 0, IST).toInstant(), w.thisStart());
    }
}
